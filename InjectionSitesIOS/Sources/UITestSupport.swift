#if DEBUG
import Foundation

@MainActor enum UITestSupport {
    static var observesExportRequestsOnly: Bool {
        ProcessInfo.processInfo.arguments.contains("-insofinaUITestExportRequestsOnly")
    }

    static func historyStore() -> StateStore? {
        guard ProcessInfo.processInfo.arguments.contains("-insofinaUITestHistory") else { return nil }
        // Isolated, in-memory fixture. Never read or write the real history container.
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("history-ui-\(UUID().uuidString)/state.json")
        let store = StateStore(file: file, writer: { _, _ in })
        let state = AppState(records: [
            RecordItem(area: .RIGHT_ARM, zone: 0, mode: .INSULINA, insulinType: .RAPIDA, eventDateTime: 1_788_868_200_000, createdAt: 1_788_868_200_000, id: "ui-history-rapid"),
            RecordItem(area: .LEFT_THIGH, zone: 1, mode: .INSULINA, insulinType: .BASALE, eventDateTime: 1_788_864_600_000, createdAt: 1_788_864_600_000, id: "ui-history-basal"),
            RecordItem(area: .ABDOMEN, zone: 6, mode: .SENSORE, eventDateTime: 1_788_861_000_000, createdAt: 1_788_861_000_000, id: "ui-history-sensor")
        ])
        do { try store.commit(state) }
        catch { preconditionFailure("Impossibile preparare lo storico isolato per il test UI") }
        return store
    }
}
#endif
