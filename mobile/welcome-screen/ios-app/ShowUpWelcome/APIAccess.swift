import Foundation
import ShowUpAPI

/// The app's configured API client.
///
/// This is the whole of the app target's dependency on the network layer, and it exists now, before
/// any screen calls an endpoint, for two reasons.
///
/// First, it is the proof that the app target actually links `ShowUpAPI`. The package builds and
/// tests on its own in CI, but "the package is correct" and "the app can use the package" are
/// different claims -- and on this project the gap between a file existing and a target seeing it
/// has already cost a week of tests that never ran. A target that compiles `import ShowUpAPI` has
/// resolved the local package, built the OpenAPI plugin's output, and linked the module.
///
/// Second, it is where the environment and the token store are decided, once. A screen that built
/// its own client would be a screen that could pick the wrong environment.
///
/// Both scene-level models are built from it: `PhoneAuthModel`, which signs in and writes the
/// tokens, and `BasicsModel`, which spends them. They share `tokens` on purpose — see the comment
/// beside `phoneAuth` in `ShowUpWelcomeApp`.
enum APIAccess {

    /// Where credentials live in the app: the Keychain, never in memory.
    ///
    /// `KeychainTokenStore` is an actor, so this is safe to share. It is deliberately NOT the
    /// in-memory store the package's own tests use -- that one loses the session on relaunch.
    static let tokens = KeychainTokenStore()

    /// The client every screen will eventually call.
    ///
    /// `.development` while the app is only ever run against a local backend. This is a build-time
    /// choice and the one line to change when there is a staging deployment; it is not a runtime
    /// condition, because a runtime condition can be wrong and then a release build talks to the
    /// wrong server.
    static let client = ShowUpAPI(environment: .development, tokens: tokens)
}
