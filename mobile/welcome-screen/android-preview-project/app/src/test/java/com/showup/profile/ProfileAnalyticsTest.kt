package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tracking vocabulary, pinned to registry 1.3.0.
 *
 * WHY THESE EXIST
 *
 * Every value here came from a JSON file that shipped four times with a version badge concatenated
 * into its identifiers -- `emailv1.2`, `agev1.2`, `dobv1.2` -- and the corruption survived two
 * bundle refreshes before it was fixed. These assertions are what make the next such drift a
 * failing build rather than a payload nobody reads until a funnel is already broken.
 *
 * They test pure builders. Neither app has an analytics sink yet (brief Step 2, its own ticket), so
 * nothing here proves an event was delivered -- only that if it were, it would carry the vocabulary
 * the registry defines.
 */
class ProfileAnalyticsTest {

    // ── the vocabulary that was corrupted ─────────────────────────────────────

    @Test
    fun `step ids carry no version suffix`() {
        // The exact defect: enums.json §2 read "namev1.2", "emailv1.2", "email_verifyv1.2",
        // "dobv1.2". Fixed in registry 1.3.0; this is what keeps it fixed.
        val ids = BasicsStep.entries.map { it.stepId }
        assertEquals(listOf("name", "email", "email_verify", "dob"), ids)
        ids.forEach { assertFalse("'$it' carries a version badge", Regex("v\\d+\\.\\d+$").containsMatchIn(it)) }
    }

    @Test
    fun `step indices match the registry, with email_verify holding at 2`() {
        // §2 gives 1 / 2 / 2 / 3. email_verify sharing 2 with email is the whole reason
        // screen_viewed and profile_step_viewed are not duplicates of one another.
        assertEquals(listOf(1, 2, 2, 3), BasicsStep.entries.map { it.stepIndex })
    }

    @Test
    fun `the field registry version matches the bundle that defined the vocabulary`() {
        // enums.json bumped 1.2.0 -> 1.3.0 when the identifiers were corrected, 1.3.1 -> 1.4.2
        // on 16 September 2026, and 1.4.4 -> 1.4.5 on 25 September, which RETIRED §18 prompt
        // `entry_point`, took `position` off every prompt event with it, and moved `is_edit` onto
        // `prompt_editor_dismissed`. Stamping an older string would claim a vocabulary these
        // payloads are not using -- and this test is the thing that noticed, twice, which is
        // exactly what it is for.
        assertEquals("1.4.5", Stamp.FIELD_REGISTRY_VERSION)
    }

    // ── consent_changed, unblocked by registry 1.3.0 ──────────────────────────
    //
    // The unblocking was 1.3.0's; the stamp on the payload is whatever the CURRENT bundle is, and
    // those are two different facts. A test that pinned the stamp to the version that unblocked
    // the event would fail on every later bump for no reason.

    @Test
    fun `consent uses the marketing_email channel, not email`() {
        // One address, three uses. "email" is the Stay reachable toggle about match contact;
        // transactional mail has no consent at all. Filing a marketing opt-in against "email"
        // would put it on the wrong channel.
        val (_, payload) = ProfileAnalytics.consentChanged(on = true)
        assertEquals("marketing_email", payload["channel"])
    }

    @Test
    fun `consent defaults to the profile_creation surface`() {
        val (_, payload) = ProfileAnalytics.consentChanged(on = true)
        assertEquals("profile_creation", payload["surface"])
    }

    @Test
    fun `consent fires in both directions`() {
        // Unlike field_display_opted_out, which is opt-out only. A consent record that logs the
        // grant but not the withdrawal cannot answer "was this person opted in on date X".
        val (nameOn, on) = ProfileAnalytics.consentChanged(on = true)
        val (nameOff, off) = ProfileAnalytics.consentChanged(on = false)
        assertEquals(nameOn, nameOff)
        assertEquals(true, on["on"])
        assertEquals(false, off["on"])
    }

    @Test
    fun `consent is a class 1 attribute event`() {
        val (_, payload) = ProfileAnalytics.consentChanged(on = true)
        assertEquals(1, payload["sensitivity_class"])
        assertEquals("1.4.5", payload["field_registry_version"])
    }

    // ── the rule the ticket calls a bug to break ──────────────────────────────

    @Test
    fun `email validation only ever reports format, never disposable`() {
        // "disposable is reserved in the enum and deliberately not implemented... emitting it today
        // is a bug" -- SHOWUP-152. The value stays in the registry so it need not be re-added.
        val (_, payload) = ProfileAnalytics.emailValidationFailed()
        assertEquals("format", payload["rule"])
    }

    // ── SHOWUP-155 · the bridge is a screen and NOT a step ────────────────────

    @Test
    fun `the bridge has a screen row and reports the build variant`() {
        val (name, payload) = ProfileAnalytics.embraceBridgeViewed()
        assertEquals("embrace_bridge_viewed", name)
        assertEquals("build_profile", payload["variant"])
        assertEquals(0, payload["sensitivity_class"])
    }

    @Test
    fun `the bridge screen carries both registry vocabularies`() {
        val (_, payload) = ProfileAnalytics.screenViewed(ProfileScreen.EmbraceBuild)
        // §11: screen_id is the stable key, screen_name the human label. Two vocabularies, and
        // collapsing them breaks one of the two uses.
        assertEquals("profile_embrace_build", payload["screen_id"])
        assertEquals("ProfileEmbraceBuild", payload["screen_name"])
    }

    @Test
    fun `no BasicsStep exists for the bridge, so no step event can name it`() {
        // The registry's own words: the §2 row is deliberately absent "because firing
        // profile_step_viewed on it would put a phantom step in the completion funnel". The rule
        // is carried by the type system -- `stepViewed` takes a BasicsStep and there is none --
        // and this test is what would notice somebody adding one.
        assertEquals(
            listOf("name", "email", "email_verify", "dob"),
            BasicsStep.entries.map { it.stepId },
        )
    }

    @Test
    fun `the sibling bridge variant is registered but unused`() {
        // §16 is a closed pair. It lives in one place so the sibling bridge does not arrive with
        // its value typed into a second ticket, which is how a vocabulary drifts.
        assertEquals("add_details", EmbraceVariant.ADD_DETAILS)
        assertEquals(
            "add_details",
            ProfileAnalytics.embraceBridgeViewed(EmbraceVariant.ADD_DETAILS).second["variant"],
        )
    }

    // ── privacy ───────────────────────────────────────────────────────────────

    @Test
    fun `no profile payload carries an address, a name or a raw date`() {
        val payloads = listOf(
            ProfileAnalytics.nameSubmitted(charCount = 3).second,
            ProfileAnalytics.emailSubmitted(domain = "hey.com").second,
            ProfileAnalytics.consentChanged(on = true).second,
            ProfileAnalytics.screenViewed(ProfileScreen.Email).second,
        )
        val forbidden = listOf("leo@hey.com", "Leo", "1998-04-23")
        payloads.forEach { p ->
            val rendered = p.values.joinToString(" ")
            forbidden.forEach { secret ->
                assertFalse("payload leaked '$secret': $p", rendered.contains(secret))
            }
        }
        // The domain is the one part of an address that does travel -- a disposable-domain rate is
        // answerable, a list of addresses is not something we collect.
        assertTrue(
            ProfileAnalytics.emailSubmitted(domain = "hey.com").second.containsValue("hey.com"),
        )
    }
}
