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
        XCTAssertTrue(save.isHittable)
        save.tap()
        XCTAssertEqual(save.label, "Salvato")
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
