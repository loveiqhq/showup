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
    ///   - onSessionEnded: told when the server refuses to renew the session — see
    ///     ``TokenRefresher``. Runs off the main actor, after the tokens are already gone.
    public init(
        environment: APIEnvironment = .development,
        tokens: any TokenStoring,
        transport: any ClientTransport = URLSessionTransport(),
        onSessionEnded: @escaping @Sendable () -> Void = {}
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

        let refresher = TokenRefresher(tokens: tokens, onSessionEnded: onSessionEnded) { refreshToken in
            try await Self.exchangeRefresh(bare, refreshToken: refreshToken, userAgent: Self.userAgent)
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
    /// What the backend records against a session so a person can see where they are signed in.
    ///
    /// Public because two routes need it and only one of them lives in this package: refresh is
    /// here, and `/auth/phone/verify` is called from the app. A second definition would make the
    /// devices list disagree with itself -- the same phone appearing under two names depending on
    /// which request created the row.
    ///
    /// NO SLASH, deliberately, and this is not a style choice. Apple's generator serialises a
    /// header parameter as a URI component, so `ShowUp-iOS/0.1` leaves the app as
    /// `ShowUp-iOS%2F0.1` and that is what the backend stores. Every refresh this app has ever
    /// sent recorded the encoded form; nothing asserted the header, so nothing noticed. Only
    /// unreserved characters survive the encoding unchanged, so the version is joined with a
    /// hyphen. Android sends `ShowUp-Android/<version>` unencoded through Retrofit, so the two
    /// platforms read slightly differently in that list -- readable and correct beats matching
    /// and wrong.
    public static let userAgent = "ShowUp-iOS-0.1"

    /// One refresh call, read into a `TokenRefresher.Outcome`.
    ///
    /// Here rather than inline so the auth tests run THIS mapping, not a copy of it: the line
    /// between "the server said no" and "the server was not there" is the line between sending
    /// someone to sign-in and leaving them signed in, and a test of a copy proves nothing about it.
    ///
    /// EXHAUSTIVE, NOT `default`. A response the contract gains later has to be placed on one side
    /// of that line by somebody deciding, not by a fallthrough.
    static func exchangeRefresh(
        _ bare: Client,
        refreshToken: String,
        userAgent: String
    ) async throws -> TokenRefresher.Outcome {
        let response = try await bare.refreshAuthToken(
            // Apple's generator renders a hyphen in a header name as `_hyphen_`, so `user-agent`
            // becomes `user_hyphen_agent`. The Kotlin generator called the same parameter
            // `userAgent`; the contract is shared but the naming conventions are not.
            headers: .init(user_hyphen_agent: userAgent),
            body: .json(.init(refreshToken: refreshToken))
        )
        switch response {
        case .ok(let ok):
            let payload = try ok.body.json
            // Both tokens, not just the access token: the backend ROTATES the refresh token on
            // every successful refresh, so keeping the old one would leave the app holding a token
            // that is already spent, and the NEXT refresh would sign the user out.
            return .renewed(TokenRefresher.TokenPair(
                accessToken: payload.accessToken,
                refreshToken: payload.refreshToken
            ))
        case .badRequest:
            // A refresh token the server cannot even read. Sending it again cannot help.
            return .refused
        case .internalServerError:
            return .unreachable
        case .undocumented(let statusCode, _):
            // Where the server's 401 arrives: the contract documents only 200, 400 and 500.
            return .ofFailedResponse(statusCode: statusCode)
        }
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
