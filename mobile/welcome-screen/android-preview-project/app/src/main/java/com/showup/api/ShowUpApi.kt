package com.showup.api

import com.showup.BuildConfig
import com.showup.api.generated.api.AccountApi
import com.showup.api.generated.api.AuthApi
import com.showup.api.generated.api.CheckInsApi
import com.showup.api.generated.api.MatchingApi
import com.showup.api.generated.api.ProfilesApi
import com.showup.api.generated.api.SafetyApi
import com.showup.api.generated.infrastructure.ApiClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The app's single entry point to the backend.
 *
 * Wraps the generated `ApiClient` rather than replacing it: the generated one already knows how to
 * build Retrofit with the right converter, and it accepts an OkHttpClient.Builder -- which is the
 * seam auth goes through. Nothing here duplicates generated behaviour.
 *
 * NOT a singleton object. It takes its base URL and token store as parameters so a test can point
 * it at a local MockWebServer with a fake store, which is how the client is verified without a
 * backend, a network or an emulator.
 */
class ShowUpApi(
    baseUrl: String = BuildConfig.API_BASE_URL,
    tokens: TokenStore,
) {
    private val client = ApiClient(
        baseUrl = baseUrl,
        okHttpClientBuilder = OkHttpClient.Builder()
            // Auth is added as an application interceptor, not a network one: application
            // interceptors run once per call rather than once per network attempt, so a redirect
            // or a retry cannot produce a second, differently-authenticated request.
            .addInterceptor(AuthInterceptor(tokens))
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
}
