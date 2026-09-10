import XCTest

final class InSofinaUITests: XCTestCase {
    @MainActor func testSettingsSaveConfirmationAndSafeAreaAfterRotation() throws {
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
        let landscapeSave = app.buttons["saveSettings"]
        let readyAfterRotation = NSPredicate { _, _ in
            let window = app.windows.firstMatch
            guard landscapeSave.exists, window.exists else { return false }
            let frame = landscapeSave.frame
            let windowFrame = window.frame
            guard !frame.isNull, !frame.isInfinite, !frame.isEmpty,
                  !windowFrame.isNull, !windowFrame.isInfinite, !windowFrame.isEmpty,
                  [frame.minX, frame.minY, frame.maxX, frame.maxY,
                   windowFrame.minX, windowFrame.minY, windowFrame.maxX, windowFrame.maxY].allSatisfy({ $0.isFinite }),
                  windowFrame.width > windowFrame.height,
                  windowFrame.contains(frame), frame.maxY < windowFrame.maxY - 8 else { return false }
            return landscapeSave.isHittable
        }
        let rotationExpectation = XCTNSPredicateExpectation(predicate: readyAfterRotation, object: landscapeSave)
        let rotationResult = XCTWaiter.wait(for: [rotationExpectation], timeout: 10)
        XCTAssertEqual(rotationResult, .completed, "Il pulsante deve essere visibile e toccabile nella safe area dopo la rotazione")
        guard rotationResult == .completed else { return }
        let landscapeFrame = landscapeSave.frame
        let visibleWindow = app.windows.firstMatch.frame
        XCTAssertFalse(landscapeFrame.isNull)
        XCTAssertFalse(landscapeFrame.isInfinite)
        XCTAssertFalse(landscapeFrame.isEmpty)
        XCTAssertTrue(visibleWindow.contains(landscapeFrame))
        XCTAssertLessThan(landscapeFrame.maxY, visibleWindow.maxY - 8)
        landscapeSave.tap()
        XCTAssertEqual(landscapeSave.label, "Salvato")
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
}
