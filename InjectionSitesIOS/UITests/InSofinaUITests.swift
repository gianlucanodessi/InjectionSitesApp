import XCTest

final class InSofinaUITests: XCTestCase {
    @MainActor func testSettingsStayPortraitAndSafeAfterDeviceRotation() throws {
        let app = XCUIApplication()
        app.launch()
        app.buttons["Impostazioni"].tap()
        let save = app.buttons["saveSettings"]
        XCTAssertTrue(save.waitForExistence(timeout: 5))
        XCTAssertTrue(save.isHittable)
        save.tap()
        XCTAssertTrue(app.staticTexts["Impostazioni salvate"].waitForExistence(timeout: 5))
        XCTAssertEqual(save.label, "Salvato")
        XCTAssertLessThan(save.frame.maxY, app.windows.firstMatch.frame.maxY - 8)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Impostazioni salvate e safe area"; attachment.lifetime = .keepAlways
        add(attachment)
        XCUIDevice.shared.orientation = .landscapeLeft
        let rotatedSave = app.buttons["saveSettings"]
        let readyAfterRotation = NSPredicate { _, _ in
            let window = app.windows.firstMatch
            guard rotatedSave.exists, window.exists else { return false }
            let frame = rotatedSave.frame
            let windowFrame = window.frame
            guard !frame.isNull, !frame.isInfinite, !frame.isEmpty,
                  !windowFrame.isNull, !windowFrame.isInfinite, !windowFrame.isEmpty,
                  [frame.minX, frame.minY, frame.maxX, frame.maxY,
                   windowFrame.minX, windowFrame.minY, windowFrame.maxX, windowFrame.maxY].allSatisfy({ $0.isFinite }),
                  windowFrame.height > windowFrame.width,
                  windowFrame.contains(frame), frame.maxY < windowFrame.maxY - 8 else { return false }
            return rotatedSave.isHittable
        }
        let rotationExpectation = XCTNSPredicateExpectation(predicate: readyAfterRotation, object: rotatedSave)
        let rotationResult = XCTWaiter.wait(for: [rotationExpectation], timeout: 10)
        XCTAssertEqual(rotationResult, .completed, "Il pulsante deve essere visibile e toccabile nella safe area dopo la rotazione")
        guard rotationResult == .completed else { return }
        let rotatedFrame = rotatedSave.frame
        let visibleWindow = app.windows.firstMatch.frame
        XCTAssertGreaterThan(visibleWindow.height, visibleWindow.width, "L’app deve restare verticale anche ruotando il telefono")
        XCTAssertFalse(rotatedFrame.isNull)
        XCTAssertFalse(rotatedFrame.isInfinite)
        XCTAssertFalse(rotatedFrame.isEmpty)
        XCTAssertTrue(visibleWindow.contains(rotatedFrame))
        XCTAssertLessThan(rotatedFrame.maxY, visibleWindow.maxY - 8)
        rotatedSave.tap()
        XCTAssertEqual(rotatedSave.label, "Salvato")
        XCUIDevice.shared.orientation = .portrait
        app.terminate(); app.launch()
        app.buttons["Storico"].tap()
        XCTAssertTrue(app.navigationBars["Storico"].waitForExistence(timeout: 5))
    }
    @MainActor func testCalendarAnd24HourWheelsInAnatomicalDetail() throws {
        let app = XCUIApplication()
        app.launch()
        let area = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Braccio destro")).firstMatch
        for _ in 0..<12 {
            if area.exists && area.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(area.isHittable)
        area.tap()
        XCTAssertTrue(app.navigationBars["Braccio destro"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.datePickers.firstMatch.exists)
        let time = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Ora:")).firstMatch
        for _ in 0..<4 { if time.isHittable { break }; app.swipeUp() }
        time.tap()
        XCTAssertTrue(app.navigationBars["Seleziona l’ora"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.pickerWheels.count, 2)
        app.pickerWheels.element(boundBy: 0).adjust(toPickerWheelValue: "23")
        app.pickerWheels.element(boundBy: 1).adjust(toPickerWheelValue: "59")
        app.buttons["Conferma"].tap()
        let updatedTime = app.buttons.matching(NSPredicate(format: "label == %@", "Ora: 23:59")).firstMatch
        XCTAssertTrue(updatedTime.waitForExistence(timeout: 5))
        XCTAssertEqual(updatedTime.label, "Ora: 23:59")
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Dettaglio anatomico e ora 24 ore"; attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor private func openBackup(_ label: String, in app: XCUIApplication) {
        app.buttons["Impostazioni"].tap()
        let button = app.buttons[label]
        for _ in 0..<8 { if button.exists && button.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(button.isHittable)
        button.tap()
        XCTAssertTrue(app.secureTextFields["backupPassword"].waitForExistence(timeout: 5))
    }

    @MainActor func testExportPasswordAndAppOwnedRequestRetainDocumentForRetry() throws {
        let app = XCUIApplication()
        // Exercise the real app flow and button action, substituting only the
        // system-owned document presenter (covered by the README manual check).
        app.launchArguments.append("-insofinaUITestExportRequestsOnly")
        app.launch()
        openBackup("Esporta backup", in: app)
        app.secureTextFields["backupPassword"].tap()
        app.secureTextFields["backupPassword"].typeText("Test-backup-123")
        app.secureTextFields["backupPasswordConfirmation"].tap()
        app.secureTextFields["backupPasswordConfirmation"].typeText("Test-backup-123")
        app.buttons["prepareBackup"].tap()
        XCTAssertTrue(app.staticTexts["backupReady"].waitForExistence(timeout: 15))
        XCTAssertFalse(app.progressIndicators["backupBusy"].exists)
        let saveFile = app.buttons["saveBackupFile"]
        XCTAssertTrue(saveFile.waitForExistence(timeout: 5))
        XCTAssertTrue(saveFile.isHittable)
        XCTAssertEqual(saveFile.value as? String, "Documento disponibile")
        for request in 1...2 {
            saveFile.tap()
            let requested = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "exists == true AND hittable == true AND value == %@", "Esportazione richiesta: \(request). Documento disponibile"),
                object: app.buttons["saveBackupFile"])
            XCTAssertEqual(XCTWaiter.wait(for: [requested], timeout: 5), .completed)
            XCTAssertFalse(app.progressIndicators["backupBusy"].exists)
        }
        XCTAssertTrue(app.staticTexts["backupReady"].exists)
        app.buttons["closeBackup"].tap()
        XCTAssertTrue(app.buttons["Esporta backup"].waitForExistence(timeout: 5))
    }

    @MainActor func testImportPasswordStaysOpenForInputAndValidation() throws {
        let app = XCUIApplication()
        app.launch()
        openBackup("Importa backup", in: app)
        app.secureTextFields["backupPassword"].tap()
        app.secureTextFields["backupPassword"].typeText("Test-backup-123")
        app.buttons["prepareBackup"].tap()
        XCTAssertTrue(app.staticTexts["Seleziona il file di backup da importare."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.secureTextFields["backupPassword"].exists)
        XCTAssertTrue(app.buttons["Seleziona file di backup"].exists)
        app.buttons["closeBackup"].tap()
        XCTAssertTrue(app.buttons["Importa backup"].waitForExistence(timeout: 5))
    }

    @MainActor func testHistoryRemainsReadableAndAccessibleWithSystemDarkAppearance() throws {
        let device = XCUIDevice.shared
        let previousAppearance = device.appearance
        device.appearance = .dark
        defer { device.appearance = previousAppearance }
        let app = XCUIApplication()
        app.launchArguments.append("-insofinaUITestHistory")
        app.launch()
        XCTAssertEqual(device.appearance, .dark, "Il sistema deve essere realmente in modalità scura")
        XCTAssertTrue(app.buttons["Storico"].waitForExistence(timeout: 5))
        app.buttons["Storico"].tap()
        XCTAssertTrue(app.navigationBars["Storico"].waitForExistence(timeout: 5))

        let records = [
            ("ui-history-rapid", "Braccio destro", "Insulina"),
            ("ui-history-basal", "Coscia sinistra", "Insulina"),
            ("ui-history-sensor", "Addome", "Sensore")
        ]
        for (id, area, mode) in records {
            let name = app.staticTexts["history-area-\(id)"]
            XCTAssertTrue(name.waitForExistence(timeout: 5))
            XCTAssertTrue(name.isHittable, "La registrazione deve essere visibile, non solo presente nei dati")
            XCTAssertEqual(name.label, area)
            XCTAssertTrue(app.staticTexts["history-zone-\(id)"].label.contains(mode))
            let date = app.staticTexts["history-date-\(id)"]
            XCTAssertTrue(date.exists); XCTAssertFalse(date.label.isEmpty)
            XCTAssertTrue(app.buttons["history-delete-\(id)"].isHittable)
        }
        XCTAssertEqual(app.staticTexts["history-insulin-ui-history-rapid"].label, "Rapida")
        XCTAssertEqual(app.staticTexts["history-insulin-ui-history-basal"].label, "Basale")
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Storico chiaro leggibile con sistema scuro - Rapida Basale Sensore"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
