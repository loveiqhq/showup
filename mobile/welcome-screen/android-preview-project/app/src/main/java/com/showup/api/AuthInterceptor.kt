package com.showup.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the bearer token to every request, in one place.
 *
 * WHY AN INTERCEPTOR AND NOT A PARAMETER
 *
 * The generated API interfaces are regenerated from the backend contract on every build, so they
 * cannot carry auth: anything added to them is erased. More importantly, auth as a parameter means
 * every call site can forget it, and a forgotten token does not fail loudly -- the endpoint just
 * behaves as though nobody were signed in.
 *
 * Here it is impossible to forget, and there is exactly one place to change when it changes.
 *
 * WHY runBlocking IS ACCEPTABLE HERE
 *
 * OkHttp's interceptor API is synchronous and [TokenStore] is suspending, so something has to
 * bridge them. This runs on OkHttp's own background dispatcher thread, never on the main thread --
 * a request is already off-main by the time an interceptor sees it. Reading a token is a keystore
 * read of a few hundred microseconds, not I/O.
 *
 * Refresh-on-401 is NOT here. It belongs with the token store, because it must be single-flight
 * across concurrent requests, and an interceptor has no way to coordinate that on its own.
 */
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokens.accessToken() }

        // No token is a normal state, not an error: the sign-in endpoints are public and are
        // called precisely when there is nothing to attach. Send the request as-is and let the
        // backend decide -- it returns 401 if the endpoint needed one.
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        return chain.proceed(request)
    }
}
