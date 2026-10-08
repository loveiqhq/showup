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
///
/// WHEN THE SESSION IS OVER, AND WHEN IT IS NOT
///
/// A refresh that fails is one of two very different things, and until 8 October 2026 this treated
/// them the same. Every failure cleared the tokens — so a user on a train, or anyone whose request
/// met a server restarting, was silently signed out by a dropped connection. And when the session
/// really had ended nobody was told: the tokens went, the screen stayed, and every save after that
/// failed with "We couldn't save that just now" and no way out.
///
/// Now there are three answers (`Outcome`), and only `.refused` — the server looked at the refresh
/// token and said no — ends the session: the tokens are cleared AND `onSessionEnded` runs, which is
/// what sends the user to sign-in with a sentence saying why. `.unreachable` keeps the tokens: the
/// request in hand fails as it would have anyway, and the next one tries again.
public actor TokenRefresher {

    /// What a successful refresh returns. Mirrors the contract's AuthResponseDto fields.
    public struct TokenPair: Sendable, Equatable {
        public let accessToken: String
        public let refreshToken: String

        public init(accessToken: String, refreshToken: String) {
            self.accessToken = accessToken
            self.refreshToken = refreshToken
        }
    }

    /// What one refresh call came back with.
    public enum Outcome: Sendable, Equatable {
        /// A new pair. Both halves are stored: the server rotates the refresh token every time.
        case renewed(TokenPair)

        /// The server answered and the answer is no: the refresh token is expired, already used,
        /// unknown, or belongs to an account that is suspended or deleted. Asking again cannot
        /// change that, so this — and only this — ends the session.
        case refused

        /// No answer about the token: no connection, a timeout, a 5xx, or a 408 or 429 (the server
        /// saying "not now", not "no"). The session may be perfectly fine, so nothing is cleared.
        case unreachable

        /// Classifies a refresh that came back WITHOUT a usable pair, by its status code.
        ///
        /// A 4xx is the server's verdict on the token, except 408 (it timed out waiting for us) and
        /// 429 (rate limited) — both about this moment, not this token. A 5xx is the server's own
        /// problem. Anything else that arrives here is a broken answer, and a broken answer is not a
        /// refusal.
        public static func ofFailedResponse(statusCode: Int) -> Outcome {
            switch statusCode {
            case 408, 429: return .unreachable
            case 400...499: return .refused
            default: return .unreachable
            }
        }
    }

    private let tokens: any TokenStoring

    /// Performs the refresh call.
    ///
    /// Injected rather than built here, and it MUST use a client with no auth middleware attached.
    /// A refresh performed through the authenticated client would itself be subject to
    /// refresh-on-401, so a revoked refresh token would trigger a refresh, which would fail, which
    /// would trigger a refresh.
    private let exchange: @Sendable (String) async throws -> Outcome

    /// Called once when the server refuses the refresh token, AFTER the tokens are cleared.
    ///
    /// Off the main actor, from inside the refresh: whatever it does must be safe there, which is
    /// why the app hands it a hop to the main actor rather than a view to change.
    private let onSessionEnded: @Sendable () -> Void

    /// The refresh currently in flight, if any. This is the single-flight mechanism.
    private var inFlight: Task<String?, Never>?

    public init(
        tokens: any TokenStoring,
        onSessionEnded: @escaping @Sendable () -> Void = {},
        exchange: @escaping @Sendable (String) async throws -> Outcome
    ) {
        self.tokens = tokens
        self.onSessionEnded = onSessionEnded
        self.exchange = exchange
    }

    /// Returns a usable access token, refreshing if necessary, or nil if there is none to be had
    /// right now — because the session is over, or because the server could not be reached.
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

        let task = Task<String?, Never> { [tokens, exchange, onSessionEnded] in
            guard let refreshToken = await tokens.refreshToken() else {
                // A 401 with no refresh token is simply a signed-out user. Not an error, there is
                // nothing to clear, and no session to announce the end of.
                return nil
            }

            // A thrown exchange is a call that never got an answer — no network, a timeout, a body
            // that would not decode.
            let outcome: Outcome
            do {
                outcome = try await exchange(refreshToken)
            } catch {
                outcome = .unreachable
            }

            switch outcome {
            case .renewed(let pair):
                await tokens.save(
                    accessToken: pair.accessToken,
                    refreshToken: pair.refreshToken
                )
                return pair.accessToken
            case .refused:
                // The session is over. Keeping a dead token only produces a second confusing
                // failure on the next request, so it goes now — and the app is told, so the user is
                // asked to sign in once, with a reason, instead of meeting failed saves.
                await tokens.clear()
                onSessionEnded()
                return nil
            case .unreachable:
                // Offline, or the server is down. The tokens are KEPT: signing someone out because
                // a train went into a tunnel is the bug this replaced.
                return nil
            }
        }

        inFlight = task
        let result = await task.value
        inFlight = nil
        return result
    }
}
