import Foundation
import CryptoKit
import CommonCrypto
import Security

struct DecodedBackup {
    let state: AppState
    let exportedAt: Int64
    let schemaVersion: Int
    let appVersion: String
}
enum BackupCodec {
    static let maximumBytes = 32 * 1024 * 1024
    private struct Envelope: Codable {
        var format = "insofia-encrypted-backup"
        var schemaVersion = 1
        var kdf = "PBKDF2WithHmacSHA256"
        var iterations = 210_000
        var salt: Data
        var cipher = "AES-256-GCM"
        var iv: Data
        var ciphertext: Data
    }
    private struct Plain: Codable {
        var schemaVersion = 1
        var appVersion: String
        var exportedAt: Int64
        var payloadJson: String
        var payloadSha256: Data
    }
    private static func random(_ count: Int) throws -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        guard SecRandomCopyBytes(kSecRandomDefault, count, &bytes) == errSecSuccess else { throw AppError.backup("Impossibile generare la protezione del backup.") }
        return Data(bytes)
    }
    static func key(password: String, salt: Data, iterations: Int) throws -> SymmetricKey {
        var passwordBytes = Array(password.utf8)
        var derived = [UInt8](repeating: 0, count: 32)
        defer { passwordBytes.withUnsafeMutableBytes { $0.initializeMemory(as: UInt8.self, repeating: 0) }; derived.withUnsafeMutableBytes { $0.initializeMemory(as: UInt8.self, repeating: 0) } }
        let status = passwordBytes.withUnsafeBytes { p in
            salt.withUnsafeBytes { s in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), p.bindMemory(to: Int8.self).baseAddress, p.count, s.bindMemory(to: UInt8.self).baseAddress, s.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), UInt32(iterations), &derived, 32)
            }
        }
        guard status == kCCSuccess else { throw AppError.backup("Impossibile proteggere il backup.") }
        return SymmetricKey(data: derived)
    }
    static func write(_ state: AppState, password: String, exportedAt: Int64 = Date().milliseconds) throws -> Data {
        guard password.utf16.count >= 8 else { throw AppError.backup("La password del backup deve contenere almeno 8 caratteri.") }
        let payload = try JSONEncoder().encode(state.validated())
        let plain = Plain(appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "2.0", exportedAt: exportedAt, payloadJson: String(decoding: payload, as: UTF8.self), payloadSha256: Data(SHA256.hash(data: payload)))
        let salt = try random(16), iv = try random(12)
        let sealed = try AES.GCM.seal(JSONEncoder().encode(plain), using: key(password: password, salt: salt, iterations: 210_000), nonce: AES.GCM.Nonce(data: iv))
        // JCA doFinal returns ciphertext || 16-byte tag; IV is a separate field.
        let data = try JSONEncoder().encode(Envelope(salt: salt, iv: iv, ciphertext: sealed.ciphertext + sealed.tag))
        guard data.count <= maximumBytes else { throw AppError.backup("Il file di backup è troppo grande.") }
        return data
    }
    static func read(_ data: Data, password: String) throws -> DecodedBackup {
        do {
            guard data.count <= maximumBytes else { throw AppError.backup("Il file di backup è troppo grande.") }
            let envelope = try JSONDecoder().decode(Envelope.self, from: data)
            guard envelope.format == "insofia-encrypted-backup", envelope.schemaVersion == 1,
                  envelope.kdf == "PBKDF2WithHmacSHA256", envelope.cipher == "AES-256-GCM",
                  (100_000...2_000_000).contains(envelope.iterations), envelope.salt.count >= 16,
                  envelope.iv.count == 12, envelope.ciphertext.count >= 16 else { throw AppError.backup("Formato, versione o parametri del backup non supportati.") }
            let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: envelope.iv), ciphertext: envelope.ciphertext.dropLast(16), tag: envelope.ciphertext.suffix(16))
            let bytes = try AES.GCM.open(box, using: key(password: password, salt: envelope.salt, iterations: envelope.iterations))
            let plain = try JSONDecoder().decode(Plain.self, from: bytes)
            guard plain.schemaVersion == envelope.schemaVersion, plain.exportedAt > 0,
                  Data(SHA256.hash(data: Data(plain.payloadJson.utf8))) == plain.payloadSha256 else { throw AppError.backup("Controllo d’integrità del backup non riuscito.") }
            let state = try JSONDecoder().decode(AppState.self, from: Data(plain.payloadJson.utf8)).validated()
            return DecodedBackup(state: state, exportedAt: plain.exportedAt, schemaVersion: plain.schemaVersion, appVersion: plain.appVersion)
        } catch let error as AppError { throw error }
        catch { throw AppError.backup("Password errata oppure backup corrotto o incompatibile.") }
    }
    static func readFile(_ url: URL) throws -> Data {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var data = Data()
        while let chunk = try handle.read(upToCount: 8192), !chunk.isEmpty {
            data.append(chunk)
            guard data.count <= maximumBytes else { throw AppError.backup("Il file di backup è troppo grande.") }
        }
        return data
    }
}
