import Foundation
import OpenAPIRuntime
import OpenAPIURLSession

/// The app's single entry point to the backend.
///
/// Everything below `Client` is generated at build time by Apple's OpenAPI generator from
/// `openapi.json`, which sits beside this file. Nothing generated is committed: it is written into
/// the build directory, so a hand-edit is erased by the next build rather than surviving as a
/// second source of truth.
public struct ShowUpAPI: Sendable {

    /// The authenticated client. Screens call operations on this by their `operationId`.
    public let client: Client

    private let tokens: any TokenStoring
    private let bare: Client

    /// Builds a client for an environment, with auth attached.
    ///
    /// - Parameters:
    ///   - environment: chosen at build time by the caller, never by a runtime condition.
    ///   - tokens: where credentials live. Inject ``InMemoryTokenStore`` in tests and
    ///     ``KeychainTokenStore`` in the app.
    public init(
        environment: APIEnvironment = .development,
        tokens: any TokenStoring,
        transport: any ClientTransport = URLSessionTransport()
    ) {
        self.tokens = tokens

        // A client with NO auth attached, used only to refresh and to sign out.
        //
        // This separation is load-bearing. A refresh performed through the authenticated client
        // would itself be subject to refresh-on-401, so a revoked refresh token would trigger a
        // refresh, which would fail 401, which would trigger a refresh.
        let bare = Client(
            serverURL: environment.baseURL,
            transport: transport
        )
        self.bare = bare

        let refresher = TokenRefresher(tokens: tokens) { refreshToken in
            let response = try await bare.refreshAuthToken(
                // Apple's generator renders a hyphen in a header name as `_hyphen_`, so `user-agent`
                // becomes `user_hyphen_agent`. The Kotlin generator called the same parameter
                // `userAgent`; the contract is shared but the naming conventions are not.
                headers: .init(user_hyphen_agent: Self.userAgent),
                body: .json(.init(refreshToken: refreshToken))
            )
            switch response {
            case .ok(let ok):
                let payload = try ok.body.json
                // Both tokens, not just the access token: the backend ROTATES the refresh token on
                // every successful refresh, so keeping the old one would leave the app holding a
                // token that is already spent, and the NEXT refresh would sign the user out.
                return TokenRefresher.TokenPair(
                    accessToken: payload.accessToken,
                    refreshToken: payload.refreshToken
                )
            default:
                return nil
            }
        }

        self.client = Client(
            serverURL: environment.baseURL,
            transport: transport,
            middlewares: [AuthMiddleware(tokens: tokens, refresher: refresher)]
        )
    }

    /// Signs the user out: revokes the session server-side, then forgets the tokens locally.
    ///
    /// THE ORDER MATTERS, AND SO DOES IGNORING THE RESULT.
    ///
    /// The server call goes first, because it needs the refresh token the second step deletes. And
    /// the local clear happens whether or not that call succeeds: a user who taps sign out on a
    /// plane must end up signed out. Leaving the tokens because the network was unavailable means
    /// the app still looks signed in, which is surprising and a real privacy problem on a shared
    /// device.
    ///
    /// The consequence -- a refresh token that stays valid server-side until it expires -- is the
    /// lesser harm, and it is what happens anyway if the app is deleted mid-session.
    public func signOut() async {
        if let refreshToken = await tokens.refreshToken() {
            _ = try? await bare.logout(body: .json(.init(refreshToken: refreshToken)))
        }
        await tokens.clear()
    }

    /// Sent on refresh because the contract requires a user-agent on that route: the backend
    /// records it against the session so a user can see where they are signed in.
    private static let userAgent = "ShowUp-iOS/0.1"
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
