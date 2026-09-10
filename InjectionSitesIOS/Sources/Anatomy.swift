import SwiftUI

struct ImageTransform {
    let source: CGRect
    let destination: CGRect
    let scale: CGFloat
    init(source: CGRect, canvas: CGSize) {
        self.source = source
        scale = min(canvas.width / source.width, canvas.height / source.height)
        destination = CGRect(x: (canvas.width - source.width * scale) / 2, y: (canvas.height - source.height * scale) / 2, width: source.width * scale, height: source.height * scale)
    }
    func point(_ p: CGPoint) -> CGPoint { CGPoint(x: destination.minX + (p.x - source.minX) * scale, y: destination.minY + (p.y - source.minY) * scale) }
    func inverse(_ p: CGPoint) -> CGPoint { CGPoint(x: (p.x - destination.minX) / scale + source.minX, y: (p.y - destination.minY) / scale + source.minY) }
}
struct AnatomicalZone: Identifiable {
    let area: BodyArea
    let index: Int
    let points: [CGPoint]
    var id: String { "\(area.rawValue)/\(index)" }
    var path: Path { Path { p in guard let first = points.first else { return }; p.move(to: first); points.dropFirst().forEach { p.addLine(to: $0) }; p.closeSubpath() } }
    var center: CGPoint { CGPoint(x: path.boundingRect.midX, y: path.boundingRect.midY) }
    func contains(_ point: CGPoint) -> Bool { path.contains(point) }
}
struct ImageAlphaMask {
    let data: Data
    let width: Int
    let height: Int
    init(asset: String, size: CGSize) {
        width = Int(size.width); height = Int(size.height)
        guard let url = Bundle.main.url(forResource: asset, withExtension: "mask"), let bytes = try? Data(contentsOf: url), bytes.count == (width * height + 7) / 8 else {
            preconditionFailure("Maschera di trasparenza mancante o non valida")
        }
        data = bytes
    }
    func contains(_ p: CGPoint) -> Bool {
        guard p.x >= 0, p.y >= 0, p.x < CGFloat(width), p.y < CGFloat(height) else { return false }
        let index = Int(p.y) * width + Int(p.x)
        return data[index / 8] & (1 << (index % 8)) != 0
    }
}
enum Anatomy {
    static func hitTest(_ point: CGPoint, zones: [AnatomicalZone], transform: ImageTransform, alpha: ImageAlphaMask) -> AnatomicalZone? {
        guard alpha.contains(transform.inverse(point)) else { return nil }
        return zones.first { $0.contains(point) }
    }
    static func viewport(avatar: AvatarStyle, area: BodyArea?, imageSize: CGSize) -> CGRect {
        let full = CGRect(origin: .zero, size: imageSize)
        guard let area else { return full }
        let points = outline(avatar: avatar, area: area, detail: false).map { CGPoint(x: $0.x * imageSize.width, y: $0.y * imageSize.height) }
        let bounds = AnatomicalZone(area: area, index: 0, points: points).path.boundingRect
        return bounds.insetBy(dx: -bounds.width * 0.4, dy: -bounds.height * 0.25).intersection(full)
    }
    static let outlines: [String: [[Double]]] = {
        guard let url = Bundle.main.url(forResource: "geometry", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let value = try? JSONDecoder().decode([String: [[Double]]].self, from: data), value.count == 42 else {
            preconditionFailure("Risorsa geometry.json mancante o non valida")
        }
        return value
    }()
    static func outline(avatar: AvatarStyle, area: BodyArea, detail: Bool) -> [CGPoint] {
        outlines["\(avatar.rawValue)/\(area.rawValue)/\(detail ? "detail" : "dashboard")", default: []].map { CGPoint(x: $0[0], y: $0[1]) }
    }
    // Sutherland–Hodgman intersection: same outline ∩ grid cell as Android.
    static func clip(_ points: [CGPoint], to rect: CGRect) -> [CGPoint] {
        var result = points
        for edge in 0..<4 {
            let input = result; result = []
            guard var previous = input.last else { break }
            func inside(_ p: CGPoint) -> Bool {
                switch edge { case 0: return p.x >= rect.minX; case 1: return p.x <= rect.maxX; case 2: return p.y >= rect.minY; default: return p.y <= rect.maxY }
            }
            func intersection(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
                if edge < 2 {
                    let x = edge == 0 ? rect.minX : rect.maxX
                    return CGPoint(x: x, y: a.y + (b.y-a.y) * (x-a.x) / (b.x-a.x))
                }
                let y = edge == 2 ? rect.minY : rect.maxY
                return CGPoint(x: a.x + (b.x-a.x) * (y-a.y) / (b.y-a.y), y: y)
            }
            for current in input {
                if inside(current) != inside(previous) { result.append(intersection(previous, current)) }
                if inside(current) { result.append(current) }
                previous = current
            }
        }
        return result
    }
    static func zones(avatar: AvatarStyle, area: BodyArea, detail: Bool, imageSize: CGSize, transform: ImageTransform) -> [AnatomicalZone] {
        let points = outline(avatar: avatar, area: area, detail: false).map { p in
            // Both views use the approved full-image coordinates and the same indices.
            transform.point(CGPoint(x: p.x * imageSize.width, y: p.y * imageSize.height))
        }
        let bounds = AnatomicalZone(area: area, index: 0, points: points).path.boundingRect
        let columns = area.zoneCount / 2
        let reversed = area == .LEFT_ARM
        return (0..<area.zoneCount).map { index in
            let column = reversed ? columns - 1 - index % columns : index % columns
            let rect = CGRect(x: bounds.minX + bounds.width * CGFloat(column) / CGFloat(columns), y: bounds.minY + bounds.height * CGFloat(index / columns) / 2, width: bounds.width / CGFloat(columns), height: bounds.height / 2)
            return AnatomicalZone(area: area, index: index, points: clip(points, to: rect))
        }
    }
}
enum Visuals {
    static let availabilityColors: [Color] = [.init(hex: 0xDC2626), .init(hex: 0xF97316), .init(hex: 0xEAB308), .init(hex: 0x16A34A)]
    static let sensorColors: [Color] = [.init(hex: 0x475569), .init(hex: 0x7C8796), .init(hex: 0xB8C0CB), .init(hex: 0xDEE3EA)]
    static func color(state: AppState, area: BodyArea, zone: Int, now: Int64) -> Color {
        let event = state.records.filter { $0.mode == .INSULINA && $0.area == area && $0.zone == zone }.map(\.eventDateTime).max()
        return availabilityColors[availability(event, now: now, settings: state.settings)]
    }
    static func markers(state: AppState, area: BodyArea, zone: Int, now: Int64) -> [(RecordItem, Int)] {
        Array(ordered(state.records.filter { $0.mode == .SENSORE && $0.area == area && $0.zone == zone }).compactMap { r in sensorStage(r.eventDateTime, now: now, settings: state.settings).map { (r, $0) } }.prefix(3))
    }
}
extension Color {
    init(hex: UInt32) { self.init(red: Double((hex >> 16) & 255)/255, green: Double((hex >> 8) & 255)/255, blue: Double(hex & 255)/255) }
}
struct BodyDiagram: View {
    let state: AppState
    var back = false
    var detail: BodyArea?
    var selected: Int?
    let onSelect: (BodyArea, Int) -> Void
    private var asset: String { state.avatar.asset(back: detail?.isBack ?? back) }
    var body: some View {
        let bitmap = UIImage(named: asset)!
        let imageSize = bitmap.size
        let alpha = ImageAlphaMask(asset: asset, size: imageSize)
        TimelineView(.periodic(from: .now, by: 60)) { timeline in
            GeometryReader { geometry in
                let viewport = Anatomy.viewport(avatar: state.avatar, area: detail, imageSize: imageSize)
                let transform = ImageTransform(source: viewport, canvas: geometry.size)
                let imageRect = CGRect(origin: transform.point(.zero), size: CGSize(width: imageSize.width * transform.scale, height: imageSize.height * transform.scale))
                let areas = detail.map { [$0] } ?? BodyArea.allCases.filter { $0.isBack == back }
                let zones = areas.flatMap { Anatomy.zones(avatar: state.avatar, area: $0, detail: detail != nil, imageSize: imageSize, transform: transform) }
                let now = timeline.date.milliseconds
                Canvas { context, _ in
                    context.draw(Image(uiImage: bitmap), in: imageRect)
                    // Clip paint to original PNG alpha, excluding transparent background.
                    context.clipToLayer { layer in layer.draw(Image(uiImage: bitmap), in: imageRect) }
                    let activeID = activeSensor(state.records)?.id
                    for zone in zones {
                        let color = Visuals.color(state: state, area: zone.area, zone: zone.index, now: now)
                        context.fill(zone.path, with: .color(color.opacity(selected == zone.index && detail != nil ? 1 : 0.72)))
                        context.stroke(zone.path, with: .color(.white.opacity(0.72)), lineWidth: 1.5)
                        let center = zone.center
                        context.fill(Path(ellipseIn: CGRect(x: center.x-10, y: center.y-10, width: 20, height: 20)), with: .color(.white.opacity(0.85)))
                        context.draw(Text("\(zone.index+1)").font(.system(size: 11, weight: .bold)).foregroundColor(.black), at: center)
                        for (offset, pair) in Visuals.markers(state: state, area: zone.area, zone: zone.index, now: now).enumerated() {
                            let active = pair.0.id == activeID
                            let radius: CGFloat = active ? 12 : 8
                            let p = CGPoint(x: center.x + CGFloat(offset)*9-9, y: center.y)
                            context.fill(Path(ellipseIn: CGRect(x: p.x-radius-3, y: p.y-radius-3, width: (radius+3)*2, height: (radius+3)*2)), with: .color(.white.opacity(active ? 0.95 : 0.55)))
                            context.fill(Path(ellipseIn: CGRect(x: p.x-radius, y: p.y-radius, width: radius*2, height: radius*2)), with: .color(Visuals.sensorColors[pair.1].opacity(active ? 1 : 0.38)))
                            if active { context.fill(Path(ellipseIn: CGRect(x: p.x-7, y: p.y-7, width: 8, height: 8)), with: .color(.white.opacity(0.45))) }
                        }
                    }
                }
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { value in
                    if let zone = Anatomy.hitTest(value.location, zones: zones, transform: transform, alpha: alpha) { onSelect(zone.area, zone.index) }
                })
                .accessibilityLabel(detail?.label ?? (back ? "Vista posteriore a specchio" : "Vista anteriore a specchio"))
                .accessibilityHint("Le stesse zone sono disponibili nell’elenco seguente")
                .clipped()
            }
        }
        .aspectRatio(detail == nil ? imageSize.width / imageSize.height : 300.0 / 310.0, contentMode: .fit)
        .background(Color(hex: 0xF6F8FC))
    }
}
