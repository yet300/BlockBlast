// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_monetization_basic-ads",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_monetization_basic-ads",
      type: .none,
      targets: ["_monetization_basic-ads"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/googleads/swift-package-manager-google-mobile-ads.git",
      exact: "13.3.0"
    ),
    .package(
      url: "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git",
      exact: "3.1.0"
    )
  ],
  targets: [
    .target(
      name: "_monetization_basic-ads",
      dependencies: [
        .product(
          name: "GoogleMobileAds",
          package: "swift-package-manager-google-mobile-ads"
        ),
        .product(
          name: "GoogleUserMessagingPlatform",
          package: "swift-package-manager-google-user-messaging-platform"
        )
      ]
    )
  ]
)
