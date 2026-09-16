package com.showup.api

/**
 * Where the access and refresh tokens live, and the only thing the network layer knows about auth.
 *
 * WHY THIS IS AN INTERFACE
 *
 * The generated API interfaces must never mention a token. If they did, every call site would be
 * responsible for attaching one, and the first one anybody forgets is an endpoint that silently
 * behaves as though the user were signed out. Auth is attached once, by an OkHttp interceptor, and
 * this is the only thing that interceptor depends on.
 *
 * It also keeps the real storage out of the network layer's tests: [InMemoryTokenStore] is enough
 * to test injection and refresh behaviour without Android's keystore, an emulator, or a device.
 */
interface TokenStore {
    /** The current access token, or null when nobody is signed in. */
    suspend fun accessToken(): String?

    /** The current refresh token, or null when nobody is signed in. */
    suspend fun refreshToken(): String?

    /** Replace both tokens. Called after sign-in and after a successful refresh. */
    suspend fun save(accessToken: String, refreshToken: String)

    /**
     * Forget both tokens.
     *
     * Called on sign-out, and also when a refresh fails: at that point the session is gone whatever
     * the user does next, and keeping a dead token only produces a second confusing failure.
     */
    suspend fun clear()
}

/**
 * A token store that keeps everything in memory.
 *
 * Used by the tests, and deliberately NOT used by the app: tokens must survive the process being
 * killed, which is what the encrypted store in [EncryptedTokenStore] is for.
 */
class InMemoryTokenStore(
    private var access: String? = null,
    private var refresh: String? = null,
) : TokenStore {
    override suspend fun accessToken(): String? = access

    override suspend fun refreshToken(): String? = refresh

    override suspend fun save(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
    }

    override suspend fun clear() {
        access = null
        refresh = null
    }
}
