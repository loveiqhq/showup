/*
 * PhotosViewModel.kt
 * ShowUp · the photo step's asynchronous work (SHOWUP-156)
 *
 * The screen loads, uploads and retries, which is exactly the trigger CLAUDE.md names for a
 * ViewModel: "introduce one the moment a screen loads, uploads or retries; then it exposes
 * StateFlow and the screen collects it lifecycle-aware". The screen itself stays a function from
 * values to pixels and holds nothing.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE RULES THIS FILE ENFORCES, AND WHERE EACH ONE CAME FROM
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD. [PhotoGridState.confirmedCount] counts
 * [UploadStatus.Confirmed] and nothing else, so an in-flight photo occupies its slot without
 * counting and a failed one never counted.
 *
 * FAILURE KEEPS THE SLOT. A failed upload stays where it was picked, with its image, so Retry
 * re-uploads the same bytes rather than sending the user back to the picker. The grid is never
 * cleared on a failure.
 *
 * `photos_minimum_met` FIRES ONCE, on the first crossing. Not on every confirmed upload above
 * four, and not again after a removal takes the count below it and back up.
 *
 * THE PERMISSION STATUS IS RE-READ ON EVERY FOREGROUND. [refreshAccess] exists for one bug: "a
 * user who granted access in Settings returning to the blocked card". Nothing is cached, so the
 * only requirement is that somebody calls it on resume, which the host does.
 *
 * ONE UPLOAD PER PHOTO, AND CANCELLING ONE DOES NOT CANCEL THE OTHERS. Each upload is its own job
 * in [viewModelScope], keyed by the photo's local id, so a removal mid-upload cancels exactly one.
 */
package com.showup.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the photo screen renders, in one value. */
data class PhotosUiState(
    val grid: PhotoGridState = PhotoGridState(),
    val library: LibraryAccess = LibraryAccess.NotNeeded,
    val camera: CameraAccess = CameraAccess.CanAsk,
    /** Whether the source sheet is up. */
    val sheetOpen: Boolean = false,
    /**
     * Which slot the sheet was opened for.
     *
     * Kept because the sheet's two rows both come back with a photo that has to land SOMEWHERE,
     * and "the slot the user tapped" is the only correct answer — a photo picked from slot 3 that
     * appended to the end would silently move the user's main photo.
     */
    val pendingSlot: Int = 0,
)

class PhotosViewModel(
    private val repo: PhotosRepository,
    private val access: PhotoAccessReader,
    /**
     * Reads a picked image into memory.
     *
     * A lambda rather than a `ContentResolver`, so this class knows nothing about Android's
     * storage APIs and a JVM test can hand it bytes. Returns null when the URI cannot be read —
     * a revoked permission, a file deleted between picking and reading — which is a failed upload
     * rather than a crash.
     */
    private val readBytes: suspend (uri: String) -> PickedBytes?,
    private val analytics: AnalyticsTracker? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(PhotosUiState())
    val state: StateFlow<PhotosUiState> = _state.asStateFlow()

    /** One job per in-flight photo, so cancelling one cannot touch another. */
    private val uploads = mutableMapOf<Long, Job>()

    private var nextLocalId = 1L

    /** A drag that could not be sent yet because the grid was not all stored. See [pushOrder]. */
    private var orderPending = false

    // ── arriving ────────────────────────────────────────────────────────────

    /**
     * Reads the photos the account already holds.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * WHY THIS WAS MISSING, AND WHAT IT COST
     * ─────────────────────────────────────────────────────────────────────────
     *
     * The prompts screen has had a `load` since persistence landed; this one never did, so the
     * grid started EMPTY on every launch however many photos the account held. Nothing looked
     * wrong -- an empty grid is what a new user sees -- right up until the seventh upload.
     *
     * The server allows six. An account already at six answers every further upload with a 400,
     * and a slot whose upload is refused renders `Upload failed` with a `Retry` that re-sends the
     * same bytes to the same full account. So a user who had already added six photos came back
     * to an empty screen, added one, and was told it failed, forever, with no way to see the six
     * that were the actual reason.
     *
     * That is the third bug of exactly this shape on this screen: a PERMANENT refusal wearing a
     * transient failure's clothes. The other two -- a HEIC the server would not take, and a photo
     * past its size cap -- were fixed by sending something acceptable. This one is not fixable at
     * the upload: the only honest fix is to SHOW the photos, because the grid being wrong is what
     * made the refusal look arbitrary.
     *
     * ANYTHING PICKED IN THIS SESSION SURVIVES. A read that landed while an upload was in flight
     * would otherwise throw away the slot the user is watching.
     */
    fun load() {
        viewModelScope.launch {
            val stored = repo.list() ?: return@launch
            _state.update { current ->
                val fromServer = stored.sortedBy { it.position }.map { photo ->
                    PickedPhoto(
                        localId = nextLocalId++,
                        // The server's URL. `PhotoFill` decodes a local uri and falls through to
                        // the placeholder for a remote one, which is honest: this build has no
                        // image loader, and a blank tile would claim the photo was not there.
                        uri = photo.url,
                        status = UploadStatus.Confirmed,
                        remoteId = photo.id,
                        progress = 1f,
                    )
                }
                // ANYTHING THE READ DID NOT MENTION IS KEPT, not just the in-flight ones. A
                // photo that confirmed between the request going out and this merge running has a
                // remote id the answer predates, and filtering on "no remote id" would drop the
                // slot the user just watched land. Same rule, same reason, as `adoptServerOrder`.
                val listed = stored.map { it.id }.toSet()
                val unstored = current.grid.photos.filter { it.remoteId !in listed }
                current.copy(
                    grid = current.grid.copy(
                        photos = fromServer + unstored,
                        // NOT A CROSSING. `photos_minimum_met` fires when the count FIRST reaches
                        // four, and an account that already held four did not reach it just now --
                        // reporting it here would put a threshold event on every relaunch.
                        minimumReported = current.grid.minimumReported ||
                            fromServer.size >= PHOTOS_REQUIRED,
                        // Six already stored means slots 5 and 6 are occupied, so the block they
                        // sit behind has to be open or two of the photos would have nowhere to be.
                        optionalRevealed = current.grid.optionalRevealed ||
                            fromServer.size > PHOTOS_REQUIRED,
                    ),
                )
            }
        }
    }

    // ── access ──────────────────────────────────────────────────────────────

    /** Re-reads both permission statuses. Call on every foreground. */
    fun refreshAccess() {
        _state.update { it.copy(library = access.library(), camera = access.camera()) }
    }

    // ── the grid ────────────────────────────────────────────────────────────

    /** A slot was tapped. Opens the source sheet — never the picker directly. */
    fun tapSlot(index: Int) {
        val occupied = _state.value.grid.at(index) != null
        analytics?.report(
            ProfileAnalytics.photoSlotTapped(
                slotIndex = index,
                isOptional = isOptionalSlot(index),
                action = slotTapAction(occupied),
            ),
        )
        _state.update { it.copy(sheetOpen = true, pendingSlot = index) }
    }

    fun dismissSheet() {
        _state.update { it.copy(sheetOpen = false) }
    }

    /** `Add more`. Reported as a slot tap with the reveal action, per the registry's own note. */
    fun revealOptional() {
        analytics?.report(
            ProfileAnalytics.photoSlotTapped(
                slotIndex = PHOTOS_REQUIRED,
                isOptional = true,
                action = "reveal_optional",
            ),
        )
        _state.update { it.copy(grid = it.grid.copy(optionalRevealed = true)) }
    }

    /**
     * A photo came back from the picker or the camera.
     *
     * The slot is occupied IMMEDIATELY, in flight, with the picked image showing — before a single
     * byte has gone anywhere. That is the state the ticket is most specific about: the user has to
     * see WHICH photo is uploading, which an empty slot with a spinner cannot say.
     */
    fun picked(uri: String, source: PhotoSource) {
        val slot = _state.value.pendingSlot
        val id = nextLocalId++
        _state.update { current ->
            val photos = current.grid.photos.toMutableList()
            val entry = PickedPhoto(localId = id, uri = uri, status = UploadStatus.InFlight)
            if (slot < photos.size) photos[slot] = entry else photos.add(entry)
            current.copy(grid = current.grid.copy(photos = photos), sheetOpen = false)
        }
        startUpload(id, uri, source)
    }

    /** Retry re-uploads into the SAME slot. The user never re-picks. */
    fun retry(index: Int) {
        val photo = _state.value.grid.at(index) ?: return
        if (photo.status != UploadStatus.Failed) return
        val uri = photo.uri ?: return
        setStatus(photo.localId, UploadStatus.InFlight, progress = 0f)
        // The source is not re-asked, because the photo is the one already picked. Library is the
        // registry's value for "it came from the library", and a retried camera shot did too by
        // this point -- it is a file on disk. Recorded here so the choice is visible rather than
        // implied by the default.
        startUpload(photo.localId, uri, PhotoSource.Library)
    }

    /** REMOVE IS IMMEDIATE AND HAS NO CONFIRMATION. Re-adding is one tap. */
    fun remove(index: Int) {
        val photo = _state.value.grid.at(index) ?: return
        uploads.remove(photo.localId)?.cancel()
        _state.update { current ->
            val photos = current.grid.photos.toMutableList()
            photos.removeAt(index)
            current.copy(grid = current.grid.copy(photos = photos))
        }
        analytics?.report(
            ProfileAnalytics.photoRemoved(index, _state.value.grid.confirmedCount),
        )
        // Only a stored photo has anything to delete on the server.
        val remoteId = photo.remoteId ?: return
        viewModelScope.launch { repo.remove(remoteId) }
    }

    /**
     * Drag finished.
     *
     * THE GRID MOVES FIRST AND THE WRITE FOLLOWS. A tile that sprang back to wait for a round trip
     * would read as the drag having failed, so the move is applied on the frame the finger lifts
     * and `PATCH /me/photos/order` catches up. If the server refuses, [pushOrder] adopts the order
     * it reports instead -- see there for why that is the safe direction.
     */
    fun reorder(from: Int, to: Int) {
        if (from == to) return
        _state.update { it.copy(grid = it.grid.copy(photos = it.grid.photos.movedTo(from, to))) }
        analytics?.report(ProfileAnalytics.photoReordered(from, to))
        pushOrder()
    }

    /**
     * Sends the current order, or remembers to send it once the grid is all stored.
     *
     * THE ROUTE TAKES THE COMPLETE SET OF STORED PHOTOS AND NOTHING ELSE, so a grid with an upload
     * still in flight -- or a failed slot the user has not retried -- has no complete list to send
     * yet. Dropping the drag on the floor in that case would lose it: the upload lands, the server
     * appends the new photo, and the order the user made is gone. So the drag is remembered in
     * [orderPending] and [confirm] pushes it the moment the last slot is stored.
     *
     * A FAILED SLOT NEVER RESOLVES ON ITS OWN, which is why this is not a wait for "no uploads in
     * flight": the pending order simply stays pending until the user retries or removes it, and
     * either of those ends with a complete grid and a push.
     */
    private fun pushOrder() {
        val photos = _state.value.grid.photos
        val ids = photos.mapNotNull { it.remoteId }
        if (ids.size != photos.size) {
            orderPending = true
            return
        }
        orderPending = false
        if (ids.isEmpty()) return
        viewModelScope.launch {
            when (val result = repo.reorder(ids)) {
                is ReorderPhotosResult.Stored -> Unit
                // The server and this grid disagree about what the account holds -- a photo removed
                // on another device, most likely. Re-read and adopt: the list it returns is the one
                // both sides can agree on, and guessing which end is stale is how two devices end
                // up overwriting each other.
                is ReorderPhotosResult.Failed -> repo.list()?.let { adoptServerOrder(it, ids) }
            }
        }
    }

    /**
     * Rebuilds the grid from the server's list, after a refusal.
     *
     * Photos the read mentions take the order it gives. A photo this grid thought was stored and
     * the read does NOT mention is dropped -- that is the whole reason for re-reading, and the
     * likeliest cause is that it was removed on another device.
     *
     * DROPPED ONLY IF IT WAS IN THE ORDER WE SENT. Anything else is newer than the answer: a photo
     * still uploading, or one that landed between the refusal and the read going out. The read
     * cannot know about either, so its silence is not evidence that they are gone, and deleting a
     * photo from the grid because of a race the user never saw is the worse of the two mistakes.
     */
    private fun adoptServerOrder(fresh: List<StoredPhoto>, sent: List<String>) {
        _state.update { current ->
            val byRemote = current.grid.photos.associateBy { it.remoteId }
            val listed = fresh.map { it.id }.toSet()
            val asked = sent.toSet()
            val ordered = fresh.sortedBy { it.position }.mapNotNull { byRemote[it.id] }
            val newer = current.grid.photos.filter { it.remoteId !in listed && it.remoteId !in asked }
            current.copy(grid = current.grid.copy(photos = ordered + newer))
        }
    }

    // ── uploading ───────────────────────────────────────────────────────────

    private fun startUpload(localId: Long, uri: String, source: PhotoSource) {
        uploads[localId]?.cancel()
        uploads[localId] = viewModelScope.launch {
            val picked = readBytes(uri)
            if (picked == null) {
                setStatus(localId, UploadStatus.Failed)
                return@launch
            }
            val result = repo.upload(
                bytes = picked.bytes,
                mimeType = picked.mimeType,
                fileName = picked.fileName,
                // Hopped onto the state flow rather than touched directly: this fires on the
                // upload thread, once per 16 KiB.
                onProgress = { fraction -> setProgress(localId, fraction) },
            )
            when (result) {
                is UploadPhotoResult.Stored -> confirm(localId, result.photo, source)
                is UploadPhotoResult.Failed -> setStatus(localId, UploadStatus.Failed)
            }
            uploads.remove(localId)
        }
    }

    private fun confirm(localId: Long, stored: StoredPhoto, source: PhotoSource) {
        var slotIndex = -1
        var confirmedAfter = 0
        var crossed = false
        _state.update { current ->
            val photos = current.grid.photos.map {
                if (it.localId == localId) {
                    it.copy(status = UploadStatus.Confirmed, remoteId = stored.id, progress = 1f)
                } else {
                    it
                }
            }
            slotIndex = photos.indexOfFirst { it.localId == localId }
            confirmedAfter = photos.count { it.status == UploadStatus.Confirmed }
            crossed = current.grid.crossesMinimum(confirmedAfter)
            current.copy(
                grid = current.grid.copy(
                    photos = photos,
                    minimumReported = current.grid.minimumReported || crossed,
                ),
            )
        }
        analytics?.report(ProfileAnalytics.photoAdded(slotIndex, source, confirmedAfter))
        // ON THE FOURTH CONFIRMED UPLOAD, NOT THE FOURTH PICK -- and once, on the first crossing.
        if (crossed) analytics?.report(ProfileAnalytics.photosMinimumMet(confirmedAfter))
        // The grid may have just become complete, and a drag made while this was uploading is
        // waiting on exactly that. The server put this photo last; the user may not have.
        if (orderPending) pushOrder()
    }

    private fun setStatus(localId: Long, status: UploadStatus, progress: Float? = null) {
        _state.update { current ->
            current.copy(
                grid = current.grid.copy(
                    photos = current.grid.photos.map {
                        if (it.localId == localId) {
                            it.copy(status = status, progress = progress ?: it.progress)
                        } else {
                            it
                        }
                    },
                ),
            )
        }
    }

    private fun setProgress(localId: Long, fraction: Float) {
        _state.update { current ->
            current.copy(
                grid = current.grid.copy(
                    photos = current.grid.photos.map {
                        if (it.localId == localId) it.copy(progress = fraction) else it
                    },
                ),
            )
        }
    }
}

/** One picked image, in memory, with what the server needs to know about it. */
data class PickedBytes(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
) {
    // A data class over a ByteArray has reference equality on the array, which makes `==` lie.
    // Overridden rather than left, because this type ends up inside state comparisons.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedBytes) return false
        return bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            fileName == other.fileName
    }

    override fun hashCode(): Int =
        (bytes.contentHashCode() * 31 + mimeType.hashCode()) * 31 + fileName.hashCode()
}
