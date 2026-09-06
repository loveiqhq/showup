package com.showup.observability

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the two things that matter about crash reporting: that it stays off unless configured,
 * and that when it is on it cannot carry personal data off the device.
 *
 * A plain JVM test. No Robolectric, no emulator, no pinned SDK level and no Sentry SDK started --
 * `Crashes.configure` deliberately takes a bare `SentryOptions` and touches no Android type, which
 * is what makes the rules cheap enough to test that they stay true.
 *
 * (The first version of this file did use Robolectric, and failed to even start: Robolectric 4.14
 * refuses a targetSdk of 36. That was a useful signal rather than an obstacle -- a test that needs
 * a whole Android runtime to check a redaction list was testing the wrong seam.)
 */
class CrashesTest {

    private fun options(
        dsn: String = "https://key@example.ingest.de.sentry.io/1",
        environment: String = "production",
        release: String = "com.showup@0.1",
    ): Pair<Boolean, SentryOptions> {
        val o = SentryOptions()
        return Crashes.configure(o, dsn, environment, release) to o
    }

    // ── the switch ──────────────────────────────────────────────────────────

    @Test
    fun `no DSN means reporting does not start`() {
        // The committed default. A build pipeline could flip the flag on before a DSN exists, and
        // that must be inert rather than a crash on launch.
        val (started, _) = options(dsn = "")
        assertFalse("no destination means no reporting", started)
    }

    @Test
    fun `a blank DSN is treated as no DSN`() {
        val (started, _) = options(dsn = "   ")
        assertFalse(started)
    }

    @Test
    fun `a DSN starts reporting with PII collection off`() {
        val (started, o) = options()

        assertTrue(started)
        assertEquals("production", o.environment)
        assertEquals("com.showup@0.1", o.release)
        // Each of these is a separate route by which the SDK would otherwise send personal data.
        assertFalse("the SDK must not attach IP or device identifiers", o.isSendDefaultPii)
        assertFalse("session tracking needs an installation identifier", o.isEnableAutoSessionTracking)
        assertEquals("breadcrumbs capture typed input and request URLs", 0, o.maxBreadcrumbs)
        assertFalse(o.isEnableUserInteractionBreadcrumbs)
    }

    @Test
    fun `a beforeSend hook is always installed when reporting is on`() {
        // Without it every setting above could be right and events would still leave unscrubbed.
        val (_, o) = options()
        assertNotNull("beforeSend must be set", o.beforeSend)
    }

    @Test
    fun `the installed beforeSend hook actually scrubs`() {
        // Asserting the hook exists is not the same as asserting it does anything. This runs the
        // real callback the SDK would run, on an event carrying a phone number.
        val (_, o) = options()
        val hook = requireNotNull(o.beforeSend)

        val event = SentryEvent().apply { extras = mapOf("phone" to "+4917612345678") }
        val out = requireNotNull(hook.execute(event, io.sentry.Hint()))

        assertEquals(Crashes.REDACTED, requireNotNull(out.extras)["phone"])
    }

    // ── the scrubber ────────────────────────────────────────────────────────

    @Test
    fun `a user object is removed entirely`() {
        val event = SentryEvent().apply {
            user = User().apply {
                email = "someone@example.com"
                ipAddress = "203.0.113.4"
            }
        }

        // None of it is needed to fix a crash, and all of it is personal data leaving the device.
        assertNull(Crashes.scrub(event).user)
    }

    @Test
    fun `prohibited extras are redacted and safe look-alikes survive`() {
        val event = SentryEvent().apply {
            extras = mapOf(
                "phone" to "+4917612345678",
                "code" to "123456",
                "refreshToken" to "secret-value",
                "screen" to "PhoneVerification",
                "age_band" to "25-34",
                "error_code" to "step_up_required",
                "token_type" to "Bearer",
            )
        }

        val extras = requireNotNull(Crashes.scrub(event).extras)

        assertEquals(Crashes.REDACTED, extras["phone"])
        assertEquals(Crashes.REDACTED, extras["code"])
        assertEquals(Crashes.REDACTED, extras["refreshToken"])
        // The look-alikes must NOT be redacted. A scrubber that eats everything useful is one
        // somebody eventually switches off, which is worse than a narrower one that stays on.
        assertEquals("PhoneVerification", extras["screen"])
        assertEquals("25-34", extras["age_band"])
        assertEquals("step_up_required", extras["error_code"])
        assertEquals("Bearer", extras["token_type"])
    }

    @Test
    fun `prohibited tags are redacted too`() {
        val event = SentryEvent().apply {
            tags = mapOf("email" to "someone@example.com", "build" to "release")
        }

        val tags = requireNotNull(Crashes.scrub(event).tags)
        assertEquals(Crashes.REDACTED, tags["email"])
        assertEquals("release", tags["build"])
    }

    @Test
    fun `naming style cannot slip a field past the scrubber`() {
        // The reason matching is on the normalised name: an SDK integration or a future call site
        // may use any casing or separator it likes.
        for (variant in listOf("Phone", "phone_number", "PHONE-NUMBER", "phoneNumber", "e_mail")) {
            val event = SentryEvent().apply { extras = mapOf(variant to "sensitive") }
            val extras = requireNotNull(Crashes.scrub(event).extras)
            assertEquals("$variant should be redacted", Crashes.REDACTED, extras[variant])
        }
    }

    @Test
    fun `breadcrumbs are cleared even if an integration added one`() {
        val event = SentryEvent().apply {
            breadcrumbs = listOf(Breadcrumb.userInteraction("click", "phoneField", null))
        }

        assertTrue(Crashes.scrub(event).breadcrumbs.isNullOrEmpty())
    }

    @Test
    fun `an event with nothing in it survives scrubbing`() {
        // The commonest real event: a stack trace with no extras and no tags. Scrubbing must not
        // throw on the nulls, or the scrubber turns every crash into a lost crash.
        val scrubbed = Crashes.scrub(SentryEvent())
        assertNull(scrubbed.user)
    }
}
