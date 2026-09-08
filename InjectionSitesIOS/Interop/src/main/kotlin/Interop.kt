package com.example.injectionsites

import android.content.Context
import java.io.File

// Public synthetic fixture password; never use for real user data.
private const val FIXTURE_PASSWORD = "Test-InSofina-à🔐"
fun main(args: Array<String>) {
    val file = File(args[1])
    if (args[0] == "generate") {
        val state = AppBackupState(listOf(
            RecordItem(BodyArea.LEFT_ARM, 0, EntryMode.SENSORE, null, 1_900_000_000_000L, 1_900_000_010_000L, "android-sensor-latest"),
            RecordItem(BodyArea.RIGHT_THIGH, 3, EntryMode.INSULINA, InsulinType.BASALE, 1_800_000_000_000L, 1_950_000_000_000L, "android-insulin-old"),
            RecordItem(BodyArea.ABDOMEN, 6, EntryMode.INSULINA, InsulinType.RAPIDA, 1_850_000_000_000L, 1_850_000_000_100L, "android-abdomen")
        ), TimingSettings(), AvatarStyle.DONNA)
        file.parentFile.mkdirs()
        file.outputStream().use { BackupManager.write(Context(), it, state, FIXTURE_PASSWORD.toCharArray(), 1_950_000_000_000L) }
        val decoded = file.inputStream().use { BackupManager.read(it, FIXTURE_PASSWORD.toCharArray()) }
        check(decoded.state.records.map { it.id }.toSet() == state.records.map { it.id }.toSet())
        println("Android BackupManager fixture generated and read successfully: ${state.records.size} records")
    } else {
        val decoded = file.inputStream().use { BackupManager.read(it, FIXTURE_PASSWORD.toCharArray()) }
        check(decoded.schemaVersion == 1)
        check(decoded.state.avatar == AvatarStyle.DONNA)
        check(decoded.state.records.map { it.id }.toSet() == setOf("android-sensor-latest", "android-insulin-old", "android-abdomen"))
        check(decoded.state.records.single { it.id == "android-insulin-old" }.eventDateTime == 1_800_000_000_000L)
        check(decoded.state.records.single { it.id == "android-insulin-old" }.createdAt == 1_950_000_000_000L)
        check(decoded.state.settings == TimingSettings())
        println("Swift export accepted by unchanged Android BackupManager; IDs, timestamps, avatar and settings verified")
    }
}
