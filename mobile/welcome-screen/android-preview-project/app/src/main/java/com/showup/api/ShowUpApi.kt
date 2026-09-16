package com.showup.api

import com.showup.BuildConfig
import com.showup.api.generated.api.AccountApi
import com.showup.api.generated.api.AuthApi
import com.showup.api.generated.api.CheckInsApi
import com.showup.api.generated.api.MatchingApi
import com.showup.api.generated.api.ProfilesApi
import com.showup.api.generated.api.SafetyApi
import com.showup.api.generated.infrastructure.ApiClient
import com.showup.api.generated.model.LogoutDto
import com.showup.api.generated.model.RefreshDto
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The app's single entry point to the backend.
 *
 * Wraps the generated `ApiClient` rather than replacing it: the generated one already builds
 * Retrofit with the right converters, and it accepts an OkHttpClient.Builder -- which is the seam
 * auth goes through. Nothing here duplicates generated behaviour.
 *
 * NOT a singleton object. It takes its base URL and token store as parameters so a test can point
 * it at a local MockWebServer with an in-memory store, which is how all of this is verified without
 * a backend, a network or an emulator.
 */
class ShowUpApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val tokens: TokenStore,
) {
    /**
     * A client with NO auth attached, used only to refresh.
     *
     * This separation is load-bearing. If the refresh call went through the authenticated client it
     * would itself be subject to refresh-on-401, so a refresh token the server has revoked would
     * trigger a refresh, which would fail 401, which would trigger a refresh. OkHttp's retry cap
     * would eventually stop it, but only after several pointless round trips and with a failure
     * that reads as a network problem rather than an expired session.
     */
    private val bareAuthApi: AuthApi by lazy {
        ApiClient(baseUrl = baseUrl, converterFactories = ApiJson.converterFactories)
            .createService(AuthApi::class.java)
    }

    private val refresher = TokenRefresher(tokens) { refreshToken ->
        val response = bareAuthApi.refreshAuthToken(USER_AGENT, RefreshDto(refreshToken = refreshToken))
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            null
        } else {
            // Both tokens, not just the access token: the backend ROTATES the refresh token on
            // every successful refresh, so storing only the new access token would leave the app
            // holding a refresh token that is already spent.
            TokenRefresher.TokenPair(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
            )
        }
    }

    private val client = ApiClient(
        baseUrl = baseUrl,
        // Omits unset optionals rather than sending them as explicit nulls -- see ApiJson. Without
        // this, any partial update wipes every field the caller did not set.
        converterFactories = ApiJson.converterFactories,
        okHttpClientBuilder = OkHttpClient.Builder()
            // An application interceptor, not a network one: application interceptors run once per
            // call rather than once per network attempt, so a redirect or retry cannot produce a
            // second, differently-authenticated request.
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(refresher))
            // Explicit timeouts. OkHttp's defaults are 10s, which is generous on a train and
            // indistinguishable from a hang to a user holding a phone.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS),
    )

    // One property per API surface the app actually uses. Deliberately not all ten generated
    // interfaces: the admin surfaces are not part of this app, and exposing them here would invite
    // a screen to call one.
    val auth: AuthApi by lazy { client.createService(AuthApi::class.java) }
    val account: AccountApi by lazy { client.createService(AccountApi::class.java) }
    val profiles: ProfilesApi by lazy { client.createService(ProfilesApi::class.java) }
    val checkIns: CheckInsApi by lazy { client.createService(CheckInsApi::class.java) }
    val matching: MatchingApi by lazy { client.createService(MatchingApi::class.java) }
    val safety: SafetyApi by lazy { client.createService(SafetyApi::class.java) }

    /**
     * Signs the user out: revokes the session server-side, then forgets the tokens locally.
     *
     * THE ORDER MATTERS, AND SO DOES IGNORING THE RESULT.
     *
     * The server call goes first, because it needs the refresh token that the second step deletes.
     * And the local clear happens whether or not that call succeeds: a user who taps sign out on a
     * plane must end up signed out. Leaving the tokens because the network was unavailable means
     * the app still looks signed in, which is both surprising and a genuine privacy problem on a
     * shared device.
     *
     * The consequence -- a refresh token that stays valid server-side until it expires -- is the
     * lesser harm, and it is the same thing that happens if the app is uninstalled mid-session.
     */
    suspend fun signOut() {
        val refreshToken = tokens.refreshToken()
        if (refreshToken != null) {
            runCatching { bareAuthApi.logout(LogoutDto(refreshToken = refreshToken)) }
        }
        tokens.clear()
    }

    companion object {
        /**
         * What the backend records against a session so a person can see where they are signed in.
         *
         * Not private: two routes send it -- refresh, here, and `/auth/phone/verify`, called from
         * PhoneAuthRepository. A second definition would make the devices list disagree with
         * itself, the same phone appearing under two names depending on which request made the row.
         */
        const val USER_AGENT = "ShowUp-Android/${BuildConfig.VERSION_NAME}"
    }
}
