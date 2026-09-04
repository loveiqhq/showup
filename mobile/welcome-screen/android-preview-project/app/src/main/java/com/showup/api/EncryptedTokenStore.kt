package com.showup.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tokens on disk, encrypted with a key held in the Android Keystore.
 *
 * WHY NOT PLAIN SharedPreferences
 *
 * Plain preferences are a world-readable-to-root XML file in the app's data directory. On a rooted
 * device, or through any backup that includes app data, a refresh token in there is a durable
 * credential somebody else can use -- and a refresh token is worth more than an access token,
 * because it mints new ones.
 *
 * EncryptedSharedPreferences encrypts both keys and values with a master key that lives in the
 * Keystore, which is hardware-backed on essentially every device at our API 30 floor. The key
 * cannot be extracted, only used, so a copied file is not a usable credential.
 *
 * WHY THE READS ARE ON Dispatchers.IO
 *
 * The first access unwraps the master key, which touches the Keystore and can take tens of
 * milliseconds. That is small but it is real file and crypto work, and it must never land on the
 * main thread.
 */
class EncryptedTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences by lazy {
        // MasterKeys is deprecated in favour of MasterKey.Builder, which only exists in
        // security-crypto 1.1.0-alpha. A credential store is the wrong place to adopt an alpha
        // dependency to silence a deprecation warning, so this stays on the stable 1.0.0 API. The
        // upgrade is a four-line change the day 1.1.0 ships stable.
        @Suppress("DEPRECATION")
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        @Suppress("DEPRECATION")
        EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACCESS, null)
    }

    override suspend fun refreshToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_REFRESH, null)
    }

    override suspend fun save(accessToken: String, refreshToken: String) {
        withContext(Dispatchers.IO) {
            // commit() rather than apply(): apply() writes asynchronously, and a process death
            // between a successful refresh and the write landing would leave the app holding a
            // refresh token the server has already rotated away -- an unrecoverable signed-out
            // state that looks like a random logout.
            prefs.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .commit()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
    }

    private companion object {
        const val FILE_NAME = "showup_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
    }
}
