/*
 * ProfileDetails.kt
 * ShowUp · "Share some details" -- the steps, their answers, and every rule that is not a pixel
 * (SHOWUP-167 to SHOWUP-173)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE TABLE FOR SEVEN SCREENS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Height, gender, orientation, dating language, education, religion and politics are one group with
 * one shell, and the differences between them are data: which step of the bar, which §1 field,
 * whether it can be skipped, whether tapping the chosen row clears it, and its options. That data
 * lives here, in [DetailStep], so a screen file holds pixels and nothing else, and every rule below
 * is unit-tested with no Compose and no device (ProfileDetailsRulesTest).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT IS STORED IS THE §1 VALUE, NEVER THE LABEL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Every ticket says it in the same words: "the option labels are display strings. What gets stored
 * and tracked is the `enums.json` §1 value, never the label." So each option carries both, and the
 * value comes from the GENERATED contract enum (`Gender.non_binary.value`, not "non_binary" typed
 * here) -- a value renamed on the server is a compile error in this file rather than a 400 the
 * user meets on Continue.
 *
 * The order of every list is content, not layout: it is the ticket's order, it is §1's order, and
 * `audit/verify-profile.py` checks both against this file.
 */
package com.showup.profile

import com.showup.api.generated.model.FlowPosition

// THE GENERATED ENUMS ARE WRITTEN OUT IN FULL below (`com.showup.api.generated.model.Gender`), not
// imported: inside [DetailStep] the simple name `Gender` is the enum ENTRY, which shadows the type,
// and an import would silently resolve to the wrong one.

/** One row on a detail screen: the §1 value that is stored, and the label that is drawn. */
data class DetailOption(val value: String, val label: String)

/**
 * How many segments the group's bar has -- `SHARE_STEPS_TOTAL` in the reference, "a prop of the
 * shell, never typed per screen" (Profile 14).
 *
 * TEN, AND THE TICKETS SAY WHY IT IS STILL OPEN: §2's step indices end at 9 (interests, life now,
 * life ahead and habits follow politics) and the kit's life screen draws 8. The count belongs to
 * the shell and every screen reads it from here, so when the design side settles it this is the
 * one number that changes.
 */
const val SHARE_STEPS_TOTAL = 10

/**
 * A step of "Share some details".
 *
 * @param stepId `enums.json` §2 `step_id`, and also the §1 `field_id` and the `hidden_fields`
 *   entry: for these seven the three vocabularies use the same string.
 * @param stepIndex §2 `step_index`, which is also the filled segment of the bar.
 * @param mandatory a step without a SkipLink cannot be passed without an answer (profile README
 *   rule 0). Gender and orientation, decided 5 October 2026.
 * @param clearsOnReselect tapping the selected row again clears it. A named exception to "a radio
 *   never clears", for the SKIPPABLE single-select steps only -- empty is a valid answer there. It is
 *   a property of the step, applied in the tap handler, and NOT a prop of `OptionRow`: the tickets
 *   say so three times ("OptionRow gains no new prop").
 * @param sensitivityClass §1's class for the field, stamped on its attribute events at emit time.
 */
enum class DetailStep(
    val stepId: String,
    val stepIndex: Int,
    val screen: ProfileScreen,
    val position: FlowPosition,
    val mandatory: Boolean,
    val clearsOnReselect: Boolean,
    val sensitivityClass: Int,
    val options: List<DetailOption>,
) {
    Height(
        "height", 1, ProfileScreen.Height, FlowPosition.height,
        mandatory = false, clearsOnReselect = false, sensitivityClass = 0,
        options = emptyList(),
    ),
    Gender(
        "gender", 2, ProfileScreen.Gender, FlowPosition.gender,
        mandatory = true, clearsOnReselect = false, sensitivityClass = 2,
        options = listOf(
            DetailOption(com.showup.api.generated.model.Gender.woman.value, "Woman"),
            DetailOption(com.showup.api.generated.model.Gender.man.value, "Man"),
            DetailOption(com.showup.api.generated.model.Gender.non_binary.value, "Non-binary"),
            DetailOption(com.showup.api.generated.model.Gender.other.value, "Other"),
        ),
    ),
    Orientation(
        "orientation", 3, ProfileScreen.Orientation, FlowPosition.orientation,
        mandatory = true, clearsOnReselect = false, sensitivityClass = 2,
        // ALL SIX, ALWAYS, IN THIS ORDER -- never filtered or reordered by the gender answer
        // (Profile 16). Nothing here reads gender, which is how that stays true.
        options = listOf(
            DetailOption(com.showup.api.generated.model.Orientation.straight.value, "Straight"),
            DetailOption(com.showup.api.generated.model.Orientation.gay.value, "Gay"),
            DetailOption(com.showup.api.generated.model.Orientation.lesbian.value, "Lesbian"),
            DetailOption(com.showup.api.generated.model.Orientation.bisexual.value, "Bisexual"),
            DetailOption(com.showup.api.generated.model.Orientation.pansexual.value, "Pansexual"),
            DetailOption(com.showup.api.generated.model.Orientation.other.value, "Other"),
        ),
    ),
    DatingLanguage(
        "dating_language", 4, ProfileScreen.DatingLanguage, FlowPosition.dating_language,
        mandatory = false, clearsOnReselect = false, sensitivityClass = 1,
        options = listOf(
            DetailOption(com.showup.api.generated.model.DatingLanguage.german.value, "German"),
            DetailOption(com.showup.api.generated.model.DatingLanguage.english.value, "English"),
            DetailOption(com.showup.api.generated.model.DatingLanguage.spanish.value, "Spanish"),
            DetailOption(com.showup.api.generated.model.DatingLanguage.italian.value, "Italian"),
            DetailOption(com.showup.api.generated.model.DatingLanguage.french.value, "French"),
            DetailOption(com.showup.api.generated.model.DatingLanguage.turkish.value, "Turkish"),
            DetailOption(com.showup.api.generated.model.DatingLanguage.russian.value, "Russian"),
            DetailOption(com.showup.api.generated.model.DatingLanguage.arabic.value, "Arabic"),
        ),
    ),
    Education(
        "education", 5, ProfileScreen.Education, FlowPosition.education,
        mandatory = false, clearsOnReselect = true, sensitivityClass = 1,
        options = listOf(
            DetailOption(com.showup.api.generated.model.Education.a_levels_abitur.value, "A-Levels / Abitur"),
            DetailOption(com.showup.api.generated.model.Education.apprenticeship.value, "Apprenticeship"),
            DetailOption(com.showup.api.generated.model.Education.university_degree.value, "University degree"),
            DetailOption(com.showup.api.generated.model.Education.phd.value, "PhD"),
        ),
    ),
    Religion(
        "religion", 6, ProfileScreen.Religion, FlowPosition.religion,
        mandatory = false, clearsOnReselect = true, sensitivityClass = 2,
        // NINE: `Muslim` before `Jewish`, added 5 October 2026 (registry 1.4.14).
        options = listOf(
            DetailOption(com.showup.api.generated.model.Religion.protestant.value, "Protestant"),
            DetailOption(com.showup.api.generated.model.Religion.catholic.value, "Catholic"),
            DetailOption(com.showup.api.generated.model.Religion.orthodox.value, "Orthodox"),
            DetailOption(com.showup.api.generated.model.Religion.muslim.value, "Muslim"),
            DetailOption(com.showup.api.generated.model.Religion.jewish.value, "Jewish"),
            DetailOption(com.showup.api.generated.model.Religion.buddhist.value, "Buddhist"),
            DetailOption(com.showup.api.generated.model.Religion.hindu.value, "Hindu"),
            DetailOption(com.showup.api.generated.model.Religion.atheist.value, "Atheist"),
            DetailOption(com.showup.api.generated.model.Religion.spiritual_other.value, "Spiritual / other"),
        ),
    ),
    Politics(
        "politics", 7, ProfileScreen.Politics, FlowPosition.politics,
        mandatory = false, clearsOnReselect = true, sensitivityClass = 2,
        // LINEAR: the left -> right spectrum, then the three off-axis answers (registry 1.4.15).
        // `Conservative` comes AFTER `Right`. Keyed on the value everywhere, never on the index.
        options = listOf(
            DetailOption(com.showup.api.generated.model.Politics.left.value, "Left"),
            DetailOption(com.showup.api.generated.model.Politics.mid_left.value, "Mid-left"),
            DetailOption(com.showup.api.generated.model.Politics.middle.value, "Middle"),
            DetailOption(com.showup.api.generated.model.Politics.mid_right.value, "Mid-right"),
            DetailOption(com.showup.api.generated.model.Politics.right.value, "Right"),
            DetailOption(com.showup.api.generated.model.Politics.conservative.value, "Conservative"),
            DetailOption(com.showup.api.generated.model.Politics.libertarian.value, "Libertarian"),
            DetailOption(com.showup.api.generated.model.Politics.apolitical.value, "Apolitical"),
        ),
    ),
    ;

    /** The `field_id`, which is the same string as the step id for all seven. */
    val fieldId: String get() = stepId

    /** The `hidden_fields` entry -- the same string again; `HIDEABLE_FIELDS` on the server. */
    val hiddenField: String get() = stepId

    /** The step after this one, or null after politics (interests is not built yet). */
    val next: DetailStep? get() = entries.getOrNull(ordinal + 1)

    /** The step before this one, or null on height, whose back goes to Embrace 2. */
    val previous: DetailStep? get() = entries.getOrNull(ordinal - 1)

    /** Whether the list is several options that may all be ticked. Dating language only. */
    val multiSelect: Boolean get() = this == DatingLanguage
}

// ── height ───────────────────────────────────────────────────────────────────

/** The screen's own bounds (Profile 14), and the server's CHECK -- `profile-details.ts`. */
const val HEIGHT_CM_MIN = 120
const val HEIGHT_CM_MAX = 230

/**
 * What the field keeps of what was typed: digits only, at most three.
 *
 * "Any other character is dropped as it is typed, and pasted text is filtered the same way." One
 * function for both, because a paste is just a long keystroke -- `onValueChange` hands this the
 * whole new string either way.
 */
fun filterHeightInput(raw: String): String = raw.filter { it in '0'..'9' }.take(3)

/** The height in cm when the text is a valid answer, else null. Whole numbers 120-230 inclusive. */
fun parseHeight(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in HEIGHT_CM_MIN..HEIGHT_CM_MAX }

/**
 * Why Continue was refused, as §8's `rule`, or null when it was not.
 *
 * Empty is `required_missing`; anything else invalid is `impossible`. §8 has no range value, and
 * the ticket says not to invent one: "if analytics wants `out_of_range`, it gets defined in
 * `enums.json` first -- not typed here."
 */
fun heightRefusal(text: String): String? = when {
    text.isEmpty() -> ValidationRule.REQUIRED_MISSING
    parseHeight(text) == null -> ValidationRule.IMPOSSIBLE
    else -> null
}

/**
 * §1's height bucket, computed ON DEVICE. "The exact cm never goes to analytics."
 *
 * `<150 · 150_159 · 160_164 · ... · 185_189 · 190_199 · 200+` -- tens at both ends, fives through
 * the middle where most answers fall.
 */
fun heightBucket(cm: Int): String = when {
    cm < 150 -> "<150"
    cm < 160 -> "150_159"
    cm < 190 -> {
        val low = cm - cm % 5
        "${low}_${low + 4}"
    }
    cm < 200 -> "190_199"
    else -> "200+"
}

// ── the lists ────────────────────────────────────────────────────────────────

/**
 * The selection after a tap on [tapped], on a single-select step.
 *
 * A different row moves the selection. The SAME row keeps it on gender and orientation -- "a radio
 * never clears", and the reference's `setValue(opt)` is a no-op there -- and clears it on the
 * skippable three, where the reference writes `v === opt ? null : opt`.
 */
fun pickSingle(step: DetailStep, current: String?, tapped: String): String? =
    if (current == tapped && step.clearsOnReselect) null else tapped

/** The ticked set after a tap on [tapped]: a ticked row unticks, any other ticks. No limit. */
fun toggleMulti(current: Set<String>, tapped: String): Set<String> =
    if (tapped in current) current - tapped else current + tapped

/**
 * The ticked languages in LIST order, never tap order.
 *
 * "Ticking Spanish, then German, saves `german,spanish`." The server canonicalises too
 * (`canonicalDatingLanguages`), so this is belt and braces for the stored value and the whole of
 * the guarantee for `value_bucketed`, which the server never sees.
 */
fun inListOrder(step: DetailStep, ticked: Set<String>): List<String> =
    step.options.map { it.value }.filter { it in ticked }

/** `value_bucketed` for dating language: list order, comma-joined, no spaces (registry 1.4.12). */
fun languagesBucketed(values: List<String>): String = values.joinToString(",")

// ── what a press does ────────────────────────────────────────────────────────

/** What the footer's Continue resolves to on a given screen, before anything is sent. */
sealed interface DetailContinue {
    /** Refused: show the toast and report [rule]. Mandatory steps and height only. */
    data class Refused(val rule: String) : DetailContinue

    /** "Continue with nothing selected is a Skip" -- dating language, education, religion, politics. */
    data object Skip : DetailContinue

    /** Save this answer and advance. */
    data object Answer : DetailContinue
}

/**
 * The one decision behind every footer in the group.
 *
 * Three behaviours across seven screens, all keyed off two facts the step already carries:
 *
 *   height                   empty or out of range -> refused (the toast), valid -> answer
 *   gender, orientation      nothing picked -> refused, picked -> answer
 *   the four skippable lists nothing picked -> SKIP, exactly as the SkipLink does; picked -> answer
 *
 * Height is refused rather than skipped even though it can be skipped, because its refusal is a
 * validation of something TYPED -- an empty field next to a working Skip link is the user asking to
 * continue with a value, and the toast tells them the range.
 */
fun resolveContinue(step: DetailStep, draft: DetailDraft): DetailContinue = when {
    step == DetailStep.Height ->
        heightRefusal(draft.heightText)?.let { DetailContinue.Refused(it) } ?: DetailContinue.Answer
    step.multiSelect ->
        if (draft.languages.isEmpty()) DetailContinue.Skip else DetailContinue.Answer
    draft.selection(step) != null -> DetailContinue.Answer
    step.mandatory -> DetailContinue.Refused(ValidationRule.REQUIRED_MISSING)
    else -> DetailContinue.Skip
}

/**
 * Everything the user has typed, picked or ticked across the group, plus the visibility boxes.
 *
 * ONE VALUE FOR THE WHOLE GROUP, because back navigation moves between the screens: going back
 * from orientation to gender must show gender's SAVED value, and going forward again must show
 * orientation's saved one -- "back discards an unsaved selection". So a screen's draft is reset
 * from what the server holds every time the screen is reached; see `ProfileDetailsViewModel.arrived`.
 */
data class DetailDraft(
    val heightText: String = "",
    val gender: String? = null,
    val orientation: String? = null,
    val languages: Set<String> = emptySet(),
    val education: String? = null,
    val religion: String? = null,
    val politics: String? = null,
    /** `hidden_fields` entries ticked on the band. Unchecked by default on every step. */
    val hidden: Set<String> = emptySet(),
) {
    /** The single-select answer for [step], or null for height and dating language. */
    fun selection(step: DetailStep): String? = when (step) {
        DetailStep.Gender -> gender
        DetailStep.Orientation -> orientation
        DetailStep.Education -> education
        DetailStep.Religion -> religion
        DetailStep.Politics -> politics
        DetailStep.Height, DetailStep.DatingLanguage -> null
    }

    fun withSelection(step: DetailStep, value: String?): DetailDraft = when (step) {
        DetailStep.Gender -> copy(gender = value)
        DetailStep.Orientation -> copy(orientation = value)
        DetailStep.Education -> copy(education = value)
        DetailStep.Religion -> copy(religion = value)
        DetailStep.Politics -> copy(politics = value)
        DetailStep.Height, DetailStep.DatingLanguage -> this
    }

    fun isHidden(step: DetailStep): Boolean = step.hiddenField in hidden
}

/**
 * What the account already holds, read from `GET /me/profile` -- the owner's own view, which is
 * the only one that carries these answers.
 *
 * Strings, not the generated enums: the response describes them as strings on purpose, so an app
 * already installed never fails to decode a value a newer server adds. A value this build does not
 * know simply matches no row.
 */
data class SavedDetails(
    val heightCm: Int? = null,
    val gender: String? = null,
    val orientation: String? = null,
    val datingLanguages: List<String> = emptyList(),
    val education: String? = null,
    val religion: String? = null,
    val politics: String? = null,
    val hiddenFields: Set<String> = emptySet(),
)

/**
 * The draft a screen opens with: the saved value and the saved visibility, for that step only.
 *
 * Every other step's part of [current] is left alone -- the reset is per screen, which is what
 * makes "back from orientation shows gender's saved value" and "back discards an unsaved
 * selection" the same rule.
 */
fun draftOnArrival(step: DetailStep, current: DetailDraft, saved: SavedDetails): DetailDraft {
    val hidden = if (step.hiddenField in saved.hiddenFields) {
        current.hidden + step.hiddenField
    } else {
        current.hidden - step.hiddenField
    }
    val reset = current.copy(hidden = hidden)
    return when (step) {
        DetailStep.Height -> reset.copy(heightText = saved.heightCm?.toString().orEmpty())
        DetailStep.DatingLanguage -> reset.copy(
            // Only values this build knows, so a ticked row is always one it can draw.
            languages = saved.datingLanguages.filter { v -> step.options.any { it.value == v } }
                .toSet(),
        )
        DetailStep.Gender -> reset.copy(gender = known(step, saved.gender))
        DetailStep.Orientation -> reset.copy(orientation = known(step, saved.orientation))
        DetailStep.Education -> reset.copy(education = known(step, saved.education))
        DetailStep.Religion -> reset.copy(religion = known(step, saved.religion))
        DetailStep.Politics -> reset.copy(politics = known(step, saved.politics))
    }
}

private fun known(step: DetailStep, value: String?): String? =
    value?.takeIf { v -> step.options.any { it.value == v } }

/**
 * Every step's part of [current] reset from [saved] -- except [onScreen], whose part is the user's
 * until they leave it.
 *
 * WHAT KEEPS A RETURNING STEP FROM FLASHING ITS OLD PICK. A step's arrival resets its part from the
 * server, but the arrival runs after the screen's first frame; if the draft still held an unsaved
 * pick from an earlier visit, that frame would show it and the row would fade out over 180 ms.
 * Keeping every off-screen part equal to the server means the first frame is already right.
 */
fun draftFromSaved(current: DetailDraft, saved: SavedDetails, onScreen: DetailStep?): DetailDraft =
    DetailStep.entries.filter { it != onScreen }.fold(current) { draft, step -> draftOnArrival(step, draft, saved) }

/**
 * The `hidden_fields` set to send with an accepted Continue on [step].
 *
 * A REPLACE, NOT A PATCH -- the server stores whatever set it is given. So the set sent is the one
 * the account already has, with this step's entry added or removed, and nothing else touched. The
 * age choice from the date-of-birth step and every earlier detail step survive because they are
 * read back, not reconstructed.
 */
fun hiddenFieldsToSend(step: DetailStep, saved: Set<String>, hideThis: Boolean): List<String> =
    (if (hideThis) saved + step.hiddenField else saved - step.hiddenField).sorted()
