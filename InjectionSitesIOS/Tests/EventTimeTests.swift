import XCTest
@testable import InSofina

final class EventTimeTests: XCTestCase {
    func testFixed24HourTextInUSAndItalianLocales() throws {
        for localeID in ["en_US", "it_IT"] {
            for zoneID in ["UTC", "Europe/Rome"] {
                var calendar = Calendar(identifier: .gregorian)
                calendar.locale = Locale(identifier: localeID)
                calendar.timeZone = try XCTUnwrap(TimeZone(identifier: zoneID))
                for (hour, minute, expected) in [(0, 5, "00:05"), (23, 59, "23:59"), (16, 39, "16:39")] {
                    let date = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 9, day: 8, hour: hour, minute: minute)))
                    XCTAssertEqual(EventTime.text(for: date, calendar: calendar), expected, "\(localeID), \(zoneID)")
                }
            }
        }
    }

    func testWheelSelectionPreservesDateAndClearsSeconds() throws {
        for localeID in ["en_US", "it_IT"] {
            for zoneID in ["UTC", "Europe/Rome"] {
                var calendar = Calendar(identifier: .gregorian)
                calendar.locale = Locale(identifier: localeID)
                calendar.timeZone = try XCTUnwrap(TimeZone(identifier: zoneID))
                let original = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 9, day: 8, hour: 16, minute: 39, second: 47, nanosecond: 500_000_000)))
                for (hour, minute, expected) in [(0, 5, "00:05"), (23, 59, "23:59")] {
                    let updated = try XCTUnwrap(EventTime.replacingTime(in: original, hour: hour, minute: minute, calendar: calendar))
                    XCTAssertEqual(calendar.dateComponents([.era, .year, .month, .day], from: updated), calendar.dateComponents([.era, .year, .month, .day], from: original))
                    XCTAssertEqual(calendar.component(.hour, from: updated), hour)
                    XCTAssertEqual(calendar.component(.minute, from: updated), minute)
                    XCTAssertEqual(calendar.component(.second, from: updated), 0)
                    XCTAssertEqual(calendar.component(.nanosecond, from: updated), 0)
                    XCTAssertEqual(EventTime.text(for: updated, calendar: calendar), expected)
                }
            }
        }
    }
}
