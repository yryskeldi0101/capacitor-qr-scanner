// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorQrScanner",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapacitorQrScanner",
            targets: ["QrCodeScannerPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "QrCodeScannerPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/QrCodeScannerPlugin"),
        .testTarget(
            name: "QrCodeScannerPluginTests",
            dependencies: ["QrCodeScannerPlugin"],
            path: "ios/Tests/QrCodeScannerPluginTests")
    ]
)