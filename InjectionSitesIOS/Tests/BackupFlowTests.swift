import XCTest
@testable import InSofina

@MainActor final class BackupFlowTests: XCTestCase {
    let secret = "Synthetic-test-backup"

    func testExportCompletesClearsBusyAndRetainsReadableDocument() async throws {
        let model = BackupFlowModel(operation: .export)
        model.password = secret; model.confirmation = secret
        await model.prepare(state: AppState())
        XCTAssertFalse(model.busy)
        XCTAssertFalse(model.messageIsError)
        let document = try XCTUnwrap(model.document)
        XCTAssertEqual(try BackupCodec.read(document.data, password: secret).state, AppState())
        XCTAssertTrue(model.password.isEmpty); XCTAssertTrue(model.confirmation.isEmpty)
        model.report("Esportazione annullata", error: true)
        XCTAssertEqual(model.document?.data, document.data, "Il selettore può essere riaperto senza perdere il file")
    }

    func testExportFailureStopsSpinnerAndAllowsRetry() async throws {
        let model = BackupFlowModel(operation: .export)
        model.password = secret; model.confirmation = secret
        var invalid = AppState(); invalid.settings.redHours = -1
        await model.prepare(state: invalid)
        XCTAssertFalse(model.busy); XCTAssertTrue(model.messageIsError); XCTAssertNil(model.document)
        await model.prepare(state: AppState())
        XCTAssertFalse(model.busy); XCTAssertFalse(model.messageIsError); XCTAssertNotNil(model.document)
    }

    func testImportWrongPasswordCanRetryThenPreviewWithoutChangingStore() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("test.insofia-backup")
        let state = AppState(avatar: .DONNA)
        try BackupCodec.write(state, password: secret).write(to: url)
        let store = StateStore(file: directory.appendingPathComponent("state.json"))
        let model = BackupFlowModel(operation: .importData)
        await model.load(url)
        XCTAssertFalse(model.busy); XCTAssertEqual(model.filename, url.lastPathComponent)
        model.password = "incorrect-password"
        await model.prepare(state: store.state)
        XCTAssertFalse(model.busy); XCTAssertTrue(model.messageIsError); XCTAssertNil(model.pending)
        model.password = secret
        await model.prepare(state: store.state)
        XCTAssertFalse(model.busy); XCTAssertFalse(model.messageIsError)
        XCTAssertEqual(model.pending?.state, state)
        XCTAssertEqual(store.state, AppState(), "L’anteprima non deve importare automaticamente")
        XCTAssertTrue(model.apply(to: store, replace: false))
        XCTAssertEqual(store.state, state); XCTAssertFalse(model.busy)
        XCTAssertNil(model.pending); XCTAssertTrue(model.retainedPassword.isEmpty)
    }

    func testMissingImportFileStopsSpinnerAndKeepsPasswordFormAvailable() async {
        let model = BackupFlowModel(operation: .importData)
        model.password = secret
        await model.prepare(state: AppState())
        XCTAssertFalse(model.busy); XCTAssertTrue(model.messageIsError); XCTAssertNil(model.pending)
        await model.load(FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
        XCTAssertFalse(model.busy); XCTAssertTrue(model.messageIsError); XCTAssertNil(model.filename)
    }

    func testApplicationSupportsOnlyPortrait() {
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey: "UISupportedInterfaceOrientations") as? [String], ["UIInterfaceOrientationPortrait"])
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey: "UISupportedInterfaceOrientations~ipad") as? [String], ["UIInterfaceOrientationPortrait"])
        XCTAssertEqual(Bundle.main.object(forInfoDictionaryKey: "UIRequiresFullScreen") as? Bool, true)
    }
}
