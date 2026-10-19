import Cocoa

let args = CommandLine.arguments
if args.count < 2 {
    exit(1)
}

func getIconBase64(path: String) -> String? {
    let icon = NSWorkspace.shared.icon(forFile: path)
    guard let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 64, pixelsHigh: 64, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0) else {
        return nil
    }

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    icon.draw(in: NSRect(x: 0, y: 0, width: 64, height: 64), from: .zero, operation: .copy, fraction: 1.0)
    NSGraphicsContext.restoreGraphicsState()

    guard let png = rep.representation(using: .png, properties: [:]) else { return nil }
    return "data:image/png;base64,\(png.base64EncodedString())"
}

if args.count == 2 {
    if let uri = getIconBase64(path: args[1]) {
        print(uri)
    }
} else {
    var dict: [String: String] = [:]
    for i in 1..<args.count {
        let p = args[i]
        if let uri = getIconBase64(path: p) {
            dict[p] = uri
        }
    }
    if let data = try? JSONSerialization.data(withJSONObject: dict),
       let str = String(data: data, encoding: .utf8) {
        print(str)
    }
}
