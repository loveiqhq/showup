/*
 * PremiumFitTest.kt
 * ShowUp · the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186), measured on every phone
 *
 * ScreenFitTest's question -- clipped, squeezed, out of reach? -- for every state of the grid, the
 * Likes list and the profile opened from either (its sheets included), on all 23 phones, at the
 * default and the largest system font. Then the geometry the tickets promise:
 *
 *   · the grid: two columns, gap 12, padding `4 16 16`, every card 248 tall, the only scroller
 *   · the card's meta line on one line at 375 x 667 -- and on every phone 375 or wider
 *   · the undo toast 16 from each side and 12 above the TabBar, wholly on screen
 *   · on a short Likes list the TabBar sits at the bottom of the screen
 *   · the sorry sheet's button fully on screen, above the home indicator
 *
 * The frames are the devices' safe areas, as everywhere in this harness.
 */
package com.showup.fit

import androidx.compose.runtime.Composable
import com.showup.api.CallFailure
import com.showup.designsystem.TAB_BAR_TAG
import com.showup.search.CloseReason
import com.showup.search.GRID_CARD_TAG_PREFIX
import com.showup.search.GRID_META_TAG_PREFIX
import com.showup.search.LIKES_LIST_TAG
import com.showup.search.Leave
import com.showup.search.LikeSheet
import com.showup.search.LikesReceivedScreen
import com.showup.search.Opened
import com.showup.search.PremiumCard
import com.showup.search.PremiumList
import com.showup.search.PremiumSurface
import com.showup.search.SEARCH_GRID_TAG
import com.showup.search.SEARCH_STACK_TAG
import com.showup.search.SEARCH_TOP_BAR_TAG
import com.showup.search.SORRY_BUTTON_TAG
import com.showup.search.SearchAnalytics
import com.showup.search.SearchGridScreen
import com.showup.search.SearchProfileScreen
import com.showup.search.SearchSamples
import com.showup.search.SearchUiState
import com.showup.search.UNDO_TOAST_TAG
import com.showup.search.UndoToast
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The grid's states, by the sheet's letters (Search 02). */
internal object GridStates {
    val rest = SearchSamples.gridRest()

    fun SearchUiState.cards(change: (PremiumCard) -> PremiumCard) = copy(grid = grid.copy(cards = grid.cards.map(change)))

    val b = rest.cards {
        when (it.userId) {
            "sample-maya" -> it.copy(notNow = true)
            "sample-sofia", "sample-emma" -> it.copy(gone = true)
            else -> it
        }
    }
    val f = rest.cards { if (it.userId == "sample-leonie") it.copy(closed = CloseReason.Matched) else it }
    val g = rest.cards { if (it.userId == "sample-anna") it.copy(gone = true) else it }
        .copy(undo = UndoToast("sample-anna", "Anna", PremiumSurface.Grid, 4))
    val j = rest.cards {
        when (it.userId) {
            "sample-sofia" -> it.copy(closed = CloseReason.Matched)
            "sample-emma" -> it.copy(closed = CloseReason.NoOverlap)
            "sample-anna" -> it.copy(profile = it.profile?.copy(meetAt = it.profile!!.meetAt.plus(Duration.ofDays(1)), meetDay = "tomorrow"))
            else -> it
        }
    }
    val k = rest.cards {
        when (it.userId) {
            "sample-sofia" -> it.copy(leaving = Leave.Liked)
            "sample-maya" -> it.copy(notNow = true)
            else -> it
        }
    }

    /** The profile opened from the grid: C, and with its sheets D, E, H and I. */
    fun opened(id: String) = rest.copy(opened = Opened(PremiumSurface.Grid, id))
    val c = opened("sample-sofia")
    val d = c.copy(sheet = LikeSheet.Confirm)
    val e = opened("sample-leonie").copy(sheet = LikeSheet.Confirm)
    val h = opened("sample-leonie").copy(sheet = LikeSheet.Refused, refused = CloseReason.Matched)
    val i = opened("sample-anna").copy(sheet = LikeSheet.Refused, refused = CloseReason.NoOverlap)
}

/** The Likes tab's states, by the sheet's letters (Search 03). */
internal object LikesStates {
    val rest = SearchSamples.likesRest()

    fun SearchUiState.cards(change: (PremiumCard) -> PremiumCard) = copy(likes = likes.copy(cards = likes.cards.map(change)))

    val d = rest.cards { if (it.userId == "sample-anna") it.copy(gone = true) else it }
        .copy(undo = UndoToast("sample-anna", "Anna", PremiumSurface.Likes, 1))
    val e = rest.cards { if (it.userId == "sample-anna") it.copy(closed = CloseReason.Matched) else it }
    val f = rest.cards { if (it.userId == "sample-anna") it.copy(closed = CloseReason.NoOverlap) else it }
    val g = rest.copy(likes = PremiumList(loading = false), likesOpen = 0)
    // H as the sheet draws it (`initialUndo="Anna" freezeLeaving={{ name: 'Anna', t: 440 }}`): the
    // card in flight with its toast already up.
    val h = rest.cards { if (it.userId == "sample-anna") it.copy(leaving = Leave.NotInterested) else it }
        .copy(undo = UndoToast("sample-anna", "Anna", PremiumSurface.Likes, 1))
    val j = rest.cards { if (it.userId == "sample-anna") it.copy(notNow = true) else it }

    fun opened(id: String) = rest.copy(opened = Opened(PremiumSurface.Likes, id))
    // B and C as the sheet draws them: `initialOpen={0}` / `initialModal={0}` index the reference's
    // unsorted LIKES_RECEIVED, whose first entry is Leonie.
    val b = opened("sample-leonie")
    val c = b.copy(sheet = LikeSheet.Confirm)
    val i = opened("sample-leonie").copy(sheet = LikeSheet.Refused, refused = CloseReason.Matched)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class PremiumFitTest {

    private val gridStates: List<Pair<String, SearchUiState>> = listOf(
        "A at rest" to GridStates.rest,
        "B gone and Not now" to GridStates.b,
        "F closed in place" to GridStates.f,
        "G undo toast" to GridStates.g,
        "J re-checked" to GridStates.j,
        "K flying" to GridStates.k,
        "loading" to SearchUiState(loading = true, layout = SearchAnalytics.Layout.Grid, premium = true),
        "failed offline" to GridStates.rest.copy(grid = PremiumList(loading = false, failure = CallFailure.Network)),
        "no results" to GridStates.rest.copy(grid = PremiumList(loading = false)),
        "99+ likes" to GridStates.rest.copy(likesOpen = 140),
        "every card closed" to GridStates.rest.copy(grid = GridStates.rest.grid.copy(cards = GridStates.rest.grid.cards.map { it.copy(closed = CloseReason.NoOverlap) })),
    )

    private val gridProfileStates: List<Pair<String, SearchUiState>> = listOf(
        "C opened from the grid" to GridStates.c,
        "C with the coach" to GridStates.c.copy(coach = true),
        "D Be sure" to GridStates.d,
        "E mutual Be sure" to GridStates.e,
        "E confirming" to GridStates.e.copy(confirming = true),
        "H sorry, matched" to GridStates.h,
        "I sorry, no overlap" to GridStates.i,
    )

    private val likesStates: List<Pair<String, SearchUiState>> = listOf(
        "A at rest" to LikesStates.rest,
        "D undo toast" to LikesStates.d,
        "E Too late" to LikesStates.e,
        "F Out of time" to LikesStates.f,
        "G no likes yet" to LikesStates.g,
        "H flying" to LikesStates.h,
        "J Not now" to LikesStates.j,
        "a 100-character message" to with(LikesStates) {
            rest.cards { if (it.userId == "sample-leonie") it.copy(message = "W".repeat(100)) else it }
        },
        "loading" to SearchUiState(loading = true, premium = true),
        "failed offline" to LikesStates.rest.copy(likes = PremiumList(loading = false, failure = CallFailure.Network)),
    )

    private val likesProfileStates: List<Pair<String, SearchUiState>> = listOf(
        "B opened from the list" to LikesStates.b,
        "C mutual Be sure" to LikesStates.c,
        "I sorry, Back to likes" to LikesStates.i,
    )

    @Composable
    private fun grid(state: SearchUiState, freeze: Int? = null) =
        SearchGridScreen(state = state, zone = SearchSamples.ZONE, freezeLeavingMs = freeze)

    @Composable
    private fun likes(state: SearchUiState, freeze: Int? = null) =
        LikesReceivedScreen(state = state, zone = SearchSamples.ZONE, freezeLeavingMs = freeze)

    @Composable
    private fun profile(state: SearchUiState) = SearchProfileScreen(state = state, zone = SearchSamples.ZONE)

    /**
     * The leaving card in a frozen fly-out frame is scaled and moved by its layer; the harness takes a
     * node's box as its transformed corner plus its untransformed size, so that one card reads as
     * past the edge while it is drawn well inside. Its own position is checked exactly in
     * [the frozen fly-out frame is where the motion puts it]; everything else on the frame is swept.
     */
    /**
     * The grid card's motivation is "on one line with ellipsis" (Search 02): where a long label meets a
     * narrow card or a large type, its ellipsis is the ticket's, not a fault.
     */
    private val motivationTitles = SearchSamples.GRID.mapNotNull { com.showup.search.motivationOf(it.profile.motivation)?.title }.toSet()

    private fun ticketedEllipsis(finding: String): Boolean =
        finding.contains("truncated, over its maxLines") && motivationTitles.any { finding.contains("\"$it\"") }

    private fun frozenArtefact(state: SearchUiState, finding: String): Boolean {
        val leaving = (state.grid.cards + state.likes.cards).filter { it.leaving != null }.map { it.userId }
        return leaving.any { id -> finding.contains("card-$id>") || finding.contains("<fly-$id>") } && finding.contains("OFF THE")
    }

    private fun sweep(label: String, states: List<Pair<String, SearchUiState>>, render: @Composable (SearchUiState) -> Unit): List<String> {
        val fresh = mutableListOf<String>()
        for ((name, state) in states) {
            // The default, a common enlarged setting, and the largest.
            for (scale in listOf(1f, 1.3f, 2f)) {
                DEVICES.forEach { d ->
                    measureFit(d, "$label/$name @$scale", fontScale = scale) { render(state) }
                        .filterNot { it.advisory }
                        .map { "$it" }
                        .filterNot { frozenArtefact(state, it) || ticketedEllipsis(it) }
                        .forEach { fresh += it }
                }
            }
        }
        return fresh
    }

    @Test
    fun `every grid state is clean on every phone at the default and the largest type`() {
        val fresh = sweep("Grid", gridStates) { s -> grid(s, freeze = if (s === GridStates.k) 440 else null) }
        java.io.File("build/premium-grid-fit-findings.txt").writeText(fresh.joinToString("\n"))
        assertTrue("Grid layout problems (${fresh.size}):\n" + fresh.take(60).joinToString("\n") { "  $it" }, fresh.isEmpty())
    }

    @Test
    fun `every state of a profile opened from the grid is clean on every phone`() {
        val fresh = sweep("Grid profile", gridProfileStates) { profile(it) }
        java.io.File("build/premium-grid-profile-fit-findings.txt").writeText(fresh.joinToString("\n"))
        assertTrue("Grid profile problems (${fresh.size}):\n" + fresh.take(60).joinToString("\n") { "  $it" }, fresh.isEmpty())
    }

    @Test
    fun `every Likes state is clean on every phone at the default and the largest type`() {
        val fresh = sweep("Likes", likesStates) { s -> likes(s, freeze = if (s === LikesStates.h) 440 else null) }
        java.io.File("build/premium-likes-fit-findings.txt").writeText(fresh.joinToString("\n"))
        assertTrue("Likes layout problems (${fresh.size}):\n" + fresh.take(60).joinToString("\n") { "  $it" }, fresh.isEmpty())
    }

    @Test
    fun `every state of a profile opened from the Likes tab is clean on every phone`() {
        val fresh = sweep("Likes profile", likesProfileStates) { profile(it) }
        java.io.File("build/premium-likes-profile-fit-findings.txt").writeText(fresh.joinToString("\n"))
        assertTrue("Likes profile problems (${fresh.size}):\n" + fresh.take(60).joinToString("\n") { "  $it" }, fresh.isEmpty())
    }

    @Test
    fun `the grid is two columns, gap 12, padding 4 16 16, every card 248 tall, on every phone`() {
        val failures = mutableListOf<String>()
        for (scale in listOf(1f, 2f)) {
            DEVICES.forEach { d ->
                val s = shootGeometry(d.width, d.safeHeight, scale, scrollTag = SEARCH_GRID_TAG) { grid(GridStates.rest) }
                val top = s.tag(SEARCH_TOP_BAR_TAG).bottom
                val first = s.tag(GRID_CARD_TAG_PREFIX + "sample-leonie")
                val second = s.tag(GRID_CARD_TAG_PREFIX + "sample-sofia")
                val third = s.tag(GRID_CARD_TAG_PREFIX + "sample-maya")
                fun near(a: Float, b: Float) = kotlin.math.abs(a - b) <= 0.6f
                if (!near(first.left, 16f)) failures += "$d @$scale  left padding ${first.left}"
                if (!near(second.right, d.width - 16f)) failures += "$d @$scale  right padding ${d.width - second.right}"
                if (!near(second.left - first.right, 12f)) failures += "$d @$scale  column gap ${second.left - first.right}"
                if (!near(first.right - first.left, second.right - second.left)) failures += "$d @$scale  the columns differ"
                if (!near(first.top - top, 4f)) failures += "$d @$scale  top padding ${first.top - top}"
                // 248 as drawn; at the largest type the cards grow -- all together, so a row stays level.
                val height = first.bottom - first.top
                if (scale == 1f && !near(height, 248f)) failures += "$d @$scale  card height $height"
                if (scale > 1f && height < 247.5f) failures += "$d @$scale  card shorter than 248 ($height)"
                if (!near(second.bottom - second.top, height) || !near(third.bottom - third.top, height)) {
                    failures += "$d @$scale  the cards differ in height"
                }
                if (!near(third.top - first.bottom, 12f)) failures += "$d @$scale  row gap ${third.top - first.bottom}"
                if (!near(second.top, first.top)) failures += "$d @$scale  the row is not level"
                if (s.scrollRegions != 1) failures += "$d @$scale  ${s.scrollRegions} scrolling regions"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the card's meta line is one line at 375 x 667, and on every phone 375 or wider`() {
        val failures = mutableListOf<String>()
        DEVICES.filter { it.width >= 375 }.forEach { d ->
            val s = shootGeometry(d.width, d.safeHeight, scrollTag = SEARCH_GRID_TAG) { grid(GridStates.rest) }
            for (c in GridStates.rest.grid.cards.take(4)) {
                val meta = s.tags[GRID_META_TAG_PREFIX + c.userId] ?: continue
                // Manrope 700 / 11.5: one line is under 17 tall; two would be over 30.
                if (meta.bottom - meta.top > 22f) failures += "$d  ${c.name}'s meta line wraps (${meta.bottom - meta.top} tall)"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the undo toast is 16 from each side and 12 above the TabBar, on every phone`() {
        val failures = mutableListOf<String>()
        for ((name, render) in listOf<Pair<String, @Composable () -> Unit>>(
            "grid" to { grid(GridStates.g) },
            "likes" to { likes(LikesStates.d) },
        )) {
            for (scale in listOf(1f, 2f)) {
                DEVICES.forEach { d ->
                    val s = shootGeometry(d.width, d.safeHeight, scale) { render() }
                    val toast = s.tag(UNDO_TOAST_TAG)
                    val tab = s.tag(TAB_BAR_TAG)
                    if (kotlin.math.abs(tab.top - toast.bottom - 12f) > 0.6f) failures += "$d @$scale $name  toast ${tab.top - toast.bottom} above the TabBar"
                    if (kotlin.math.abs(toast.left - 16f) > 0.6f) failures += "$d @$scale $name  toast left ${toast.left}"
                    if (kotlin.math.abs(d.width - toast.right - 16f) > 0.6f) failures += "$d @$scale $name  toast right ${d.width - toast.right}"
                    if (toast.top < s.tag(SEARCH_TOP_BAR_TAG).bottom) failures += "$d @$scale $name  the toast reaches the top bar"
                    if (toast.bottom - toast.top < 51.5f) failures += "$d @$scale $name  toast under its 52 minimum"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `on a short Likes list the TabBar sits at the bottom of the screen`() {
        val failures = mutableListOf<String>()
        val short = LikesStates.rest.copy(likes = LikesStates.rest.likes.copy(cards = LikesStates.rest.likes.cards.take(1)))
        for (state in listOf(short, LikesStates.g)) {
            DEVICES.forEach { d ->
                val s = shootGeometry(d.width, d.safeHeight) { likes(state) }
                if (kotlin.math.abs(s.tag(TAB_BAR_TAG).bottom - s.frame) > 0.6f) failures += "$d  the TabBar ends at ${s.tag(TAB_BAR_TAG).bottom}, not ${s.frame}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the frozen fly-out frame is where the motion puts it, on every phone`() {
        // At 440 of 800 ms: progress through `cubic-bezier(.55,0,.8,.2)`, the scale toward .12, and the
        // way toward the visible list's centre x and 72% of its height -- from the card's own box.
        val p = com.showup.search.GridLeaveEasing.transform(440f / 800f)
        val s = 1f - (1f - com.showup.search.FLY_OUT_SCALE) * p
        val failures = mutableListOf<String>()
        val cases = listOf(
            Triple("grid", "sample-sofia", SEARCH_GRID_TAG),
            Triple("likes", "sample-anna", LIKES_LIST_TAG),
        )
        DEVICES.forEach { d ->
            for ((name, id, listTag) in cases) {
                val tag = (if (name == "grid") GRID_CARD_TAG_PREFIX else com.showup.search.LIKES_CARD_TAG_PREFIX) + id
                val still = shootGeometry(d.width, d.safeHeight) { if (name == "grid") grid(GridStates.k) else likes(LikesStates.h) }
                val frozen = shootGeometry(d.width, d.safeHeight) { if (name == "grid") grid(GridStates.k, 440) else likes(LikesStates.h, 440) }
                val box = still.tag(tag)
                val list = still.tag(listTag)
                val (dx, dy) = com.showup.search.flyOutTarget(
                    box.left, box.top, box.right - box.left, box.bottom - box.top,
                    list.left, list.top, list.right - list.left, list.bottom - list.top,
                )
                val cx = (box.left + box.right) / 2f
                val cy = (box.top + box.bottom) / 2f
                val expectLeft = cx - s * (box.right - box.left) / 2f + dx * p
                val expectTop = cy - s * (box.bottom - box.top) / 2f + dy * p
                // The flying copy, drawn over the list; the slot under it stays put, empty.
                val got = frozen.tag(com.showup.search.FLY_TAG_PREFIX + id)
                if (kotlin.math.abs(got.left - expectLeft) > 1f || kotlin.math.abs(got.top - expectTop) > 1f) {
                    failures += "$d $name  frozen at (${got.left}, ${got.top}), expected (${"%.1f".format(expectLeft)}, ${"%.1f".format(expectTop)})"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the Likes list is the only element that scrolls`() {
        DEVICES.filter { it.inAcceptanceCriteria }.forEach { d ->
            val s = shootGeometry(d.width, d.safeHeight, scrollTag = LIKES_LIST_TAG) { likes(LikesStates.rest) }
            assertTrue("$d has ${s.scrollRegions} scrolling regions", s.scrollRegions == 1)
        }
    }

    @Test
    fun `the sorry sheet's button is fully on screen on every phone`() {
        val failures = mutableListOf<String>()
        // At the default type. At the largest the sheet outgrows a small phone and scrolls inside
        // its 88% cap -- the clean sweep above holds that nothing is cut or out of reach there.
        for (state in listOf(GridStates.h, GridStates.i, LikesStates.i)) {
            for (scale in listOf(1f)) {
                DEVICES.forEach { d ->
                    val s = shootGeometry(d.width, d.safeHeight, scale, scrollTag = SEARCH_STACK_TAG) { profile(state) }
                    val button = s.tags[SORRY_BUTTON_TAG]
                    if (button == null) {
                        failures += "$d @$scale  no sorry button"
                    } else if (button.bottom > s.frame + 0.5f || button.top < 0f) {
                        failures += "$d @$scale  the sorry button is off screen (${button.top}..${button.bottom} of ${s.frame})"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
