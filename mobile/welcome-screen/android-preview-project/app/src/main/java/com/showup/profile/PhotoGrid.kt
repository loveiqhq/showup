/*
 * PhotoGrid.kt
 * ShowUp · what the photo step knows, with no UI attached (SHOWUP-156)
 *
 * The rules of the grid — how many are required, which slots are optional, what the count says,
 * when Continue is refused, and when `photos_minimum_met` has crossed. All of it is plain Kotlin
 * that a JVM test can exercise with no device, which is the house rule: "no business logic inside
 * a composable... validation, formatting and routing decisions go in plain functions".
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE ONE RULE THAT MOST OF THIS FILE EXISTS TO ENFORCE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD. A photo that has been picked but not yet stored
 * has not been added, and one whose upload failed never was. The ticket's own artboard is the test
 * case: three slots look occupied — one confirmed, one in flight, one failed — and the count reads
 * "1 of 6", with Continue still refusing.
 *
 * That is not a display detail. `photos_minimum_met` fires "on the fourth CONFIRMED upload, not
 * the fourth pick", and a user who is allowed past on four in-flight photos arrives at the next
 * screen with a profile that may end up holding one.
 */
package com.showup.profile

/** Where a photo came from. `source` on `photo_added`, from the registry's closed pair. */
enum class PhotoSource {
    Library,
    Camera,
    ;

    /** The registry value. Lower-case, and never the enum name. */
    val trackingValue: String get() = if (this == Library) "library" else "camera"
}

/**
 * How far a picked photo has got.
 *
 * Three states and not two, because the gap between picking and storing is ours to explain. A
 * binary "has a photo / does not" would draw an in-flight upload as an empty slot, so the user
 * would not know which photo was being uploaded, or that anything was.
 */
enum class UploadStatus {
    /** Stored. The only status that counts towards the requirement. */
    Confirmed,

    /** Picked and on its way. Occupies its slot, shows the picked image dimmed under a ring. */
    InFlight,

    /** The upload came back an error. Keeps its slot so Retry re-uploads without re-picking. */
    Failed,
}

/**
 * One photo the user has chosen, wherever it has got to.
 *
 * @param localId a client-side identity that survives the whole lifecycle. The server's id does
 *   not exist until the upload is confirmed, so keying a slot on it would mean an in-flight photo
 *   had no stable identity and a list re-order mid-upload could move the progress ring onto a
 *   different picture.
 * @param uri what to draw. The picked image, shown from the moment it is chosen, including while
 *   it is in flight and after it has failed.
 * @param remoteId the server's id once stored. Needed to delete, and null until then.
 * @param progress 0..1 for [UploadStatus.InFlight]. A REAL value: the ring is determinate because
 *   an indeterminate spinner on a large upload says nothing about whether it is moving.
 */
data class PickedPhoto(
    val localId: Long,
    val uri: String?,
    val status: UploadStatus,
    val remoteId: String? = null,
    val progress: Float = 0f,
    /**
     * WHICH BOX ON SCREEN THIS PHOTO IS IN, 0 to 5.
     *
     * The grid is six FIXED slots, each filled or empty -- that is how the reference draws it,
     * mapping `SLOTS[i]` positionally with its own hint. This list used to be dense and a slot was
     * its index in it, which meant removing a photo from the middle slid every later photo one box
     * to the left: delete the second and the fourth appears to vanish, because the empty box moves
     * to the end. Then tapping the box you emptied was a REPLACE of the photo that had slid into
     * it, so a new photo could never go back where the old one was.
     *
     * Carried on the photo rather than implied by its position, because the list also has to stay
     * ordered for the drag and for the order sent to the server, and one of those two jobs was
     * always going to lose if a single index tried to do both.
     */
    val slot: Int = 0,
)

/** Four required, six maximum. Stated in three places on the screen, on purpose. */
const val PHOTOS_REQUIRED = 4

/** The most a profile can carry. */
const val PHOTOS_MAX = 6

/**
 * Every slot is this tall, in every state — filled, empty, uploading, failed.
 *
 * "That single number is what stops the grid reflowing mid-upload and keeps the CTA reachable at
 * the same scroll offset." A slot that sized itself to its content would change height the moment
 * an upload started, and every row below it would move under the user's finger.
 */
const val PHOTO_SLOT_HEIGHT = 158

/**
 * The state of the photo step.
 *
 * Held as one value so the host persists it in one write, and so [confirmedCount] cannot disagree
 * with the list it is derived from.
 */
data class PhotoGridState(
    /**
     * Every photo the grid holds, in reading order. The lowest [PickedPhoto.slot] is the main one.
     *
     * KEPT ORDERED AND SPARSE AT ONCE: the list is what the drag reorders and what the server's
     * order is built from, and [PickedPhoto.slot] is which box each one occupies. A photo removed
     * from the middle leaves its box empty rather than pulling the rest along.
     */
    val photos: List<PickedPhoto> = emptyList(),
    /**
     * Whether slots 5 and 6 are on screen.
     *
     * "Slots 5–6 are never on first paint" — they sit behind the `Add more` control, so the
     * screen opens asking for four things rather than six.
     */
    val optionalRevealed: Boolean = false,
    /**
     * Whether `photos_minimum_met` has already fired.
     *
     * The event fires "when filled_count FIRST reaches 4" — once per build, not on every upload
     * above the line and not again after a removal takes the count below it and back up.
     */
    val minimumReported: Boolean = false,
) {
    /** How many are stored. The number the screen shows and the only one Continue reads. */
    val confirmedCount: Int get() = photos.count { it.status == UploadStatus.Confirmed }

    /** Whether the step is satisfied. In-flight and failed photos do not count. */
    val meetsMinimum: Boolean get() = confirmedCount >= PHOTOS_REQUIRED

    /** Whether the drag hint is shown. From the SECOND photo: with one there is nothing to reorder. */
    val canReorder: Boolean get() = confirmedCount >= 2

    /** What occupies a slot, or null if it is empty. */
    fun at(index: Int): PickedPhoto? = photos.firstOrNull { it.slot == index }

    /** The lowest slot nothing occupies, or null when all six are taken. */
    fun firstFreeSlot(): Int? = (0 until PHOTOS_MAX).firstOrNull { slot -> at(slot) == null }

    /**
     * Whether this upload is the one that crosses the line.
     *
     * Asked BEFORE the state is updated, which is why it takes the count it is about to become.
     */
    fun crossesMinimum(nextConfirmed: Int): Boolean =
        !minimumReported && nextConfirmed >= PHOTOS_REQUIRED
}

/** Slots 5 and 6 are the optional ones. */
fun isOptionalSlot(index: Int): Boolean = index >= PHOTOS_REQUIRED

/**
 * What an empty slot asks for.
 *
 * The four required slots each name a KIND of photo, because "add a photo" four times gets four
 * versions of the same photo. Slot 1 on a completely empty grid is the exception: it drops its
 * category for `Start with your face`, which is the one instruction that gets somebody started.
 */
fun slotHint(index: Int, confirmedCount: Int): String =
    if (confirmedCount == 0 && index == 0) {
        PhotosCopy.FIRST_SLOT_CTA_HINT
    } else {
        PhotosCopy.SLOT_HINTS.getOrElse(index) { PhotosCopy.SLOT_HINTS.last() }
    }

/**
 * What `action` a slot tap reports.
 *
 * Three values from the registry's closed set. "replace" and "add" are told apart by whether the
 * slot already holds something — a distinction that only exists because a filled slot IS tappable,
 * and a user replacing their main photo is doing something different from one adding their fourth.
 */
fun slotTapAction(occupied: Boolean): String = if (occupied) "replace" else "add"

// ── drag to reorder ─────────────────────────────────────────────────────────
//
// THE ARITHMETIC IS HERE, NOT IN THE COMPOSABLE, and it is only possible because every slot is
// exactly [PHOTO_SLOT_HEIGHT] tall in every state. A grid whose rows resized with their contents
// would have no closed form for "which slot is under the finger" -- it would need a position map
// rebuilt on every measurement, and the answer would change mid-drag when an upload finished.
//
// Two columns, a uniform cell and a uniform gap mean the target is a division, which a JVM test
// can check at every offset without a device.

/**
 * Which slot a drag has landed on.
 *
 * @param from where the drag started.
 * @param dx , [dy] the total drag offset in pixels, from the dragged slot's own origin.
 * @param cellWidth , [cellHeight] one slot's size in pixels.
 * @param gap the space between slots, both directions.
 * @param count how many slots are on screen -- four, or six once the optional pair is revealed.
 *
 * Returns [from] when the finger has not travelled far enough to mean anything, or when the
 * computed target is outside the grid. Never returns an index a photo cannot be dropped on.
 */
fun reorderTarget(
    from: Int,
    dx: Float,
    dy: Float,
    cellWidth: Float,
    cellHeight: Float,
    gap: Float,
    count: Int,
): Int {
    if (cellWidth <= 0f || cellHeight <= 0f) return from
    val fromColumn = from % 2
    val fromRow = from / 2
    // Rounded, not truncated: the target changes when the dragged slot's CENTRE crosses into the
    // next cell, which is what the eye expects. Truncating would make the swap happen a whole
    // cell late in one direction and immediately in the other.
    val column = fromColumn + Math.round(dx / (cellWidth + gap))
    val row = fromRow + Math.round(dy / (cellHeight + gap))
    if (column !in 0..1) return from
    val target = row * 2 + column
    return if (target in 0 until count) target else from
}

/**
 * Moves one entry, keeping everything else in order.
 *
 * A remove-then-insert rather than a swap. Swapping two photos would mean dragging the fourth
 * photo to the front pushed the FIRST one to position four, which is not what a drag looks like:
 * the user is inserting, and everything between the two positions shifts by one.
 *
 * REORDERING INTO POSITION 0 IS HOW THE MAIN PHOTO CHANGES. There is no separate "make main"
 * control, and there does not need to be -- "the first one is your main photo" is the rule the
 * hint under the grid states, so moving a photo there IS the gesture.
 */
fun <T> List<T>.movedTo(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}

/**
 * A drag from one box to another, on a grid whose boxes are fixed.
 *
 * SWAPS RATHER THAN SHUFFLES. On a dense list a move means "take it out and put it back in",
 * pushing everything between along one place. On a grid of six boxes where any of them may be
 * empty, that has no meaning: there is nothing to push into an empty box. Two occupied boxes swap;
 * a photo dragged onto an empty box simply moves there.
 *
 * The list's ORDER is rebuilt from the slots afterwards, so the first box is still the main photo
 * and the order sent to the server still reads left to right, top to bottom.
 */
fun List<PickedPhoto>.slotsSwapped(from: Int, to: Int): List<PickedPhoto> {
    if (from == to) return this
    return map { photo ->
        when (photo.slot) {
            from -> photo.copy(slot = to)
            to -> photo.copy(slot = from)
            else -> photo
        }
    }.sortedBy { it.slot }
}
