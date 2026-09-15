//
//  PhotosRepository.swift
//  ShowUp · what the photo step asks the backend, and what the answers mean (SHOWUP-156)
//
//  The Swift counterpart of `profile/PhotosRepository.kt`, and it is NOT yet its equal. What is
//  here, what is not, and why, in that order — because the difference is the one real parity gap
//  in this ticket and it should be found here rather than discovered in a simulator.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  THE CONTRACT GAP, AND HOW IT WAS FIXED
//  ─────────────────────────────────────────────────────────────────────────────
//
//  `POST /me/photos` was emitted with NO REQUEST BODY. The controller carried
//  `@ApiConsumes('multipart/form-data')`, which declares the content type and nothing else, so the
//  operation had no `requestBody` and BOTH generated clients exposed an upload call that could not
//  carry a photo. Found on 15 September 2026 while wiring this screen and fixed at the source,
//  with `@ApiBody` on `photos.controller.ts`, rather than by hand-writing a multipart request —
//  which would be a second contract, and the rule against that is why neither repository has a URL
//  in it.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  WHAT IS STILL MISSING HERE, STATED PLAINLY
//  ─────────────────────────────────────────────────────────────────────────────
//
//  `upload` goes to `DevOfflinePhotos` and not to the server. The generated call exists now that
//  the contract does, but `swift-openapi-generator` spells a multipart body as a nested type whose
//  name depends on the generator version — `Operations.uploadPhoto.Input.Body.multipartForm` and a
//  per-part payload under it — and this repository is being written on a machine with no Xcode.
//  Guessing those names would fail the WHOLE iOS build, which would also hide whether the fifteen
//  other files added by this ticket compile. So the seam is here and the one call is not, and this
//  paragraph is the handover: build once on a Mac, read the generated `Operations.uploadPhoto`,
//  and write `upload` the way `BasicsRepository` writes `updateProfile`.
//
//  `list` and `remove` are equally unwritten, and deliberately so rather than half-wired: there is
//  nothing to list or delete until an upload can land, and a repository that could delete photos it
//  could not create would be a strange thing to leave behind.
//
//  Android's equivalent IS wired, tested against MockWebServer, and reports real byte-by-byte
//  upload progress. That asymmetry is the gap, and it is one function wide.
//

import Foundation

/// One photo as the server holds it.
struct StoredPhoto: Equatable, Sendable {
    let id: String
    let url: String
    let position: Int
}

/// The answer to "store this image".
enum UploadPhotoResult: Equatable, Sendable {
    case stored(StoredPhoto)
    /// The upload did not land.
    ///
    /// One case, not several, and deliberately: the slot's failed state says `Upload failed` and
    /// offers `Retry` whatever went wrong, because there is nothing the user can do differently for
    /// a 500 than for a dropped connection.
    case failed
}

/// The answer to "delete this photo".
enum RemovePhotoResult: Equatable, Sendable {
    case removed
    case failed
}

/// Reads and writes the user's photos.
///
/// A protocol so the model can be driven from a test with no network, and so the one place the
/// generated client is called has a name before it has a body.
protocol PhotosRepositoring: Sendable {
    /// - Parameter onProgress: called with 0…1 as the bytes go out. The slot's ring is
    ///   DETERMINATE — the ticket contrasts it with a spinner on purpose, because a spinner says
    ///   something is happening and a ring says how much is left.
    func upload(bytes: Data, mimeType: String, fileName: String,
                onProgress: @Sendable (Double) -> Void) async -> UploadPhotoResult
    func list() async -> [StoredPhoto]?
    func remove(id: String) async -> RemovePhotoResult
}

/// The implementation the app uses today.
///
/// Every call goes to `DevOfflinePhotos`. See the file header for what has to happen before it
/// goes anywhere else.
struct PhotosRepository: PhotosRepositoring {
    let api: ShowUpAPIAccess?

    init(api: ShowUpAPIAccess? = nil) { self.api = api }

    func upload(bytes: Data, mimeType: String, fileName: String,
                onProgress: @Sendable (Double) -> Void) async -> UploadPhotoResult {
        await DevOfflinePhotos.shared.upload(onProgress: onProgress)
    }

    func list() async -> [StoredPhoto]? {
        await DevOfflinePhotos.shared.list()
    }

    func remove(id: String) async -> RemovePhotoResult {
        await DevOfflinePhotos.shared.remove(id: id)
    }
}

/// What the repository will need once the generated call is written.
///
/// A marker rather than a concrete type, so this file does not import the API module for a
/// dependency it is not yet using — and so the day it does, the change is one line here and one
/// function above.
protocol ShowUpAPIAccess: Sendable {}

/// The stand-in for `/me/photos`, used whenever nothing else can answer.
///
/// The third of these, after `DevOfflineAuth` and the email one. The three rules are identical:
/// it is only reached when nothing answered, it never overrides a server that replied, and the
/// screen says so.
///
/// WHY THIS ONE ALSO FAILS. The other stand-ins always succeed on the happy path, because the
/// states they gate are reachable by typing something wrong. `Upload failed` and `Retry` cannot be
/// reached by doing anything wrong — they need an upload to fail — so a stand-in that always
/// succeeded would leave two of the seven states unwalkable without unplugging a router at exactly
/// the right moment. EVERY THIRD upload fails, deterministically rather than randomly, because a
/// state you cannot reproduce on demand is not one you can demonstrate.
actor DevOfflinePhotos {
    static let shared = DevOfflinePhotos()

    private var counter = 0
    private var stored: [StoredPhoto] = []

    /// How long a pretend upload takes. Long enough to see the ring fill, short enough to demo.
    private let uploadSeconds: Double = 1.4
    /// How many steps the ring takes. The real one reports per 16 KiB chunk.
    private let steps = 20
    /// Every third upload fails, so the failed slot and Retry are reachable.
    private let failEvery = 3

    func upload(onProgress: @Sendable (Double) -> Void) async -> UploadPhotoResult {
        counter += 1
        let n = counter
        for step in 1...steps {
            try? await Task.sleep(nanoseconds: UInt64(uploadSeconds / Double(steps) * 1_000_000_000))
            onProgress(Double(step) / Double(steps))
        }
        if n % failEvery == 0 { return .failed }
        let photo = StoredPhoto(
            id: "offline-\(n)",
            // Not a URL anything can fetch, and named so nobody mistakes it for one.
            url: "offline://photo/\(n)",
            position: stored.count)
        stored.append(photo)
        return .stored(photo)
    }

    /// What the stand-in is holding. Empty on a fresh launch — nothing is persisted.
    func list() -> [StoredPhoto] { stored }

    func remove(id: String) -> RemovePhotoResult {
        stored.removeAll { $0.id == id }
        return .removed
    }

    /// Forgets everything. For tests, so one case cannot leak into the next.
    func reset() {
        counter = 0
        stored.removeAll()
    }
}
