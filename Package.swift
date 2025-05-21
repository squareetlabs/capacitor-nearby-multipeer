// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SquareetlabsCapacitorNearbyMultipeer",
    platforms: [.iOS(.v13)],
    products: [
        .library(
            name: "SquareetlabsCapacitorNearbyMultipeer",
            targets: ["NearbyMultipeerPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", branch: "main")
    ],
    targets: [
        .target(
            name: "NearbyMultipeerPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/NearbyMultipeerPlugin"),
    ]
)
