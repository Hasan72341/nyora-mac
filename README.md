# Nyora for Mac

Native macOS port of [Nyora Android](../nyora-android), built around a Kotlin Multiplatform shared module and a SwiftUI front-end.

> **Status — scaffold / pre-alpha.** The folder layout, build files, domain models, JS extension runtime, and SwiftUI shell all exist, but the app is not yet feature-complete with the Android version. See [`TODO.md`](TODO.md) and [`docs/PORTING_PLAN.md`](docs/PORTING_PLAN.md) for the gap to parity.

## Layout

```
nyora-mac/
├── shared/                  # Kotlin Multiplatform module
│   └── src/
│       ├── commonMain/      # Pure-Kotlin domain types and parsers
│       ├── jvmMain/         # GraalVM JS runtime, MihonProxyServer, HelperMain
│       └── macosMain/       # macosArm64/macosX64 binding (NyoraShared.framework)
├── macApp/
│   ├── Package.swift        # SwiftPM project for the SwiftUI app
│   ├── Nyora/NyoraApp/      # SwiftUI sources
│   └── scripts/             # JVM helper launcher
└── docs/                    # Porting notes and architecture
```

## Running the helper

The SwiftUI app talks to a small JVM "helper" sidecar that owns the GraalVM JavaScript runtime and Mihon proxy bridge. Launch it once per session before opening the app:

```sh
Nyora/nyora-mac/macApp/scripts/launch-helper.sh
```

The script writes the bound port to `~/Library/Application Support/Nyora/helper.port`; the SwiftUI app discovers the helper there.

## Building the SwiftUI app

```sh
cd Nyora/nyora-mac/macApp
swift run
```

(Or open `Package.swift` in Xcode.)

## Building the shared framework

```sh
cd Nyora/nyora-mac
./gradlew :shared:assembleNyoraSharedXCFramework
```

This emits `shared/build/XCFrameworks/release/NyoraShared.xcframework`, which can be dragged into the Xcode project once we promote `macApp` to a full `.xcodeproj`.

## Credits

Forked from [Nyora Android](../nyora-android). See [`LICENSE`](../LICENSE) — GPLv3.
