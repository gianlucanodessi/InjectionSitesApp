import SwiftUI

enum HistoryPalette {
    static let primaryText: UInt32 = 0x111827
    static let secondaryText: UInt32 = 0x374151
    static let rapidText: UInt32 = 0x9A3412
    static let basalText: UInt32 = 0x1557C0
    static let deleteText: UInt32 = 0xB91C1C
    static let rapidBackground: UInt32 = 0xE5F5E9
    static let basalBackground: UInt32 = 0xF0E6FA
    static let sensorBackground: UInt32 = 0xE5E7EB

    static func background(for record: RecordItem) -> Color {
        Color(hex: record.mode == .SENSORE ? sensorBackground : record.insulinType == .RAPIDA ? rapidBackground : basalBackground)
    }
}
