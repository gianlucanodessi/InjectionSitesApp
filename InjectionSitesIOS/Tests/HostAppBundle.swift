import Foundation

enum HostAppBundle {
    private static let identifier = "com.example.insofina.ios"

    static func locate(from testBundle: Bundle) throws -> Bundle {
        // Hosted layout: InSofina.app/PlugIns/InSofinaTests.xctest.
        var candidate = testBundle.bundleURL.standardizedFileURL
        while candidate.path != "/" {
            if candidate.pathExtension == "app", let bundle = Bundle(url: candidate),
               bundle.bundleIdentifier == identifier { return bundle }
            let parent = candidate.deletingLastPathComponent()
            if parent == candidate { break }
            candidate = parent
        }
        // Also support a separately located XCTest bundle with a loaded host app.
        let loaded = Bundle(identifier: identifier).map { [$0] } ?? []
        if let host = (loaded + Bundle.allBundles).first(where: {
            $0.bundleIdentifier == identifier && $0.bundleURL.pathExtension == "app"
        }) { return host }
        throw NSError(domain: "InSofinaTests.HostAppBundle", code: 1, userInfo: [
            NSLocalizedDescriptionKey: "Bundle host InSofina.app (\(identifier)) non trovato partendo da \(testBundle.bundleURL.path). Eseguire il test nel target ospitato dall’app."
        ])
    }
}
