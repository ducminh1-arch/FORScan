// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CarScanCore",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "CarScanCore",
            targets: ["CarScanCore"]
        ),
        .executable(
            name: "CarScanApp",
            targets: ["CarScanApp"]
        ),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "CarScanCore",
            dependencies: [],
            path: "Sources/CarScanCore",
            resources: [
                .copy("Resources/Spec")
            ]
        ),
        .executableTarget(
            name: "CarScanApp",
            dependencies: ["CarScanCore"],
            path: "Sources/CarScanApp",
            resources: [
                .process("Assets.xcassets")
            ]
        ),
        .testTarget(
            name: "CarScanCoreTests",
            dependencies: ["CarScanCore"],
            path: "Tests/CarScanCoreTests"
        ),
    ]
)
