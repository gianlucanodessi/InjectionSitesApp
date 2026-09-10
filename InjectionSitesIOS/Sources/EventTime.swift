import Foundation

enum EventTime {
    static func text(for date: Date, calendar: Calendar = .current) -> String {
        // Calendar hours are 0...23. Format numbers directly so a device's
        // 12-hour preference cannot rewrite the hour cycle or add AM/PM.
        String(format: "%02d:%02d", locale: Locale(identifier: "en_US_POSIX"),
               calendar.component(.hour, from: date), calendar.component(.minute, from: date))
    }

    static func replacingTime(in date: Date, hour: Int, minute: Int, calendar: Calendar = .current) -> Date? {
        guard (0..<24).contains(hour), (0..<60).contains(minute),
              let result = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: date),
              calendar.isDate(result, inSameDayAs: date) else { return nil }
        return result
    }
}
