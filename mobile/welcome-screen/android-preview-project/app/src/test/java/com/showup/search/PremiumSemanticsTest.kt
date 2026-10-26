/*
 * PremiumSemanticsTest.kt
 * ShowUp · what TalkBack is told on the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186)
 *
 * Both tickets' Accessibility sections, asserted on the semantics tree: one control per grid card
 * with its exact name and mark, the heart out of reach, a closed card a named group and no control,
 * the Likes card's photo and `View profile` going to the same place, the tab's spoken badge, the
 * toast a polite live region with a 44-high Undo that holds the window while focused, the profile
 * opened from a list -- its back pill, `Liked you`, the three actions -- and the mutual Be sure and
 * sorry sheets as named dialogs over a hidden screen.
 */
package com.showup.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class PremiumSemanticsTest {

    private fun roleOf(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun pane(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)
    private fun button(label: String) = hasContentDescription(label) and roleOf(Role.Button)

    private val grid = SearchSamples.gridRest()
    private val likes = SearchSamples.likesRest()

    private fun SearchUiState.gridCards(change: (PremiumCard) -> PremiumCard) = copy(grid = grid.copy(cards = grid.cards.map(change)))
    private fun SearchUiState.likeCards(change: (PremiumCard) -> PremiumCard) = copy(likes = likes.copy(cards = likes.cards.map(change)))

    // ── the grid ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every grid card is one button named Open name's profile, and the heart is not a second control`() = runComposeUiTest {
        setContent { SearchGridScreen(grid, SearchSamples.ZONE) }
        // A card that liked the viewer says so: its badge is read as part of it.
        for (c in grid.grid.cards) {
            onNode(button(if (c.likedYou) "Open ${c.name}'s profile, liked you" else "Open ${c.name}'s profile")).assertExists()
        }
        // Six cards, six buttons -- nothing else in the grid takes a tap.
        val cards = onAllNodes(hasClickAction() and hasContentDescription("profile", substring = true)).fetchSemanticsNodes()
        assertEquals(6, cards.size)
        assertEquals("no heart button", 0, onAllNodes(hasContentDescription("heart", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun `a marked card says its mark in its name -- liked, not now, not interested`() = runComposeUiTest {
        val marked = grid.gridCards {
            when (it.userId) {
                "sample-sofia" -> it.copy(leaving = Leave.Liked)
                "sample-maya" -> it.copy(notNow = true)
                "sample-emma" -> it.copy(leaving = Leave.NotInterested)
                else -> it
            }
        }
        setContent { SearchGridScreen(marked, SearchSamples.ZONE) }
        onNode(hasContentDescription("Open Sofia's profile, liked")).assertExists()
        onNode(button("Open Maya's profile, not now")).assertExists()
        onNode(hasContentDescription("Open Emma's profile, not interested")).assertExists()
    }

    @Test
    fun `a closed card is a named group, announced, and not a control`() = runComposeUiTest {
        val closed = grid.gridCards { if (it.userId == "sample-leonie") it.copy(closed = CloseReason.Matched) else it }
        setContent { SearchGridScreen(closed, SearchSamples.ZONE) }
        val node = onNode(hasContentDescription("Leonie. Too late — Leonie has a date with someone else.")).fetchSemanticsNode()
        assertFalse("not a tap target", node.config.contains(SemanticsActions.OnClick))
        assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
        assertEquals(0, onAllNodes(button("Open Leonie's profile")).fetchSemanticsNodes().size)
    }

    @Test
    fun `the Premium top bar reads Premium, the likes chip its count, the filter Filters`() = runComposeUiTest {
        setContent { SearchGridScreen(grid, SearchSamples.ZONE) }
        onNode(hasContentDescription(PremiumCopy.PREMIUM_TAG)).assertExists()
        assertEquals("the tag is a label, not a control", 0, onAllNodes(button(PremiumCopy.PREMIUM_TAG)).fetchSemanticsNodes().size)
        onNode(button("2 likes")).assertExists()
        onNode(button(SearchCopy.FILTERS_LABEL)).assertExists()
    }

    @Test
    fun `the Likes tab reads Likes with its open count`() = runComposeUiTest {
        setContent { SearchGridScreen(grid, SearchSamples.ZONE) }
        onNode(hasContentDescription("Likes, 2 open likes")).assertExists()
    }

    // ── the undo toast ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the toast is a polite live region and Undo a 44-high button that holds the window while focused`() = runComposeUiTest {
        val withToast = grid.gridCards { if (it.userId == "sample-anna") it.copy(gone = true) else it }
            .copy(undo = UndoToast("sample-anna", "Anna", PremiumSurface.Grid, 4))
        val focus = mutableListOf<Boolean>()
        lateinit var input: InputModeManager
        setContent {
            input = LocalInputModeManager.current
            SearchGridScreen(withToast, SearchSamples.ZONE, onUndoFocus = { focus += it })
        }
        runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        val toast = onNodeWithTag(UNDO_TOAST_TAG).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, toast.config.getOrNull(SemanticsProperties.LiveRegion))
        onNode(hasText("Anna's like declined.")).assertExists()
        val undo = onNode(hasText(PremiumCopy.UNDO) and hasClickAction())
        val height = undo.fetchSemanticsNode().size.height / density.density
        assertTrue("Undo is $height tall", height >= 43.5f)
        undo.requestFocus()
        waitForIdle()
        undo.assertIsFocused()
        assertTrue("focus on Undo reaches the view model", focus.contains(true))
    }

    // ── the Likes tab ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a Likes card's photo and View profile both open it, and there is no decision on the list`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent { LikesReceivedScreen(likes, SearchSamples.ZONE, onOpen = { opened += it }) }
        onNode(button("Open Anna's profile")).performClickAction()
        onAllNodes(button(PremiumCopy.VIEW_PROFILE)).fetchSemanticsNodes().let { assertEquals("one per open card", 2, it.size) }
        onAllNodes(button(PremiumCopy.VIEW_PROFILE))[0].performClickAction()
        assertEquals(listOf("sample-anna", "sample-anna"), opened)
        assertEquals(0, onAllNodes(hasText(PremiumCopy.CONFIRM_DATE)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(button(SearchCopy.NOT_INTERESTED)).fetchSemanticsNodes().size)
    }

    @Test
    fun `a Not now card's photo says so, and a closed card is announced by its first line with no action`() = runComposeUiTest {
        val state = likes.likeCards { if (it.userId == "sample-anna") it.copy(notNow = true) else it }
        setContent { LikesReceivedScreen(state, SearchSamples.ZONE) }
        onNode(button("Open Anna's profile, not now")).assertExists()
        val head = onAllNodes(hasText("Too late — Clara has a date with someone else."), useUnmergedTree = true).fetchSemanticsNodes().single()
        assertEquals(LiveRegionMode.Polite, head.config.getOrNull(SemanticsProperties.LiveRegion))
        assertEquals(0, onAllNodes(button("Open Clara's profile")).fetchSemanticsNodes().size)
    }

    @Test
    fun `the Likes tab has no back button, no filter and no likes chip, and its tab is selected`() = runComposeUiTest {
        setContent { LikesReceivedScreen(likes, SearchSamples.ZONE) }
        assertEquals(0, onAllNodes(button(SearchCopy.FILTERS_LABEL)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(button("2 likes")).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(button(PremiumCopy.ALL_LIKES)).fetchSemanticsNodes().size)
        val tab = onNode(hasContentDescription("Likes, 2 open likes")).fetchSemanticsNode()
        assertEquals(true, tab.config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun `the empty state offers Back to search`() = runComposeUiTest {
        var back = 0
        setContent { LikesReceivedScreen(likes.copy(likes = PremiumList(loading = false), likesOpen = 0), SearchSamples.ZONE, onBackToSearch = { back++ }) }
        onNode(hasText(PremiumCopy.EMPTY_BODY)).assertExists()
        onNode(hasText(PremiumCopy.BACK_TO_SEARCH) and hasClickAction()).performClickAction()
        assertEquals(1, back)
        assertEquals("no badge at zero", 0, onAllNodes(hasContentDescription("open like", substring = true)).fetchSemanticsNodes().size)
    }

    // ── the profile opened from a list ──────────────────────────────────────────────────────────

    @Test
    fun `from the grid -- All profiles, the Liked you badge, and Not interested, Not now and Confirm date`() = runComposeUiTest {
        setContent { SearchProfileScreen(grid.copy(opened = Opened(PremiumSurface.Grid, "sample-leonie"))) }
        onNode(button(PremiumCopy.ALL_PROFILES)).assertExists()
        onNode(hasContentDescription(PremiumCopy.LIKED_YOU)).assertExists()
        onNode(button(SearchCopy.NOT_INTERESTED)).assertExists()
        onNode(button(SearchCopy.NOT_NOW)).assertExists()
        onNodeWithTag(SEARCH_LIKE_TAG).fetchSemanticsNode().let { assertEquals(Role.Button, it.config.getOrNull(SemanticsProperties.Role)) }
        onNode(hasText(PremiumCopy.CONFIRM_DATE)).assertExists()
        onNode(hasContentDescription(PremiumCopy.PREMIUM_TAG)).assertExists()
        onNode(button(SearchCopy.FILTERS_LABEL)).assertExists()
    }

    @Test
    fun `from the Likes tab -- All likes, the Likes tab active, no filter and no chip`() = runComposeUiTest {
        setContent { SearchProfileScreen(likes.copy(opened = Opened(PremiumSurface.Likes, "sample-anna"))) }
        onNode(button(PremiumCopy.ALL_LIKES)).assertExists()
        assertEquals(0, onAllNodes(button(SearchCopy.FILTERS_LABEL)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(button("2 likes")).fetchSemanticsNodes().size)
        val tab = onNode(hasContentDescription("Likes, 2 open likes")).fetchSemanticsNode()
        assertEquals(true, tab.config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun `with none of the new state the profile is Search 01 -- no back pill, no tag, Like`() = runComposeUiTest {
        setContent { SearchProfileScreen(SearchSamples.rest()) }
        assertEquals(0, onAllNodes(button(PremiumCopy.ALL_PROFILES)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasContentDescription(PremiumCopy.PREMIUM_TAG)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasContentDescription(PremiumCopy.LIKED_YOU)).fetchSemanticsNodes().size)
        onNode(hasText(SearchCopy.LIKE)).assertExists()
    }

    @Test
    fun `mutual Be sure is a named dialog with Confirm date, which says it is waiting while in flight`() = runComposeUiTest {
        var state by mutableStateOf(grid.copy(opened = Opened(PremiumSurface.Grid, "sample-leonie"), sheet = LikeSheet.Confirm))
        setContent { SearchProfileScreen(state) }
        onNode(pane(SearchCopy.BE_SURE)).assertExists()
        onNode(hasText(PremiumCopy.CONFIRM_DATE) and hasClickAction()).assertExists()
        onNode(hasText("Confirm, and you and Leonie have a ", substring = true)).assertExists()
        assertEquals("the screen behind is hidden", 0, onAllNodes(button(SearchCopy.NOT_NOW)).fetchSemanticsNodes().size)
        state = state.copy(confirming = true)
        waitForIdle()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Waiting for an answer")).assertExists()
    }

    @Test
    fun `the sorry sheet is a dialog named by its reason and person, with its one button`() = runComposeUiTest {
        var state by mutableStateOf(
            grid.copy(opened = Opened(PremiumSurface.Grid, "sample-leonie"), sheet = LikeSheet.Refused, refused = CloseReason.Matched),
        )
        setContent { SearchProfileScreen(state) }
        onNode(pane("Too late. Leonie")).assertExists()
        onNode(hasText(PremiumCopy.FIND_SOMEONE_ELSE) and hasClickAction()).assertExists()
        assertEquals(0, onAllNodes(button(SearchCopy.NOT_NOW)).fetchSemanticsNodes().size)
        state = likes.copy(opened = Opened(PremiumSurface.Likes, "sample-leonie"), sheet = LikeSheet.Refused, refused = CloseReason.NoOverlap)
        waitForIdle()
        onNode(pane("Out of time. Leonie")).assertExists()
        onNode(hasText(PremiumCopy.BACK_TO_LIKES) and hasClickAction()).assertExists()
    }

    @Test
    fun `closing mutual Be sure gives focus back to Confirm date`() = runComposeUiTest {
        var state by mutableStateOf(grid.copy(opened = Opened(PremiumSurface.Grid, "sample-leonie"), sheet = LikeSheet.Confirm))
        lateinit var input: InputModeManager
        setContent {
            input = LocalInputModeManager.current
            SearchProfileScreen(state)
        }
        runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        state = state.copy(sheet = null)
        waitForIdle()
        onNodeWithTag(SEARCH_LIKE_TAG).assertIsFocused()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickAction() {
        val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)
        assertTrue("clickable", action != null)
        action!!.action!!.invoke()
    }
}
