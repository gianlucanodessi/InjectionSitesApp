package com.example.injectionsites

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal data class AppBackupState(val records: List<RecordItem>, val settings: TimingSettings, val avatar: AvatarStyle)
internal data class DecodedBackup(val state: AppBackupState, val exportedAt: Long, val schemaVersion: Int, val appVersion: String)
internal data class ImportOutcome(val state: AppBackupState, val importedCount: Int)
internal class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal object BackupManager {
    private const val FORMAT = "insofia-encrypted-backup"
    private const val CURRENT_SCHEMA = 1
    private const val KDF_ITERATIONS = 210_000
    private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
    private val random = SecureRandom()

    fun write(context: Context, output: OutputStream, state: AppBackupState, password: CharArray, exportedAt: Long = System.currentTimeMillis()) {
        require(password.size >= 8) { "La password del backup deve contenere almeno 8 caratteri." }
        val payloadJson = payloadJson(state).toString()
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        val plain = JSONObject()
            .put("schemaVersion", CURRENT_SCHEMA)
            .put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "sconosciuta")
            .put("exportedAt", exportedAt)
            .put("payloadJson", payloadJson)
            .put("payloadSha256", encode(sha256(payloadBytes)))
            .toString().toByteArray(StandardCharsets.UTF_8)
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        val encrypted = cipher.doFinal(plain)
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", CURRENT_SCHEMA)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", KDF_ITERATIONS)
            .put("salt", encode(salt))
            .put("cipher", "AES-256-GCM")
            .put("iv", encode(iv))
            .put("ciphertext", encode(encrypted))
        output.bufferedWriter(StandardCharsets.UTF_8).use { it.write(envelope.toString()) }
    }

    fun read(input: InputStream, password: CharArray): DecodedBackup {
        try {
            val envelope = JSONObject(String(readLimited(input), StandardCharsets.UTF_8))
            if (envelope.optString("format") != FORMAT) throw BackupException("Il file selezionato non è un backup InSofina valido.")
            val outerSchema = envelope.optInt("schemaVersion", -1)
            checkSchema(outerSchema)
            if (envelope.optString("kdf") != "PBKDF2WithHmacSHA256" || envelope.optString("cipher") != "AES-256-GCM") throw BackupException("Il metodo di protezione del backup non è supportato.")
            val iterations = envelope.optInt("iterations", 0)
            if (iterations < 100_000 || iterations > 2_000_000) throw BackupException("I parametri di sicurezza del backup non sono validi.")
            val salt = decode(envelope.getString("salt")); val iv = decode(envelope.getString("iv")); val encrypted = decode(envelope.getString("ciphertext"))
            if (salt.size < 16 || iv.size != 12 || encrypted.size < 16) throw BackupException("Il backup è corrotto o incompleto.")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv)) }
            val plain = JSONObject(String(cipher.doFinal(encrypted), StandardCharsets.UTF_8))
            val schema = plain.optInt("schemaVersion", -1); checkSchema(schema)
            if (schema != outerSchema) throw BackupException("Le informazioni di versione del backup non sono coerenti.")
            val exportedAt = plain.optLong("exportedAt", -1L)
            if (exportedAt <= 0L) throw BackupException("La data del backup non è valida.")
            val payloadJson = plain.getString("payloadJson"); val expectedHash = decode(plain.getString("payloadSha256"))
            if (!MessageDigest.isEqual(expectedHash, sha256(payloadJson.toByteArray(StandardCharsets.UTF_8)))) throw BackupException("Controllo d'integrità non riuscito: il backup è corrotto.")
            val migrated = migrate(JSONObject(payloadJson), schema)
            return DecodedBackup(parseState(migrated), exportedAt, CURRENT_SCHEMA, plain.optString("appVersion", "sconosciuta"))
        } catch (error: BackupException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw BackupException("Password errata oppure backup corrotto.", error)
        } catch (error: Exception) {
            throw BackupException("Impossibile leggere il backup: file corrotto, incompatibile o password errata.", error)
        }
    }

    fun applyImport(context: Context, current: AppBackupState, decoded: DecodedBackup, replace: Boolean, password: CharArray): ImportOutcome {
        val incoming = decoded.state
        val existingIds = current.records.mapTo(HashSet()) { it.id }
        val importedCount: Int
        val targetRecords = if (replace) {
            createSafetyBackup(context, current, password)
            importedCount = incoming.records.size
            incoming.records
        } else {
            val added = incoming.records.filter { existingIds.add(it.id) }
            importedCount = added.size
            current.records + added
        }
        val target = AppBackupState(targetRecords.sortedByDescending { it.time }, incoming.settings, incoming.avatar)
        if (!persistAtomically(context, target)) throw BackupException("Impossibile salvare i dati importati. I dati locali non sono stati sostituiti.")
        return ImportOutcome(target, importedCount)
    }

    private fun createSafetyBackup(context: Context, state: AppBackupState, password: CharArray) {
        val directory = File(context.filesDir, "safety-backups")
        if (!directory.exists() && !directory.mkdirs()) throw BackupException("Impossibile creare il backup di sicurezza; sostituzione annullata.")
        val formatter = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val destination = File(directory, "InSofina-pre-import-${formatter.format(Date())}.insofia-backup")
        val temporary = File.createTempFile("pre-import-", ".tmp", directory)
        try {
            temporary.outputStream().use { write(context, it, state, password) }
            if (!temporary.renameTo(destination)) throw BackupException("Impossibile finalizzare il backup di sicurezza; sostituzione annullata.")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun persistAtomically(context: Context, state: AppBackupState): Boolean {
        val history = JSONArray().apply { state.records.forEach { put(it.toJson()) } }
        return context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit()
            .putString(HISTORY_KEY, history.toString())
            .remove(LEGACY_SENSOR_KEY)
            .putFloat("red", state.settings.redHours)
            .putFloat("orange", state.settings.orangeHours)
            .putFloat("yellow", state.settings.yellowHours)
            .putLong("sensorStage", state.settings.sensorStageDays)
            .putLong("sensorHidden", state.settings.sensorHiddenDays)
            .putString(AVATAR_KEY, state.avatar.name)
            .commit()
    }

    private fun payloadJson(state: AppBackupState) = JSONObject()
        .put("schemaVersion", CURRENT_SCHEMA)
        .put("records", JSONArray().apply { state.records.forEach { put(it.toJson()) } })
        .put("settings", JSONObject()
            .put("redHours", state.settings.redHours)
            .put("orangeHours", state.settings.orangeHours)
            .put("yellowHours", state.settings.yellowHours)
            .put("sensorStageDays", state.settings.sensorStageDays)
            .put("sensorHiddenDays", state.settings.sensorHiddenDays))
        .put("avatar", state.avatar.name)

    private fun parseState(payload: JSONObject): AppBackupState {
        val recordsJson = payload.getJSONArray("records")
        if (recordsJson.length() > 100_000) throw BackupException("Il backup contiene troppi elementi.")
        val records = ArrayList<RecordItem>(recordsJson.length()); val ids = HashSet<String>()
        repeat(recordsJson.length()) { index ->
            val record = recordFromJson(recordsJson.getJSONObject(index)) ?: throw BackupException("Elemento dello storico non valido alla posizione ${index + 1}.")
            if (record.id.isBlank() || record.id.length > 200 || record.zone !in record.area.zones.indices || !ids.add(record.id)) throw BackupException("Il backup contiene identificativi o zone non validi.")
            records += record
        }
        val settingsJson = payload.getJSONObject("settings")
        val settings = TimingSettings(settingsJson.getDouble("redHours").toFloat(), settingsJson.getDouble("orangeHours").toFloat(), settingsJson.getDouble("yellowHours").toFloat(), settingsJson.getLong("sensorStageDays"), settingsJson.getLong("sensorHiddenDays"))
        if (!settings.valid()) throw BackupException("Il backup contiene impostazioni non valide.")
        val avatar = runCatching { AvatarStyle.valueOf(payload.getString("avatar")) }.getOrElse { throw BackupException("Il backup contiene una selezione avatar non valida.") }
        return AppBackupState(records.sortedByDescending { it.time }, settings, avatar)
    }

    private fun migrate(payload: JSONObject, sourceSchema: Int): JSONObject {
        return when (sourceSchema) {
            CURRENT_SCHEMA -> payload
            else -> throw BackupException("Non esiste una migrazione disponibile per lo schema $sourceSchema.")
        }
    }

    private fun checkSchema(schema: Int) {
        if (schema < 1) throw BackupException("Versione del backup non valida.")
        if (schema > CURRENT_SCHEMA) throw BackupException("Backup creato con una versione più recente dell'app. Aggiorna InSofina prima di importarlo.")
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = KDF_ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(encoded, "AES") } finally { encoded.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_BACKUP_BYTES) throw BackupException("Il file di backup è troppo grande.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
    private fun encode(data: ByteArray) = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
}
