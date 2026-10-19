package com.showup.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Exchanges a refresh token for a new pair, at most once at a time.
 *
 * WHY SINGLE-FLIGHT IS THE WHOLE POINT
 *
 * A screen typically fires several requests at once. When the access token has expired they all
 * come back 401 together, and the naive response is one refresh per failed request. That is bad in
 * three separate ways:
 *
 *   - the backend ROTATES the refresh token on every successful refresh, so the second call
 *     presents a token the first has already invalidated. It fails, and the user is signed out for
 *     no reason.
 *   - whichever refresh finishes last wins the write, so the tokens stored may not be the ones the
 *     retried requests were given.
 *   - it is a burst of identical calls against the endpoint most likely to be rate-limited.
 *
 * It is also a bug that only appears under concurrency, which means it will not show up while
 * anybody is clicking through screens by hand.
 *
 * HOW THE COORDINATION WORKS
 *
 * One mutex, plus a check that makes waiting callers cheap: each caller passes the access token its
 * request actually used. After acquiring the lock, if the stored token is no longer that one,
 * somebody else has already refreshed and the caller simply takes the new token. So ten concurrent
 * 401s produce exactly one network call and nine instant reuses.
 *
 * WHEN THE SESSION IS OVER, AND WHEN IT IS NOT
 *
 * A refresh that fails is one of two very different things, and until 8 October 2026 this treated
 * them the same. Every failure cleared the tokens -- so a user on a train, or anyone whose request
 * met a server restarting, was silently signed out by a dropped connection. And when the session
 * really had ended nobody was told: the tokens went, the screen stayed, and every save after that
 * failed with "We couldn't save that just now" and no way out.
 *
 * Now there are three answers ([Outcome]), and only [Outcome.Refused] -- the server looked at the
 * refresh token and said no -- ends the session: the tokens are cleared AND [onSessionEnded] fires,
 * which is what sends the user to sign-in with a sentence saying why. [Outcome.Unreachable] keeps
 * the tokens: the request in hand fails as it would have anyway, and the next one tries again.
 */
class TokenRefresher(
    private val tokens: TokenStore,
    /**
     * Called once when the server refuses the refresh token, AFTER the tokens are cleared.
     *
     * On OkHttp's dispatcher thread, never the main thread: whatever it does must be safe there,
     * which is why the app hands it a flag to set rather than a screen to change.
     */
    private val onSessionEnded: () -> Unit = {},
    /**
     * Performs the refresh call.
     *
     * Injected rather than built here, and it MUST use a client with no auth interceptor and no
     * authenticator attached. A refresh performed through the authenticated client would itself be
     * subject to refresh-on-401, and a failing refresh would then recurse.
     */
    private val exchange: suspend (refreshToken: String) -> Outcome,
) {
    /** What a successful refresh returns. Mirrors the fields of the contract's AuthResponseDto. */
    data class TokenPair(val accessToken: String, val refreshToken: String)

    /** What one refresh call came back with. */
    sealed interface Outcome {
        /** A new pair. Both halves are stored: the server rotates the refresh token every time. */
        data class Renewed(val pair: TokenPair) : Outcome

        /**
         * The server answered and the answer is no: the refresh token is expired, already used,
         * unknown, or belongs to an account that is suspended or deleted. Asking again cannot
         * change that, so this -- and only this -- ends the session.
         */
        data object Refused : Outcome

        /**
         * No answer about the token: no connection, a timeout, a 5xx, or a 408 or 429 (the server
         * saying "not now", not "no"). The session may be perfectly fine, so nothing is cleared.
         */
        data object Unreachable : Outcome

        companion object {
            /**
             * Classifies a refresh that came back WITHOUT a usable pair, by its status code.
             *
             * A 4xx is the server's verdict on the token, except 408 (it timed out waiting for us)
             * and 429 (rate limited) -- both of which are about this moment, not this token. A 5xx
             * is the server's own problem. Anything else that arrives here, such as a 2xx with no
             * body, is a broken answer, and a broken answer is not a refusal.
             */
            fun ofFailedResponse(statusCode: Int): Outcome = when {
                statusCode == 408 || statusCode == 429 -> Unreachable
                statusCode in 400..499 -> Refused
                else -> Unreachable
            }
        }
    }

    private val mutex = Mutex()

    /**
     * Returns a usable access token, refreshing if necessary, or null if there is none to be had
     * right now -- because the session is over, or because the server could not be reached.
     *
     * @param usedToken the access token the failing request carried. Null when it carried none.
     */
    suspend fun refresh(usedToken: String?): String? = mutex.withLock {
        // Someone refreshed while this caller waited for the lock. Nothing to do.
        val current = tokens.accessToken()
        if (current != null && current != usedToken) return@withLock current

        val refreshToken = tokens.refreshToken()
        if (refreshToken == null) {
            // A 401 with no refresh token is simply a signed-out user. Not an error worth
            // reporting, and there is nothing to clear.
            return@withLock null
        }

        // A thrown exchange is a call that never got an answer -- no network, a timeout, a body
        // that would not decode. NOT runCatching: that would also swallow the cancellation of the
        // coroutine waiting on it, and a cancelled screen would read as an unreachable server.
        val outcome = try {
            exchange(refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Outcome.Unreachable
        }

        when (outcome) {
            is Outcome.Renewed -> {
                tokens.save(outcome.pair.accessToken, outcome.pair.refreshToken)
                outcome.pair.accessToken
            }
            // The session is over. Keeping a dead token only produces a second confusing failure
            // on the next request, so it goes now -- and the app is told, so the user is asked to
            // sign in once, with a reason, instead of meeting failed saves.
            Outcome.Refused -> {
                tokens.clear()
                onSessionEnded()
                null
            }
            // Offline, or the server is down. The tokens are KEPT: signing someone out because a
            // train went into a tunnel is the bug this replaced. The request in hand fails as it
            // would have anyway; the next one refreshes again.
            Outcome.Unreachable -> null
        }
    }
}
