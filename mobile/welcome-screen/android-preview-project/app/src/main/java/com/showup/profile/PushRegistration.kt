/*
 * PushRegistration.kt
 * ShowUp · getting the device token to the backend once the user says yes (SHOWUP-162)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS EXISTS SEPARATELY FROM THE SCREEN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The ticket names it as the one failure that looks exactly like success: "granting the permission
 * and never registering the device is a silent failure". The screen cannot tell the difference --
 * the user tapped, the sheet appeared, they said yes, the flow advanced -- and nothing about that
 * sequence reveals that no token ever reached the server.
 *
 * So registration is its own thing, with its own seam, and its absence is loud.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THERE IS NO TOKEN SOURCE IN THIS BUILD, AND THAT IS DELIBERATE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Acquiring an FCM token needs `firebase-messaging` and a `google-services.json`, which means a
 * Firebase project. That is a service-enablement decision and it has not been taken -- the
 * standing instruction on this project is that third-party providers stay off until they are
 * explicitly turned on, and the BACKEND already models exactly this: `FcmPushSender` is selected
 * only when service-account credentials are configured and `LogPushSender` stands in otherwise.
 *
 * This is the client half of the same arrangement. [PushTokenSource] is the seam; [NoPushToken] is
 * the stub that ships today and returns null. When Firebase is enabled, one implementation of one
 * interface is added and nothing else changes.
 *
 * WHAT THAT MEANS FOR THE TICKET. Its acceptance criterion -- "on grant, the device registers for
 * push and the token reaches the backend, verified not assumed" -- CANNOT be met by this build,
 * and pretending otherwise is the silent failure the ticket is warning about. The upload path, the
 * endpoint and the error handling are all built and tested against a fake source; the real source
 * is one class and a dependency away. Recorded in `audit/CONFLICTS-2026-08-27.md`.
 */
package com.showup.profile

import android.util.Log
import com.showup.BuildConfig
import com.showup.api.ShowUpApi
import com.showup.api.generated.model.RegisterPushTokenDto

/**
 * Where a device token comes from.
 *
 * One method, so the real implementation is FCM's `getToken()` and nothing else.
 */
interface PushTokenSource {
    /** The device's current push token, or null when there is no way to get one. */
    suspend fun token(): String?
}

/**
 * The stub that ships today: no Firebase, so no token.
 *
 * NOT AN ERROR AND NOT SILENT. Returning null is the honest answer, and [PushRegistration] treats
 * it as a reportable condition rather than a no-op -- the whole point is that a build which cannot
 * register must not look like one that did.
 */
object NoPushToken : PushTokenSource {
    override suspend fun token(): String? = null
}

/** What happened, so a caller can tell "registered" from "could not". */
sealed interface PushRegistrationResult {
    /** The token reached the backend. */
    data object Registered : PushRegistrationResult

    /** There is no token source in this build. See the file header. */
    data object NoTokenSource : PushRegistrationResult

    /** A token existed and the upload failed. Worth retrying; never worth blocking the flow. */
    data class Failed(val reason: String) : PushRegistrationResult
}

/**
 * Sends the device token to `POST /me/push-tokens`.
 *
 * NEVER BLOCKS THE FLOW. The ticket is explicit that both outcomes of the permission sheet advance
 * to the next screen, and a registration that fails is a background problem rather than something
 * to hold a user on a screen for. The result is returned for logging and for a test to assert on,
 * and the caller is expected to ignore it.
 */
open class PushRegistration(
    private val api: ShowUpApi,
    private val source: PushTokenSource = NoPushToken,
) {
    open suspend fun register(): PushRegistrationResult {
        val token = source.token() ?: run {
            // Loud in a debug build, because "push silently never worked" is the exact outcome
            // this class exists to prevent. Not a crash: the flow must still advance.
            if (BuildConfig.DEBUG) {
                Log.w(
                    "PushRegistration",
                    "permission granted but no token source is configured -- see " +
                        "PushRegistration.kt. The device will receive nothing.",
                )
            }
            return PushRegistrationResult.NoTokenSource
        }
        return runCatching {
            val response = api.notifications.registerPushToken(
                RegisterPushTokenDto(token = token, platform = RegisterPushTokenDto.Platform.android),
            )
            if (response.isSuccessful) {
                PushRegistrationResult.Registered
            } else {
                PushRegistrationResult.Failed("http ${response.code()}")
            }
        }.getOrElse { PushRegistrationResult.Failed(it::class.simpleName ?: "unknown") }
    }
}
