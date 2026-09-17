/*
 * MediaPrompts.kt
 * ShowUp · the eleven prompts and the escape hatch (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * IDS ARE STORED, STRINGS ARE SHOWN, AND THEY ARE NOT THE SAME THING
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Every id here is `media_prompt_id` from `enums.json` §20. The display strings come from the
 * reference file's `MEDIA_PROMPTS`, verbatim and in order, and they are CONTENT: they will be
 * edited. That is the whole reason the id exists — "an edit must not orphan the clips recorded
 * under them" — so nothing outside this file ever handles a display string as an identity.
 *
 * `media_prompt_id` IS NOT `prompt_id`. `prompt_id` and `topic_id` (§6, §17) belong to the WRITTEN
 * profile prompts on screen 07 — a different registry for a different screen. The recording events
 * carried `prompt_id` until taxonomy 1.4 and were renamed for exactly this collision, so a build
 * that reuses `PromptTopics` here would be re-making the mistake the rename fixed.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE ORDER NEVER CHANGES; ONLY THE PREVIEW DOES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The sheet renders this list in this order on every open, for both media. The tracking spec is
 * explicit: "The list order never changes — only the preview does. A list that reorders itself
 * makes a returning user hunt for the row they wanted." So there is no sort, no ranking and no
 * per-user shuffle in this file or anywhere it is read.
 *
 * [OWN_IDEA] is last, always, and is the only entry that is not a prompt. It never ranks and is
 * never removed.
 */
package com.showup.profile

/**
 * Which medium a recording is.
 *
 * Its [trackingValue] is `type` on every event this screen fires. Not optional and not derivable:
 * "without it a tap on the voice card and a tap on the video card are the same row, and nothing
 * about this screen can be answered".
 */
enum class MediaKind(val trackingValue: String) {
    Video("video"),
    Voice("voice"),
    ;

    /** `step_id` from §2. Both share `step_index` 3 — one screen carries two steps. */
    val stepId: String
        get() = when (this) {
            Video -> "media_video"
            Voice -> "media_voice"
        }

    companion object {
        /** §2 gives both media ids the same index: one screen, step 3 of 3 of "The real you". */
        const val STEP_INDEX = 3

        fun fromTrackingValue(value: String): MediaKind? =
            entries.firstOrNull { it.trackingValue == value }
    }
}

/** One row of §20: a stable id and the copy it shows. */
data class MediaPrompt(
    val id: String,
    val position: Int,
    val display: String,
) {
    /** `is_own_prompt` on every event that carries a prompt. True for exactly one row. */
    val isOwn: Boolean get() = id == MediaPrompts.OWN_IDEA
}

object MediaPrompts {

    /** The escape hatch's id. Selectable and recordable; never previewed, never ranked. */
    const val OWN_IDEA = "own_idea"

    /**
     * The eleven, then the escape hatch, in §20 `position` order.
     *
     * Do not retype these and do not reorder them — the ticket says so twice. They are checked
     * character-for-character by `MediaPromptsTest` against the strings quoted in the ticket.
     */
    val ALL: List<MediaPrompt> = listOf(
        MediaPrompt("everyday_good_mood", 0, "The little everyday thing that instantly puts me in a good mood"),
        MediaPrompt("ideal_sunny_morning", 1, "What my ideal sunny morning looks like"),
        MediaPrompt("relaxing_sound", 2, "A sound that always makes me feel relaxed"),
        MediaPrompt("comfort_snack", 3, "My go-to comfort snack when having a good day"),
        MediaPrompt("friends_three_words", 4, "How my friends would describe my energy in three words"),
        MediaPrompt("best_weather", 5, "The kind of weather that brings out the best in me"),
        MediaPrompt("song_makes_me_move", 6, "A song that always makes me want to move"),
        MediaPrompt("relaxed_and_happy", 7, "What I usually look like when I'm relaxed and happy"),
        MediaPrompt("simple_pleasure", 8, "The best simple pleasure in my daily routine"),
        MediaPrompt("made_me_smile", 9, "Something cute or funny that made me smile this week"),
        MediaPrompt("easy_30_outside", 10, "My favorite way to spend an easy 30 minutes outside"),
        MediaPrompt(OWN_IDEA, 11, "Something else — my own idea"),
    )

    /**
     * How many the eyebrow claims: `One of 11 prompts`.
     *
     * DERIVED, NOT TYPED. The copy names a number that has to stay true if a twelfth prompt is ever
     * added, and a hard-coded 11 beside a list of twelve entries is the kind of drift no test
     * notices. The escape hatch is excluded because it is not one of the prompts.
     */
    val COUNT: Int get() = ALL.count { !it.isOwn }

    /**
     * The cold-start preview per medium — §20's `cold_start_preview` column.
     *
     * A FALLBACK, NOT A DESIGN CHOICE. The live preview is ranked by the server and arrives on the
     * state read; this is what an empty card shows when the server has sent nothing — a fresh
     * install, an offline first run, or a market the ranking has no sample for. Different per
     * medium so the two cards never read as duplicates: one you would show, one you would play.
     */
    fun coldStart(kind: MediaKind): MediaPrompt = when (kind) {
        MediaKind.Video -> byId("relaxed_and_happy")!!
        MediaKind.Voice -> byId("relaxing_sound")!!
    }

    fun byId(id: String?): MediaPrompt? = ALL.firstOrNull { it.id == id }

    /**
     * The prompt an empty card previews, given what the server said.
     *
     * THE CLIENT NEVER RANKS — decision 33. This resolves a value; it does not choose one. An
     * unrecognised id falls back rather than rendering blank, because a card with no prompt on it
     * is the one thing worse than a slightly stale prompt. `own_idea` is refused for the same
     * reason the server refuses it: "Something else" suggests nothing.
     */
    fun preview(kind: MediaKind, fromServer: String?): MediaPrompt {
        val served = byId(fromServer)
        return if (served != null && !served.isOwn) served else coldStart(kind)
    }
}
