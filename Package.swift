// swift-tools-version: 6.0
import PackageDescription

// Builds the Swift sources that Unity copies into the Xcode project, so they can be tested without Unity.
// Unity's Xcode project compiles them in Swift 5 mode, so this package does too.
let package = Package(
    name: "GamePlatform",
    platforms: [.iOS(.v15), .macOS(.v12)],
    products: [
        .library(name: "GamePlatform", targets: ["GamePlatform"]),
    ],
    targets: [
        .target(
            name: "GamePlatform",
            path: "Packages/io.github.khbworks.gameplatform/Plugins/iOS/GamePlatform"
        ),
        .testTarget(
            name: "GamePlatformTests",
            dependencies: ["GamePlatform"],
            path: "Tests/GamePlatformTests"
        ),
    ],
    swiftLanguageModes: [.v5]
)
