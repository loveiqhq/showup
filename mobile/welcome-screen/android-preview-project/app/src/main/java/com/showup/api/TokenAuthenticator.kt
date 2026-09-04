package com.showup.api

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Retries a 401 once, with a freshly refreshed token.
 *
 * WHY AN Authenticator RATHER THAN LOGIC IN THE INTERCEPTOR
 *
 * OkHttp calls an Authenticator only after a response comes back 401, and it re-sends the request
 * that the Authenticator returns. Doing the same thing inside an interceptor means hand-rolling the
 * retry, and hand-rolled retries are how a request loop gets written by accident.
 *
 * OkHttp also stops calling this after a small number of consecutive attempts on one call, so a
 * server that returns 401 to everything cannot produce an infinite loop even if the refresh keeps
 * appearing to succeed.
 *
 * Returning null means "give up": the caller sees the original 401, which is correct -- the session
 * is genuinely over and the UI needs to route to sign-in.
 */
class TokenAuthenticator(private val refresher: TokenRefresher) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // The token this request actually carried. Passing it to the refresher is what makes
        // concurrent 401s collapse into one refresh: a caller whose token is already stale-by-
        // comparison takes the new one instead of fetching another.
        val used = response.request.header("Authorization")?.removePrefix("Bearer ")

        // Already retried once with a fresh token and still 401. The problem is not the token.
        if (response.priorResponse != null) return null

        // Bridging suspend to OkHttp's synchronous API. This runs on OkHttp's own dispatcher
        // thread, never the main thread -- see AuthInterceptor for the same reasoning.
        val fresh = runBlocking { refresher.refresh(used) } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }
}
