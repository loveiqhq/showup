//
//  MediaPrompts.swift
//  ShowUp · the eleven prompts and the escape hatch (SHOWUP-161)
//
//  ─────────────────────────────────────────────────────────────────────────────
//  IDS ARE STORED, STRINGS ARE SHOWN, AND THEY ARE NOT THE SAME THING
//  ─────────────────────────────────────────────────────────────────────────────
//
//  Every id here is `media_prompt_id` from `enums.json` §20. The display strings come from the
//  reference file's `MEDIA_PROMPTS`, verbatim and in order, and they are CONTENT: they will be
//  edited. That is the whole reason the id exists — "an edit must not orphan the clips recorded
//  under them" — so nothing outside this file ever handles a display string as an identity.
//
//  `media_prompt_id` IS NOT `prompt_id`. `prompt_id` and `topic_id` (§6, §17) belong to the
//  WRITTEN profile prompts on screen 07 — a different registry for a different screen. The
//  recording events carried `prompt_id` until taxonomy 1.4 and were renamed for exactly this
//  collision, so a build that reuses `PromptTopics` here would be re-making the mistake the rename
//  fixed.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  THE ORDER NEVER CHANGES; ONLY THE PREVIEW DOES
//  ─────────────────────────────────────────────────────────────────────────────
//
//  The sheet renders this list in this order on every open, for both media. The tracking spec is
//  explicit: "The list order never changes — only the preview does. A list that reorders itself
//  makes a returning user hunt for the row they wanted." So there is no sort, no ranking and no
//  per-user shuffle in this file or anywhere it is read.
//

import Foundation

/// Which medium a recording is.
///
/// Its `trackingValue` is `type` on every event this screen fires. Not optional and not derivable:
/// "without it a tap on the voice card and a tap on the video card are the same row, and nothing
/// about this screen can be answered".
enum MediaKind: String, Sendable, CaseIterable, Codable {
    case video
    case voice

    var trackingValue: String { rawValue }

    /// `step_id` from §2. Both share `step_index` 3 — one screen carries two steps.
    var stepId: String {
        switch self {
        case .video: return "media_video"
        case .voice: return "media_voice"
        }
    }

    /// §2 gives both media ids the same index: one screen, step 3 of 3 of "The real you".
    static let stepIndex = 3

    static func fromTrackingValue(_ value: String) -> MediaKind? { MediaKind(rawValue: value) }
}

/// One row of §20: a stable id and the copy it shows.
struct MediaPrompt: Equatable, Sendable {
    let id: String
    let position: Int
    let display: String

    /// `is_own_prompt` on every event that carries a prompt. True for exactly one row.
    var isOwn: Bool { id == MediaPrompts.ownIdea }
}

enum MediaPrompts {

    /// The escape hatch's id. Selectable and recordable; never previewed, never ranked.
    static let ownIdea = "own_idea"

    /// The eleven, then the escape hatch, in §20 `position` order.
    ///
    /// Do not retype these and do not reorder them — the ticket says so twice. They are checked
    /// character-for-character by `MediaPromptsTests` against the strings quoted in the ticket.
    static let all: [MediaPrompt] = [
        MediaPrompt(id: "everyday_good_mood", position: 0,
                    display: "The little everyday thing that instantly puts me in a good mood"),
        MediaPrompt(id: "ideal_sunny_morning", position: 1,
                    display: "What my ideal sunny morning looks like"),
        MediaPrompt(id: "relaxing_sound", position: 2,
                    display: "A sound that always makes me feel relaxed"),
        MediaPrompt(id: "comfort_snack", position: 3,
                    display: "My go-to comfort snack when having a good day"),
        MediaPrompt(id: "friends_three_words", position: 4,
                    display: "How my friends would describe my energy in three words"),
        MediaPrompt(id: "best_weather", position: 5,
                    display: "The kind of weather that brings out the best in me"),
        MediaPrompt(id: "song_makes_me_move", position: 6,
                    display: "A song that always makes me want to move"),
        MediaPrompt(id: "relaxed_and_happy", position: 7,
                    display: "What I usually look like when I'm relaxed and happy"),
        MediaPrompt(id: "simple_pleasure", position: 8,
                    display: "The best simple pleasure in my daily routine"),
        MediaPrompt(id: "made_me_smile", position: 9,
                    display: "Something cute or funny that made me smile this week"),
        MediaPrompt(id: "easy_30_outside", position: 10,
                    display: "My favorite way to spend an easy 30 minutes outside"),
        MediaPrompt(id: ownIdea, position: 11, display: "Something else — my own idea"),
    ]

    /// How many the eyebrow claims: `One of 11 prompts`.
    ///
    /// DERIVED, NOT TYPED. The copy names a number that has to stay true if a twelfth prompt is
    /// ever added, and a hard-coded 11 beside a list of twelve entries is the kind of drift no test
    /// notices. The escape hatch is excluded because it is not one of the prompts.
    /// `let`, not a computed `var`: the value cannot change and `check-swift-concurrency.py`
    /// cannot tell a computed `var` from a stored one at file scope.
    static let count: Int = all.filter { !$0.isOwn }.count

    /// The cold-start preview per medium — §20's `cold_start_preview` column.
    ///
    /// A FALLBACK, NOT A DESIGN CHOICE. The live preview is ranked by the server and arrives on the
    /// state read; this is what an empty card shows when the server has sent nothing. Different per
    /// medium so the two cards never read as duplicates: one you would show, one you would play.
    static func coldStart(_ kind: MediaKind) -> MediaPrompt {
        switch kind {
        case .video: return byId("relaxed_and_happy")!
        case .voice: return byId("relaxing_sound")!
        }
    }

    static func byId(_ id: String?) -> MediaPrompt? {
        guard let id else { return nil }
        return all.first { $0.id == id }
    }

    /// The prompt an empty card previews, given what the server said.
    ///
    /// THE CLIENT NEVER RANKS — decision 33. This resolves a value; it does not choose one. An
    /// unrecognised id falls back rather than rendering blank, because a card with no prompt on it
    /// is the one thing worse than a slightly stale prompt. `own_idea` is refused for the same
    /// reason the server refuses it: "Something else" suggests nothing.
    static func preview(_ kind: MediaKind, fromServer: String?) -> MediaPrompt {
        if let served = byId(fromServer), !served.isOwn { return served }
        return coldStart(kind)
    }
}
