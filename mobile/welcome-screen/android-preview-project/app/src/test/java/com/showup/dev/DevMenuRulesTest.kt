package com.showup.dev

import com.showup.analytics.EventLog
import com.showup.profile.LocationArrival
import com.showup.profile.LocationState
import com.showup.welcome.FlowScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The developer menu's rules: the event log, the screen list, and where a jump lands.
 *
 * The jump itself reuses the walk's own ways in (`afterMedia`, `enterLocation`), which are covered
 * where they live; what is pinned here is what the menu PROMISES before the tap -- that it names
 * the same skip the walk would make, and refuses what a signed-out user cannot use.
 */
class DevMenuRulesTest {

    // ── the event log ──────────────────────────────────────────────────────────

    @Test
    fun `events are listed newest first, with what they carried`() {
        var now = 1_000L
        val log = EventLog(clock = { now })
        log.record("screen_viewed", mapOf("screen_id" to "profile_height"))
        now = 2_000L
        log.record("detail_answered", mapOf("field_id" to "height"))

        val entries = log.entries.value
        assertEquals(listOf("detail_answered", "screen_viewed"), entries.map { it.event })
        assertEquals(2_000L, entries.first().atMillis)
        assertEquals("profile_height", entries.last().properties["screen_id"])
    }

    @Test
    fun `the log is capped, dropping the oldest`() {
        val log = EventLog(capacity = 3)
        repeat(5) { log.record("e$it", emptyMap()) }

        assertEquals(listOf("e4", "e3", "e2"), log.entries.value.map { it.event })
    }

    @Test
    fun `a recorded event cannot be changed afterwards by its caller`() {
        val log = EventLog()
        val props = mutableMapOf<String, Any>("a" to 1)
        log.record("x", props)
        props["a"] = 2

        assertEquals(1, log.entries.value.single().properties["a"])
    }

    @Test
    fun `clear empties it, and ids keep rising so rows never collide`() {
        val log = EventLog()
        log.record("a", emptyMap())
        val firstId = log.entries.value.single().id
        log.clear()
        assertTrue(log.entries.value.isEmpty())

        log.record("b", emptyMap())
        assertTrue(log.entries.value.single().id > firstId)
    }

    @Test
    fun `a prohibited key is withheld in the log, everything else is shown`() {
        assertEquals("[withheld]", shownValue("email", "someone@example.com"))
        assertEquals("[withheld]", shownValue("phone", "+4917600000000"))
        assertEquals("profile_height", shownValue("screen_id", "profile_height"))
        // The tutorial's card NUMBER, not a payment card -- withheld, it read as a broken event.
        assertEquals("2", shownValue("card", 2))
        assertEquals("[withheld]", shownValue("cardnumber", "4111111111111111"))
    }

    // ── the screen list ────────────────────────────────────────────────────────

    @Test
    fun `every screen is listed exactly once, in flow order within its section`() {
        val listed = DevSection.entries.flatMap { section -> FlowScreen.entries.filter { it.devSection() == section } }
        assertEquals(FlowScreen.entries.toList(), listed)
    }

    @Test
    fun `every screen has its own name`() {
        val labels = FlowScreen.entries.map { it.devLabel() }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `the profile screens need an account, sign-up, the tutorial and home do not`() {
        assertFalse(FlowScreen.SignUp.needsAccount)
        assertFalse(FlowScreen.ThirtyMinutes.needsAccount)
        assertFalse(FlowScreen.Home.needsAccount)
        assertTrue(FlowScreen.ProfileName.needsAccount)
        assertTrue(FlowScreen.ProfilePhotos.needsAccount)
        assertTrue(FlowScreen.ProfileLocation.needsAccount)
        assertTrue(FlowScreen.ProfileGender.needsAccount)
    }

    // ── where a jump lands ─────────────────────────────────────────────────────

    private val ask = LocationArrival.Show(LocationState.Ask)

    private fun jump(
        target: FlowScreen,
        signedIn: Boolean = true,
        notificationsAsk: Boolean = true,
        oldAndroid: Boolean = false,
        location: LocationArrival = ask,
    ) = devJump(target, signedIn, notificationsAsk, oldAndroid, location)

    @Test
    fun `signed out, a profile screen is refused and the tutorial is not`() {
        assertEquals(DevJump.NeedsLogIn, jump(FlowScreen.ProfileHeight, signedIn = false))
        assertEquals(DevJump.Lands(FlowScreen.MatchMeansMeet), jump(FlowScreen.MatchMeansMeet, signedIn = false))
    }

    @Test
    fun `notifications already answered skip to stay reachable, as the walk does`() {
        val skipped = jump(FlowScreen.ProfileNotifications, notificationsAsk = false)
        assertTrue(skipped is DevJump.Skipped)
        assertEquals(FlowScreen.ProfileReachability, (skipped as DevJump.Skipped).lands)
        assertEquals("Skipped: already answered on this phone", skipped.why)

        val old = jump(FlowScreen.ProfileNotifications, notificationsAsk = false, oldAndroid = true) as DevJump.Skipped
        assertEquals("Skipped: Android 12 and older never asks", old.why)

        assertEquals(DevJump.Lands(FlowScreen.ProfileNotifications), jump(FlowScreen.ProfileNotifications))
    }

    @Test
    fun `location already allowed skips to embrace 2, any other status shows the screen`() {
        val skipped = jump(FlowScreen.ProfileLocation, location = LocationArrival.Skip) as DevJump.Skipped
        assertEquals(FlowScreen.ProfileEmbraceDetails, skipped.lands)

        for (state in LocationState.entries) {
            assertEquals(
                DevJump.Lands(FlowScreen.ProfileLocation),
                jump(FlowScreen.ProfileLocation, location = LocationArrival.Show(state)),
            )
        }
    }

    @Test
    fun `every other screen lands where asked`() {
        val special = setOf(FlowScreen.ProfileNotifications, FlowScreen.ProfileLocation)
        for (screen in FlowScreen.entries - special) {
            assertEquals(screen.name, DevJump.Lands(screen), jump(screen))
        }
    }
}
