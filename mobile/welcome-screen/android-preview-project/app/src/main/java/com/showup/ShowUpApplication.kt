package com.showup

import android.app.Application
import com.showup.observability.Crashes

/**
 * The application object, which exists for one reason: crash reporting has to start before anything
 * else does.
 *
 * `Application.onCreate` is the earliest hook the app owns. Starting Sentry in
 * `MainActivity.onCreate` instead would miss every crash that happens during application startup --
 * exactly the crashes that are hardest to reproduce and most likely to affect every user at once.
 *
 * Nothing else belongs here. An Application class is a tempting place for global state, and global
 * state is what `CLAUDE.md` forbids.
 */
class ShowUpApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Returns false and does nothing at all unless a DSN is configured for this build type.
        // Deliberately not logged: a line saying "crash reporting disabled" on every launch is
        // noise, and one saying it is enabled would be a place to accidentally print the DSN.
        Crashes.start(
            context = this,
            enabled = BuildConfig.SENTRY_ENABLED,
            dsn = BuildConfig.SENTRY_DSN,
            environment = if (BuildConfig.DEBUG) "development" else "production",
            release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}",
        )
    }
}
