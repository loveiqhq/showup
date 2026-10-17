/*
 * CheckInRules.kt
 * ShowUp · the 48hrs Check-In's rules and copy, with no UI (SHOWUP-178 to SHOWUP-183)
 *
 * Everything a Check-In screen decides is here as a plain function -- the 7am day, the next full
 * hour, the tap cycle, the summary, the vibe limit, the filter changes, the uplift count and the
 * contact-info rule -- so each is tested on its own (`CheckInRulesTest`), the same functions on iOS
 * (`CheckInRules.swift`). Screens take values and draw them.
 *
 * Copy is verbatim from the six tickets (`design_handoff_showup/checkin/tickets`), quoted back by
 * `audit/verify-checkin.py`. The UI string is `Check-in` as drawn, by decision; the product term
 * in comments is Check-In.
 */
package com.showup.checkin

import com.showup.profile.ProfileScreen
import com.showup.welcome.BrandIcon
import java.text.BreakIterator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** The step bar's six segments -- the intro does not count (epic, `StepProgress steps={6}`). */
const val CHECK_IN_STEPS_TOTAL = 6

/**
 * §28 `check_in_step`, in flow order, with the §11 screen each is reported as and its place on the
 * step bar. The intro is a value (`check_in_closed` can fire on it) but has no segment.
 */
enum class CheckInStep(val stepId: String, val screen: ProfileScreen, val progress: Int?) {
    Intro("intro", ProfileScreen.CheckinIntro, null),
    Today("today", ProfileScreen.CheckinToday, 1),
    Tomorrow("tomorrow", ProfileScreen.CheckinTomorrow, 2),
    Motivation("motivation", ProfileScreen.CheckinMotivation, 3),
    Vibe("vibe", ProfileScreen.CheckinVibe, 4),
    Filters("filters", ProfileScreen.CheckinFilters, 5),
    Uplift("uplift", ProfileScreen.CheckinUplift, 6),
}

// ── availability (Check-In 02) ──────────────────────────────────────────────────────────────────

/** A picked hour's tier: the 1st tap or the 2nd. Both are stored and used (story 02). */
enum class SlotTier(val wire: String) { Preferred("preferred"), Available("available") }

/** "A tap cycles a slot idle → preferred → available → idle." */
fun cycleSlot(current: SlotTier?): SlotTier? = when (current) {
    null -> SlotTier.Preferred
    SlotTier.Preferred -> SlotTier.Available
    SlotTier.Available -> null
}

/** "A Check-In day runs 7am → 7am": both grids end at the 6am slot. */
const val CHECK_IN_DAY_START = 7

/**
 * Today and tomorrow at [now], on the device's clock.
 *
 * "Today's first slot is the next full hour" -- an hour that has started is never offered, because
 * with the one-hour meet buffer it cannot produce a date. "Today" is the 7am → 7am block that
 * contains that hour: at 4:20pm it starts at 5pm; at 1:20am it is 2am-6am, the night of the
 * evening before; at 6:20am the next full hour is 7am and Today is the new day.
 *
 * @property todayDate the date the Today block STARTS on (its 7am).
 */
data class CheckInDays(val todayDate: LocalDate, val todayStartHour: Int) {
    val tomorrowDate: LocalDate get() = todayDate.plusDays(1)
}

fun checkInDays(now: LocalDateTime): CheckInDays {
    val next = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
    val date = if (next.hour >= CHECK_IN_DAY_START) next.toLocalDate() else next.toLocalDate().minusDays(1)
    return CheckInDays(date, next.hour)
}

/** The hours a grid offers, from [startHour] through the 6am slot. Tomorrow: 7am, 24 slots. */
fun availabilityHours(startHour: Int): List<Int> {
    val span = ((CHECK_IN_DAY_START - 1 - startHour + 24) % 24) + 1
    return List(span) { (startHour + it) % 24 }
}

/**
 * The instant hour [hour] of the Check-In day that starts on [blockDate] begins, in [zone]. Hours
 * before 7am belong to the evening before, so they fall on the next calendar date.
 */
fun slotStart(blockDate: LocalDate, hour: Int, zone: ZoneId): ZonedDateTime =
    ZonedDateTime.of(
        if (hour >= CHECK_IN_DAY_START) blockDate else blockDate.plusDays(1),
        LocalTime.of(hour, 0),
        zone,
    )

/** An hour's label: `7am` … `11am` · `noon` · `1pm` … `11pm` · `12am` · `1am` … `6am`. */
fun formatHour(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    return when {
        h == 0 -> "12am"
        h == 12 -> "noon"
        h < 12 -> "${h}am"
        else -> "${h - 12}pm"
    }
}

/**
 * The picked hours collapsed into ranges, in grid order. "A slot means h until h+1, so 6pm + 7pm +
 * 8pm reads 6–9pm. Runs across midnight stay one range. Tiers are not distinguished."
 */
fun availabilityRanges(hours: List<Int>, picked: Map<Int, SlotTier>): List<String> {
    val runs = mutableListOf<MutableList<Int>>()
    var current: MutableList<Int>? = null
    for (h in hours) {
        if (h !in picked) {
            current = null
            continue
        }
        val run = current ?: mutableListOf<Int>().also { runs += it }
        run += h
        current = run
    }
    fun period(x: Int) = if (x < 12) "am" else "pm"
    fun number(x: Int) = if (x % 12 == 0) 12 else x % 12
    return runs.map { run ->
        val a = run.first()
        val b = (run.last() + 1) % 24
        if (period(a) == period(b)) "${number(a)}–${number(b)}${period(b)}"
        else "${number(a)}${period(a)}–${number(b)}${period(b)}"
    }
}

/** How many ranges the one-line summary shows before it says `+N more` (story 02). */
const val SUMMARY_MAX_RUNS = 4

/** The summary's text, or null for "nothing selected yet". Never wraps: past four runs, `+N more`. */
fun availabilitySummary(hours: List<Int>, picked: Map<Int, SlotTier>): String? {
    val ranges = availabilityRanges(hours, picked)
    if (ranges.isEmpty()) return null
    val shown = if (ranges.size > SUMMARY_MAX_RUNS) ranges.take(SUMMARY_MAX_RUNS - 1) else ranges
    val more = ranges.size - shown.size
    return shown.joinToString(" · ") + if (more > 0) " · +$more more" else ""
}

/** Picks the grid still offers -- "a slot that has started disappears from the grid and from the selection". */
fun keepOffered(picked: Map<Int, SlotTier>, hours: List<Int>): Map<Int, SlotTier> =
    picked.filterKeys { it in hours }

/** `preferred_hours` / `available_hours` on `availability_slots_set`. */
fun tierCount(picked: Map<Int, SlotTier>, tier: SlotTier): Int = picked.values.count { it == tier }

// ── motivation (Check-In 03) ────────────────────────────────────────────────────────────────────

/** §30 `check_in_motivation`, in this order, with the title, subline and icon each row shows. */
enum class MotivationOption(val id: String, val title: String, val subline: String, val icon: BrandIcon) {
    LongTerm("long_term", "Long-term partner", "Looking for a meaningful connection", BrandIcon.HeartFilled),
    NonMonogamy("non_monogamy", "Long-term Non-Monogamy", "Non-monogamous lifestyle", BrandIcon.Compass),
    ShortTerm("short_term", "Short-term fun", "Physical chemistry, no commitment", BrandIcon.Flame),
    Casual("casual", "Casual dating", "Light, fun, non-committal", BrandIcon.Sparkles),
    Friends("friends", "New friends", "Strictly platonic connections", BrandIcon.Users),
    ;

    companion object {
        fun of(id: String?): MotivationOption? = entries.firstOrNull { it.id == id }
    }
}

// ── vibe (Check-In 04) ──────────────────────────────────────────────────────────────────────────

/** "Multi-select, at most five, across all groups." */
const val VIBE_MAX = 5

data class VibeOption(val id: String, val label: String)

/** §31 `check_in_vibe` in three groups, in this order. Search and Instant read the labels here. */
enum class VibeGroup(val groupId: String, val label: String, val hint: String, val vibes: List<VibeOption>) {
    Activity(
        "activity", "What you want to do", "Activities",
        listOf(
            VibeOption("coffee_chat", "Coffee & a chat"),
            VibeOption("casual_drink", "Kiosk beer / Casual drink"),
            VibeOption("grab_food", "Grabbing food"),
            VibeOption("nature_walk", "Park or nature walk"),
            VibeOption("bouldering_table_tennis", "Bouldering / Table tennis"),
            VibeOption("workout", "Run or active workout"),
            VibeOption("sunset", "Watching the sunset"),
            VibeOption("drinks_music", "Drinks & music"),
        ),
    ),
    Headspace(
        "headspace", "How you feel right now", "Headspace",
        listOf(
            VibeOption("calm", "Calm & peaceful"),
            VibeOption("deep_talk", "Deep conversation"),
            VibeOption("playful", "Fun & playful"),
            VibeOption("cozy", "Cozy & comfortable"),
            VibeOption("spontaneous", "Spontaneous / Up for anything"),
            VibeOption("curious", "Curious / Open-minded"),
        ),
    ),
    Pacing(
        "pacing", "Your energy & pacing", "Boundaries",
        listOf(
            VibeOption("quick_hello", "Quick 30-min hello"),
            VibeOption("low_key", "Low-key energy"),
            VibeOption("high_energy", "High social energy"),
            VibeOption("no_rush", "Free evening (no rush)"),
            VibeOption("early_night", "Early night (home early)"),
            VibeOption("meet_now", "Ready to meet now"),
        ),
    ),
}

/** §31 in list order -- the order `vibe_ids` is sent and stored in, never tap order. */
val VIBE_ORDER: List<String> = VibeGroup.entries.flatMap { g -> g.vibes.map { it.id } }

/** Every display surface's label for a §31 value (`CHECKIN_VIBE_LABEL`). */
val VIBE_LABEL: Map<String, String> = VibeGroup.entries.flatMap { it.vibes }.associate { it.id to it.label }

/** What a tap on a vibe chip did. */
sealed interface VibeTap {
    data class Changed(val vibes: Set<String>) : VibeTap
    /** "At 5/5 a tap on an idle chip selects nothing" and raises the max toast. */
    data object Limit : VibeTap
}

fun tapVibe(picked: Set<String>, id: String): VibeTap = when {
    id in picked -> VibeTap.Changed(picked - id)
    picked.size >= VIBE_MAX -> VibeTap.Limit
    else -> VibeTap.Changed(picked + id)
}

/** `vibe_ids`: the picked values in list order, comma-joined, no spaces. */
fun vibeIds(picked: Set<String>): String = VIBE_ORDER.filter { it in picked }.joinToString(",")

// ── filters (Check-In 05) ───────────────────────────────────────────────────────────────────────

/** §10 `meet_gender` -- the §1 gender set, shown in the plural because the filter selects people. */
enum class CheckInGender(val id: String, val label: String) {
    Woman("woman", "Women"),
    Man("man", "Men"),
    NonBinary("non_binary", "Non-binary"),
    Other("other", "Other"),
}

const val AGE_MIN = 18

/** The top stop, "65+", is NO upper limit and is stored as null. */
const val AGE_MAX = 65
const val DISTANCE_MIN = 1
const val DISTANCE_MAX = 50
const val DISTANCE_DEFAULT = 10

/** The Filters step's values. [maxAge] [AGE_MAX] is "no upper limit". */
data class CheckInFilters(
    val genders: Set<String>,
    val minAge: Int = AGE_MIN,
    val maxAge: Int = AGE_MAX,
    val distanceKm: Int = DISTANCE_DEFAULT,
) {
    /** `max_age` as sent and stored: null for 65+. */
    val maxAgeOrNull: Int? get() = maxAge.takeIf { it < AGE_MAX }
}

/** The age row's value: `Any age` · `30+` · `25 – 35`. */
fun ageLabel(minAge: Int, maxAge: Int): String = when {
    minAge == AGE_MIN && maxAge == AGE_MAX -> "Any age"
    maxAge == AGE_MAX -> "$minAge+"
    else -> "$minAge – $maxAge"
}

/** The distance row's value. */
fun distanceLabel(km: Int): String = "$km km"

/** A gender chip tapped: any combination is valid, zero is not (Continue checks that). */
fun toggleGender(picked: Set<String>, id: String): Set<String> = if (id in picked) picked - id else picked + id

/**
 * An age thumb moved to [value]. "The thumbs may meet (one age) but never cross: dragging one past
 * the other carries the other with it."
 *
 * @param thumb 0 for the minimum, 1 for the maximum.
 */
fun moveAgeThumb(filters: CheckInFilters, thumb: Int, value: Int): CheckInFilters {
    val v = value.coerceIn(AGE_MIN, AGE_MAX)
    return if (thumb == 0) {
        filters.copy(minAge = v, maxAge = maxOf(filters.maxAge, v))
    } else {
        filters.copy(maxAge = v, minAge = minOf(filters.minAge, v))
    }
}

/**
 * `filters_changed`: the §10 filter_ids whose value differs from what the step showed on entry, in
 * the order `meet_gender,age_range,distance_km`, or `none`. Never the values themselves --
 * `meet_gender` is Class 2.
 */
fun filtersChanged(entry: CheckInFilters, now: CheckInFilters): String {
    val changed = buildList {
        if (entry.genders != now.genders) add("meet_gender")
        if (entry.minAge != now.minAge || entry.maxAge != now.maxAge) add("age_range")
        if (entry.distanceKm != now.distanceKm) add("distance_km")
    }
    return if (changed.isEmpty()) "none" else changed.joinToString(",")
}

// ── uplift (Check-In 06) ────────────────────────────────────────────────────────────────────────

/** "100 characters, counted as graphemes, so an emoji is one." */
const val UPLIFT_MAX = 100

/** How many user-perceived characters [text] holds -- an emoji, a flag or a family is one. */
fun graphemeCount(text: String): Int {
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    var count = 0
    while (it.next() != BreakIterator.DONE) count++
    return count
}

/** [text] cut at [UPLIFT_MAX] graphemes -- "input past 100 is not accepted, and a paste is cut at 100". */
fun clampUplift(text: String): String {
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    var end = 0
    var count = 0
    while (count < UPLIFT_MAX) {
        val next = it.next()
        if (next == BreakIterator.DONE) return text
        end = next
        count++
    }
    return text.substring(0, end)
}

/** `{n} characters left` -- `1 character left` at one. */
fun upliftCounterLabel(text: String): String {
    val left = UPLIFT_MAX - graphemeCount(text)
    return if (left == 1) "1 character left" else "$left characters left"
}

private val LINK_TLD = Regex(
    "\\b[a-z0-9-]+\\.(com|de|net|org|io|me|app|co|info|biz|link|ly|gg|tv|eu|at|ch|uk|xyz|online|site)\\b",
    RegexOption.IGNORE_CASE,
)
private val EMAIL = Regex("[^\\s@]+@[^\\s@]+\\.[a-z]{2,}", RegexOption.IGNORE_CASE)
private val URL = Regex("(https?://|www\\.)\\S+", RegexOption.IGNORE_CASE)
private val DIGIT_RUN = Regex("\\+?\\d[\\d\\s()./-]{5,}\\d")

/**
 * Whether [text] carries contact info -- an email, a phone number (seven or more digits, with any
 * spaces, dashes, dots, brackets, slashes or `+`) or a link (`http(s)://`, `www.`, or a bare domain
 * with a common TLD). `upliftContact` in the reference, the same rules the server repeats.
 */
fun upliftHasContact(text: String): Boolean =
    EMAIL.containsMatchIn(text) ||
        URL.containsMatchIn(text) ||
        LINK_TLD.containsMatchIn(text) ||
        DIGIT_RUN.findAll(text).any { run -> run.value.count { it.isDigit() } >= 7 }

/** `answer_value` on Uplift's `check_in_step_answered`: the trimmed text, or nothing. */
fun upliftAnswerValue(text: String): String = if (text.trim().isEmpty()) "empty" else "written"

// ── copy ────────────────────────────────────────────────────────────────────────────────────────

/** Every string the Check-In draws, verbatim from the tickets. */
object CheckInCopy {
    // Header and close (every step).
    const val HEADER = "Check-in"
    const val CLOSE_LABEL = "Close Check-In"

    // 01 · A
    val INTRO_HEADLINE = listOf("Check-in to be " to false, "dated soon." to true)
    val INTRO_LIST = listOf(
        "Define your availability",
        "Show your motivation",
        "Define your desired dating vibe",
        "Filter who you wish to date",
        "Give reasons to match you",
    )
    val INTRO_CLOSING = listOf("The rest is " to false, "magic" to true, " to happen." to false)
    const val CHECK_IN_CTA = "Check-in for a date"

    // 01 · B
    val GATE_HEADLINE = listOf("Check-in to " to false, "start dating." to true)
    const val GATE_BODY = "Date search and Instant open once you've told us when you're free to date."

    // 02
    val TODAY_HEADLINE = listOf("When can you date " to false, "today" to true, "?" to false)
    /** The subline's runs: `How it works` in primary 700, as styled text, not a link. */
    val TODAY_SUB = listOf(
        "Tap each hour you're free. We'll only show you people free at the same times. Can't date today? " +
            "Just continue to tomorrow. See " to false,
        "How it works" to true,
        " for more info." to false,
    )
    val TOMORROW_HEADLINE = listOf("When can you date " to false, "tomorrow" to true, "?" to false)
    const val TOMORROW_SUB = "Tap each hour you're free to meet. We'll only show people free within your hours."
    const val HOW_IT_WORKS = "How it works"
    const val CLEAR_ALL = "Clear all"
    /** The panel's five paragraphs, as runs of (text, bold). */
    val HOW_IT_WORKS_PANEL = listOf(
        listOf("We only show you people who are free to date at the " to false, "same times as you" to true, "." to false),
        listOf(
            "Only pick hours when you can " to false, "actually be at a meeting spot" to true,
            " — make sure you have time for the transit." to false,
        ),
        listOf(
            "When you get a Match, we suggest a meet time based on your " to false,
            "overlapping availability" to true, "." to false,
        ),
        listOf(
            "Example:" to true,
            " At 7pm you get a Match. Your overlapping availability is 6pm–11pm. We suggest meeting at 8pm, " +
                "so you both can arrive on time." to false,
        ),
        listOf("That's the magic of Show Up — " to false, "you actually meet someone — fast." to true),
    )
    const val SUMMARY_LABEL = "Your availability"
    const val SUMMARY_EMPTY = "nothing selected yet"
    const val LEGEND_LEAD = "Tap an hour to cycle through:"
    const val LEGEND_PREFERRED = "1st tap Preferred"
    const val LEGEND_AVAILABLE = "2nd tap Available"
    const val LEGEND_CLEAR = "3rd tap Clear"
    const val CONTINUE = "Continue"
    const val TOMORROW_TOAST = "Pick at least one hour you can date tomorrow."
    val NO_HOURS_TITLE = listOf("No hours " to false, "today" to true, "?" to false)
    const val NO_HOURS_BODY = "You didn't add any available hours to date today. Continue to set your " +
        "availability for tomorrow — or cancel and choose the hours you can date today."
    const val NO_HOURS_CONFIRM = "Continue to tomorrow"
    const val NO_HOURS_CANCEL = "Cancel, pick hours today"
    /** The dialog's accessible name, from the reference (`aria-label`). */
    const val NO_HOURS_DIALOG_LABEL = "No available hours today"

    // 03
    val MOTIVATION_HEADLINE = listOf("What is your " to false, "motivation" to true, " to date?" to false)
    const val MOTIVATION_NOTE = "This will be highly visible on your profile and a key decision point for " +
        "someone to date you."
    const val MOTIVATION_TOAST = "Pick your motivation to date."
    const val MOTIVATION_GROUP = "Motivation to date"

    // 04
    val VIBE_HEADLINE = listOf("What best describes your " to false, "dating vibe" to true, " today?" to false)
    const val VIBE_NOTE = "Your vibes tell people how you feel, what you long for, and how you'd like to meet " +
        "— a quick read on where your head is at today."
    const val VIBE_COUNTER_LEAD = "Pick up to 5 — mix and match"
    const val VIBE_TOAST_EMPTY = "Pick at least one dating vibe (up to 5)."
    const val VIBE_TOAST_MAX = "You can pick up to 5 vibes."

    // 05
    val FILTERS_HEADLINE = listOf("Filter who " to false, "you wish to date" to true)
    const val FILTERS_NOTE = "This will limit who you will see to potentially date."
    const val FILTER_GENDER = "Gender"
    const val FILTER_AGE = "Age range"
    const val FILTER_DISTANCE = "How far away?"
    const val SLIDER_MIN_AGE = "Minimum age"
    const val SLIDER_MAX_AGE = "Maximum age"
    const val SLIDER_DISTANCE = "Distance in km"
    const val FILTERS_TOAST = "Pick at least one gender to date."

    // 06
    val UPLIFT_HEADLINE = listOf("Give people a reason to date " to false, "you" to true, " over others!" to false)
    const val UPLIFT_NOTE = "Your place to offer something special and get preferred over others! This will " +
        "be highly visible on your profile."
    const val UPLIFT_PLACEHOLDER = "e.g. If we meet today, I will take you on a rafting tour on Wannsee with Picnic :)"
    const val UPLIFT_FIELD_LABEL = "Your reason to be picked"
    const val INSPIRATIONS_LABEL = "Some inspirations that matched well"
    val INSPIRATIONS = listOf(
        "have 2x tickets for Eminem today",
        "will take you out on my boat on Wannsee",
        "need a co-pilot for my flight training today",
        "made fresh brownies today, free to share",
        "still looking for someone to join my dance class",
    )
    const val FIND_MY_DATE = "Find my date"
    const val CONTACT_ERROR = "No emails, phone numbers or links allowed."
    const val SAVE_ERROR = "Couldn't save your Check-in. Check your connection and try again."
}
