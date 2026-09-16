package com.showup.observability

import android.content.Context
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

/**
 * Crash and error reporting, off unless it is switched on and given a destination.
 *
 * WHY THE SWITCH, AND WHY OFF IS THE DEFAULT
 *
 * The same shape as the backend's `initSentry` (Epic 16): with `SENTRY_ENABLED` false, or with no
 * DSN, nothing is initialised and nothing is ever sent -- so the app builds and runs with no
 * account, no credentials and no network calls to a third party. Anyone can clone this repository
 * and build it without asking for a key.
 *
 * WHAT IS DELIBERATELY NOT COLLECTED
 *
 * A crash reporter is a pipe out of the device, and this is a dating app: the data within reach of
 * a stack trace includes phone numbers, one-time codes, coordinates and message text. So:
 *
 *   - `isSendDefaultPii = false`, so the SDK never attaches IP address or device identifiers of
 *     its own accord.
 *   - `beforeSend` walks every event and redacts any field whose NAME is prohibited, using the same
 *     list as the backend. Names, not values -- a value-based check cannot recognise a phone number
 *     it has not seen the format of.
 *   - breadcrumbs are dropped entirely. They are the most useful feature here and also the most
 *     dangerous: they capture UI interactions and network URLs automatically, which on the phone
 *     screen means the number being typed. Reinstate them only with an explicit allowlist.
 *   - session tracking is off, because it exists to measure release health and needs an installation
 *     identifier to do it.
 *
 * Auto-init is disabled in the manifest, so this function is the only way the SDK ever starts.
 */
object Crashes {

    /**
     * Starts reporting if configured, and returns whether it did.
     *
     * A thin wrapper. The gate and the whole configuration live in [configure], which needs no
     * Android at all -- so the rules that actually matter are unit-tested without Robolectric, an
     * emulator, or a pinned SDK level.
     */
    fun start(
        context: Context,
        enabled: Boolean,
        dsn: String,
        environment: String,
        release: String,
    ): Boolean {
        // Checked here as well as in configure, because the SDK must not be touched at all when
        // reporting is off -- SentryAndroid.init installs integrations before the options lambda
        // has any say.
        if (!enabled || dsn.isBlank()) return false

        SentryAndroid.init(context) { options ->
            configure(options, dsn, environment, release)
        }
        return true
    }

    /**
     * Applies every setting, and returns whether reporting should happen.
     *
     * Separate from [start] and free of Android types on purpose: this is the part with the rules
     * in it, and a rule nobody can test cheaply is a rule that quietly stops being true.
     */
    internal fun configure(
        options: SentryOptions,
        dsn: String,
        environment: String,
        release: String,
    ): Boolean {
        if (dsn.isBlank()) return false

        options.dsn = dsn
        options.environment = environment
        options.release = release

        // Never let the SDK attach personal information of its own accord.
        options.isSendDefaultPii = false

        // Release health needs an installation identifier to attribute sessions. Not worth a
        // persistent identifier on this app.
        options.isEnableAutoSessionTracking = false

        // See the note above: breadcrumbs auto-capture typed UI and request URLs.
        options.maxBreadcrumbs = 0
        options.isEnableUserInteractionBreadcrumbs = false

        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
        return true
    }

    /**
     * Redacts prohibited fields from an event, in place.
     *
     * Returns the event rather than null: dropping the whole report would lose the crash as well as
     * the personal data, and the crash is the reason any of this exists.
     */
    internal fun scrub(event: SentryEvent): SentryEvent {
        // A user object here would carry an id, an email or an IP. None of it is needed to fix a
        // crash, and all of it is personal data leaving the device.
        event.user = null as User?

        event.extras = event.extras?.mapValues { (key, value) ->
            if (ProhibitedFields.isProhibited(key)) REDACTED else value
        }

        event.tags = event.tags?.mapValues { (key, value) ->
            if (ProhibitedFields.isProhibited(key)) REDACTED else value
        }

        // Breadcrumbs are configured off, but an SDK integration can still add one before this
        // runs, so they are cleared here too rather than trusted to stay empty.
        event.breadcrumbs = emptyList()

        return event
    }

    internal const val REDACTED = "[redacted]"
}
