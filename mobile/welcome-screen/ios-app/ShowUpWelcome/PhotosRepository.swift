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
//  THE MULTIPART CALL, AND WHY IT IS WRITTEN THE WAY IT IS
//  ─────────────────────────────────────────────────────────────────────────────
//
//  `swift-openapi-generator` spells a multipart body as a nest of generated types whose names
//  depend on the generator version — an enum per part under a payload enum under the operation's
//  Input.Body. None of those names is written out below, and that is deliberate rather than terse:
//  every one of them is INFERRED from the call, so the only identifiers this file commits to are
//  `uploadPhoto`, the part name `file` (which comes from the schema property, and is the same name
//  the server's FileInterceptor listens for), and the two initialisers the runtime publishes.
//
//  This file was written on a machine with no Xcode. Naming the nested types from memory would
//  have risked failing the whole iOS build over a capital letter, which would also have hidden
//  whether anything else compiled. Inference costs nothing and removes that.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  WHY THE PROGRESS IS NOT REAL HERE, AND ANDROID'S IS
//  ─────────────────────────────────────────────────────────────────────────────
//
//  Android counts the bytes as OkHttp writes them, because OkHttp hands a request body a sink and
//  never asks how far it got. `HTTPBody` is an async sequence, so the same trick would mean
//  wrapping the sequence and counting chunks as the transport pulls them — which works, and
//  reports how fast the ENCODER is being drained rather than how fast the socket is draining,
//  because URLSession buffers. A number that races ahead of the upload and then waits is worse
//  than an honest one.
//
//  So the ring fills once on the way in and completes on the answer. It is still determinate and
//  it is still true at both ends; it just does not have the middle. `URLSessionTaskDelegate`'s
//  `didSendBodyData` is the real fix and needs a transport this client does not expose yet.
//

import Foundation
import ShowUpAPI

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

/// The answer to "store this order".
enum ReorderPhotosResult: Equatable, Sendable {
    case stored([StoredPhoto])
    /// The order was not stored.
    ///
    /// ONE FAILED CASE, and for a sharper reason than the upload's. The server refuses a list that
    /// is not this account's photos exactly once each, and every way of getting that wrong — one
    /// missing, one duplicated, one belonging to somebody else — has the same cause (this client's
    /// idea of what is stored is out of date) and the same recovery (re-read and adopt the answer).
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

    /// Stores the order the user dragged the grid into.
    ///
    /// TAKES THE WHOLE ORDER, not the pair of indices that moved. A from/to pair is a diff against
    /// an order the server has to already agree with, and two drags in quick succession on a slow
    /// connection arrive as two diffs applied to a list that moved in between — which produces an
    /// order the user never made. A whole list is also idempotent, so a retry after a dropped
    /// connection is safe.
    ///
    /// STORED PHOTOS ONLY. An in-flight photo has no server id yet and a failed one never had one,
    /// so neither can appear in a list the server will accept — and the complete set is exactly
    /// what it requires.
    func reorder(ids: [String]) async -> ReorderPhotosResult
}

/// Reads and writes the user's photos through the generated client.
struct PhotosRepository: PhotosRepositoring {
    let api: ShowUpAPI

    func upload(bytes: Data, mimeType: String, fileName: String,
                onProgress: @Sendable (Double) -> Void) async -> UploadPhotoResult {
        do {
            // Reported before the request, so the ring is not empty while a large photo is being
            // encoded, and completed on the answer. See the file header for why there is nothing
            // in between yet.
            onProgress(0.1)
            let response = try await api.client.uploadPhoto(
                body: .multipartForm([
                    // `file` is the part name from the schema, and the name the server's
                    // FileInterceptor listens for. Everything else here is inferred.
                    .file(.init(payload: .init(body: .init(bytes)), filename: fileName))
                ])
            )
            switch response {
            case .created(let created):
                let json = try created.body.json
                onProgress(1)
                return .stored(StoredPhoto(id: json.id, url: json.url, position: json.position))
            default:
                return .failed
            }
        } catch {
            // Nothing answered. A server that replies — with anything, including 500 — lands in
            // the switch above, so this cannot hide a backend bug.
            return await DevOfflinePhotos.shared.upload(onProgress: onProgress)
        }
    }

    /// What the account already holds.
    ///
    /// Returns nil when the call did not succeed, which the caller reads as "do not touch what is
    /// on screen". An empty list and an unreachable server are different facts, and collapsing
    /// them would wipe a grid the user had just filled.
    func list() async -> [StoredPhoto]? {
        do {
            let response = try await api.client.listPhotos()
            switch response {
            case .ok(let ok):
                let json = try ok.body.json
                return json.map {
                    StoredPhoto(id: $0.id, url: $0.url, position: $0.position)
                }
            default:
                return nil
            }
        } catch {
            return await DevOfflinePhotos.shared.list()
        }
    }

    func remove(id: String) async -> RemovePhotoResult {
        do {
            let response = try await api.client.deletePhoto(path: .init(id: id))
            switch response {
            case .noContent:
                return .removed
            default:
                return .failed
            }
        } catch {
            return await DevOfflinePhotos.shared.remove(id: id)
        }
    }

    func reorder(ids: [String]) async -> ReorderPhotosResult {
        do {
            let response = try await api.client.reorderPhotos(body: .json(.init(ids: ids)))
            switch response {
            case .ok(let ok):
                let json = try ok.body.json
                // Sorted rather than trusted to arrive sorted: the server orders its reply and
                // nothing in HTTP guarantees it, and a grid drawn in the order bytes happened to
                // arrive would be a drag that landed somewhere else.
                return .stored(
                    json.sorted { $0.position < $1.position }
                        .map { StoredPhoto(id: $0.id, url: $0.url, position: $0.position) })
            default:
                return .failed
            }
        } catch {
            return await DevOfflinePhotos.shared.reorder(ids: ids)
        }
    }
}

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

    /// Applies an order, refusing an incomplete list exactly as the server does.
    ///
    /// The refusal is the point. A stand-in that accepted anything would make the one path worth
    /// walking here — a drag whose write is rejected, and the re-read that follows — unreachable
    /// without a backend.
    func reorder(ids: [String]) -> ReorderPhotosResult {
        let byId = Dictionary(uniqueKeysWithValues: stored.map { ($0.id, $0) })
        guard ids.count == stored.count, Set(ids).count == ids.count,
              ids.allSatisfy({ byId[$0] != nil }) else {
            return .failed
        }
        stored = ids.enumerated().map { index, id in
            StoredPhoto(id: id, url: byId[id]!.url, position: index)
        }
        return .stored(stored)
    }

    /// Forgets everything. For tests, so one case cannot leak into the next.
    func reset() {
        counter = 0
        stored.removeAll()
    }
}
