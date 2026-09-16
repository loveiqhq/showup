package com.showup.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 */
class TokenRefresher(
    private val tokens: TokenStore,
    /**
     * Performs the refresh call.
     *
     * Injected rather than built here, and it MUST use a client with no auth interceptor and no
     * authenticator attached. A refresh performed through the authenticated client would itself be
     * subject to refresh-on-401, and a failing refresh would then recurse.
     */
    private val exchange: suspend (refreshToken: String) -> TokenPair?,
) {
    /** What a successful refresh returns. Mirrors the fields of the contract's AuthResponseDto. */
    data class TokenPair(val accessToken: String, val refreshToken: String)

    private val mutex = Mutex()

    /**
     * Returns a usable access token, refreshing if necessary, or null if the session is over.
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

        val pair = runCatching { exchange(refreshToken) }.getOrNull()
        if (pair == null) {
            // The refresh token is spent, revoked or the call failed. Either way the session is
            // over: keeping a dead token only produces a second confusing failure on the next
            // request, so it goes now and the user is asked to sign in once.
            tokens.clear()
            return@withLock null
        }

        tokens.save(pair.accessToken, pair.refreshToken)
        pair.accessToken
    }
}
