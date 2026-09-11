import XCTest
@testable import InSofina

final class HistoryPaletteTests: XCTestCase {
    func testEveryHistoryTextColorHasAtLeastNormalTextContrastOnEveryPastel() {
        let texts = [HistoryPalette.primaryText, HistoryPalette.secondaryText, HistoryPalette.rapidText, HistoryPalette.basalText, HistoryPalette.deleteText]
        let backgrounds = [HistoryPalette.rapidBackground, HistoryPalette.basalBackground, HistoryPalette.sensorBackground]
        for text in texts {
            for background in backgrounds {
                let a = luminance(text), b = luminance(background)
                let contrast = (max(a, b) + 0.05) / (min(a, b) + 0.05)
                XCTAssertGreaterThanOrEqual(contrast, 4.5, String(format: "Testo #%06X, sfondo #%06X: %.2f:1", text, background, contrast))
            }
        }
    }

    func testApprovedBackgroundColorsAreUnchanged() {
        XCTAssertEqual(HistoryPalette.rapidBackground, 0xE5F5E9)
        XCTAssertEqual(HistoryPalette.basalBackground, 0xF0E6FA)
        XCTAssertEqual(HistoryPalette.sensorBackground, 0xE5E7EB)
    }

    private func luminance(_ hex: UInt32) -> Double {
        let rgb = [16, 8, 0].map { shift -> Double in
            let channel = Double((hex >> shift) & 255) / 255
            return channel <= 0.04045 ? channel / 12.92 : pow((channel + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
    }
}
