import Foundation
import Combine

@MainActor final class StateStore: ObservableObject {
    @Published private(set) var state: AppState
    @Published var error: String?
    private(set) var loadFailed = false
    let file: URL
    private let writer: (Data, URL) throws -> Void

    init(file: URL, writer: @escaping (Data, URL) throws -> Void = { try $0.write(to: $1, options: [.atomic, .completeFileProtection]) }) {
        self.file = file; self.writer = writer; state = AppState()
        do {
            var directory = file.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var values = URLResourceValues(); values.isExcludedFromBackup = true
            try directory.setResourceValues(values)
            if FileManager.default.fileExists(atPath: file.path) { state = try JSONDecoder().decode(AppState.self, from: Data(contentsOf: file)).validated() }
        } catch {
            loadFailed = true
            self.error = "Impossibile leggere lo storico locale. Il file è stato conservato: riapri l’app dopo aver verificato lo spazio disponibile e lo sblocco del dispositivo."
        }
    }
    static func live() -> StateStore {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return StateStore(file: base.appendingPathComponent("InSofina/state.json"))
    }
    func commit(_ candidate: AppState) throws {
        guard !loadFailed else { throw AppError.persistence }
        let validated = try candidate.validated()
        do { try writer(JSONEncoder().encode(validated), file) } catch { throw AppError.persistence }
        state = validated
    }
    func add(_ record: RecordItem) throws { var next = state; next.records.append(record); try commit(next) }
    func delete(_ record: RecordItem) throws { var next = state; next.records.removeAll { $0.id == record.id }; try commit(next) }
    func save(settings: TimingSettings, avatar: AvatarStyle) throws { var next = state; next.settings = settings; next.avatar = avatar; try commit(next) }
    @discardableResult func apply(_ incoming: AppState, replace: Bool, password: String) throws -> Int {
        let incoming = try incoming.validated()
        var next = incoming
        let added: Int
        if replace {
            let safety = try BackupCodec.write(state, password: password)
            let url = file.deletingLastPathComponent().appendingPathComponent("pre-import-\(UUID().uuidString).insofia-backup")
            do { try writer(safety, url) } catch { throw AppError.persistence }
            added = incoming.records.count
        } else {
            var ids = Set(state.records.map(\.id))
            let records = incoming.records.filter { ids.insert($0.id).inserted }
            next.records = state.records + records; added = records.count
        }
        try commit(next)
        return added
    }
}
