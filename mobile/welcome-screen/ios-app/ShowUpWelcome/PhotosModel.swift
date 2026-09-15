//
//  PhotosModel.swift
//  ShowUp · the photo step's asynchronous work (SHOWUP-156)
//
//  The Swift port of `profile/PhotosViewModel.kt`. The screen loads, uploads and retries, which is
//  exactly the trigger the iOS CLAUDE.md names for `@Observable`: "when a screen owns asynchronous
//  work that must outlive a redraw and be cancellable". The view itself stays a function from
//  values to pixels and holds nothing.
//
//  THE RULES THIS FILE ENFORCES, and where each came from:
//
//    · THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD.
//    · FAILURE KEEPS THE SLOT, with its image, so Retry re-uploads rather than re-picking.
//    · `photos_minimum_met` FIRES ONCE, on the first crossing.
//    · THE PERMISSION STATUS IS RE-READ ON EVERY FOREGROUND — `refreshAccess` exists for one bug,
//      "a user who granted access in Settings returning to the blocked card".
//    · ONE UPLOAD PER PHOTO, keyed by local id, so a removal mid-upload cancels exactly one.
//

import Foundation

/// One picked image, in memory, with what the server needs to know about it.
struct PickedBytes: Sendable {
    let bytes: Data
    let mimeType: String
    let fileName: String
}

@MainActor
@Observable
final class PhotosModel {
    private(set) var grid = PhotoGridState()
    private(set) var library: LibraryAccess = .notNeeded
    private(set) var camera: CameraAccess = .canAsk
    private(set) var sheetOpen = false
    /// Which slot the sheet was opened for.
    ///
    /// Kept because both rows come back with a photo that has to land SOMEWHERE, and "the slot the
    /// user tapped" is the only correct answer — a photo picked from slot 3 that appended to the
    /// end would silently move the user's main photo.
    private(set) var pendingSlot = 0

    private let repo: any PhotosRepositoring
    private let access: any PhotoAccessReading
    private let analytics: (any AnalyticsTracking)?

    /// One task per in-flight photo, so cancelling one cannot touch another.
    private var uploads: [Int64: Task<Void, Never>] = [:]
    /// The bytes behind each picked photo, kept until it is confirmed or removed.
    ///
    /// WHY THIS DIFFERS FROM ANDROID, which keeps a content URI and re-reads it on Retry.
    /// `PHPickerViewController` hands back an item provider that is loaded ONCE; there is no
    /// durable URI to go back to, and asking PhotoKit for one is the library permission this whole
    /// screen is arranged to avoid. So the bytes are held until the upload is confirmed — which is
    /// also what makes Retry re-upload the same photo rather than sending the user back to the
    /// picker, the behaviour the ticket requires on both platforms.
    private var pickedBytes: [Int64: PickedBytes] = [:]
    private var nextLocalId: Int64 = 1

    init(repo: any PhotosRepositoring,
         access: any PhotoAccessReading,
         analytics: (any AnalyticsTracking)? = nil) {
        self.repo = repo
        self.access = access
        self.analytics = analytics
    }

    // MARK: access

    /// Re-reads both permission statuses. Call on every foreground.
    func refreshAccess() {
        library = access.library()
        camera = access.camera()
    }

    // MARK: the grid

    /// A slot was tapped. Opens the source sheet — never the picker directly.
    func tapSlot(_ index: Int) {
        let occupied = grid.at(index) != nil
        analytics?.report(ProfileAnalytics.photoSlotTapped(
            slotIndex: index, isOptional: isOptionalSlot(index),
            action: slotTapAction(occupied: occupied)))
        pendingSlot = index
        sheetOpen = true
    }

    func dismissSheet() { sheetOpen = false }

    /// `Add more`. Reported as a slot tap with the reveal action, per the registry's own note.
    func revealOptional() {
        analytics?.report(ProfileAnalytics.photoSlotTapped(
            slotIndex: photosRequired, isOptional: true, action: "reveal_optional"))
        grid.optionalRevealed = true
    }

    /// A photo came back from the picker or the camera.
    ///
    /// The slot is occupied IMMEDIATELY, in flight, with the picked image showing — before a byte
    /// has gone anywhere. That is the state the ticket is most specific about: the user has to see
    /// WHICH photo is uploading, which an empty slot with a spinner cannot say.
    func picked(_ bytes: PickedBytes, source: PhotoSource) {
        let slot = pendingSlot
        let id = nextLocalId
        nextLocalId += 1
        pickedBytes[id] = bytes
        // `uri` is a marker for the slot rather than something to fetch: on this platform the
        // bytes ARE the photo. It is non-nil so the slot draws as occupied from this frame.
        let entry = PickedPhoto(localId: id, uri: "picked://\(id)", status: .inFlight)
        if slot < grid.photos.count { grid.photos[slot] = entry } else { grid.photos.append(entry) }
        sheetOpen = false
        startUpload(localId: id, source: source)
    }

    /// Retry re-uploads into the SAME slot. The user never re-picks.
    func retry(_ index: Int) {
        guard let photo = grid.at(index), photo.status == .failed,
              pickedBytes[photo.localId] != nil else { return }
        setStatus(photo.localId, .inFlight, progress: 0)
        // The source is not re-asked: the photo is the one already picked. Recorded here so the
        // choice is visible rather than implied by a default.
        startUpload(localId: photo.localId, source: .library)
    }

    /// REMOVE IS IMMEDIATE AND HAS NO CONFIRMATION. Re-adding is one tap.
    func remove(_ index: Int) {
        guard let photo = grid.at(index) else { return }
        uploads.removeValue(forKey: photo.localId)?.cancel()
        pickedBytes.removeValue(forKey: photo.localId)
        grid.photos.remove(at: index)
        analytics?.report(ProfileAnalytics.photoRemoved(
            slotIndex: index, filledCount: grid.confirmedCount))
        // Only a stored photo has anything to delete on the server.
        guard let remoteId = photo.remoteId else { return }
        Task { _ = await repo.remove(id: remoteId) }
    }

    /// Drag finished.
    ///
    /// Local only. `PATCH` for a photo's position is NOT in the contract — `PhotoDto` carries a
    /// `position` and no route writes it — so the order survives until the screen is left and no
    /// further. Recorded as a dependency rather than papered over with a delete and re-upload,
    /// which would lose the photo if the second call failed.
    func reorder(from: Int, to: Int) {
        guard from != to else { return }
        grid.photos = moved(grid.photos, from: from, to: to)
        analytics?.report(ProfileAnalytics.photoReordered(from: from, to: to))
    }

    // MARK: uploading

    private func startUpload(localId: Int64, source: PhotoSource) {
        uploads[localId]?.cancel()
        guard let picked = pickedBytes[localId] else {
            setStatus(localId, .failed)
            return
        }
        uploads[localId] = Task { [weak self] in
            guard let self else { return }
            let result = await self.repo.upload(
                bytes: picked.bytes, mimeType: picked.mimeType, fileName: picked.fileName,
                // Hopped back onto the main actor rather than touched directly: this fires on the
                // upload's own executor, once per chunk.
                onProgress: { fraction in
                    Task { @MainActor [weak self] in self?.setProgress(localId, fraction) }
                })
            guard !Task.isCancelled else { return }
            switch result {
            case .stored(let photo): self.confirm(localId: localId, stored: photo, source: source)
            case .failed: self.setStatus(localId, .failed)
            }
            self.uploads.removeValue(forKey: localId)
        }
    }

    private func confirm(localId: Int64, stored: StoredPhoto, source: PhotoSource) {
        guard let index = grid.photos.firstIndex(where: { $0.localId == localId }) else { return }
        grid.photos[index].status = .confirmed
        grid.photos[index].remoteId = stored.id
        grid.photos[index].progress = 1
        // Confirmed means the server has it; holding the bytes past that is megabytes of nothing.
        pickedBytes.removeValue(forKey: localId)
        let confirmedAfter = grid.confirmedCount
        let crossed = grid.crossesMinimum(nextConfirmed: confirmedAfter)
        if crossed { grid.minimumReported = true }
        analytics?.report(ProfileAnalytics.photoAdded(
            slotIndex: index, source: source, filledCount: confirmedAfter))
        // ON THE FOURTH CONFIRMED UPLOAD, NOT THE FOURTH PICK — and once, on the first crossing.
        if crossed { analytics?.report(ProfileAnalytics.photosMinimumMet(count: confirmedAfter)) }
    }

    private func setStatus(_ localId: Int64, _ status: UploadStatus, progress: Double? = nil) {
        guard let index = grid.photos.firstIndex(where: { $0.localId == localId }) else { return }
        grid.photos[index].status = status
        if let progress { grid.photos[index].progress = progress }
    }

    private func setProgress(_ localId: Int64, _ fraction: Double) {
        guard let index = grid.photos.firstIndex(where: { $0.localId == localId }) else { return }
        grid.photos[index].progress = fraction
    }
}
