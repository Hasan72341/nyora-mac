import SwiftUI
import AppKit

extension Image {
    /// Loads `<name>.png` from the main app bundle's Resources.
    ///
    /// Why not `Image(name, bundle: .module)`? The hand-assembled `Nyora.app`
    /// (see `macApp/scripts/build-dmg.sh`) can't use SwiftPM's `Bundle.module` —
    /// its generated accessor resolves to a build-dir path that hangs in
    /// `_CFIterateDirectory` from the relocated binary — and SwiftPM here ships a
    /// *raw* `Assets.xcassets` that is never compiled to an `Assets.car`, so named
    /// asset lookups fail too. We therefore ship the logos as loose PNGs in
    /// `Contents/Resources/<name>.png` and load them through `Bundle.main`, which is
    /// reliable. Falls back to the asset catalog for `swift run` dev builds.
    init(bundleResource name: String) {
        if let url = Bundle.main.url(forResource: name, withExtension: "png"),
           let nsImage = NSImage(contentsOf: url) {
            self.init(nsImage: nsImage)
        } else {
            self.init(name, bundle: .module)
        }
    }
}
