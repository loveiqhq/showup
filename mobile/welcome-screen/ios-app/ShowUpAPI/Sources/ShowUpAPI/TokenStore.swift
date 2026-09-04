import Foundation

/// Where the access and refresh tokens live, and the only thing the network layer knows about auth.
///
/// A protocol so the middleware depends on behaviour rather than on the Keychain: that is what lets
/// the auth layer be tested with no entitlements, no signed app and no device, using
/// ``InMemoryTokenStore``.
///
/// `Sendable` because the middleware is handed across concurrency domains with the store captured.
public protocol TokenStoring: Sendable {
    /// The current access token, or nil when nobody is signed in.
    func accessToken() async -> String?

    /// The current refresh token, or nil when nobody is signed in.
    func refreshToken() async -> String?

    /// Replace both tokens. Called after sign-in and after every successful refresh.
    func save(accessToken: String, refreshToken: String) async

    /// Forget both tokens: sign-out, and also a failed refresh -- at that point the session is
    /// over whatever the user does next, and a dead token only produces a second confusing failure.
    func clear() async
}

/// A token store held in memory.
///
/// An `actor` rather than a class, so it is safe to share without `@unchecked Sendable` -- which
/// this project forbids and `audit/check-swift-concurrency.py` fails the build on.
///
/// Used by the tests, and deliberately not by the app: tokens must survive the process being
/// killed, which is what ``KeychainTokenStore`` is for.
public actor InMemoryTokenStore: TokenStoring {
    private var access: String?
    private var refresh: String?

    public init(accessToken: String? = nil, refreshToken: String? = nil) {
        self.access = accessToken
        self.refresh = refreshToken
    }

    public func accessToken() async -> String? { access }
    public func refreshToken() async -> String? { refresh }

    public func save(accessToken: String, refreshToken: String) async {
        access = accessToken
        refresh = refreshToken
    }

    public func clear() async {
        access = nil
        refresh = nil
    }
}
