import Foundation
import OpenAPIRuntime
import OpenAPIURLSession

/// The app's single entry point to the backend.
///
/// Everything below `Client` is generated at build time by Apple's OpenAPI generator from
/// `openapi.json`, which sits beside this file. Nothing generated is committed: it is written into
/// the build directory, so a hand-edit is erased by the next build rather than surviving as a
/// second source of truth.
///
/// WHY THIS FACADE EXISTS
///
/// The generated `Client` needs a server URL and a transport on every construction, and screens
/// must never assemble those themselves -- that is how a build ends up talking to the wrong
/// environment. This decides both once.
public struct ShowUpAPI: Sendable {
    /// The generated client. Screens call operations on this by their `operationId`.
    public let client: Client

    /// Builds a client for an environment.
    ///
    /// `middlewares` is how authentication is attached. It is a parameter rather than a hardcoded
    /// list so this package stays free of any dependency on Keychain or app state, which is also
    /// what lets it be tested with no auth at all.
    public init(
        environment: APIEnvironment = .development,
        middlewares: [any ClientMiddleware] = []
    ) throws {
        self.client = Client(
            serverURL: environment.baseURL,
            transport: URLSessionTransport(),
            middlewares: middlewares
        )
    }
}

/// Which backend a build talks to.
///
/// Chosen at build time rather than by a runtime condition: a runtime check can be wrong -- a debug
/// flag left on, an unset variable -- and then a release build talks to staging. These mirror the
/// three-branch workflow in CONTRIBUTING.md.
public enum APIEnvironment: Sendable {
    case development
    case staging
    case production

    public var baseURL: URL {
        switch self {
        // The iOS simulator shares the host's network stack, so localhost is the host machine --
        // unlike the Android emulator, which needs 10.0.2.2.
        case .development: return URL(string: "http://localhost:3000")!
        case .staging: return URL(string: "https://staging-api.showup.example")!
        case .production: return URL(string: "https://api.showup.example")!
        }
    }
}
