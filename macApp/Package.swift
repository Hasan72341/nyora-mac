// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "Nyora",
    platforms: [
        .macOS(.v14),
    ],
    products: [
        .executable(name: "Nyora", targets: ["NyoraApp"]),
    ],
    targets: [
        .executableTarget(
            name: "NyoraApp",
            path: "Nyora/NyoraApp",
            resources: [
                .process("Assets.xcassets"),
            ]
        ),
    ]
)
