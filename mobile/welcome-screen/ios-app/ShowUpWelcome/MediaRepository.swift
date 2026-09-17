//
//  MediaRepository.swift
//  ShowUp · what the media step asks the backend, and what the answers mean (SHOWUP-161)
//
//  Every call goes through the GENERATED client. Nothing here describes a URL or a request body.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  ONE READ RETURNS THE WHOLE SCREEN
//  ─────────────────────────────────────────────────────────────────────────────
//
//  `GET /me/media` answers with both artefacts AND both previewed prompt ids AND `preview_source`,
//  in one response. That is not convenience. `media_screen_viewed` fires on every mount carrying
//  all five of those facts together, and served from two endpoints they can disagree — a view that
//  names a preview it had not yet loaded is precisely the attribution hole the tracking spec calls
//  out. One call, one snapshot.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  THE CLIENT NEVER RANKS
//  ─────────────────────────────────────────────────────────────────────────────
//
//  Decision 33. This file carries the two prompt ids the server sent and resolves nothing. The
//  fallback to §20 lives in `MediaPrompts.preview` and is a RESOLUTION, not a ranking: it decides
//  what to draw when the server said nothing, and it has no access to completion counts, no
//  ordering, and no memory between calls.
//

import Foundation
import ShowUpAPI

/// One recording as the server holds it.
struct StoredMedia: Equatable, Sendable {
    let id: String
    let kind: MediaKind
    let url: String
    let promptId: String
    let durationMs: Int
}

/// Everything the screen reads in one go.
///
/// The previews are ids as the server spelled them, NOT resolved prompts: resolving is the screen's
/// job and it is a pure function, so keeping the raw value here means the tracking payload reports
/// what the server actually said rather than what the client decided to draw.
struct MediaSnapshot: Equatable, Sendable {
    let items: [StoredMedia]
    let previewVideoId: String?
    let previewVoiceId: String?
    let previewSource: MediaPreviewSource
}

/// The answer to "store this take".
enum UploadMediaResult: Equatable, Sendable {
    case stored(StoredMedia)

    /// The upload did not land.
    ///
    /// One case, not several, and for the same reason as the photo upload: the card says the upload
    /// failed and offers a retry whatever went wrong, because there is nothing the user can do
    /// differently for a 500 than for a dropped connection.
    case failed
}

/// The answer to "delete this recording".
enum RemoveMediaResult: Equatable, Sendable {
    case removed
    case failed
}

protocol MediaRepositoring: Sendable {
    func load() async -> MediaSnapshot?
    func upload(kind: MediaKind, promptId: String, durationMs: Int,
                bytes: Data, mimeType: String, fileName: String,
                onProgress: @Sendable (Double) -> Void) async -> UploadMediaResult
    func remove(id: String) async -> RemoveMediaResult
}

/// Reads and writes the user's recordings through the generated client.
struct MediaRepository: MediaRepositoring {
    let api: ShowUpAPI

    /// What the account already holds, and what the empty cards should preview.
    ///
    /// Returns nil when the call did not succeed, which the caller reads as "do not touch what is
    /// on screen". An empty response and an unreachable server are different facts, and collapsing
    /// them would clear a card the user had just filled.
    func load() async -> MediaSnapshot? {
        do {
            let response = try await api.client.getMedia()
            switch response {
            case .ok(let ok):
                let json = try ok.body.json
                return MediaSnapshot(
                    items: json.items.compactMap { dto in
                        // An unknown kind is dropped rather than guessed. `kind` is the one field
                        // on this response that is still a closed set, and deliberately: video and
                        // voice are the two slots the screen draws, so it cannot grow without a new
                        // screen -- unlike the prompt ids beside it, which are meant to change.
                        guard let kind = MediaKind.fromTrackingValue(dto.kind.rawValue) else {
                            return nil
                        }
                        return StoredMedia(id: dto.id, kind: kind, url: dto.url,
                                           promptId: dto.mediaPromptId, durationMs: dto.durationMs)
                    },
                    previewVideoId: json.previewVideo,
                    previewVoiceId: json.previewVoice,
                    previewSource: MediaPreviewSource.fromServer(json.previewSource)
                )
            default:
                return nil
            }
        } catch {
            return await DevOfflineMedia.shared.load()
        }
    }

    /// Stores one take, replacing whatever was in that slot.
    ///
    /// - Parameter onProgress: called with 0…1 as the bytes go out. A ten-second video is by a wide
    ///   margin the largest thing this app uploads, so the card shows a real fraction rather than a
    ///   spinner — the same argument as the photo grid.
    func upload(kind: MediaKind, promptId: String, durationMs: Int,
                bytes: Data, mimeType: String, fileName: String,
                onProgress: @Sendable (Double) -> Void) async -> UploadMediaResult {
        do {
            // Reported before the request, so the card's progress is not empty while a large take
            // is being read, and completed on the answer.
            onProgress(0.1)
            let response = try await api.client.uploadMedia(
                body: .multipartForm([
                    // `file` is the part name from the schema, and the name the server's
                    // FileInterceptor listens for.
                    .file(.init(payload: .init(body: .init(bytes)), filename: fileName)),
                    .kind(.init(payload: .init(body: .init(kind.rawValue)))),
                    .mediaPromptId(.init(payload: .init(body: .init(promptId)))),
                    .durationMs(.init(payload: .init(body: .init(String(durationMs))))),
                ])
            )
            switch response {
            case .created(let created):
                let json = try created.body.json
                onProgress(1)
                return .stored(StoredMedia(id: json.id, kind: kind, url: json.url,
                                           promptId: json.mediaPromptId,
                                           durationMs: json.durationMs))
            default:
                return .failed
            }
        } catch {
            // Nothing answered. A server that replies — with anything, including 500 — lands in
            // the switch above, so this cannot hide a backend bug.
            return await DevOfflineMedia.shared.upload(
                kind: kind, promptId: promptId, durationMs: durationMs, onProgress: onProgress
            )
        }
    }

    func remove(id: String) async -> RemoveMediaResult {
        do {
            let response = try await api.client.deleteMedia(path: .init(id: id))
            switch response {
            case .noContent: return .removed
            default: return .failed
            }
        } catch {
            return await DevOfflineMedia.shared.remove(id: id)
        }
    }
}

/// Walking the media step with no backend at all.
///
/// The fourth of these, after `DevOfflineAuth`, `DevOfflineBasics` and `DevOfflinePhotos`. Read the
/// first for the argument; the three rules are identical and so are the reasons: DEBUG only, only
/// when nothing answered, and it says so with an `offline-` id.
///
/// WHY THIS ONE ALSO FAILS. The card's failed state cannot be reached by doing anything wrong — it
/// needs an upload to fail — so a stand-in that always succeeded would leave it unwalkable without
/// unplugging a router at exactly the right moment. Every third upload fails, deterministically.
///
/// THE PREVIEW ALWAYS READS `fallback` HERE, and that is honest rather than lazy: there is no
/// ranking job in this process, so there is nothing that could have ranked.
actor DevOfflineMedia {
    static let shared = DevOfflineMedia()

    private var counter = 0
    private var stored: [MediaKind: StoredMedia] = [:]

    private let uploadSeconds = 1.6
    private let steps = 20
    private let failEvery = 3

    func load() -> MediaSnapshot {
        MediaSnapshot(items: Array(stored.values), previewVideoId: nil, previewVoiceId: nil,
                      previewSource: .fallback)
    }

    func upload(kind: MediaKind, promptId: String, durationMs: Int,
                onProgress: @Sendable (Double) -> Void) async -> UploadMediaResult {
        counter += 1
        let n = counter
        for step in 0..<steps {
            try? await Task.sleep(nanoseconds: UInt64(uploadSeconds / Double(steps) * 1_000_000_000))
            onProgress(Double(step + 1) / Double(steps))
        }
        if n % failEvery == 0 { return .failed }
        let media = StoredMedia(
            id: "offline-\(n)", kind: kind,
            // No bytes were sent anywhere, so there is no URL that could serve them. The card plays
            // the LOCAL file while an upload is in flight and keeps doing so here, which is the
            // same path a real optimistic fill takes before the server answers.
            url: "", promptId: promptId, durationMs: durationMs
        )
        // Replace, exactly as the server's unique index does -- one artefact per kind.
        stored[kind] = media
        return .stored(media)
    }

    func remove(id: String) -> RemoveMediaResult {
        stored = stored.filter { $0.value.id != id }
        return .removed
    }
}
