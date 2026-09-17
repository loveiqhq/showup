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
    /// A drag that could not be sent yet because the grid was not all stored. See `pushOrder`.
    private var orderPending = false

    init(repo: any PhotosRepositoring,
         access: any PhotoAccessReading,
         analytics: (any AnalyticsTracking)? = nil) {
        self.repo = repo
        self.access = access
        self.analytics = analytics
    }

    // MARK: arriving

    /// Reads the photos the account already holds.
    ///
    /// WHY THIS WAS MISSING, AND WHAT IT COST. The prompts screen has had a `load` since
    /// persistence landed; this one never did, so the grid started EMPTY on every launch however
    /// many photos the account held. Nothing looked wrong — an empty grid is what a new user sees
    /// — right up until the seventh upload.
    ///
    /// The server allows six. An account already at six answers every further upload with a 400,
    /// and a refused slot renders `Upload failed` with a `Retry` that re-sends the same bytes to
    /// the same full account. So a user who had already added six came back to an empty screen,
    /// added one, and was told it failed, forever, with no sight of the six that were the reason.
    ///
    /// That is the third bug of this shape on this screen: a PERMANENT refusal wearing a
    /// transient failure's clothes. The other two — a HEIC the server would not take, and a photo
    /// past its size cap — were fixed by sending something acceptable. This one is not fixable at
    /// the upload: the only honest fix is to SHOW the photos, because the grid being wrong is
    /// what made the refusal look arbitrary.
    ///
    /// ANYTHING PICKED IN THIS SESSION SURVIVES a read that lands while an upload is in flight.
    func load() {
        Task { [weak self] in
            guard let self, let stored = await self.repo.list() else { return }
            let fromServer = stored.sorted { $0.position < $1.position }
                .enumerated().map { index, photo -> PickedPhoto in
                let id = self.nextLocalId
                self.nextLocalId += 1
                // The server's URL. The slot draws a placeholder for anything it cannot decode
                // locally, which is honest: this build has no image loader, and a blank tile would
                // claim the photo was not there.
                var entry = PickedPhoto(localId: id, uri: photo.url, status: .confirmed)
                entry.remoteId = photo.id
                entry.progress = 1
                entry.slot = index
                return entry
            }
            // ANYTHING THE READ DID NOT MENTION IS KEPT, not just the in-flight ones. A photo
            // that confirmed between the request going out and this merge running has a remote id
            // the answer predates, and filtering on "no remote id" would drop the slot the user
            // just watched land. Same rule, same reason, as `adoptServerOrder`.
            let listed = Set(stored.map(\.id))
            let unstored = self.grid.photos.filter { $0.remoteId.map { !listed.contains($0) } ?? true }
            self.grid.photos = fromServer + unstored
            // NOT A CROSSING. `photos_minimum_met` fires when the count FIRST reaches four, and an
            // account that already held four did not reach it just now — reporting it here would
            // put a threshold event on every relaunch.
            if fromServer.count >= photosRequired { self.grid.minimumReported = true }
            // Six already stored means slots 5 and 6 are occupied, so the block they sit behind
            // has to be open or two of the photos would have nowhere to be.
            if fromServer.count > photosRequired { self.grid.optionalRevealed = true }
        }
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
        // THE BOX THE USER TAPPED, and a photo already in it is replaced rather than pushed
        // aside — "on a filled slot the chosen photo replaces that one". `Add more` taps the first
        // free box, so an append is just a tap on an empty one.
        var entry = PickedPhoto(localId: id, uri: "picked://\(id)", status: .inFlight)
        entry.slot = slot
        grid.photos = (grid.photos.filter { $0.slot != slot } + [entry])
            .sorted { $0.slot < $1.slot }
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
        // THE BOX STAYS EMPTY. It used to close up, so deleting the second photo slid the third
        // and fourth left and the empty box appeared at the END — which reads as "the last one was
        // deleted", and left no way to put a new photo back where the old one was.
        grid.photos.removeAll { $0.slot == index }
        analytics?.report(ProfileAnalytics.photoRemoved(
            slotIndex: index, filledCount: grid.confirmedCount))
        // Only a stored photo has anything to delete on the server.
        guard let remoteId = photo.remoteId else { return }
        Task { _ = await repo.remove(id: remoteId) }
    }

    /// Drag finished.
    ///
    /// THE GRID MOVES FIRST AND THE WRITE FOLLOWS. A tile that sprang back to wait for a round
    /// trip would read as the drag having failed, so the move is applied on the frame the finger
    /// lifts and `PATCH /me/photos/order` catches up. If the server refuses, `pushOrder` adopts
    /// the order it reports instead — see there for why that is the safe direction.
    func reorder(from: Int, to: Int) {
        guard from != to else { return }
        // A SWAP, not a shuffle. On a grid of six boxes where any may be empty, "take it out and
        // push everything along" has nothing to mean — there is nothing to push into an empty box.
        grid.photos = grid.photos.map { photo in
            var p = photo
            if photo.slot == from { p.slot = to } else if photo.slot == to { p.slot = from }
            return p
        }.sorted { $0.slot < $1.slot }
        analytics?.report(ProfileAnalytics.photoReordered(from: from, to: to))
        pushOrder()
    }

    /// Sends the current order, or remembers to send it once the grid is all stored.
    ///
    /// THE ROUTE TAKES THE COMPLETE SET OF STORED PHOTOS AND NOTHING ELSE, so a grid with an
    /// upload still in flight — or a failed slot the user has not retried — has no complete list
    /// to send yet. Dropping the drag on the floor in that case would lose it: the upload lands,
    /// the server appends the new photo, and the order the user made is gone. So the drag is
    /// remembered in `orderPending` and `confirm` pushes it the moment the last slot is stored.
    ///
    /// A FAILED SLOT NEVER RESOLVES ON ITS OWN, which is why this is not a wait for "no uploads in
    /// flight": the pending order simply stays pending until the user retries or removes it, and
    /// either of those ends with a complete grid and a push.
    private func pushOrder() {
        let ids = grid.photos.sorted { $0.slot < $1.slot }.compactMap(\.remoteId)
        guard ids.count == grid.photos.count else {
            orderPending = true
            return
        }
        orderPending = false
        guard !ids.isEmpty else { return }
        Task { [weak self] in
            guard let self else { return }
            switch await self.repo.reorder(ids: ids) {
            case .stored:
                break
            case .failed:
                // The server and this grid disagree about what the account holds — a photo removed
                // on another device, most likely. Re-read and adopt: the list it returns is the one
                // both sides can agree on, and guessing which end is stale is how two devices end
                // up overwriting each other.
                if let fresh = await self.repo.list() {
                    self.adoptServerOrder(fresh, sent: ids)
                }
            }
        }
    }

    /// Rebuilds the grid from the server's list, after a refusal.
    ///
    /// Photos the read mentions take the order it gives. A photo this grid thought was stored and
    /// the read does NOT mention is dropped — that is the whole reason for re-reading, and the
    /// likeliest cause is that it was removed on another device.
    ///
    /// DROPPED ONLY IF IT WAS IN THE ORDER WE SENT. Anything else is newer than the answer: a
    /// photo still uploading, or one that landed between the refusal and the read going out. The
    /// read cannot know about either, so its silence is not evidence that they are gone, and
    /// deleting a photo from the grid because of a race the user never saw is the worse of the two
    /// mistakes.
    private func adoptServerOrder(_ fresh: [StoredPhoto], sent: [String]) {
        let byRemote = Dictionary(
            grid.photos.compactMap { photo in photo.remoteId.map { ($0, photo) } },
            uniquingKeysWith: { first, _ in first })
        let listed = Set(fresh.map(\.id))
        let asked = Set(sent)
        let ordered = fresh.sorted { $0.position < $1.position }.compactMap { byRemote[$0.id] }
        let newer = grid.photos.filter { photo in
            guard let remoteId = photo.remoteId else { return true }
            return !listed.contains(remoteId) && !asked.contains(remoteId)
        }
        grid.photos = ordered + newer
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
        // The grid may have just become complete, and a drag made while this was uploading is
        // waiting on exactly that. The server put this photo last; the user may not have.
        if orderPending { pushOrder() }
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
