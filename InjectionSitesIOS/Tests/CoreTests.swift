import XCTest
import SwiftUI
import CryptoKit
@testable import InSofina

final class CoreTests: XCTestCase {
    let now: Int64 = 2_000_000_000_000
    let password = "Test-InSofina-à🔐"
    func record(_ id: String = "test", event: Int64 = 1_900_000_000_000, created: Int64 = 1_950_000_000_000) -> RecordItem {
        RecordItem(area: .LEFT_ARM, zone: 0, mode: .SENSORE, eventDateTime: event, createdAt: created, id: id)
    }
    func fixture() -> AppState { AppState(records: [record()], avatar: .DONNA) }

    func testMirrorMappingAcrossAll21Details() {
        for avatar in AvatarStyle.allCases {
            for area in BodyArea.allCases {
                XCTAssertNotNil(UIImage(named: avatar.asset(back: area.isBack)))
                XCTAssertEqual(Anatomy.outline(avatar: avatar, area: area, detail: true).isEmpty, false)
                if area != .ABDOMEN {
                    let left = area.rawValue.hasPrefix("LEFT")
                    let outline = Anatomy.outline(avatar: avatar, area: area, detail: false)
                    XCTAssertTrue(outline.allSatisfy { left ? $0.x <= 0.5 : $0.x >= 0.5 })
                    let size = UIImage(named: avatar.asset(back: area.isBack))!.size
                    let focus = Anatomy.viewport(avatar: avatar, area: area, imageSize: size)
                    XCTAssertEqual(focus.midX < size.width / 2, left)
                    XCTAssertTrue(area.label.contains(left ? "sinistro" : "destro") || area.label.contains(left ? "sinistra" : "destra"))
                }
            }
            for zone in 0..<8 { XCTAssertTrue(BodyArea.ABDOMEN.zoneName(zone).contains(zone % 4 < 2 ? "sinistra" : "destra")) }
        }
    }
    func testAspectPreservingTransformAndInverse() {
        for source in [CGSize(width: 901, height: 1746), CGSize(width: 1024, height: 1536), CGSize(width: 300, height: 310)] {
            for canvas in [CGSize(width: 320, height: 650), CGSize(width: 1024, height: 768), CGSize(width: 450, height: 900)] {
                let t = ImageTransform(source: CGRect(origin: .zero, size: source), canvas: canvas)
                XCTAssertEqual(t.destination.width / t.destination.height, source.width / source.height, accuracy: 0.000001)
                XCTAssertLessThanOrEqual(t.destination.width, canvas.width)
                XCTAssertLessThanOrEqual(t.destination.height, canvas.height)
                for point in [CGPoint.zero, CGPoint(x: source.width/3, y: source.height/4), CGPoint(x: source.width, y: source.height)] {
                    let inverse = t.inverse(t.point(point))
                    XCTAssertEqual(inverse.x, point.x, accuracy: 0.00001); XCTAssertEqual(inverse.y, point.y, accuracy: 0.00001)
                }
            }
        }
    }
    func testAllDrawnPathsShareHitTestAndExcludeOutsideOutline() {
        for avatar in AvatarStyle.allCases {
            for area in BodyArea.allCases {
                for detail in [false, true] {
                    let image = UIImage(named: avatar.asset(back: area.isBack))!
                    let viewport = Anatomy.viewport(avatar: avatar, area: detail ? area : nil, imageSize: image.size)
                    let transform = ImageTransform(source: viewport, canvas: CGSize(width: 390, height: 740))
                    let zones = Anatomy.zones(avatar: avatar, area: area, detail: detail, imageSize: image.size, transform: transform)
                    let alpha = ImageAlphaMask(asset: avatar.asset(back: area.isBack), size: image.size)
                    XCTAssertEqual(Set(zones.map(\.index)), Set(0..<area.zoneCount))
                    let outlinePoints = Anatomy.outline(avatar: avatar, area: area, detail: false).map { transform.point(CGPoint(x: $0.x * image.size.width, y: $0.y * image.size.height)) }
                    let outline = AnatomicalZone(area: area, index: 0, points: outlinePoints)
                    for zone in zones {
                        XCTAssertGreaterThan(zone.path.boundingRect.width, 0)
                        XCTAssertGreaterThan(zone.path.boundingRect.height, 0)
                        XCTAssertTrue(zone.contains(zone.center))
                    }
                    for x in stride(from: CGFloat(1.37), to: 390, by: 9.17) {
                        for y in stride(from: CGFloat(2.61), to: 740, by: 11.31) {
                            let p = CGPoint(x: x, y: y)
                            let hits = zones.filter { $0.contains(p) }
                            XCTAssertEqual(!hits.isEmpty, outline.path.contains(p))
                            XCTAssertLessThanOrEqual(hits.count, 1)
                            let painted = !hits.isEmpty && alpha.contains(transform.inverse(p))
                            XCTAssertEqual(Anatomy.hitTest(p, zones: zones, transform: transform, alpha: alpha) != nil, painted)
                        }
                    }
                    XCTAssertFalse(zones.contains { $0.contains(.zero) })
                }
            }
        }
    }
    func testDashboardAndDetailUseIdenticalApprovedColumnNumbering() {
        let t = ImageTransform(source: CGRect(x: 0, y: 0, width: 300, height: 310), canvas: CGSize(width: 300, height: 310))
        for avatar in AvatarStyle.allCases {
            for area in [BodyArea.LEFT_ARM, .RIGHT_ARM, .LEFT_THIGH, .RIGHT_THIGH] {
                for detail in [false, true] {
                    let zones = Anatomy.zones(avatar: avatar, area: area, detail: detail, imageSize: CGSize(width: 300, height: 310), transform: t)
                    let reversed = area == .LEFT_ARM
                    XCTAssertEqual(zones[0].center.x > zones[1].center.x, reversed)
                }
            }
        }
    }
    func testBackdatedSensorDoesNotBecomeCurrentAndCreatedAtOnlyBreaksTies() {
        let recent = record("recent", event: now-86_400_000, created: 1)
        let old = record("old", event: now-12*86_400_000, created: 999)
        let tied = record("tie", event: recent.eventDateTime, created: 2)
        XCTAssertEqual(activeSensor([old, recent])?.id, "recent")
        XCTAssertEqual(ordered([old, recent, tied]).map(\.id), ["tie", "recent", "old"])
        XCTAssertEqual(sensorStage(old.eventDateTime, now: now, settings: TimingSettings()), 1)
        XCTAssertEqual(sensorStage(recent.eventDateTime, now: now, settings: TimingSettings()), 0)
    }
    func testSensorThresholdsOpacityStagesAndInjectionBoundaries() {
        let settings = TimingSettings()
        for (days, expected) in [(0,0),(9,0),(10,1),(19,1),(20,2),(29,2),(30,3),(39,3)] {
            XCTAssertEqual(sensorStage(now-Int64(days)*86_400_000, now: now, settings: settings), expected)
        }
        XCTAssertNil(sensorStage(now-40*86_400_000, now: now, settings: settings))
        XCTAssertEqual(sensorStage(now+1000, now: now, settings: settings), 0)
        for (hours, expected) in [(0,0),(11,0),(12,1),(23,1),(24,2),(35,2),(36,3)] {
            XCTAssertEqual(availability(now-Int64(hours)*3_600_000, now: now, settings: settings), expected)
        }
        XCTAssertEqual(availability(nil, now: now, settings: settings), 3)
        var state = fixture()
        state.records = (0..<5).map { record("\($0)", event: now-Int64($0)*86_400_000) }
        XCTAssertEqual(Visuals.markers(state: state, area: .LEFT_ARM, zone: 0, now: now).map { $0.0.id }, ["0","1","2"])
    }
    func testAndroidSerializationAndLegacyPersistentIdentifier() throws {
        let data = try JSONEncoder().encode(record())
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertNil(json["eventDateTime"]); XCTAssertNotNil(json["time"]); XCTAssertNil(json["insulinType"])
        let legacy = Data(#"{"area":"LEFT_ARM","zone":0,"mode":"SENSORE","time":1900000000000}"#.utf8)
        let first = try JSONDecoder().decode(RecordItem.self, from: legacy)
        XCTAssertEqual(first.id, try JSONDecoder().decode(RecordItem.self, from: legacy).id)
        XCTAssertEqual(first.createdAt, first.eventDateTime)
        XCTAssertEqual(first.id, "37eed299-574c-3795-9833-884a3354b9ba")
        let explicit = Data(#"{"id":"explicit","area":"LEFT_ARM","zone":0,"mode":"SENSORE","time":1,"eventDateTime":2,"createdAt":3}"#.utf8)
        XCTAssertEqual(try JSONDecoder().decode(RecordItem.self, from: explicit).eventDateTime, 2)
    }
    func testBackupRoundTripWrongPasswordCorruptionAndMetadata() throws {
        let data = try BackupCodec.write(fixture(), password: password, exportedAt: now)
        let decoded = try BackupCodec.read(data, password: password)
        XCTAssertEqual(decoded.state, fixture()); XCTAssertEqual(decoded.exportedAt, now); XCTAssertEqual(decoded.schemaVersion, 1)
        XCTAssertThrowsError(try BackupCodec.read(data, password: "incorrect"))
        XCTAssertThrowsError(try BackupCodec.read(Data(data.dropLast()), password: password))
        var envelope = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        var encrypted = try XCTUnwrap(Data(base64Encoded: envelope["ciphertext"] as! String)); encrypted[0] ^= 1
        envelope["ciphertext"] = encrypted.base64EncodedString()
        XCTAssertThrowsError(try BackupCodec.read(JSONSerialization.data(withJSONObject: envelope), password: password))
        envelope["iterations"] = 99_999
        XCTAssertThrowsError(try BackupCodec.read(JSONSerialization.data(withJSONObject: envelope), password: password))
        XCTAssertThrowsError(try BackupCodec.write(fixture(), password: "short"))
    }
    func testPayloadSHA256CheckedEvenWithValidGCMTag() throws {
        let original = try BackupCodec.write(fixture(), password: password)
        var envelope = try XCTUnwrap(JSONSerialization.jsonObject(with: original) as? [String: Any])
        let salt = Data(base64Encoded: envelope["salt"] as! String)!, iv = Data(base64Encoded: envelope["iv"] as! String)!
        let encrypted = Data(base64Encoded: envelope["ciphertext"] as! String)!
        let key = try BackupCodec.key(password: password, salt: salt, iterations: 210_000)
        let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: encrypted.dropLast(16), tag: encrypted.suffix(16))
        var plain = try XCTUnwrap(JSONSerialization.jsonObject(with: AES.GCM.open(box, using: key)) as? [String: Any])
        plain["payloadSha256"] = Data(repeating: 0, count: 32).base64EncodedString()
        let resealed = try AES.GCM.seal(JSONSerialization.data(withJSONObject: plain), using: key, nonce: AES.GCM.Nonce(data: iv))
        envelope["ciphertext"] = (resealed.ciphertext + resealed.tag).base64EncodedString()
        XCTAssertThrowsError(try BackupCodec.read(JSONSerialization.data(withJSONObject: envelope), password: password))
    }
    func testOriginalAndroidFixtureAndExportForAndroidReader() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "android", withExtension: "insofia-backup"), "Run scripts/prepare_interop.py and the Interop generate task before XcodeGen; CI does this automatically.")
        let decoded = try BackupCodec.read(Data(contentsOf: url), password: password)
        XCTAssertEqual(decoded.state.avatar, .DONNA)
        XCTAssertEqual(Set(decoded.state.records.map(\.id)), Set(["android-sensor-latest", "android-insulin-old", "android-abdomen"]))
        XCTAssertEqual(decoded.state.records.first?.eventDateTime, 1_900_000_000_000)
        let export = try BackupCodec.write(decoded.state, password: password)
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try export.write(to: directory.appendingPathComponent("ios-export.insofia-backup"), options: .atomic)
    }
}

@MainActor final class PersistenceTests: XCTestCase {
    var directory: URL!
    @MainActor override func setUp() async throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }
    @MainActor override func tearDown() async throws { try FileManager.default.removeItem(at: directory) }
    var file: URL { directory.appendingPathComponent("state.json") }
    let password = "synthetic-test-password"
    func testHistorySettingsAndAvatarSurviveStoreRecreation() throws {
        let store = StateStore(file: file)
        let record = RecordItem(area: .ABDOMEN, zone: 7, mode: .INSULINA, insulinType: .RAPIDA, eventDateTime: 1_700_000_000_000)
        try store.add(record)
        let settings = TimingSettings(redHours: 5, orangeHours: 10, yellowHours: 20, sensorStageDays: 5, sensorHiddenDays: 21)
        try store.save(settings: settings, avatar: .UOMO)
        let reload = StateStore(file: file)
        XCTAssertEqual(reload.state, store.state); XCTAssertEqual(reload.state.records, [record])
        XCTAssertEqual(reload.state.settings, settings); XCTAssertEqual(reload.state.avatar, .UOMO)
    }
    func testExportClearImportMergeAndTransactionalReplacement() throws {
        let store = StateStore(file: file)
        let a = RecordItem(area: .LEFT_GLUTE, zone: 1, mode: .SENSORE, eventDateTime: 1_700_000_000_000, id: "a")
        try store.add(a)
        let backup = try BackupCodec.write(store.state, password: password)
        try store.commit(AppState())
        let decoded = try BackupCodec.read(backup, password: password)
        XCTAssertEqual(try store.apply(decoded.state, replace: false, password: password), 1)
        XCTAssertEqual(try store.apply(decoded.state, replace: false, password: password), 0)
        XCTAssertEqual(store.state.records, [a])
        var incoming = decoded.state; incoming.records[0].id = "b"; incoming.avatar = .DONNA
        XCTAssertEqual(try store.apply(incoming, replace: true, password: password), 1)
        XCTAssertEqual(StateStore(file: file).state, incoming)
        let safety = try XCTUnwrap(FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil).first { $0.pathExtension == "insofia-backup" })
        XCTAssertEqual(try BackupCodec.read(Data(contentsOf: safety), password: password).state.records, [a])
    }
    func testFailedWriteNeverPublishesSettingsOrImportedState() throws {
        let store = StateStore(file: file)
        try store.commit(AppState())
        let originalBytes = try Data(contentsOf: file)
        let failing = StateStore(file: file, writer: { data, url in
            if url.pathExtension == "insofia-backup" { try data.write(to: url, options: .atomic) }
            else { throw AppError.persistence }
        })
        XCTAssertThrowsError(try failing.save(settings: TimingSettings(redHours: 1), avatar: .UOMO))
        var incoming = AppState(); incoming.avatar = .DONNA
        XCTAssertThrowsError(try failing.apply(incoming, replace: true, password: password))
        XCTAssertEqual(failing.state, AppState()); XCTAssertEqual(try Data(contentsOf: file), originalBytes)
    }
    func testInvalidOrUnreadableStateCannotEraseHistory() throws {
        let corrupt = Data("corrupt history".utf8); try corrupt.write(to: file)
        let store = StateStore(file: file)
        XCTAssertTrue(store.loadFailed)
        XCTAssertThrowsError(try store.commit(AppState()))
        XCTAssertEqual(try Data(contentsOf: file), corrupt)
    }
    func testDuplicateIDsAndInvalidSettingsAreRejectedBeforeWriting() throws {
        let store = StateStore(file: file)
        try store.commit(AppState())
        let item = RecordItem(area: .LEFT_ARM, zone: 0, mode: .SENSORE, eventDateTime: 1)
        XCTAssertThrowsError(try store.commit(AppState(records: [item,item])))
        XCTAssertThrowsError(try store.save(settings: TimingSettings(redHours: 24, orangeHours: 12), avatar: .YETI))
        XCTAssertEqual(store.state, AppState())
    }
}
