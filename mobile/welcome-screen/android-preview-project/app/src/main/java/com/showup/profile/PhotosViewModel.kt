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
     * Local only. `PATCH` for a photo's position is NOT in the contract -- `PhotoDto` carries a
     * `position` and no route writes it -- so the order survives until the screen is left and no
     * further. Recorded as a dependency on SHOWUP-156 rather than papered over with a delete and
     * re-upload, which would lose the photo if the second call failed.
     */
    fun reorder(from: Int, to: Int) {
        if (from == to) return
        _state.update { it.copy(grid = it.grid.copy(photos = it.grid.photos.movedTo(from, to))) }
        analytics?.report(ProfileAnalytics.photoReordered(from, to))
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
