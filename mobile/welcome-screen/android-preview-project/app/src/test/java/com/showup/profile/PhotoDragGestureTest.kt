/*
 * PhotoDragGestureTest.kt
 * ShowUp · does a finger actually reorder a photo (SHOWUP-156)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS FILE EXISTS, WHICH IS THE POINT OF IT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * "Drag to reorder" is written on the screen and did not work on a Pixel. It was fixed once, by
 * reasoning about modifier order, and it STILL did not work on the Pixel -- because nothing
 * anywhere drove a gesture. `reorderTarget` had thorough unit tests and they all passed: the
 * arithmetic was never the broken part. `PhotosReorderTest` proved the PATCH request. Between a
 * tested function and a tested request sat the one thing nobody tested, which is the finger.
 *
 * So this suite performs the gesture on the real composable at a real size and asserts that
 * `onReorder` is called with the right pair. It is the only test in this project that would have
 * failed while the bug was present, and the only one that can tell a working drag from a
 * plausible-looking modifier chain.
 *
 * WHAT IT CANNOT PROVE: that the tile follows the finger smoothly, or that the long press feels
 * right. Robolectric injects events; it does not draw at 60fps or have a thumb.
 */
package com.showup.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.showup.fit.FIT_ROOT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoDragGestureTest {

    /**
     * Four confirmed photos, ONE PER BOX, so a drag has somewhere to go in both axes.
     *
     * `slot = it` is the whole of it: `at(index)` matches on `PickedPhoto.slot`, which defaults
     * to 0, so a list built without it puts every photo in box 0 -- and `reorderable` is enabled
     * per slot on `at(index) != null`, which makes exactly one tile draggable. The first version
     * of this suite did that and reported "only slot 0 drags", which was true of the fixture and
     * not of the screen.
     */
    private fun grid(n: Int = 4) = PhotoGridState(
        photos = List(n) { PickedPhoto(it.toLong(), null, UploadStatus.Confirmed, slot = it) },
    )

    private data class Dragged(val from: Int, val to: Int)

    /**
     * Drags slot [from] by [by] (in dp) after a long press, and returns the reorder it caused.
     *
     * THE MOVE IS BROKEN INTO STEPS, because one giant jump is not what a finger does and not
     * what the gesture detector sees: `detectDragGesturesAfterLongPress` reports deltas, and a
     * detector that only works for a single enormous delta would pass here and fail on a phone.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun drag(from: Int, byX: Float, byY: Float, state: PhotoGridState = grid()): Dragged? {
        var result: Dragged? = null
        runComposeUiTest {
            setContent {
                val cfg = android.content.res.Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = 390
                    screenHeightDp = 844
                }
                CompositionLocalProvider(
                    LocalConfiguration provides cfg,
                    LocalDensity provides Density(LocalDensity.current.density, 1f),
                ) {
                    Box(Modifier.testTag(FIT_ROOT).requiredSize(390.dp, 844.dp)) {
                        ProfilePhotosScreen(
                            state = state,
                            onReorder = { f, t -> result = Dragged(f, t) },
                        )
                    }
                }
            }
            waitForIdle()

            // ONE UNBROKEN STREAM. The long press and the move are the same gesture, so they
            // are one `performTouchInput` block with its own timing -- a `longClick()` followed
            // by a `swipe()` is two presses, and the second one starts from nothing. The long press and the move have to be
            // one unbroken stream, which is why this is a single `performTouchInput` block with
            // its own timing rather than `longClick()` followed by `swipe()`.
            onNodeWithTag(slotTag(from), useUnmergedTree = true).performTouchInput {
                val start = center
                down(start)
                // Past the long-press timeout, without moving: anything else is a scroll.
                advanceEventTime(700)
                move()
                val steps = 8
                for (i in 1..steps) {
                    moveTo(Offset(start.x + byX * i / steps, start.y + byY * i / steps))
                    advanceEventTime(16)
                }
                up()
            }
            waitForIdle()
        }
        return result
    }

    /** The tile's own node, by tag. FROM THE SCREEN, so the two cannot drift apart. */
    private fun slotTag(index: Int) = photoSlotTag(index)

    // ── the gesture the screen advertises ───────────────────────────────────

    /**
     * THE TEST THAT WOULD HAVE CAUGHT IT.
     *
     * One cell to the right is slot 0 -> slot 1, and it is the simplest thing the label on the
     * screen promises. While the pointer input lived inside the translated layer this reported
     * nothing at all: the deltas collapsed to zero after the first frame, `reorderTarget` was
     * handed a dx of roughly nothing, and it correctly returned `from`.
     */
    @Test
    fun `dragging a photo one cell right reorders it`() {
        val cell = 165f // a 390-wide frame, two columns, the screen's own gutters and gap
        val moved = drag(from = 0, byX = cell, byY = 0f)
        assertNotNull("a drag across a whole cell must reorder something", moved)
        assertEquals(0, moved!!.from)
        assertEquals(1, moved.to)
    }

    @Test
    fun `dragging a photo down a row reorders it`() {
        val moved = drag(from = 0, byX = 0f, byY = 175f)
        assertNotNull("a drag down a whole row must reorder something", moved)
        assertEquals(2, moved!!.to)
    }

    @Test
    fun `dragging the last photo to the front makes it the main photo`() {
        // REORDERING INTO POSITION 0 IS HOW THE MAIN PHOTO CHANGES -- there is no separate
        // control, so this gesture is the whole feature.
        val moved = drag(from = 3, byX = -165f, byY = -175f)
        assertNotNull(moved)
        assertEquals(3, moved!!.from)
        assertEquals(0, moved.to)
    }

    // ── what must NOT happen ────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a short press and move does not reorder, because that is a scroll`() {
        // The grid is inside the screen's only scrolling region and six tiles cover most of it.
        // A drag that started without a long press has to stay a scroll, or the screen cannot be
        // scrolled with a finger that happens to land on a photo.
        var result: Dragged? = null
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag(FIT_ROOT).requiredSize(390.dp, 844.dp)) {
                    ProfilePhotosScreen(
                        state = grid(),
                        onReorder = { f, t -> result = Dragged(f, t) },
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(slotTag(0), useUnmergedTree = true).performTouchInput {
                val start = center
                down(start)
                advanceEventTime(16)
                moveTo(Offset(start.x + 180f, start.y))
                advanceEventTime(16)
                up()
            }
            waitForIdle()
        }
        assertNull("a drag with no long press is a scroll, not a reorder", result)
    }

    @Test
    fun `a single photo cannot be dragged anywhere`() {
        val moved = drag(from = 0, byX = 165f, byY = 0f, state = grid(1))
        assertNull("dragging the only photo onto itself is nothing happening expensively", moved)
    }

    /**
     * EVERY SLOT, EVERY DIRECTION, in one table.
     *
     * The individual tests above each prove one cell of this, and the table is here because the
     * bug that started all of it was only visible as a SHAPE: "slot 0 drags, nothing else does".
     * A per-slot assertion says "slot 3 cannot drag left" and reads as a broken drag; the table
     * says which slots can move where, and a fixture error shows up as a whole row of nulls
     * rather than as one puzzling failure.
     *
     * A null is the RIGHT answer wherever the target is off the grid: slot 0 has nothing above
     * or to its left, slot 3 nothing below or to its right. `reorderTarget` returns `from` there
     * and `from == to` fires no reorder, which is the gesture correctly doing nothing.
     */
    @Test
    fun `every slot drags in every direction the grid allows`() {
        // One cell plus the gap, so each drag lands squarely in the neighbouring box.
        val step = 176f
        val actual = (0..3).joinToString("\n") { slot ->
            val r = drag(from = slot, byX = step, byY = 0f)?.to
            val d = drag(from = slot, byX = 0f, byY = step)?.to
            val l = drag(from = slot, byX = -step, byY = 0f)?.to
            val u = drag(from = slot, byX = 0f, byY = -step)?.to
            "$slot: right=$r down=$d left=$l up=$u"
        }
        val expected = listOf(
            // A 2 x 2 grid of four photos. Read it as a map: the top-left tile can go right and
            // down and nowhere else, and so on round.
            "0: right=1 down=2 left=null up=null",
            "1: right=null down=3 left=0 up=null",
            "2: right=3 down=null left=null up=0",
            "3: right=null down=null left=2 up=1",
        ).joinToString("\n")
        assertEquals(expected, actual)
    }

    // ── the slots are addressable at all ────────────────────────────────────

    /**
     * The tags this suite drives exist and land on the right nodes.
     *
     * Asserted separately because every test above would pass VACUOUSLY if the tag were wrong in
     * a way that threw -- and would fail for a reason that has nothing to do with the gesture.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `every drawn slot carries its own tag`() {
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag(FIT_ROOT).requiredSize(390.dp, 844.dp)) {
                    // `optionalRevealed`, or boxes 5 and 6 are not drawn at all: they sit behind
                    // the `Add more` divider until the four required ones are met or the user
                    // asks for them. Six photos in state is not the same as six boxes on screen.
                    ProfilePhotosScreen(state = grid(6).copy(optionalRevealed = true))
                }
            }
            waitForIdle()
            val root = onNodeWithTag(FIT_ROOT, useUnmergedTree = true).fetchSemanticsNode()
            val tags = mutableSetOf<String>()
            fun walk(n: SemanticsNode) {
                n.config.getOrNull(SemanticsProperties.TestTag)?.let { tags += it }
                n.children.forEach { walk(it) }
            }
            walk(root)
            for (i in 0 until 6) {
                assertTrue("slot $i must be addressable, found $tags", slotTag(i) in tags)
            }
        }
    }
}
