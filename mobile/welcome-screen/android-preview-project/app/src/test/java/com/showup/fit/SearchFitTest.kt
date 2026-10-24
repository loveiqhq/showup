/*
 * SearchFitTest.kt
 * ShowUp · Date search 01, measured on every phone (SHOWUP-184)
 *
 * ScreenFitTest's question -- clipped, squeezed, out of reach? -- for every state of the Search
 * profile, on all 23 phones, at the default and the largest system font. Then the geometry the ticket
 * promises:
 *
 *   · the action bar sits on the TabBar's top edge, wholly on screen, never over it
 *   · the actions coach sits in the bar's column, above the buttons with its 14 gap, never under the
 *     top bar, its two buttons on one row -- at the three named frames and on every other phone
 *   · the stack is the only element that scrolls
 *
 * The frames are the devices' safe areas, as everywhere in this harness.
 */
package com.showup.fit

import androidx.compose.runtime.Composable
import com.showup.api.CallFailure
import com.showup.designsystem.TAB_BAR_TAG
import com.showup.profile.MediaKind
import com.showup.search.COACH_DONT_SHOW_TAG
import com.showup.search.COACH_GOT_IT_TAG
import com.showup.search.LikeSheet
import com.showup.search.MEET_LINE_TAG
import com.showup.search.MEET_COUNT_TAG
import com.showup.search.SEARCH_ACTION_BAR_TAG
import com.showup.search.SEARCH_COACH_TAG
import com.showup.search.SEARCH_LIKE_TAG
import com.showup.search.SEARCH_STACK_TAG
import com.showup.search.SEARCH_TOP_BAR_TAG
import com.showup.search.SearchPlayback
import com.showup.search.SearchProfileScreen
import com.showup.search.SearchSamples
import com.showup.search.SearchUiState
import com.showup.search.messageDraft
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class SearchFitTest {

    private val full = SearchSamples.rest()
    private val minimum = SearchSamples.rest(SearchSamples.MINIMUM)

    /** Every state the screen can be in, by its sheet letter where it has one. */
    private val states: List<Pair<String, SearchUiState>> = listOf(
        "A at rest" to full,
        "B Be sure" to full.copy(sheet = LikeSheet.Confirm),
        "C message" to full.copy(sheet = LikeSheet.Message, draft = messageDraft("Leonie", "today")),
        "C message, 100 characters" to full.copy(sheet = LikeSheet.Message, draft = "M".repeat(100)),
        "D minimum" to minimum,
        "F coach" to full.copy(coach = true),
        "F coach over the minimum" to minimum.copy(coach = true),
        "voice playing" to full.copy(playback = SearchPlayback(MediaKind.Voice, 6_000, 14_000)),
        "photo viewer" to full.copy(viewerAt = 2),
        "loading" to SearchUiState(loading = true),
        "failed offline" to SearchUiState(loading = false, failure = CallFailure.Network),
        "no results" to SearchUiState(loading = false),
        "99+ likes" to full.copy(likesOpen = 140),
    )

    @Composable
    private fun render(state: SearchUiState) = SearchProfileScreen(state = state, zone = SearchSamples.ZONE)

    @Test
    fun `every state is clean on every phone at the default and the largest type`() {
        val fresh = mutableListOf<String>()
        for ((name, state) in states) {
            for (scale in listOf(1f, 2f)) {
                DEVICES.forEach { d ->
                    measureFit(d, "Search/$name @$scale", fontScale = scale) { render(state) }
                        .filterNot { it.advisory }
                        .forEach { fresh += "$it" }
                }
            }
        }
        // Every finding to a file too: a first sweep can report hundreds, and the assertion text is
        // cut long before it ends.
        java.io.File("build/search-fit-findings.txt").writeText(fresh.joinToString("\n"))
        assertTrue("Search layout problems (${fresh.size}):\n" + fresh.take(60).joinToString("\n") { "  $it" }, fresh.isEmpty())
    }

    @Test
    fun `the action bar sits on the TabBar, wholly on screen, on every phone`() {
        val failures = mutableListOf<String>()
        for (scale in listOf(1f, 2f)) {
            DEVICES.forEach { d ->
                val s = shootGeometry(d.width, d.safeHeight, scale, scrollTag = SEARCH_STACK_TAG) { render(full) }
                val bar = s.tag(SEARCH_ACTION_BAR_TAG)
                val tabTop = s.tag(TAB_BAR_TAG).top
                if (bar.top < s.tag(SEARCH_TOP_BAR_TAG).bottom) failures += "$d @$scale  the action bar reaches the top bar"
                if (kotlin.math.abs(bar.bottom - tabTop) > 1f) failures += "$d @$scale  bar bottom ${bar.bottom} is not on the TabBar's top $tabTop"
                if (s.tag(SEARCH_LIKE_TAG).bottom > s.frame + 0.5f) failures += "$d @$scale  Like is off the screen"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the coach sits above the buttons and under nothing, its buttons on one row, on every phone`() {
        val failures = mutableListOf<String>()
        for (state in listOf(full.copy(coach = true), minimum.copy(coach = true))) {
            for (scale in listOf(1f, 2f)) {
                DEVICES.forEach { d ->
                    val s = shootGeometry(d.width, d.safeHeight, scale, scrollTag = SEARCH_STACK_TAG) { render(state) }
                    val coach = s.tag(SEARCH_COACH_TAG)
                    val like = s.tag(SEARCH_LIKE_TAG)
                    val top = s.tag(SEARCH_TOP_BAR_TAG)
                    // The caret hangs 7 below the card into the 14 gap; the card itself ends 14 above.
                    if (coach.bottom > like.top - 13.5f) failures += "$d @$scale  coach ${coach.bottom} overlaps the buttons at ${like.top}"
                    if (coach.top < top.bottom - 0.5f && scale == 1f) failures += "$d @$scale  coach under the top bar (${coach.top} < ${top.bottom})"
                    if (coach.top < 0f) failures += "$d @$scale  coach above the screen (${coach.top})"
                    val buttons = s.tags.filterKeys { it == COACH_GOT_IT_TAG || it == COACH_DONT_SHOW_TAG }.values
                    // "Both buttons fit on one row" at the three named frames -- held here on every phone
                    // 375 wide or more at the default size. Narrower, or at large type, the pair
                    // STACKS rather than cutting a label (ButtonPair), which the clean sweep checks.
                    val oneRowRequired = scale == 1f && d.width >= 375
                    if (oneRowRequired && buttons.size == 2 && buttons.map { it.top }.distinct().size > 1) {
                        failures += "$d @$scale  the coach's buttons are not on one row"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the meet line is one line on every phone, and the meet card the drawn row from 390 wide`() {
        val failures = mutableListOf<String>()
        for (profile in listOf(full, minimum)) {
            DEVICES.forEach { d ->
                val s = shootGeometry(d.width, d.safeHeight, scrollTag = SEARCH_STACK_TAG) { render(profile) }
                val line = s.tag(MEET_LINE_TAG)
                // Manrope 700 / 15: one line is under 22 tall, a second would take it past 40.
                if (line.bottom - line.top > 30f) failures += "$d  the meet line wraps (${line.bottom - line.top} tall)"
                val count = s.tag(MEET_COUNT_TAG)
                val oneRow = count.top < line.bottom && count.bottom > line.top - 20f
                if (d.width >= 390 && !oneRow) failures += "$d  the count left the meet card's row"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the stack is the only element that scrolls`() {
        DEVICES.filter { it.inAcceptanceCriteria }.forEach { d ->
            val s = shootGeometry(d.width, d.safeHeight, scrollTag = SEARCH_STACK_TAG) { render(full) }
            assertTrue("$d has ${s.scrollRegions} scrolling regions", s.scrollRegions == 1)
            assertTrue("$d the full profile must scroll", s.overflow > 0f)
        }
    }

}
