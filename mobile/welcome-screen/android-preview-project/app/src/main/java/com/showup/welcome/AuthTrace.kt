/*
 * AuthTrace.kt
 * ShowUp · proving which issued code a verification attempt was answering
 *
 * WHY THIS EXISTS
 *
 * On 15 September 2026 a correct code was reported as rejected, a resend appeared to do nothing,
 * and a later code then worked. Every one of those is a statement about WHICH CHALLENGE was live
 * at each step, and nothing in the app recorded that -- so the report could not be confirmed or
 * dismissed from anything but theory. This is the record that makes the next one answerable.
 *
 * WHAT IT DELIBERATELY DOES NOT LOG
 *
 * Not the code. Not the phone number. Not a token. Not an email. The shared CLAUDE.md is
 * unconditional about it -- "No logging of a phone number, a verification code, a token or an
 * email. Ever." -- and a debug build is still a build that runs on somebody's phone, with a
 * logcat any installed app could read before Android 4.1 and any USB cable can read today.
 *
 * That constraint costs nothing here, because the question is not "what was the code" but "was
 * the code being answered the one the server had issued last". A challenge NUMBER answers that
 * exactly, and reveals nothing: it is a counter that starts at 1 each launch.
 *
 * DEBUG ONLY. `BuildConfig.DEBUG` is a compile-time constant, so R8 removes both the call sites
 * and this object from a release build -- verified with dexdump, the same way DevOfflineAuth is.
 */
package com.showup.welcome

import android.util.Log
import com.showup.BuildConfig

/** A running account of challenges issued and answers received, for the emulator and nowhere else. */
object AuthTrace {

    private const val TAG = "ShowUpAuth"

    /**
     * Records one step of the challenge lifecycle.
     *
     * Callers pass challenge numbers and outcomes. If a caller ever needs to pass a code to make
     * a message useful, that is the signal that the message is the wrong shape -- not that the
     * rule should bend.
     */
    fun log(message: String) {
        if (BuildConfig.DEBUG) sink(message)
    }

    /**
     * Where a line goes. Logcat on a device; a collector in a test.
     *
     * Swappable for two reasons, and the second is the better one. `android.util.Log` is not
     * implemented in a JVM unit test, so a hard reference makes every test that touches this
     * throw. And a seam here lets a test ASSERT the trace -- which matters, because the trace is
     * the thing that proves which issued code an attempt was answering. A record nobody checks
     * is a record that quietly stops being written.
     */
    internal var sink: (String) -> Unit = { Log.i(TAG, it) }
}
