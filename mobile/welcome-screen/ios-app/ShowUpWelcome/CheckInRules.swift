//
//  CheckInRules.swift
//  ShowUp · the 48hrs Check-In's rules and copy, with no UI (SHOWUP-178 to SHOWUP-183)
//
//  The Swift twin of `checkin/CheckInRules.kt`. Everything a Check-In screen decides is here as a
//  plain function -- the 7am day, the next full hour, the tap cycle, the summary, the vibe limit,
//  the filter changes, the uplift count and the contact-info rule -- so each is tested on its own
//  (`CheckInRulesTests`). Screens take values and draw them.
//
//  Copy is verbatim from the six tickets (`design_handoff_showup/checkin/tickets`). The UI string is
//  `Check-in` as drawn, by decision; the product term in comments is Check-In.
//

import Foundation

/// The step bar's six segments -- the intro does not count (epic, `StepProgress steps={6}`).
let checkInStepsTotal = 6

/// §28 `check_in_step`, in flow order, with the §11 screen each is reported as and its place on the
/// step bar. The intro is a value (`check_in_closed` can fire on it) but has no segment.
enum CheckInStep: String, CaseIterable, Sendable {
    case intro, today, tomorrow, motivation, vibe, filters, uplift

    var stepId: String { rawValue }

    var screen: ProfileScreen {
        switch self {
        case .intro: return .checkinIntro
        case .today: return .checkinToday
        case .tomorrow: return .checkinTomorrow
        case .motivation: return .checkinMotivation
        case .vibe: return .checkinVibe
        case .filters: return .checkinFilters
        case .uplift: return .checkinUplift
        }
    }

    var progress: Int? {
        switch self {
        case .intro: return nil
        case .today: return 1
        case .tomorrow: return 2
        case .motivation: return 3
        case .vibe: return 4
        case .filters: return 5
        case .uplift: return 6
        }
    }
}

// MARK: - the wall clock

/// A calendar date with no zone -- Java's `LocalDate`. The Check-In's days are wall-clock days.
struct CivilDate: Hashable, Comparable, Sendable {
    let year: Int
    let month: Int
    let day: Int

    static func < (a: CivilDate, b: CivilDate) -> Bool {
        (a.year, a.month, a.day) < (b.year, b.month, b.day)
    }

    /// `days` later, or earlier, across month and year ends. Counted in UTC, where no day is
    /// anything but 24 hours long.
    func adding(days: Int) -> CivilDate {
        let utc = CivilDateTime.calendar(TimeZone(secondsFromGMT: 0) ?? .current)
        guard let start = utc.date(from: DateComponents(year: year, month: month, day: day)),
              let moved = utc.date(byAdding: .day, value: days, to: start) else { return self }
        let c = utc.dateComponents([.year, .month, .day], from: moved)
        return CivilDate(year: c.year ?? year, month: c.month ?? month, day: c.day ?? day)
    }
}

/// A wall-clock time on a date, to the minute -- Java's `LocalDateTime`. Picked hours are keyed by
/// it (see `CheckInModel`'s header for why it is the time and not the hour number).
struct CivilDateTime: Hashable, Comparable, Sendable {
    let date: CivilDate
    let hour: Int
    let minute: Int

    init(date: CivilDate, hour: Int, minute: Int = 0) {
        self.date = date
        self.hour = hour
        self.minute = minute
    }

    /// What the wall clock in `zone` reads at `instant`.
    init(_ instant: Date, in zone: TimeZone) {
        let c = Self.calendar(zone).dateComponents([.year, .month, .day, .hour, .minute], from: instant)
        self.init(date: CivilDate(year: c.year ?? 1970, month: c.month ?? 1, day: c.day ?? 1),
                  hour: c.hour ?? 0, minute: c.minute ?? 0)
    }

    static func < (a: CivilDateTime, b: CivilDateTime) -> Bool {
        (a.date, a.hour, a.minute) < (b.date, b.hour, b.minute)
    }

    /// The instant this wall-clock time is in `zone`. A time the clocks skip lands after the gap and
    /// a time they repeat on its first occurrence -- `java.time`'s rules for `atZone`.
    func instant(in zone: TimeZone) -> Date {
        let parts = DateComponents(year: date.year, month: date.month, day: date.day, hour: hour, minute: minute)
        return Self.calendar(zone).date(from: parts) ?? Date(timeIntervalSince1970: 0)
    }

    static func calendar(_ zone: TimeZone) -> Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = zone
        return c
    }
}

// MARK: - availability (Check-In 02)

/// A picked hour's tier: the 1st tap or the 2nd. Both are stored and used (story 02).
enum SlotTier: String, Sendable {
    case preferred, available
    var wire: String { rawValue }
}

/// "A tap cycles a slot idle → preferred → available → idle."
func cycleSlot(_ current: SlotTier?) -> SlotTier? {
    switch current {
    case .none: return .preferred
    case .some(.preferred): return .available
    case .some(.available): return nil
    }
}

/// "A Check-In day runs 7am → 7am": both grids end at the 6am slot.
let checkInDayStart = 7

/// Today and tomorrow at a moment on the device's clock.
///
/// "Today's first slot is the next full hour" -- an hour that has started is never offered, because
/// with the one-hour meet buffer it cannot produce a date. "Today" is the 7am → 7am block that
/// contains that hour: at 4:20pm it starts at 5pm; at 1:20am it is 2am-6am, the night of the
/// evening before; at 6:20am the next full hour is 7am and Today is the new day.
///
/// `todayDate` is the date the Today block STARTS on (its 7am).
struct CheckInDays: Equatable, Sendable {
    let todayDate: CivilDate
    let todayStartHour: Int
    var tomorrowDate: CivilDate { todayDate.adding(days: 1) }
}

func checkInDays(_ now: CivilDateTime) -> CheckInDays {
    let nextHour = (now.hour + 1) % 24
    let nextDate = nextHour == 0 ? now.date.adding(days: 1) : now.date
    let date = nextHour >= checkInDayStart ? nextDate : nextDate.adding(days: -1)
    return CheckInDays(todayDate: date, todayStartHour: nextHour)
}

/// The hours a grid offers, from `startHour` through the 6am slot. Tomorrow: 7am, 24 slots.
func availabilityHours(_ startHour: Int) -> [Int] {
    let span = ((checkInDayStart - 1 - startHour + 24) % 24) + 1
    return (0..<span).map { (startHour + $0) % 24 }
}

/// The instant hour `hour` of the Check-In day that starts on `blockDate` begins, in `zone`. Hours
/// before 7am belong to the evening before, so they fall on the next calendar date.
func slotStart(_ blockDate: CivilDate, hour: Int, zone: TimeZone) -> Date {
    let date = hour >= checkInDayStart ? blockDate : blockDate.adding(days: 1)
    return CivilDateTime(date: date, hour: hour).instant(in: zone)
}

/// An hour's label: `7am` … `11am` · `noon` · `1pm` … `11pm` · `12am` · `1am` … `6am`.
func formatHour(_ hour: Int) -> String {
    let h = ((hour % 24) + 24) % 24
    if h == 0 { return "12am" }
    if h == 12 { return "noon" }
    return h < 12 ? "\(h)am" : "\(h - 12)pm"
}

/// The picked hours collapsed into ranges, in grid order. "A slot means h until h+1, so 6pm + 7pm +
/// 8pm reads 6–9pm. Runs across midnight stay one range. Tiers are not distinguished."
func availabilityRanges(_ hours: [Int], _ picked: [Int: SlotTier]) -> [String] {
    var runs: [[Int]] = []
    var open = false
    for h in hours {
        guard picked[h] != nil else {
            open = false
            continue
        }
        if open {
            runs[runs.count - 1].append(h)
        } else {
            runs.append([h])
            open = true
        }
    }
    func period(_ x: Int) -> String { x < 12 ? "am" : "pm" }
    func number(_ x: Int) -> Int { x % 12 == 0 ? 12 : x % 12 }
    return runs.compactMap { run in
        guard let a = run.first, let lastHour = run.last else { return nil }
        let b = (lastHour + 1) % 24
        return period(a) == period(b)
            ? "\(number(a))–\(number(b))\(period(b))"
            : "\(number(a))\(period(a))–\(number(b))\(period(b))"
    }
}

/// How many ranges the one-line summary shows before it says `+N more` (story 02).
let summaryMaxRuns = 4

/// The summary's text, or nil for "nothing selected yet". Never wraps: past four runs, `+N more`.
func availabilitySummary(_ hours: [Int], _ picked: [Int: SlotTier]) -> String? {
    let ranges = availabilityRanges(hours, picked)
    if ranges.isEmpty { return nil }
    let shown = ranges.count > summaryMaxRuns ? Array(ranges.prefix(summaryMaxRuns - 1)) : ranges
    let more = ranges.count - shown.count
    return shown.joined(separator: " · ") + (more > 0 ? " · +\(more) more" : "")
}

/// Picks the grid still offers -- "a slot that has started disappears from the grid and from the
/// selection".
func keepOffered(_ picked: [Int: SlotTier], _ hours: [Int]) -> [Int: SlotTier] {
    picked.filter { hours.contains($0.key) }
}

/// `preferred_hours` / `available_hours` on `availability_slots_set`.
func tierCount(_ picked: [Int: SlotTier], _ tier: SlotTier) -> Int {
    picked.values.filter { $0 == tier }.count
}

// MARK: - motivation (Check-In 03)

/// §30 `check_in_motivation`, in this order, with the title, subline and icon each row shows.
enum MotivationOption: String, CaseIterable, Sendable {
    case longTerm = "long_term"
    case nonMonogamy = "non_monogamy"
    case shortTerm = "short_term"
    case casual = "casual"
    case friends = "friends"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .longTerm: return "Long-term partner"
        case .nonMonogamy: return "Long-term Non-Monogamy"
        case .shortTerm: return "Short-term fun"
        case .casual: return "Casual dating"
        case .friends: return "New friends"
        }
    }

    var subline: String {
        switch self {
        case .longTerm: return "Looking for a meaningful connection"
        case .nonMonogamy: return "Non-monogamous lifestyle"
        case .shortTerm: return "Physical chemistry, no commitment"
        case .casual: return "Light, fun, non-committal"
        case .friends: return "Strictly platonic connections"
        }
    }

    var icon: BrandIcon {
        switch self {
        case .longTerm: return .heartFilled
        case .nonMonogamy: return .compass
        case .shortTerm: return .flame
        case .casual: return .sparkles
        case .friends: return .users
        }
    }

    static func of(_ id: String?) -> MotivationOption? {
        id.flatMap(MotivationOption.init(rawValue:))
    }
}

// MARK: - vibe (Check-In 04)

/// "Multi-select, at most five, across all groups."
let vibeMax = 5

struct VibeOption: Equatable, Sendable {
    let id: String
    let label: String
}

/// §31 `check_in_vibe` in three groups, in this order. Search and Instant read the labels here.
enum VibeGroup: String, CaseIterable, Sendable {
    case activity = "activity"
    case headspace = "headspace"
    case pacing = "pacing"

    var groupId: String { rawValue }

    var label: String {
        switch self {
        case .activity: return "What you want to do"
        case .headspace: return "How you feel right now"
        case .pacing: return "Your energy & pacing"
        }
    }

    var hint: String {
        switch self {
        case .activity: return "Activities"
        case .headspace: return "Headspace"
        case .pacing: return "Boundaries"
        }
    }

    var vibes: [VibeOption] {
        switch self {
        case .activity:
            return [
                VibeOption(id: "coffee_chat", label: "Coffee & a chat"),
                VibeOption(id: "casual_drink", label: "Kiosk beer / Casual drink"),
                VibeOption(id: "grab_food", label: "Grabbing food"),
                VibeOption(id: "nature_walk", label: "Park or nature walk"),
                VibeOption(id: "bouldering_table_tennis", label: "Bouldering / Table tennis"),
                VibeOption(id: "workout", label: "Run or active workout"),
                VibeOption(id: "sunset", label: "Watching the sunset"),
                VibeOption(id: "drinks_music", label: "Drinks & music"),
            ]
        case .headspace:
            return [
                VibeOption(id: "calm", label: "Calm & peaceful"),
                VibeOption(id: "deep_talk", label: "Deep conversation"),
                VibeOption(id: "playful", label: "Fun & playful"),
                VibeOption(id: "cozy", label: "Cozy & comfortable"),
                VibeOption(id: "spontaneous", label: "Spontaneous / Up for anything"),
                VibeOption(id: "curious", label: "Curious / Open-minded"),
            ]
        case .pacing:
            return [
                VibeOption(id: "quick_hello", label: "Quick 30-min hello"),
                VibeOption(id: "low_key", label: "Low-key energy"),
                VibeOption(id: "high_energy", label: "High social energy"),
                VibeOption(id: "no_rush", label: "Free evening (no rush)"),
                VibeOption(id: "early_night", label: "Early night (home early)"),
                VibeOption(id: "meet_now", label: "Ready to meet now"),
            ]
        }
    }
}

/// §31 in list order -- the order `vibe_ids` is sent and stored in, never tap order.
let vibeOrder: [String] = VibeGroup.allCases.flatMap { g in g.vibes.map(\.id) }

/// Every display surface's label for a §31 value (`CHECKIN_VIBE_LABEL`).
let vibeLabel: [String: String] = Dictionary(
    uniqueKeysWithValues: VibeGroup.allCases.flatMap(\.vibes).map { ($0.id, $0.label) })

/// What a tap on a vibe chip did.
enum VibeTap: Equatable, Sendable {
    case changed(Set<String>)
    /// "At 5/5 a tap on an idle chip selects nothing" and raises the max toast.
    case limit
}

func tapVibe(_ picked: Set<String>, _ id: String) -> VibeTap {
    if picked.contains(id) { return .changed(picked.subtracting([id])) }
    if picked.count >= vibeMax { return .limit }
    return .changed(picked.union([id]))
}

/// `vibe_ids`: the picked values in list order, comma-joined, no spaces.
func vibeIds(_ picked: Set<String>) -> String {
    vibeOrder.filter { picked.contains($0) }.joined(separator: ",")
}

// MARK: - filters (Check-In 05)

/// §10 `meet_gender` -- the §1 gender set, shown in the plural because the filter selects people.
enum CheckInGender: String, CaseIterable, Sendable {
    case woman = "woman"
    case man = "man"
    case nonBinary = "non_binary"
    case other = "other"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .woman: return "Women"
        case .man: return "Men"
        case .nonBinary: return "Non-binary"
        case .other: return "Other"
        }
    }
}

let ageMin = 18
/// The top stop, "65+", is NO upper limit and is stored as nil.
let ageMax = 65
let distanceMin = 1
let distanceMax = 50
let distanceDefault = 10

/// The Filters step's values. `maxAge` at `ageMax` is "no upper limit".
struct CheckInFilters: Equatable, Sendable {
    var genders: Set<String>
    var minAge: Int = ageMin
    var maxAge: Int = ageMax
    var distanceKm: Int = distanceDefault

    /// `max_age` as sent and stored: nil for 65+.
    var maxAgeOrNil: Int? { maxAge < ageMax ? maxAge : nil }
}

/// The age row's value: `Any age` · `30+` · `25 – 35`.
func ageLabel(_ minAge: Int, _ maxAge: Int) -> String {
    if minAge == ageMin && maxAge == ageMax { return "Any age" }
    if maxAge == ageMax { return "\(minAge)+" }
    return "\(minAge) – \(maxAge)"
}

/// The distance row's value.
func distanceLabel(_ km: Int) -> String { "\(km) km" }

/// A gender chip tapped: any combination is valid, zero is not (Continue checks that).
func toggleGender(_ picked: Set<String>, _ id: String) -> Set<String> {
    picked.contains(id) ? picked.subtracting([id]) : picked.union([id])
}

/// An age thumb moved to `value`. "The thumbs may meet (one age) but never cross: dragging one past
/// the other carries the other with it." `thumb` is 0 for the minimum, 1 for the maximum.
func moveAgeThumb(_ filters: CheckInFilters, thumb: Int, value: Int) -> CheckInFilters {
    let v = min(max(value, ageMin), ageMax)
    var next = filters
    if thumb == 0 {
        next.minAge = v
        next.maxAge = max(filters.maxAge, v)
    } else {
        next.maxAge = v
        next.minAge = min(filters.minAge, v)
    }
    return next
}

/// `filters_changed`: the §10 filter_ids whose value differs from what the step showed on entry, in
/// the order `meet_gender,age_range,distance_km`, or `none`. Never the values themselves --
/// `meet_gender` is Class 2.
func filtersChanged(_ entry: CheckInFilters, _ now: CheckInFilters) -> String {
    var changed: [String] = []
    if entry.genders != now.genders { changed.append("meet_gender") }
    if entry.minAge != now.minAge || entry.maxAge != now.maxAge { changed.append("age_range") }
    if entry.distanceKm != now.distanceKm { changed.append("distance_km") }
    return changed.isEmpty ? "none" : changed.joined(separator: ",")
}

/// What Filters sets, one per §10 filter_id.
enum FilterField: Sendable { case genders, ages, distance }

/// Filters the user set on the offline stand-in, once the server's own settings arrive. What they
/// changed on the stand-in stays; everything they left alone is the server's. The stand-in shows
/// 18 - 65+ and 10 km because nothing better is known, not because the user chose them: ticking a
/// gender and continuing must not replace a saved 25 - 35 the user never saw -- the record is
/// shared with Search. Android's `filtersOverStandIn`.
func filtersOverStandIn(_ local: CheckInFilters, server: CheckInFilters, edited: Set<FilterField>) -> CheckInFilters {
    var out = server
    if edited.contains(.genders) { out.genders = local.genders }
    if edited.contains(.ages) {
        out.minAge = local.minAge
        out.maxAge = local.maxAge
    }
    if edited.contains(.distance) { out.distanceKm = local.distanceKm }
    return out
}

// MARK: - uplift (Check-In 06)

/// "100 characters, counted as graphemes, so an emoji is one."
let upliftMax = 100

/// How many user-perceived characters `text` holds -- an emoji, a flag or a family is one. A Swift
/// `Character` IS an extended grapheme cluster, so this is the count Android's BreakIterator makes.
func graphemeCount(_ text: String) -> Int { text.count }

/// `text` cut at `upliftMax` graphemes -- "input past 100 is not accepted, and a paste is cut at
/// 100". Never splits a grapheme: an emoji is kept whole or not at all.
func clampUplift(_ text: String) -> String {
    text.count <= upliftMax ? text : String(text.prefix(upliftMax))
}

/// `{n} characters left` -- `1 character left` at one.
func upliftCounterLabel(_ text: String) -> String {
    let left = upliftMax - graphemeCount(text)
    return left == 1 ? "1 character left" : "\(left) characters left"
}

/// The four contact patterns, character for character the Kotlin side's: ICU reads both, so they
/// match the same text on both phones.
private let linkTldPattern =
    "\\b[a-z0-9-]+\\.(com|de|net|org|io|me|app|co|info|biz|link|ly|gg|tv|eu|at|ch|uk|xyz|online|site)\\b"
private let emailPattern = "[^\\s@]+@[^\\s@]+\\.[a-z]{2,}"
private let urlPattern = "(https?://|www\\.)\\S+"
private let digitRunPattern = "\\+?\\d[\\d\\s()./-]{5,}\\d"

/// Whether `text` carries contact info -- an email, a phone number (seven or more digits, with any
/// spaces, dashes, dots, brackets, slashes or `+`) or a link (`http(s)://`, `www.`, or a bare domain
/// with a common TLD). `upliftContact` in the reference, the same rules the server repeats.
func upliftHasContact(_ text: String) -> Bool {
    let insensitive: String.CompareOptions = [.regularExpression, .caseInsensitive]
    if text.range(of: emailPattern, options: insensitive) != nil { return true }
    if text.range(of: urlPattern, options: insensitive) != nil { return true }
    if text.range(of: linkTldPattern, options: insensitive) != nil { return true }
    guard let digitRun = try? NSRegularExpression(pattern: digitRunPattern) else { return false }
    let whole = NSRange(text.startIndex..., in: text)
    return digitRun.matches(in: text, range: whole).contains { match in
        guard let range = Range(match.range, in: text) else { return false }
        // Kotlin's `isDigit`: a decimal digit in any script, which is also what `\d` matched.
        return text[range].unicodeScalars.filter { $0.properties.generalCategory == .decimalNumber }.count >= 7
    }
}

/// `answer_value` on Uplift's `check_in_step_answered`: the trimmed text, or nothing.
func upliftAnswerValue(_ text: String) -> String {
    text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "empty" : "written"
}

// MARK: - copy

/// Every string the Check-In draws, verbatim from the tickets. Runs are (text, emphasised), written
/// `("...", bold: ...)` so `audit/verify-checkin.py` reads a run list as the one sentence it is.
enum CheckInCopy {
    // Header and close (every step).
    static let header = "Check-in"
    static let closeLabel = "Close Check-In"

    // 01 · A
    static let introHeadline: [(String, Bool)] = [("Check-in to be ", bold: false), ("dated soon.", bold: true)]
    static let introList = [
        "Define your availability",
        "Show your motivation",
        "Define your desired dating vibe",
        "Filter who you wish to date",
        "Give reasons to match you",
    ]
    static let introClosing: [(String, Bool)] = [("The rest is ", bold: false), ("magic", bold: true), (" to happen.", bold: false)]
    static let checkInCta = "Check-in for a date"

    // 01 · B
    static let gateHeadline: [(String, Bool)] = [("Check-in to ", bold: false), ("start dating.", bold: true)]
    static let gateBody = "Date search and Instant open once you've told us when you're free to date."

    // 02
    static let todayHeadline: [(String, Bool)] = [("When can you date ", bold: false), ("today", bold: true), ("?", bold: false)]
    /// The subline's runs: `How it works` in primary 700, as styled text, not a link.
    static let todaySub: [(String, Bool)] = [
        ("Tap each hour you're free. We'll only show you people free at the same times. Can't date today? "
            + "Just continue to tomorrow. See ", bold: false),
        ("How it works", bold: true),
        (" for more info.", bold: false),
    ]
    static let tomorrowHeadline: [(String, Bool)] = [("When can you date ", bold: false), ("tomorrow", bold: true), ("?", bold: false)]
    static let tomorrowSub = "Tap each hour you're free to meet. We'll only show people free within your hours."
    static let howItWorks = "How it works"
    static let clearAll = "Clear all"
    /// The panel's five paragraphs, as runs of (text, bold).
    static let howItWorksPanel: [[(String, Bool)]] = [
        [("We only show you people who are free to date at the ", bold: false), ("same times as you", bold: true), (".", bold: false)],
        [
            ("Only pick hours when you can ", bold: false), ("actually be at a meeting spot", bold: true),
            (" — make sure you have time for the transit.", bold: false),
        ],
        [
            ("When you get a Match, we suggest a meet time based on your ", bold: false),
            ("overlapping availability", bold: true), (".", bold: false),
        ],
        [
            ("Example:", bold: true),
            (" At 7pm you get a Match. Your overlapping availability is 6pm–11pm. We suggest meeting at 8pm, "
                + "so you both can arrive on time.", bold: false),
        ],
        [("That's the magic of Show Up — ", bold: false), ("you actually meet someone — fast.", bold: true)],
    ]
    static let summaryLabel = "Your availability"
    static let summaryEmpty = "nothing selected yet"
    static let legendLead = "Tap an hour to cycle through:"
    static let legendPreferred = "1st tap Preferred"
    static let legendAvailable = "2nd tap Available"
    static let legendClear = "3rd tap Clear"
    static let continueLabel = "Continue"
    static let tomorrowToast = "Pick at least one hour you can date tomorrow."
    static let noHoursTitle: [(String, Bool)] = [("No hours ", bold: false), ("today", bold: true), ("?", bold: false)]
    static let noHoursBody = "You didn't add any available hours to date today. Continue to set your "
        + "availability for tomorrow — or cancel and choose the hours you can date today."
    static let noHoursConfirm = "Continue to tomorrow"
    static let noHoursCancel = "Cancel, pick hours today"
    /// The dialog's accessible name, from the reference (`aria-label`).
    static let noHoursDialogLabel = "No available hours today"

    // 03
    static let motivationHeadline: [(String, Bool)] = [("What is your ", bold: false), ("motivation", bold: true), (" to date?", bold: false)]
    static let motivationNote = "This will be highly visible on your profile and a key decision point for "
        + "someone to date you."
    static let motivationToast = "Pick your motivation to date."
    static let motivationGroup = "Motivation to date"

    // 04
    static let vibeHeadline: [(String, Bool)] = [
        ("What best describes your ", bold: false), ("dating vibe", bold: true), (" today?", bold: false),
    ]
    static let vibeNote = "Your vibes tell people how you feel, what you long for, and how you'd like to meet "
        + "— a quick read on where your head is at today."
    static let vibeCounterLead = "Pick up to 5 — mix and match"
    static let vibeToastEmpty = "Pick at least one dating vibe (up to 5)."
    static let vibeToastMax = "You can pick up to 5 vibes."

    // 05
    static let filtersHeadline: [(String, Bool)] = [("Filter who ", bold: false), ("you wish to date", bold: true)]
    static let filtersNote = "This will limit who you will see to potentially date."
    static let filterGender = "Gender"
    static let filterAge = "Age range"
    static let filterDistance = "How far away?"
    static let sliderMinAge = "Minimum age"
    static let sliderMaxAge = "Maximum age"
    static let sliderDistance = "Distance in km"
    static let filtersToast = "Pick at least one gender to date."

    // 06
    static let upliftHeadline: [(String, Bool)] = [
        ("Give people a reason to date ", bold: false), ("you", bold: true), (" over others!", bold: false),
    ]
    static let upliftNote = "Your place to offer something special and get preferred over others! This will "
        + "be highly visible on your profile."
    static let upliftPlaceholder = "e.g. If we meet today, I will take you on a rafting tour on Wannsee with Picnic :)"
    static let upliftFieldLabel = "Your reason to be picked"
    static let inspirationsLabel = "Some inspirations that matched well"
    static let inspirations = [
        "have 2x tickets for Eminem today",
        "will take you out on my boat on Wannsee",
        "need a co-pilot for my flight training today",
        "made fresh brownies today, free to share",
        "still looking for someone to join my dance class",
    ]
    static let findMyDate = "Find my date"
    static let contactError = "No emails, phone numbers or links allowed."
    static let saveError = "Couldn't save your Check-in. Check your connection and try again."
}
