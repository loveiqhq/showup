/*
 * DateLocationGateTest.kt
 * ShowUp · G, the location gate over the Date screen (SHOWUP-195): the date card, the copy, the pill
 */
package com.showup.profile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class DateLocationGateTest {
    private val meet = Instant.parse("2026-11-10T18:00:00Z")

    private fun context(now: Instant, day: String = "today", place: String? = "Cafe Krone") = LocationScreenContext.Date(
        name = "Leonie", meetDay = day, meetAt = meet, place = place, photoUrl = null,
        now = now, zone = ZoneId.of("Europe/Berlin"), onTab = {},
    )

    @Test
    fun `gatePill counts down today, counts up after, and is silent tomorrow`() {
        assertEquals(GatePill.Starts(1, 18), gatePill("today", meet, meet.minusSeconds(78 * 60)))
        assertEquals(GatePill.Started(10), gatePill("today", meet, meet.plusSeconds(10 * 60)))
        assertNull(gatePill("tomorrow", meet, meet.minusSeconds(20 * 3600)))
    }

    @Test
    fun `denied draws the date card, the date headline, its body and Open Settings, with no privacy note`() = runComposeUiTest {
        var settings = 0
        setContent {
            ProfileLocationScreen(
                state = LocationState.Denied, onOpenSettings = { settings++ },
                context = context(meet.minusSeconds(78 * 60)),
            )
        }
        onNodeWithTag(DATE_GATE_CARD_TAG).assertExists()
        onNodeWithText("You and Leonie are meeting today", substring = true).assertExists()
        onNodeWithText("Starts in 1h 18m").assertExists()
        onNodeWithTag(GATE_HEADLINE_TAG).assertExists()
        onNodeWithText("Urgent! Turn on location to get access to your", substring = true).assertExists()
        onNodeWithText(
            "Location is off for Show Up, so your date screen is locked. Turn it on to see your date, get directions " +
                "and check in when you arrive, so Leonie knows you’re there.",
        ).assertExists()
        onAllNodesWithTag(GATE_NOTE_TAG).assertCountEquals(0)
        onNodeWithText("Open Settings").assertExists()
        onNodeWithTag(DATE_GATE_CTA_TAG).performClick()
        assertEquals(1, settings)
    }

    @Test
    fun `not determined draws Allow location access and the privacy note`() = runComposeUiTest {
        var allowed = 0
        setContent {
            ProfileLocationScreen(state = LocationState.Ask, onAllow = { allowed++ }, context = context(meet.minusSeconds(78 * 60)))
        }
        onNodeWithText("Urgent! Allow location to get access to your", substring = true).assertExists()
        onNodeWithText("Allow location access").assertExists()
        onNodeWithTag(GATE_NOTE_TAG).assertExists()
        onNodeWithTag(DATE_GATE_CTA_TAG).performClick()
        assertEquals(1, allowed)
    }

    @Test
    fun `late, the pill is red and the match may be waiting`() = runComposeUiTest {
        setContent { ProfileLocationScreen(state = LocationState.Denied, context = context(meet.plusSeconds(10 * 60))) }
        onNodeWithText("Started 10 min ago").assertExists()
        onNodeWithText("Leonie may be waiting").assertExists()
    }

    @Test
    fun `tomorrow draws no pill`() = runComposeUiTest {
        setContent { ProfileLocationScreen(state = LocationState.Denied, context = context(meet.minusSeconds(20 * 3600), day = "tomorrow")) }
        onNodeWithText("You and Leonie are meeting tomorrow", substring = true).assertExists()
        onAllNodesWithText("Starts in", substring = true).assertCountEquals(0)
    }
}
