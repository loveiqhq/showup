/*
 * ChangeSheetsTest.kt
 * ShowUp · the pickers, one test per frame (SHOWUP-196, Date 05 A, B, K, M, N)
 *
 * Each asserts the ticket's copy exactly, as the screen draws it: the strings are the contract, and the
 * verifier that quotes the ticket reads the same ones.
 */
package com.showup.change

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class ChangeSheetsTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-17T18:00:00Z") // 19:00 in Berlin
    private val slots = listOf("2026-11-17T18:15:00Z", "2026-11-17T18:30:00Z", "2026-11-17T18:45:00Z", "2026-11-17T19:00:00Z", "2026-11-17T21:00:00Z")
        .map(Instant::parse)

    private val ctx = ChangeSheetContext(name = "Leonie", bindingTime = meet, bindingPlace = "Cafe Krone", slots = slots, zone = berlin)

    private fun ui(kind: ChangeKind, block: ChangePickerUi.() -> ChangePickerUi = { this }) =
        ChangePickerUi(kind = kind, entry = ChangeEntry.CoordinateTile).block()

    private val note = "19:00 at Cafe Krone stays binding unless Leonie agrees by 18:30. Not showing up there counts as a no-show."

    // ── A ────────────────────────────────────────────────────────────────────

    @Test
    fun `A reads the ticket's copy, the slots as local times, and a disabled Pick a time`() = runComposeUiTest {
        setContent { TimeChangeSheet(ctx, ui(ChangeKind.Time), onSelect = {}, onSuggest = {}, onCancel = {}) }
        onNodeWithText("Suggest a new time").assertExists()
        onNodeWithText("Your current meet time: 19:00").assertExists()
        onNodeWithText("Choose a time within Leonie's availability. Latest possible meet time: 22:00.").assertExists()
        onNodeWithText("Leonie has to agree by 18:30, so you both still arrive on time. If not, 19:00 stays binding.").assertExists()
        for (t in listOf("19:15", "19:30", "19:45", "20:00", "22:00")) onNodeWithText(t).assertExists()
        onNodeWithText(note).assertExists()
        onNodeWithTag(CHANGE_PRIMARY_TAG).assertIsNotEnabled()
        onNodeWithText("Pick a time").assertExists()
        onNodeWithText("Cancel").assertExists()
        onAllNodesWithText("Google Maps").assertCountEquals(0)
    }

    @Test
    fun `A with a slot selected offers Suggest and that slot, and a tap on a slot reports it`() = runComposeUiTest {
        val picked = mutableListOf<Instant>()
        var suggested = 0
        setContent {
            TimeChangeSheet(ctx, ui(ChangeKind.Time) { copy(slot = slots[1]) }, onSelect = { picked += it }, onSuggest = { suggested++ }, onCancel = {})
        }
        onNodeWithText("Suggest 19:30").assertExists()
        onNodeWithTag(CHANGE_PRIMARY_TAG).assertIsEnabled().performClick()
        assertEquals(1, suggested)
        onNodeWithText("19:45").performClick()
        assertEquals(listOf(slots[2]), picked)
    }

    @Test
    fun `Cancel closes, and a send in flight makes both buttons inert`() = runComposeUiTest {
        var cancelled = 0
        var suggested = 0
        var busy by androidx.compose.runtime.mutableStateOf(false)
        setContent {
            TimeChangeSheet(ctx, ui(ChangeKind.Time) { copy(slot = slots[1], sending = busy) }, onSelect = {}, onSuggest = { suggested++ }, onCancel = { cancelled++ })
        }
        onNodeWithTag(CHANGE_CANCEL_TAG).performClick()
        assertEquals(1, cancelled)
        busy = true
        waitForIdle()
        onNodeWithTag(CHANGE_PRIMARY_TAG).performClick()
        onNodeWithTag(CHANGE_CANCEL_TAG).performClick()
        assertEquals(0, suggested)
        assertEquals(1, cancelled)
    }

    // ── K ────────────────────────────────────────────────────────────────────

    @Test
    fun `K keeps the selection, replaces the binding note with the error and Suggest retries`() = runComposeUiTest {
        var suggested = 0
        setContent {
            TimeChangeSheet(ctx, ui(ChangeKind.Time) { copy(slot = slots[1], sendFailed = true) }, onSelect = {}, onSuggest = { suggested++ }, onCancel = {})
        }
        onNodeWithText("Couldn't send your suggestion. Check your connection and try again.").assertExists()
        onAllNodesWithText(note).assertCountEquals(0)
        onNodeWithText("Suggest 19:30").assertExists()
        onNodeWithTag(CHANGE_PRIMARY_TAG).performClick()
        assertEquals(1, suggested)
    }

    // ── B ────────────────────────────────────────────────────────────────────

    private val listed = PlacesView.Loaded(NEARBY + PlaceOption("p-kanal", "Kanal Bar", PlaceType.Bar, "Paul-Lincke-Ufer 21, Kreuzberg", 1100), google = true, forQuery = "")

    @Test
    fun `B reads the ticket's copy, the nearby list with its rows, and the Google attribution`() = runComposeUiTest {
        setContent { PlaceChangeSheet(ctx, ui(ChangeKind.Place) { copy(list = listed) }, onQuery = {}, onSelect = {}, onRetry = {}, onSuggest = {}, onCancel = {}) }
        onNodeWithText("Suggest a new place").assertExists()
        onNodeWithText("Your current meet place: Cafe Krone").assertExists()
        onNodeWithText("Choose a public place: café, bar or restaurant.").assertExists()
        onNodeWithText("Leonie has to agree by 18:30, so you both still arrive on time. If not, Cafe Krone stays binding.").assertExists()
        onNodeWithText("Search cafés, bars, restaurants").assertExists()
        onNodeWithText("Nearby public places").assertExists()
        onNodeWithText("Bar Lenz").assertExists()
        onAllNodesWithText("BAR", useUnmergedTree = true).assertCountEquals(2)
        onNodeWithText("RESTAURANT", useUnmergedTree = true).assertExists()
        onNodeWithText("CAFÉ", useUnmergedTree = true).assertExists()
        onNodeWithText("Lausitzer Platz 9, Kreuzberg · 350 m").assertExists()
        onNodeWithText("Paul-Lincke-Ufer 21, Kreuzberg · 1.1 km").assertExists()
        onNodeWithText("Pick a place").assertExists()
        onNodeWithTag(CHANGE_PRIMARY_TAG).assertIsNotEnabled()
        onNodeWithText("Google Maps").assertExists()
        onNodeWithText(note).assertExists()
    }

    @Test
    fun `B shows no attribution when Google did not answer, and a selected row reads Suggest and its venue`() = runComposeUiTest {
        val picked = mutableListOf<PlaceOption>()
        setContent {
            PlaceChangeSheet(
                ctx, ui(ChangeKind.Place) { copy(list = listed.copy(google = false), place = NEARBY[0]) },
                onQuery = {}, onSelect = { picked += it }, onRetry = {}, onSuggest = {}, onCancel = {},
            )
        }
        onAllNodesWithText("Google Maps").assertCountEquals(0)
        onNodeWithText("Suggest Bar Lenz").assertExists()
        onNodeWithTag(CHANGE_PRIMARY_TAG).assertIsEnabled()
        onNodeWithTag(placeRowTag("p-nove")).performClick()
        assertEquals(listOf(NEARBY[1]), picked)
    }

    @Test
    fun `B while the list loads is empty, with an accessibility description and no visible text`() = runComposeUiTest {
        setContent { PlaceChangeSheet(ctx, ui(ChangeKind.Place), onQuery = {}, onSelect = {}, onRetry = {}, onSuggest = {}, onCancel = {}) }
        onNodeWithTag(CHANGE_LOADING_TAG).assertExists()
        onNode(hasContentDescription("Loading places")).assertExists()
        onAllNodesWithText("Nearby public places").assertCountEquals(0)
        onNodeWithTag(CHANGE_SEARCH_TAG).assertExists()
    }

    @Test
    fun `B typing reports the query, and a typed query's list reads Results`() = runComposeUiTest {
        val typed = mutableListOf<String>()
        setContent {
            PlaceChangeSheet(
                ctx, ui(ChangeKind.Place) { copy(query = "bar", list = listed.copy(forQuery = "bar")) },
                onQuery = { typed += it }, onSelect = {}, onRetry = {}, onSuggest = {}, onCancel = {},
            )
        }
        onNodeWithText("Results").assertExists()
        onAllNodesWithText("Nearby public places").assertCountEquals(0)
        onNodeWithTag(CHANGE_SEARCH_TAG).performTextInput("x")
        assertTrue(typed.last().contains("bar") && typed.last().contains("x"))
    }

    // ── M ────────────────────────────────────────────────────────────────────

    @Test
    fun `M replaces the list with the message and Try again, the search stays and Suggest is disabled`() = runComposeUiTest {
        var retried = 0
        setContent {
            PlaceChangeSheet(ctx, ui(ChangeKind.Place) { copy(list = PlacesView.Failed) }, onQuery = {}, onSelect = {}, onRetry = { retried++ }, onSuggest = {}, onCancel = {})
        }
        onNodeWithText("Couldn't load places. Check your connection and try again.").assertExists()
        onNodeWithText("Try again").assertExists().performClick()
        assertEquals(1, retried)
        onNodeWithTag(CHANGE_SEARCH_TAG).assertExists()
        onNodeWithTag(CHANGE_PRIMARY_TAG).assertIsNotEnabled()
        onAllNodesWithText("Nearby public places").assertCountEquals(0)
        onAllNodesWithText("Google Maps").assertCountEquals(0)
    }

    // ── N ────────────────────────────────────────────────────────────────────

    @Test
    fun `N says nothing was found for the query, with no Try again`() = runComposeUiTest {
        setContent {
            PlaceChangeSheet(
                ctx, ui(ChangeKind.Place) { copy(query = "sushi", list = PlacesView.Loaded(emptyList(), google = true, forQuery = "sushi")) },
                onQuery = {}, onSelect = {}, onRetry = {}, onSuggest = {}, onCancel = {},
            )
        }
        onNodeWithText("No cafés, bars or restaurants found for “sushi”.").assertExists()
        onNodeWithText("Try another name.").assertExists()
        onAllNodesWithText("Try again").assertCountEquals(0)
        onAllNodesWithText("Results").assertCountEquals(0)
        onNodeWithTag(CHANGE_PRIMARY_TAG).assertIsNotEnabled()
    }
}
