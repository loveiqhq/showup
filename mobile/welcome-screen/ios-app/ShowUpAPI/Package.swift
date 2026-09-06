// swift-tools-version: 5.9
import PackageDescription

// The generated API client, as a local Swift package.
//
// WHY A PACKAGE RATHER THAN A PLUGIN ON THE APP TARGET
//
// ShowUpWelcome.xcodeproj is written by gen_pbxproj.py, by hand. Declaring a build plugin inside a
// pbxproj is the least documented corner of that format, and it could only be verified through CI
// -- guessing at project-file internals across build cycles, on a platform we cannot compile
// locally. In Package.swift a build plugin is a first-class, documented construct.
//
// It is also better regardless of that risk: `swift build` verifies this package on any Mac with no
// Xcode project involved, so the client is provably correct even if app wiring lags behind.
//
// The app depends on this through an XCLocalSwiftPackageReference, which is a far simpler pbxproj
// construct than plugin invocation.
let package = Package(
    name: "ShowUpAPI",
    // macOS is here so `swift build` works standalone in CI. iOS 17 is the app's real floor and
    // matches IPHONEOS_DEPLOYMENT_TARGET in gen_pbxproj.py.
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "ShowUpAPI", targets: ["ShowUpAPI"]),
    ],
    dependencies: [
        // Apple's own generator, run as a build plugin: generation happens during the build, so
        // nothing generated is committed and there is nothing to hand-edit.
        .package(url: "https://github.com/apple/swift-openapi-generator", from: "1.5.0"),
        .package(url: "https://github.com/apple/swift-openapi-runtime", from: "1.5.0"),
        .package(url: "https://github.com/apple/swift-openapi-urlsession", from: "1.0.2"),
        // A transitive dependency of the runtime, declared explicitly because the tests import it
        // directly to build stub requests and responses. Relying on a transitive module being
        // importable is how a build breaks when an upstream package stops re-exporting it.
        .package(url: "https://github.com/apple/swift-http-types", from: "1.3.0"),
    ],
    targets: [
        .target(
            name: "ShowUpAPI",
            dependencies: [
                .product(name: "OpenAPIRuntime", package: "swift-openapi-runtime"),
                .product(name: "OpenAPIURLSession", package: "swift-openapi-urlsession"),
                // AuthMiddleware imports HTTPTypes directly, so it must be declared HERE and not
                // only on the test target.
                //
                // Leaving it off compiled fine and `swift build` passed, because the module is
                // reachable transitively through OpenAPIRuntime. It failed at LINK time, and only
                // in the Xcode build, which links the package as a framework and resolves symbols
                // strictly: "Undefined symbol: static HTTPTypes.HTTPField.Name.authorization".
                //
                // A dependency you use but do not declare is a dependency that works until the
                // thing linking it changes.
                .product(name: "HTTPTypes", package: "swift-http-types"),
            ],
            // openapi.json and openapi-generator-config.yaml must sit in this target's directory --
            // the plugin resolves both relative to the target, which is why the contract is copied
            // here rather than referenced from the repository root. `npm run openapi:emit` writes
            // BOTH copies, and CI diffs both, so the copy cannot drift from the source.
            plugins: [
                .plugin(name: "OpenAPIGenerator", package: "swift-openapi-generator"),
            ]
        ),
        .testTarget(
            name: "ShowUpAPITests",
            dependencies: [
                "ShowUpAPI",
                .product(name: "HTTPTypes", package: "swift-http-types"),
                .product(name: "OpenAPIRuntime", package: "swift-openapi-runtime"),
            ]
        ),
    ]
)
