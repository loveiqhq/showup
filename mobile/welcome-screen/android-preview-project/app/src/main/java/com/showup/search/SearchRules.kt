/*
 * SearchRules.kt
 * ShowUp · Date search 01's rules and copy, with no UI (SHOWUP-184)
 *
 * Everything the Search profile decides is here as a plain function -- the distance line, the name
 * line, the meet line, the Show-up count, the shared vibes and interests, the section subtitles,
 * the Essentials rows, How I live, the message draft and its 100-character rule, the Be sure line --
 * so each is tested on its own (`SearchRulesTest`), the same functions on iOS (`SearchRules.swift`).
 * Screens take values and draw them.
 *
 * Copy is verbatim from the ticket (`design_handoff_showup/search/tickets/01-profile.md`), quoted
 * back by `audit/verify-search.py`. Labels for stored answers are NOT here: they are the profile
 * flow's own tables (`DetailStep`, `LifestyleField`, `InterestSection`, `VibeGroup`,
 * `MotivationOption`), read, never copied.
 */
package com.showup.search

import com.showup.checkin.AGE_MAX
import com.showup.checkin.AGE_MIN
import com.showup.checkin.CheckInFilters
import com.showup.checkin.DISTANCE_DEFAULT
import com.showup.checkin.MotivationOption
import com.showup.checkin.VibeGroup
import com.showup.profile.DetailStep
import com.showup.profile.InterestSection
import com.showup.profile.LifestyleField
import com.showup.profile.formatTakeLength
import com.showup.welcome.BrandIcon
import java.text.BreakIterator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The ticket's copy, one constant per string, so the verifier can quote each against it. */
object SearchCopy {
    const val MEET_LABEL = "You could meet at:"
    const val SHARED_VIBES_TITLE = "You and %s both want today"
    const val HIGHLIGHT_TITLE = "%s's highlight"
    const val VIBE_TODAY_TITLE = "%s's vibe today"
    const val VOICE_TITLE = "%s's voice"
    const val ESSENTIALS_TITLE = "%s's essentials"
    const val VIDEO_TITLE = "%s's video"
    const val EVERYDAY_TITLE = "%s's everyday"
    const val EVERYDAY_SUB = "How I live"
    const val INTERESTS_TITLE = "%s's interests"
    const val CHARMS_TITLE = "Charms from past dates"
    const val FILTERS_LABEL = "Filters"
    const val NOT_INTERESTED = "Not interested"
    const val NOT_NOW = "Not now"
    const val LIKE = "Like"

    // Be sure (callout 22)
    const val BE_SURE = "Be sure."
    const val BE_SURE_LINE_LEAD = "If %s becomes your match, you two have a "
    const val BE_SURE_LINE_EMPHASIS = "confirmed, binding date %s"
    const val BE_SURE_SUB = "What happens after a match?"
    val BE_SURE_ROWS = listOf(
        BrandIcon.MapPin to "A safe, curated meeting spot — so you can focus on each other, not on logistics.",
        BrandIcon.Clock to "A time that fits both of your stated availability windows.",
        BrandIcon.MessageCircle to "Stuck for words? Match Mate has topics, ice breakers, and quick games.",
        BrandIcon.Sparkles to "Only 30 minutes committed. If there's no spark — leave with a smile.",
        BrandIcon.ShieldCheck to "You meet who you matched — the Show-up Rate keeps it real.",
    )
    const val CANCEL = "Cancel"
    const val CONTINUE = "Continue"

    // Optional message (callout 23)
    const val MESSAGE_TITLE_EMPHASIS = "Optional"
    const val MESSAGE_TITLE_REST = " message to %s"
    const val MESSAGE_BODY =
        "Premium users can see who sent them a like and can see your message. Adding a personal " +
            "message increases your chances of getting matched."
    const val MESSAGE_DRAFT = "Hi %s, I love the vibe of your profile. Would be great to meet you %s :)"
    const val SKIP_MESSAGE = "Skip message"
    const val SEND_MESSAGE = "Send message"

    // Actions coach (F, callouts 24 and 25)
    const val COACH_TITLE_LEAD = "Three ways to "
    const val COACH_TITLE_EMPHASIS = "decide"
    const val COACH_LABEL = "What the three buttons do"
    const val COACH_DONT_SHOW = "Don't show again"
    const val COACH_GOT_IT = "Got it"

    // ── states the sheet does not draw: approved by Philipp, 27 Oct 2026 ──────────────────────────
    const val LOADING_LABEL = "Loading profiles"
    const val LOAD_FAILED = "Couldn't load profiles. Check your connection and try again."
    const val RETRY = "Retry"
    const val TRY_AGAIN = "Try again"
    /**
     * PROPOSED, NOT YET THE DESIGN'S: the empty state takes the headline and body of the kit's
     * `search-empty` screen (27 Oct 2026), a file not in any handoff zip yet -- asked for 28 Oct.
     */
    const val NO_RESULTS_PROPOSED = "No results"
    /** Opens Filters for now (27 Oct 2026); the kit's sub-line and `Adjust filters manually` are left out. */
    const val WIDEN_MY_SEARCH = "Widen my search"
}

/** Search's essentials row labels, in the ticket's order. */
enum class EssentialRow(val label: String, val icon: BrandIcon) {
    Height("Height", BrandIcon.Sliders),
    Gender("Gender", BrandIcon.User),
    Orientation("Orientation", BrandIcon.Heart),
    DatesIn("Dates in", BrandIcon.Globe),
    Education("Education", BrandIcon.BookOpen),
    Religion("Religion", BrandIcon.Faith),
    Politics("Politics", BrandIcon.Vote),
}

/** One coach row: the button it explains, in that button's icon and tone. */
enum class CoachRow(val title: String, val body: String, val icon: BrandIcon, val tone: ActionTone) {
    NotInterested(SearchCopy.NOT_INTERESTED, "You won't see this profile again.", BrandIcon.Close, ActionTone.Danger),
    NotNow(SearchCopy.NOT_NOW, "This profile can come back at your next Check-In.", BrandIcon.Clock, ActionTone.Muted),
    Like(SearchCopy.LIKE, "You'd like to meet. If they like you back, you have a date.", BrandIcon.Heart, ActionTone.Primary),
}

/** The action bar's three tones (`ActionBtn`): danger outline, muted outline, sunset. */
enum class ActionTone { Danger, Muted, Primary }

/** "The message is max 100 characters" -- counted as a person counts them, not in code units. */
const val LIKE_MESSAGE_MAX = 100

/** At most three shared vibes in the row, then `+N` (callout 10). */
const val SHARED_VIBES_SHOWN = 3

// ── the hero ────────────────────────────────────────────────────────────────────────────────────

/**
 * `< 1 km` or `~N km` -- `searchDistance` in the reference. The server sends whole kilometres and
 * 0 for "under one", so the exact distance never reaches the phone.
 */
fun searchDistance(km: Int): String = if (km < 1) "< 1 km" else "~$km km"

/**
 * The distance pill's runs: the distance, `·`, the district -- or the distance alone when no district
 * was named. Separate because the reference writes `{distance} · {district}` straight into a flex row
 * with a 6 gap, and the signed-off sheet renders each text node as its own item: 6 either side of
 * the dot, not a word space (the 184 evidence review, 24 Oct 2026).
 */
fun distanceParts(km: Int, district: String?): List<String> =
    district?.trim()?.takeIf { it.isNotEmpty() }?.let { listOf(searchDistance(km), "·", it) } ?: listOf(searchDistance(km))

/** `< 1 km · {district}` / `~{n} km · {district}`: the pill as one line, which is what a screen reader hears. */
fun distanceLine(km: Int, district: String?): String = distanceParts(km, district).joinToString(" ")

/**
 * `filters_active` on `search_viewed`: the §10 filters set away from Check-In 05's defaults, in §10
 * order. `meet_gender` when the saved genders, as a set, are not the viewer's own pre-fill
 * ([defaultGenders], from their current profile, 27 Oct 2026) -- the IDs only, never a gender. Left
 * out when that pre-fill is unknown: a guess would count every unread profile as a change.
 *
 * And the dealbreakers "when true" (Philipp, 27 Oct 2026): Check-In 05b's Age and Distance switches
 * (SHOWUP-189), each listed when it is on, in §10's order beside the filter it hardens.
 */
fun filtersActive(filters: CheckInFilters, defaultGenders: Set<String>? = null): List<String> = buildList {
    if (defaultGenders != null && filters.genders != defaultGenders) add("meet_gender")
    if (filters.minAge > AGE_MIN || filters.maxAge < AGE_MAX) add("age_range")
    if (filters.ageDealbreaker) add("age_dealbreaker")
    if (filters.distanceKm != DISTANCE_DEFAULT) add("distance_km")
    if (filters.distanceDealbreaker) add("distance_dealbreaker")
}

/** `{name}, {age}`, or `{name}` when the age is hidden -- never a dangling comma. */
fun nameLine(name: String, age: Int?): String = if (age != null) "$name, $age" else name

// ── the meet card ───────────────────────────────────────────────────────────────────────────────

private val MEET_TIME = DateTimeFormatter.ofPattern("HH:mm")

/** `19:00`: the meet hour on the phone's clock, 24-hour as drawn. */
fun meetTime(meetAt: Instant, zone: ZoneId): String = MEET_TIME.format(meetAt.atZone(zone))

/** `{HH:MM} {day}` -- `19:00 today`. The day is the server's, by the Check-In's 7am day. */
fun meetLine(meetAt: Instant, day: String, zone: ZoneId): String = "${meetTime(meetAt, zone)} $day"

// ── the overlap ─────────────────────────────────────────────────────────────────────────────────

/**
 * The vibes both want today, in the PROFILE's order. Only fields both filled in are compared, so
 * an empty side shares nothing.
 */
fun sharedVibes(theirs: List<String>, mine: Set<String>): List<String> = theirs.filter { it in mine }

/** The shared vibes row: at most three, and `+N` for the rest. Empty when nothing is shared. */
data class SharedVibesRow(val shown: List<String>, val more: Int)

fun sharedVibesRow(shared: List<String>): SharedVibesRow =
    SharedVibesRow(shared.take(SHARED_VIBES_SHOWN), (shared.size - SHARED_VIBES_SHOWN).coerceAtLeast(0))

/** The interests both have, in the profile's order. A viewer with none marks nothing. */
fun sharedInterests(theirs: List<String>, mine: Set<String>): Set<String> = theirs.filter { it in mine }.toSet()

/** `{n} things {name} is into`, plus ` · {k} in common` only when k is at least 1. */
fun interestsSub(name: String, total: Int, common: Int): String =
    "$total things $name is into" + if (common >= 1) " · $common in common" else ""

/** §31's label for a vibe id, from the Check-In's own table. Unknown ids (a newer server) are null. */
fun vibeLabel(id: String): String? = VibeGroup.entries.firstNotNullOfOrNull { g -> g.vibes.firstOrNull { it.id == id }?.label }

/** §3's label for an interest tag, from the profile's own table. */
fun interestLabel(tagId: String): String? =
    InterestSection.entries.firstNotNullOfOrNull { s -> s.options.firstOrNull { it.value == tagId }?.label }

/** The motivation pill: §30's title and icon, or null for a value this app does not know. */
fun motivationOf(id: String?): MotivationOption? = MotivationOption.of(id)

// ── media ───────────────────────────────────────────────────────────────────────────────────────

// The take's length as its owner saw it on the media step (`formatTakeLength`, truncated): a second
// formatter here could show the same recording as two different lengths.
fun voiceSub(durationMs: Int): String = "Voice prompt · ${formatTakeLength(durationMs)}"

fun videoSub(durationMs: Int): String = "Video prompt · ${formatTakeLength(durationMs)}"

/**
 * The voice card's 48 bars, as heights from 0 to 1 -- the reference's deterministic waveform: a sine
 * envelope rising and falling across the card, with a fixed jitter, never below 0.12. The same every
 * time, so the card never animates or reshuffles; it is a picture of a voice, not a reading of one.
 */
val VOICE_BARS: List<Float> = List(48) { i ->
    val env = kotlin.math.sin(i / 47.0 * Math.PI)
    val jitter = (kotlin.math.sin(i * 12.9898) * 43758.5453) % 1.0
    maxOf(0.12, env * 0.85 + kotlin.math.abs(jitter) * 0.25).toFloat()
}

/** At rest the reference paints the first 18 of the 48 bars violet (`i < 18`); the card is drawn so. */
const val VOICE_REST_VIOLET_BARS = 18

/**
 * How many bars, from the left, are violet. At rest: the reference's 18, as the spec sheet draws the
 * card. While playing -- a state the sheet does not draw -- the violet follows the playhead.
 */
fun voiceVioletBars(playing: Boolean, progress: Float): Int =
    if (playing) (progress.coerceIn(0f, 1f) * VOICE_BARS.size).toInt() else VOICE_REST_VIOLET_BARS

// ── Essentials and How I live ───────────────────────────────────────────────────────────────────

/** One Essentials row: what is drawn. */
data class Essential(val row: EssentialRow, val value: String)

/** The answers the payload carries for Essentials; a missing one is hidden or unanswered. */
data class EssentialAnswers(
    val heightCm: Int? = null,
    val gender: String? = null,
    val orientation: String? = null,
    val datingLanguages: List<String> = emptyList(),
    val education: String? = null,
    val religion: String? = null,
    val politics: String? = null,
)

private fun labelIn(step: DetailStep, value: String?): String? =
    value?.let { v -> step.options.firstOrNull { it.value == v }?.label }

/**
 * The Essentials rows, in the ticket's order: Height · Gender · Orientation · Dates in · Education
 * · Religion · Politics. A row with no answer -- hidden, skipped, or a value this app does not
 * know -- is not drawn: never an empty row, never a raw id.
 */
fun essentials(a: EssentialAnswers): List<Essential> = buildList {
    a.heightCm?.let { add(Essential(EssentialRow.Height, "$it cm")) }
    labelIn(DetailStep.Gender, a.gender)?.let { add(Essential(EssentialRow.Gender, it)) }
    labelIn(DetailStep.Orientation, a.orientation)?.let { add(Essential(EssentialRow.Orientation, it)) }
    a.datingLanguages.mapNotNull { labelIn(DetailStep.DatingLanguage, it) }
        .takeIf { it.isNotEmpty() }
        ?.let { add(Essential(EssentialRow.DatesIn, it.joinToString(", "))) }
    labelIn(DetailStep.Education, a.education)?.let { add(Essential(EssentialRow.Education, it)) }
    labelIn(DetailStep.Religion, a.religion)?.let { add(Essential(EssentialRow.Religion, it)) }
    labelIn(DetailStep.Politics, a.politics)?.let { add(Essential(EssentialRow.Politics, it)) }
}

/** One How I live tile: the group and the answer's label. */
data class LifestyleTile(val field: LifestyleField, val value: String)

/**
 * The answered How I live groups, in the profile's group order (`LIFESTYLE_GROUPS`). Unanswered
 * groups are not drawn, and the card is absent when none is answered.
 */
fun lifestyleTiles(answers: Map<String, String>): List<LifestyleTile> =
    LifestyleField.entries.mapNotNull { f ->
        answers[f.fieldId]?.let { v -> f.options.firstOrNull { it.value == v }?.label }?.let { LifestyleTile(f, it) }
    }

/** The tile icons (`HOW_I_LIVE_ICONS`): zap, leaf, wine, cigarette, clock, users. */
fun lifestyleIcon(field: LifestyleField): BrandIcon = when (field) {
    LifestyleField.Activity -> BrandIcon.Zap
    LifestyleField.Diet -> BrandIcon.Leaf
    LifestyleField.Alcohol -> BrandIcon.Wine
    LifestyleField.Smoking -> BrandIcon.Cigarette
    LifestyleField.Rhythm -> BrandIcon.Clock
    LifestyleField.Social -> BrandIcon.Users
}

// ── the like flow ───────────────────────────────────────────────────────────────────────────────

/** How many characters a person sees: grapheme clusters, so an emoji family is one, not seven. */
fun graphemes(text: String): Int {
    val boundaries = BreakIterator.getCharacterInstance()
    boundaries.setText(text)
    var n = 0
    while (boundaries.next() != BreakIterator.DONE) n++
    return n
}

/**
 * "Input past 100 is not accepted, and a paste is cut at 100." The first 100 graphemes, cut on a
 * grapheme boundary so a flag or a family is never split in half.
 */
fun limitMessage(text: String): String {
    if (graphemes(text) <= LIKE_MESSAGE_MAX) return text
    val boundaries = BreakIterator.getCharacterInstance()
    boundaries.setText(text)
    var end = 0
    repeat(LIKE_MESSAGE_MAX) { end = boundaries.next() }
    return text.substring(0, end)
}

/** `{n} / 100`. */
fun messageCounter(text: String): String = "${graphemes(text)} / $LIKE_MESSAGE_MAX"

/** The pre-filled draft, cut to the limit so a long name never makes it start over 100. */
fun messageDraft(name: String, day: String): String = limitMessage(SearchCopy.MESSAGE_DRAFT.format(name, day))

/** What `Send message` sends: the trimmed text, or none for empty or whitespace only. */
fun messageToSend(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

// ── the actions coach ───────────────────────────────────────────────────────────────────────────

/**
 * Whether the coach shows over this profile: once per Search visit, over the first profile with a
 * `Like` button, until `Don't show again` set the server's flag. An empty queue shows no coach.
 */
fun shouldShowCoach(hiddenForever: Boolean, shownThisVisit: Boolean, hasProfile: Boolean, likedYou: Boolean): Boolean =
    !hiddenForever && !shownThisVisit && hasProfile && !likedYou
