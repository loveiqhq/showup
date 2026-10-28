//
//  SearchRules.swift
//  ShowUp · Date search 01's rules and copy, with no UI (SHOWUP-184)
//
//  The Swift twin of `search/SearchRules.kt`. Everything the Search profile decides is here as a plain
//  function -- the distance line, the name line, the meet line, the shared vibes and interests, the
//  section subtitles, the Essentials rows, How I live, the message draft and its 100-character rule --
//  so each is tested on its own (`SearchRulesTests`). Screens take values and draw them.
//
//  Copy is verbatim from the ticket, quoted back by `audit/verify-search.py`. Labels for stored
//  answers are NOT here: they are the profile flow's own tables (`DetailStep`, `LifestyleField`,
//  `InterestSection`, `VibeGroup`, `MotivationOption`), read, never copied.
//

import Foundation

/// The ticket's copy, one constant per string, so the verifier can quote each against it.
enum SearchCopy {
    static let meetLabel = "You could meet at:"
    static let filtersLabel = "Filters"
    static let notInterested = "Not interested"
    static let notNow = "Not now"
    static let like = "Like"
    static let everydaySub = "How I live"
    static let charmsTitle = "Charms from past dates"

    static func sharedVibesTitle(_ name: String) -> String { "You and \(name) both want today" }
    static func highlightTitle(_ name: String) -> String { "\(name)'s highlight" }
    static func vibeTodayTitle(_ name: String) -> String { "\(name)'s vibe today" }
    static func voiceTitle(_ name: String) -> String { "\(name)'s voice" }
    static func essentialsTitle(_ name: String) -> String { "\(name)'s essentials" }
    static func videoTitle(_ name: String) -> String { "\(name)'s video" }
    static func everydayTitle(_ name: String) -> String { "\(name)'s everyday" }
    static func interestsTitle(_ name: String) -> String { "\(name)'s interests" }

    // Be sure (callout 22)
    static let beSure = "Be sure."
    static func beSureLineLead(_ name: String) -> String { "If \(name) becomes your match, you two have a " }
    static func beSureLineEmphasis(_ day: String) -> String { "confirmed, binding date \(day)" }
    static let beSureSub = "What happens after a match?"
    static let beSureRows: [(BrandIcon, String)] = [
        (.mapPin, "A safe, curated meeting spot — so you can focus on each other, not on logistics."),
        (.clock, "A time that fits both of your stated availability windows."),
        (.messageCircle, "Stuck for words? Match Mate has topics, ice breakers, and quick games."),
        (.sparkles, "Only 30 minutes committed. If there's no spark — leave with a smile."),
        (.shieldCheck, "You meet who you matched — the Show-up Rate keeps it real."),
    ]
    static let cancel = "Cancel"
    static let `continue` = "Continue"

    // Optional message (callout 23)
    static let messageTitleEmphasis = "Optional"
    static func messageTitleRest(_ name: String) -> String { " message to \(name)" }
    static let messageBody =
        "Premium users can see who sent them a like and can see your message. Adding a personal " +
        "message increases your chances of getting matched."
    static func messageDraftText(_ name: String, _ day: String) -> String {
        "Hi \(name), I love the vibe of your profile. Would be great to meet you \(day) :)"
    }
    static let skipMessage = "Skip message"
    static let sendMessage = "Send message"

    // Actions coach (F, callouts 24 and 25)
    static let coachTitleLead = "Three ways to "
    static let coachTitleEmphasis = "decide"
    static let coachLabel = "What the three buttons do"
    static let coachDontShow = "Don't show again"
    static let coachGotIt = "Got it"

    static let loadingLabel = "Loading profiles"
    static let loadFailedProposed = "Couldn't load profiles. Check your connection and try again."
    static let retry = "Retry"
    static let tryAgain = "Try again"
    static let noResultsProposed = "No results"
    static let widenFiltersProposed = "Widen my search"
}

/// Search's essentials row labels, in the ticket's order.
enum EssentialRow: CaseIterable, Sendable {
    case height, gender, orientation, datesIn, education, religion, politics

    var label: String {
        switch self {
        case .height: return "Height"
        case .gender: return "Gender"
        case .orientation: return "Orientation"
        case .datesIn: return "Dates in"
        case .education: return "Education"
        case .religion: return "Religion"
        case .politics: return "Politics"
        }
    }

    var icon: BrandIcon {
        switch self {
        case .height: return .sliders
        case .gender: return .user
        case .orientation: return .heart
        case .datesIn: return .globe
        case .education: return .bookOpen
        case .religion: return .faith
        case .politics: return .vote
        }
    }

    /// The kit's own marks draw at 16 / 2; Search's section marks at 16 / 1.8 (`SearchIcon`).
    var isKit: Bool { self == .height || self == .gender || self == .orientation }
}

/// The action bar's three tones (`ActionBtn`): danger outline, muted outline, sunset.
enum ActionTone: Sendable { case danger, muted, primary }

/// One coach row: the button it explains, in that button's icon and tone.
enum CoachRow: CaseIterable, Sendable {
    case notInterested, notNow, like

    var title: String {
        switch self {
        case .notInterested: return SearchCopy.notInterested
        case .notNow: return SearchCopy.notNow
        case .like: return SearchCopy.like
        }
    }

    var body: String {
        switch self {
        case .notInterested: return "You won't see this profile again."
        case .notNow: return "This profile can come back at your next Check-In."
        case .like: return "You'd like to meet. If they like you back, you have a date."
        }
    }

    var icon: BrandIcon {
        switch self {
        case .notInterested: return .close
        case .notNow: return .clock
        case .like: return .heart
        }
    }

    var tone: ActionTone {
        switch self {
        case .notInterested: return .danger
        case .notNow: return .muted
        case .like: return .primary
        }
    }
}

/// "The message is max 100 characters" -- counted as a person counts them, not in code units.
let likeMessageMax = 100

/// At most three shared vibes in the row, then `+N` (callout 10).
let sharedVibesShown = 3

// MARK: - the hero

/// `< 1 km` or `~N km` -- `searchDistance` in the reference. The server sends whole kilometres and
/// 0 for "under one", so the exact distance never reaches the phone.
func searchDistance(_ km: Int) -> String { km < 1 ? "< 1 km" : "~\(km) km" }

/// The distance pill's runs: the distance, `·`, the district -- or the distance alone when no
/// district was named. Separate because the signed-off sheet renders each text node of the reference's
/// `{distance} · {district}` as its own flex item: 6 either side of the dot, not a word space.
func distanceParts(_ km: Int, _ district: String?) -> [String] {
    let named = district?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    return named.isEmpty ? [searchDistance(km)] : [searchDistance(km), "·", named]
}

/// `< 1 km · {district}`: the pill as one line, which is what VoiceOver reads.
func distanceLine(_ km: Int, _ district: String?) -> String { distanceParts(km, district).joined(separator: " ") }

/// `filters_active` on `search_viewed`: the §10 filters set away from Check-In 05's defaults, in §10
/// order. Gender is compared with the current profile's client-computed default; only field IDs,
/// never gender values, are returned. Dealbreaker IDs are included only for true flags.
func filtersActive(_ filters: CheckInFilters, defaultGenders: Set<String>? = nil,
                   dealbreakers: [String: Bool] = [:]) -> [String] {
    var out: [String] = []
    if let defaultGenders, filters.genders != defaultGenders { out.append("meet_gender") }
    if filters.minAge > ageMin || filters.maxAge < ageMax { out.append("age_range") }
    if filters.distanceKm != distanceDefault { out.append("distance_km") }
    out.append(contentsOf: dealbreakers.filter { $0.value }.keys.sorted().filter { !out.contains($0) })
    return out
}

/// `{name}, {age}`, or `{name}` when the age is hidden -- never a dangling comma.
func nameLine(_ name: String, _ age: Int?) -> String {
    if let age { return "\(name), \(age)" }
    return name
}

// MARK: - the meet card

/// `19:00`: the meet hour on the phone's clock, 24-hour as drawn.
func meetTime(_ meetAt: Date, zone: TimeZone) -> String {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = zone
    let c = calendar.dateComponents([.hour, .minute], from: meetAt)
    return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
}

/// `{HH:MM} {day}` -- `19:00 today`. The day is the server's, by the Check-In's 7am day.
func meetLine(_ meetAt: Date, day: String, zone: TimeZone) -> String { "\(meetTime(meetAt, zone: zone)) \(day)" }

// MARK: - the overlap

/// The vibes both want today, in the PROFILE's order. An empty side shares nothing.
func sharedVibes(_ theirs: [String], mine: Set<String>) -> [String] { theirs.filter { mine.contains($0) } }

/// The shared vibes row: at most three, and `+N` for the rest. Empty when nothing is shared.
struct SharedVibesRow: Equatable, Sendable {
    let shown: [String]
    let more: Int
}

func sharedVibesRow(_ shared: [String]) -> SharedVibesRow {
    SharedVibesRow(shown: Array(shared.prefix(sharedVibesShown)), more: max(0, shared.count - sharedVibesShown))
}

/// The interests both have. A viewer with none marks nothing.
func sharedInterests(_ theirs: [String], mine: Set<String>) -> Set<String> { Set(theirs.filter { mine.contains($0) }) }

/// `{n} things {name} is into`, plus ` · {k} in common` only when k is at least 1.
func interestsSub(_ name: String, total: Int, common: Int) -> String {
    "\(total) things \(name) is into" + (common >= 1 ? " · \(common) in common" : "")
}

/// §31's label for a vibe id, from the Check-In's own table. Unknown ids (a newer server) are nil.
func vibeLabel(_ id: String) -> String? {
    for group in VibeGroup.allCases {
        if let v = group.vibes.first(where: { $0.id == id }) { return v.label }
    }
    return nil
}

/// §3's label for an interest tag, from the profile's own table.
func interestLabel(_ tagId: String) -> String? {
    for section in InterestSection.allCases {
        if let o = section.options.first(where: { $0.value == tagId }) { return o.label }
    }
    return nil
}

/// The motivation pill: §30's title and icon, or nil for a value this app does not know.
func motivationOf(_ id: String?) -> MotivationOption? { MotivationOption.of(id) }

// MARK: - media

// The take's length as its owner saw it on the media step (`formatTakeLength`, truncated): a second
// formatter here could show the same recording as two different lengths.
func voiceSub(_ durationMs: Int) -> String { "Voice prompt · \(formatTakeLength(durationMs))" }

func videoSub(_ durationMs: Int) -> String { "Video prompt · \(formatTakeLength(durationMs))" }

/// The voice card's 48 bars, as heights from 0 to 1 -- the reference's deterministic waveform: a sine
/// envelope with a fixed jitter, never below 0.12. A picture of a voice, not a reading of one.
let voiceBars: [Double] = (0..<48).map { i in
    let env = sin(Double(i) / 47.0 * Double.pi)
    let jitter = (sin(Double(i) * 12.9898) * 43758.5453).truncatingRemainder(dividingBy: 1.0)
    return max(0.12, env * 0.85 + abs(jitter) * 0.25)
}

/// At rest the reference paints the first 18 of the 48 bars violet (`i < 18`); the card is drawn so.
let voiceRestVioletBars = 18

/// How many bars, from the left, are violet. At rest: the reference's 18, as the spec sheet draws the
/// card. While playing -- a state the sheet does not draw -- the violet follows the playhead.
func voiceVioletBars(playing: Bool, progress: Double) -> Int {
    playing ? Int(min(1, max(0, progress)) * Double(voiceBars.count)) : voiceRestVioletBars
}

// MARK: - Essentials and How I live

/// One Essentials row: what is drawn.
struct Essential: Equatable, Sendable {
    let row: EssentialRow
    let value: String
}

/// The answers the payload carries for Essentials; a missing one is hidden or unanswered.
struct EssentialAnswers: Equatable, Sendable {
    var heightCm: Int? = nil
    var gender: String? = nil
    var orientation: String? = nil
    var datingLanguages: [String] = []
    var education: String? = nil
    var religion: String? = nil
    var politics: String? = nil
}

private func label(in step: DetailStep, _ value: String?) -> String? {
    guard let value else { return nil }
    return step.options.first(where: { $0.value == value })?.label
}

/// The Essentials rows, in the ticket's order. A row with no answer -- hidden, skipped, or a value
/// this app does not know -- is not drawn: never an empty row, never a raw id.
func essentials(_ a: EssentialAnswers) -> [Essential] {
    var out: [Essential] = []
    if let h = a.heightCm { out.append(Essential(row: .height, value: "\(h) cm")) }
    if let v = label(in: .gender, a.gender) { out.append(Essential(row: .gender, value: v)) }
    if let v = label(in: .orientation, a.orientation) { out.append(Essential(row: .orientation, value: v)) }
    let languages = a.datingLanguages.compactMap { label(in: .datingLanguage, $0) }
    if !languages.isEmpty { out.append(Essential(row: .datesIn, value: languages.joined(separator: ", "))) }
    if let v = label(in: .education, a.education) { out.append(Essential(row: .education, value: v)) }
    if let v = label(in: .religion, a.religion) { out.append(Essential(row: .religion, value: v)) }
    if let v = label(in: .politics, a.politics) { out.append(Essential(row: .politics, value: v)) }
    return out
}

/// One How I live tile: the group and the answer's label.
struct LifestyleTile: Equatable, Sendable {
    let field: LifestyleField
    let value: String
}

/// The answered How I live groups, in the profile's group order. Unanswered groups are not drawn.
func lifestyleTiles(_ answers: [String: String]) -> [LifestyleTile] {
    LifestyleField.allCases.compactMap { f in
        guard let v = answers[f.fieldId], let label = f.options.first(where: { $0.value == v })?.label else { return nil }
        return LifestyleTile(field: f, value: label)
    }
}

/// The tile icons (`HOW_I_LIVE_ICONS`): zap, leaf, wine, cigarette, clock, users.
func lifestyleIcon(_ field: LifestyleField) -> BrandIcon {
    switch field {
    case .activity: return .zap
    case .diet: return .leaf
    case .alcohol: return .wine
    case .smoking: return .cigarette
    case .rhythm: return .clock
    case .social: return .users
    }
}

// MARK: - the like flow

/// How many characters a person sees: a Swift `Character` is a grapheme cluster, so an emoji family
/// is one, not seven -- the count Android takes from `BreakIterator`.
func graphemes(_ text: String) -> Int { text.count }

/// "Input past 100 is not accepted, and a paste is cut at 100." Cut on a grapheme boundary, so a
/// flag or a family is never split in half.
func limitMessage(_ text: String) -> String { text.count <= likeMessageMax ? text : String(text.prefix(likeMessageMax)) }

/// `{n} / 100`.
func messageCounter(_ text: String) -> String { "\(graphemes(text)) / \(likeMessageMax)" }

/// The pre-filled draft, cut to the limit so a long name never makes it start over 100.
func messageDraft(_ name: String, _ day: String) -> String { limitMessage(SearchCopy.messageDraftText(name, day)) }

/// What `Send message` sends: the trimmed text, or none for empty or whitespace only.
func messageToSend(_ text: String) -> String? {
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? nil : trimmed
}

// MARK: - the actions coach

/// Whether the coach shows over this profile: once per Search visit, over the first profile with a
/// `Like` button, until `Don't show again` set the server's flag. An empty queue shows no coach.
func shouldShowCoach(hiddenForever: Bool, shownThisVisit: Bool, hasProfile: Bool, likedYou: Bool) -> Bool {
    !hiddenForever && !shownThisVisit && hasProfile && !likedYou
}
