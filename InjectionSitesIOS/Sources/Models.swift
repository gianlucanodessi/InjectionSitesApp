import Foundation
import CryptoKit

enum AvatarStyle: String, Codable, CaseIterable, Identifiable {
    case UOMO, DONNA, YETI
    var id: String { rawValue }
    var label: String { switch self { case .UOMO: return "Uomo"; case .DONNA: return "Donna"; case .YETI: return "Yeti" } }
    var assetPrefix: String { switch self { case .UOMO: return "avatar_man"; case .DONNA: return "avatar_woman"; case .YETI: return "avatar_yeti" } }
    func asset(back: Bool) -> String { assetPrefix + (back ? "_back" : "_front") }
}

enum BodyArea: String, Codable, CaseIterable, Identifiable {
    case RIGHT_ARM, LEFT_ARM, ABDOMEN, RIGHT_THIGH, LEFT_THIGH, RIGHT_GLUTE, LEFT_GLUTE
    var id: String { rawValue }
    var label: String {
        switch self {
        case .RIGHT_ARM: return "Braccio destro"
        case .LEFT_ARM: return "Braccio sinistro"
        case .ABDOMEN: return "Addome"
        case .RIGHT_THIGH: return "Coscia destra"
        case .LEFT_THIGH: return "Coscia sinistra"
        case .RIGHT_GLUTE: return "Gluteo destro"
        case .LEFT_GLUTE: return "Gluteo sinistro"
        }
    }
    var isBack: Bool { self == .LEFT_GLUTE || self == .RIGHT_GLUTE }
    var zoneCount: Int { self == .ABDOMEN ? 8 : (isBack ? 2 : 4) }
    func zoneName(_ index: Int) -> String {
        if isBack { return index == 0 ? "Superiore" : "Inferiore" }
        if self == .ABDOMEN {
            return (index < 4 ? "Superiore" : "Inferiore") + (index % 4 < 2 ? " sinistra" : " destra") + ([0,3].contains(index % 4) ? " esterna" : " interna")
        }
        return (index < 2 ? "Superiore" : "Inferiore") + (index % 2 == 0 ? " interna" : " esterna")
    }
}
enum EntryMode: String, Codable, CaseIterable { case INSULINA, SENSORE
    var label: String { self == .INSULINA ? "Insulina" : "Sensore" }
}
enum InsulinType: String, Codable, CaseIterable { case RAPIDA, BASALE
    var label: String { self == .RAPIDA ? "Rapida" : "Basale" }
}
struct TimingSettings: Codable, Equatable {
    var redHours: Float = 12
    var orangeHours: Float = 24
    var yellowHours: Float = 36
    var sensorStageDays: Int64 = 10
    var sensorHiddenDays: Int64 = 40
    var valid: Bool {
        redHours.isFinite && orangeHours.isFinite && yellowHours.isFinite && redHours > 0 && redHours < orangeHours && orangeHours < yellowHours && sensorStageDays > 0 && sensorStageDays <= Int64.max / 3 && sensorStageDays * 3 < sensorHiddenDays
    }
}
struct RecordItem: Codable, Equatable, Identifiable {
    var area: BodyArea
    var zone: Int
    var mode: EntryMode
    var insulinType: InsulinType?
    var eventDateTime: Int64
    var createdAt: Int64 = Date().milliseconds
    var id: String = UUID().uuidString.lowercased()
    enum CodingKeys: String, CodingKey { case id, area, zone, mode, insulinType, time, eventDateTime, createdAt }
    init(area: BodyArea, zone: Int, mode: EntryMode, insulinType: InsulinType? = nil, eventDateTime: Int64, createdAt: Int64 = Date().milliseconds, id: String = UUID().uuidString.lowercased()) {
        self.area = area; self.zone = zone; self.mode = mode; self.insulinType = insulinType
        self.eventDateTime = eventDateTime; self.createdAt = createdAt; self.id = id
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        area = try c.decode(BodyArea.self, forKey: .area); zone = try c.decode(Int.self, forKey: .zone)
        mode = try c.decode(EntryMode.self, forKey: .mode)
        let insulin = try c.decodeIfPresent(String.self, forKey: .insulinType)
        if let insulin, !insulin.isEmpty, insulin != "null" {
            guard let value = InsulinType(rawValue: insulin) else { throw AppError.invalidData }
            insulinType = value
        }
        eventDateTime = try c.decodeIfPresent(Int64.self, forKey: .eventDateTime) ?? c.decode(Int64.self, forKey: .time)
        createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? eventDateTime
        let incomingID = try c.decodeIfPresent(String.self, forKey: .id)
        if let incomingID, !incomingID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { id = incomingID }
        else {
            // Java UUID.nameUUIDFromBytes: MD5 UUID v3, no namespace prefix.
            let seed = "\(area.rawValue)|\(zone)|\(mode.rawValue)|\(insulinType?.rawValue ?? "null")|\(eventDateTime)|\(createdAt)"
            var bytes = Array(Insecure.MD5.hash(data: Data(seed.utf8)))
            bytes[6] = (bytes[6] & 0x0f) | 0x30; bytes[8] = (bytes[8] & 0x3f) | 0x80
            let hex = bytes.map { String(format: "%02x", $0) }.joined()
            id = [0..<8,8..<12,12..<16,16..<20,20..<32].map { String(Array(hex)[$0]) }.joined(separator: "-")
        }
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id); try c.encode(area, forKey: .area); try c.encode(zone, forKey: .zone)
        try c.encode(mode, forKey: .mode); try c.encodeIfPresent(insulinType, forKey: .insulinType)
        try c.encode(eventDateTime, forKey: .time); try c.encode(createdAt, forKey: .createdAt)
    }
}
struct AppState: Codable, Equatable {
    var schemaVersion = 1
    var records: [RecordItem] = []
    var settings = TimingSettings()
    var avatar = AvatarStyle.YETI
    func validated() throws -> AppState {
        guard schemaVersion == 1, settings.valid, records.count <= 100_000 else { throw AppError.invalidData }
        var ids = Set<String>()
        for r in records {
            guard !r.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, r.id.utf16.count <= 200, (0..<r.area.zoneCount).contains(r.zone), ids.insert(r.id).inserted else { throw AppError.invalidData }
        }
        var result = self; result.records = ordered(records); return result
    }
}
enum AppError: LocalizedError {
    case invalidData, backup(String), persistence
    var errorDescription: String? {
        switch self {
        case .invalidData: return "I dati contengono una versione, impostazioni, zone o identificativi non validi."
        case .backup(let message): return message
        case .persistence: return "Impossibile salvare i dati sul dispositivo. I dati precedenti sono stati conservati."
        }
    }
}
extension Date {
    var milliseconds: Int64 { Int64((timeIntervalSince1970 * 1000).rounded(.down)) }
    init(milliseconds: Int64) { self.init(timeIntervalSince1970: Double(milliseconds) / 1000) }
}
func ordered(_ records: [RecordItem]) -> [RecordItem] {
    records.sorted { $0.eventDateTime == $1.eventDateTime ? $0.createdAt > $1.createdAt : $0.eventDateTime > $1.eventDateTime }
}
func activeSensor(_ records: [RecordItem]) -> RecordItem? { ordered(records.filter { $0.mode == .SENSORE }).first }
func sensorStage(_ event: Int64, now: Int64, settings: TimingSettings) -> Int? {
    let days = max(0, (Double(now) - Double(event)) / 86_400_000).rounded(.down)
    for stage in 0..<3 where days < Double(settings.sensorStageDays) * Double(stage + 1) { return stage }
    return days < Double(settings.sensorHiddenDays) ? 3 : nil
}
func availability(_ event: Int64?, now: Int64, settings: TimingSettings) -> Int {
    guard let event else { return 3 }
    let hours = max(0, (Double(now) - Double(event)) / 3_600_000)
    return hours < Double(settings.redHours) ? 0 : hours < Double(settings.orangeHours) ? 1 : hours < Double(settings.yellowHours) ? 2 : 3
}
