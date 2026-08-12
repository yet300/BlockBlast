// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_core_telemetry",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_core_telemetry",
      type: .none,
      targets: ["_core_telemetry"]
    )
  ],
  dependencies: [
  ],
  targets: [
    .target(
      name: "_core_telemetry",
      dependencies: [
      ]
    )
  ]
)
