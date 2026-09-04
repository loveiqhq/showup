import Foundation

/// Exchanges a refresh token for a new pair, at most once at a time.
///
/// WHY SINGLE-FLIGHT IS THE WHOLE POINT
///
/// A screen typically fires several requests at once. When the access token has expired they all
/// come back 401 together, and the naive response is one refresh per failed request. That is bad in
/// three separate ways:
///
/// - the backend ROTATES the refresh token on every successful refresh, so the second call presents
///   a token the first has already invalidated. It fails, and the user is signed out for no reason.
/// - whichever refresh finishes last wins the write, so the tokens stored may not be the ones the
///   retried requests were given.
/// - it is a burst of identical calls against the endpoint most likely to be rate-limited.
///
/// It is also a bug that only appears under concurrency, so it will never show up while somebody
/// taps through screens by hand.
///
/// HOW THE COORDINATION WORKS
///
/// An actor holding the in-flight `Task`. The first caller creates it; every caller that arrives
/// while it is running awaits the *same* task and receives the same result. That is stronger than a
/// lock: with a mutex, ten callers would queue and each would then have to work out that its turn
/// was unnecessary. Here nine of them never do any work at all.
public actor TokenRefresher {

    /// What a successful refresh returns. Mirrors the contract's AuthResponseDto fields.
    public struct TokenPair: Sendable {
        public let accessToken: String
        public let refreshToken: String

        public init(accessToken: String, refreshToken: String) {
            self.accessToken = accessToken
            self.refreshToken = refreshToken
        }
    }

    private let tokens: any TokenStoring

    /// Performs the refresh call.
    ///
    /// Injected rather than built here, and it MUST use a client with no auth middleware attached.
    /// A refresh performed through the authenticated client would itself be subject to
    /// refresh-on-401, so a revoked refresh token would trigger a refresh, which would fail, which
    /// would trigger a refresh.
    private let exchange: @Sendable (String) async throws -> TokenPair?

    /// The refresh currently in flight, if any. This is the single-flight mechanism.
    private var inFlight: Task<String?, Never>?

    public init(
        tokens: any TokenStoring,
        exchange: @escaping @Sendable (String) async throws -> TokenPair?
    ) {
        self.tokens = tokens
        self.exchange = exchange
    }

    /// Returns a usable access token, refreshing if necessary, or nil if the session is over.
    ///
    /// - Parameter usedToken: the access token the failing request carried, or nil if it carried
    ///   none. Used to recognise a caller whose token has already been replaced by somebody else's
    ///   refresh, so it takes the new one instead of asking for another.
    public func validToken(after usedToken: String?) async -> String? {
        // Somebody else already refreshed. Nothing to do.
        if let current = await tokens.accessToken(), current != usedToken {
            return current
        }

        // A refresh is already running: await its result rather than starting a second one.
        if let existing = inFlight {
            return await existing.value
        }

        let task = Task<String?, Never> { [tokens, exchange] in
            guard let refreshToken = await tokens.refreshToken() else {
                // A 401 with no refresh token is simply a signed-out user. Not an error, and there
                // is nothing to clear.
                return nil
            }

            let pair = try? await exchange(refreshToken)
            guard let pair else {
                // Spent, revoked, or the call failed. Either way the session is over, and keeping a
                // dead token only produces a second confusing failure on the next request.
                await tokens.clear()
                return nil
            }

            await tokens.save(
                accessToken: pair.accessToken,
                refreshToken: pair.refreshToken
            )
            return pair.accessToken
        }

        inFlight = task
        let result = await task.value
        inFlight = nil
        return result
    }
}
