/*
 * PhotoGridTest.kt
 * ShowUp · the photo step's rules, checked with no device (SHOWUP-156)
 *
 * The three the ticket is most specific about, and the two nobody would notice breaking:
 *
 *   · THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD -- state C is the worked example, and it is
 *     the one that would silently let somebody past with four photos that never landed.
 *   · `photos_minimum_met` FIRES ONCE, on the first crossing.
 *   · EVERY SLOT IS 158 -- asserted as a constant, because the whole grid's geometry, and
 *     therefore `reorderTarget`, depends on it being one.
 *   · The reorder arithmetic, at every offset it can be handed.
 *   · `movedTo` is an INSERT, not a swap, which is the difference between dragging a photo to the
 *     front and trading it with whatever was there.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGridTest {

    private fun photo(id: Long, status: UploadStatus) = PickedPhoto(id, null, status)

    // ── the count ───────────────────────────────────────────────────────────

    @Test
    fun `an in-flight photo occupies its slot and does not count`() {
        val state = PhotoGridState(
            photos = listOf(
                photo(0, UploadStatus.Confirmed),
                photo(1, UploadStatus.InFlight),
            ),
        )
        // Two slots look occupied.
        assertEquals(2, state.photos.size)
        // One has been added.
        assertEquals(1, state.confirmedCount)
    }

    @Test
    fun `state C reads one of six with three slots occupied`() {
        // The ticket's own artboard: one confirmed, one uploading, one failed. The count row
        // reads "1 of 6" and Continue still refuses -- which is the whole point of the state.
        val state = PhotoGridState(
            photos = listOf(
                photo(0, UploadStatus.Confirmed),
                photo(1, UploadStatus.InFlight),
                photo(2, UploadStatus.Failed),
            ),
        )
        assertEquals("1 of 6", PhotosCopy.countValue(state.confirmedCount))
        assertFalse(state.meetsMinimum)
    }

    @Test
    fun `four in flight is not four photos`() {
        val state = PhotoGridState(photos = (0L..3L).map { photo(it, UploadStatus.InFlight) })
        assertEquals(0, state.confirmedCount)
        assertFalse("Continue must still refuse at four in flight", state.meetsMinimum)
    }

    @Test
    fun `four confirmed is enough and no more is required`() {
        val state = PhotoGridState(photos = (0L..3L).map { photo(it, UploadStatus.Confirmed) })
        assertTrue(state.meetsMinimum)
        assertEquals("4 of 6", PhotosCopy.countValue(state.confirmedCount))
    }

    // ── photos_minimum_met fires once ───────────────────────────────────────

    @Test
    fun `the minimum crossing is reported on the fourth confirmed upload`() {
        val three = PhotoGridState(photos = (0L..2L).map { photo(it, UploadStatus.Confirmed) })
        assertTrue(three.crossesMinimum(nextConfirmed = 4))
    }

    @Test
    fun `the minimum crossing is not reported twice`() {
        val already = PhotoGridState(
            photos = (0L..3L).map { photo(it, UploadStatus.Confirmed) },
            minimumReported = true,
        )
        // A fifth and a sixth photo are still uploads; they are not a second crossing.
        assertFalse(already.crossesMinimum(nextConfirmed = 5))
        assertFalse(already.crossesMinimum(nextConfirmed = 6))
        // And neither is dropping below four and coming back -- a funnel step that can fire twice
        // for one user cannot be counted.
        assertFalse(already.crossesMinimum(nextConfirmed = 4))
    }

    // ── the reorder hint ────────────────────────────────────────────────────

    @Test
    fun `the reorder hint appears from the second photo, not the first`() {
        assertFalse(PhotoGridState(photos = listOf(photo(0, UploadStatus.Confirmed))).canReorder)
        assertTrue(
            PhotoGridState(
                photos = listOf(photo(0, UploadStatus.Confirmed), photo(1, UploadStatus.Confirmed)),
            ).canReorder,
        )
    }

    // ── the slot hints ──────────────────────────────────────────────────────

    @Test
    fun `an empty grid asks slot one for a face`() {
        assertEquals("Start with your face", slotHint(index = 0, confirmedCount = 0))
        // And only slot one, and only while the grid is empty.
        assertEquals("With friends", slotHint(index = 1, confirmedCount = 0))
        assertEquals("Portrait", slotHint(index = 0, confirmedCount = 1))
    }

    @Test
    fun `every slot has a hint and the optional pair share one`() {
        assertEquals(PHOTOS_MAX, PhotosCopy.SLOT_HINTS.size)
        assertEquals("Anything you like", slotHint(4, 2))
        assertEquals("Anything you like", slotHint(5, 2))
    }

    @Test
    fun `slots five and six are the optional ones`() {
        (0 until PHOTOS_REQUIRED).forEach { assertFalse(isOptionalSlot(it)) }
        (PHOTOS_REQUIRED until PHOTOS_MAX).forEach { assertTrue(isOptionalSlot(it)) }
    }

    // ── the one number the grid's geometry rests on ─────────────────────────

    @Test
    fun `every slot is 158 tall`() {
        // Asserted as a constant rather than measured, because what matters is that there is ONE
        // of it: the slot's four renderings all read this, and reorderTarget's arithmetic is only
        // valid because the cell height does not depend on what is in the cell.
        assertEquals(158, PHOTO_SLOT_HEIGHT)
    }

    // ── the reorder arithmetic ──────────────────────────────────────────────
    //
    // A 158 cell, a 12 gap, and a width that does not matter as long as it is the same for both
    // columns. The numbers below are in pixels; the function does not care which unit.

    private val cell = 170f
    private val tall = 158f
    private val gap = 12f

    private fun target(from: Int, dx: Float, dy: Float, count: Int = 4) =
        reorderTarget(from, dx, dy, cell, tall, gap, count)

    @Test
    fun `a drag that has not moved lands where it started`() {
        assertEquals(0, target(from = 0, dx = 0f, dy = 0f))
        // And a nudge shorter than half a cell is still not a move.
        assertEquals(0, target(from = 0, dx = 40f, dy = 20f))
    }

    @Test
    fun `dragging right by one cell moves one column`() {
        assertEquals(1, target(from = 0, dx = cell + gap, dy = 0f))
    }

    @Test
    fun `dragging down by one row moves two slots`() {
        assertEquals(2, target(from = 0, dx = 0f, dy = tall + gap))
    }

    @Test
    fun `the last slot dragged to the front becomes the main photo`() {
        // Slot 3 is bottom-right; the front is top-left, so one column left and one row up.
        assertEquals(0, target(from = 3, dx = -(cell + gap), dy = -(tall + gap)))
    }

    @Test
    fun `a drag off the side of the grid is refused rather than wrapped`() {
        // Column -1 does not exist, and wrapping it to the previous row's right-hand slot would
        // move the photo somewhere the finger never was.
        assertEquals(0, target(from = 0, dx = -(cell + gap), dy = 0f))
        assertEquals(1, target(from = 1, dx = cell + gap, dy = 0f))
    }

    @Test
    fun `a drag below the last slot is refused`() {
        assertEquals(3, target(from = 3, dx = 0f, dy = (tall + gap) * 2, count = 4))
        // And is allowed once the optional pair is on screen.
        assertEquals(5, target(from = 3, dx = 0f, dy = tall + gap, count = 6))
    }

    @Test
    fun `an unmeasured cell cannot move anything`() {
        // The modifier reports a size of zero for one frame before layout. Dividing by it would
        // produce a NaN target and, rounded, slot 0 -- which would silently promote a photo to
        // main on a drag that had not finished.
        assertEquals(2, reorderTarget(2, 500f, 500f, 0f, 0f, gap, 4))
    }

    // ── moving is an insert, not a swap ─────────────────────────────────────

    @Test
    fun `moving to the front shifts everything else along`() {
        assertEquals(listOf("d", "a", "b", "c"), listOf("a", "b", "c", "d").movedTo(3, 0))
    }

    @Test
    fun `moving to the back shifts everything else back`() {
        assertEquals(listOf("b", "c", "d", "a"), listOf("a", "b", "c", "d").movedTo(0, 3))
    }

    @Test
    fun `a move to the same place changes nothing, and neither does one off the end`() {
        val list = listOf("a", "b", "c")
        assertEquals(list, list.movedTo(1, 1))
        assertEquals(list, list.movedTo(1, 9))
        assertEquals(list, list.movedTo(-1, 0))
    }

    // ── the tracking vocabulary ─────────────────────────────────────────────

    @Test
    fun `a tap on a filled slot is a replace and an empty one is an add`() {
        assertEquals("add", slotTapAction(occupied = false))
        assertEquals("replace", slotTapAction(occupied = true))
    }

    @Test
    fun `the source values are the registry's, not the enum names`() {
        assertEquals("library", PhotoSource.Library.trackingValue)
        assertEquals("camera", PhotoSource.Camera.trackingValue)
    }
}
