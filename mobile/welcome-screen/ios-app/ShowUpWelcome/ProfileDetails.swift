//
//  ProfileDetails.swift
//  ShowUp · "Share some details" — the steps, their answers, and every rule that is not a pixel
//  (SHOWUP-167 to SHOWUP-173)
//
//  The Swift twin of `profile/ProfileDetails.kt`. Read that file's header for the argument: one
//  table for seven screens, so a screen file holds pixels and nothing else, and every rule here is
//  unit-tested with no view (ProfileDetailsRulesTests).
//
//  WHAT IS STORED IS THE §1 VALUE, NEVER THE LABEL, and the value comes from the GENERATED contract
//  enum (`Components.Schemas.Gender.non_binary.rawValue`), so a value renamed on the server is a
//  compile error here rather than a 400 the user meets on Continue.
//

import Foundation
import ShowUpAPI

/// One row on a detail screen: the §1 value that is stored, and the label that is drawn.
struct DetailOption: Equatable, Sendable {
    let value: String
    let label: String
}

/// How many segments the group's bar has — `SHARE_STEPS_TOTAL`, "a prop of the shell, never typed
/// per screen". Ten, and still open: §2's indices end at 9. One number to change when it settles.
let shareStepsTotal = 10

/// A step of "Share some details". Mirrors `DetailStep` in Kotlin case for case.
enum DetailStep: String, CaseIterable, Sendable {
    case height
    case gender
    case orientation
    case datingLanguage = "dating_language"
    case education
    case religion
    case politics

    /// `enums.json` §2 `step_id` — and the §1 `field_id`, and the `hidden_fields` entry.
    var stepId: String { rawValue }
    var fieldId: String { rawValue }
    var hiddenField: String { rawValue }

    /// §2 `step_index`, which is also the filled segment of the bar.
    var stepIndex: Int { (Self.allCases.firstIndex(of: self) ?? 0) + 1 }

    var screen: ProfileScreen {
        switch self {
        case .height: return .height
        case .gender: return .gender
        case .orientation: return .orientation
        case .datingLanguage: return .datingLanguage
        case .education: return .education
        case .religion: return .religion
        case .politics: return .politics
        }
    }

    /// The saved flow position this step advances.
    var position: Components.Schemas.FlowPosition {
        switch self {
        case .height: return .height
        case .gender: return .gender
        case .orientation: return .orientation
        case .datingLanguage: return .dating_language
        case .education: return .education
        case .religion: return .religion
        case .politics: return .politics
        }
    }

    /// No SkipLink, so it cannot be passed without an answer (README rule 0). Decided 5 Oct 2026.
    var mandatory: Bool { self == .gender || self == .orientation }

    /// Tapping the selected row clears it — the named exception for the skippable single-selects.
    /// Applied in the tap handler (`pickSingle`), never a prop of `OptionRow`.
    var clearsOnReselect: Bool { self == .education || self == .religion || self == .politics }

    /// §1's class, stamped on this field's attribute events at emit time.
    var sensitivityClass: Int {
        switch self {
        case .height: return 0
        case .datingLanguage, .education: return 1
        case .gender, .orientation, .religion, .politics: return 2
        }
    }

    var multiSelect: Bool { self == .datingLanguage }

    var next: DetailStep? {
        guard let i = Self.allCases.firstIndex(of: self), i + 1 < Self.allCases.count else { return nil }
        return Self.allCases[i + 1]
    }

    var previous: DetailStep? {
        guard let i = Self.allCases.firstIndex(of: self), i > 0 else { return nil }
        return Self.allCases[i - 1]
    }

    /// The options, in the ticket's order — which is also §1's order. Keyed on the VALUE everywhere.
    var options: [DetailOption] {
        switch self {
        case .height:
            return []
        case .gender:
            return [
                .init(value: Components.Schemas.Gender.woman.rawValue, label: "Woman"),
                .init(value: Components.Schemas.Gender.man.rawValue, label: "Man"),
                .init(value: Components.Schemas.Gender.non_binary.rawValue, label: "Non-binary"),
                .init(value: Components.Schemas.Gender.other.rawValue, label: "Other"),
            ]
        case .orientation:
            // ALL SIX, ALWAYS, IN THIS ORDER — never filtered by the gender answer (Profile 16).
            return [
                .init(value: Components.Schemas.Orientation.straight.rawValue, label: "Straight"),
                .init(value: Components.Schemas.Orientation.gay.rawValue, label: "Gay"),
                .init(value: Components.Schemas.Orientation.lesbian.rawValue, label: "Lesbian"),
                .init(value: Components.Schemas.Orientation.bisexual.rawValue, label: "Bisexual"),
                .init(value: Components.Schemas.Orientation.pansexual.rawValue, label: "Pansexual"),
                .init(value: Components.Schemas.Orientation.other.rawValue, label: "Other"),
            ]
        case .datingLanguage:
            return [
                .init(value: Components.Schemas.DatingLanguage.german.rawValue, label: "German"),
                .init(value: Components.Schemas.DatingLanguage.english.rawValue, label: "English"),
                .init(value: Components.Schemas.DatingLanguage.spanish.rawValue, label: "Spanish"),
                .init(value: Components.Schemas.DatingLanguage.italian.rawValue, label: "Italian"),
                .init(value: Components.Schemas.DatingLanguage.french.rawValue, label: "French"),
                .init(value: Components.Schemas.DatingLanguage.turkish.rawValue, label: "Turkish"),
                .init(value: Components.Schemas.DatingLanguage.russian.rawValue, label: "Russian"),
                .init(value: Components.Schemas.DatingLanguage.arabic.rawValue, label: "Arabic"),
            ]
        case .education:
            return [
                .init(value: Components.Schemas.Education.a_levels_abitur.rawValue, label: "A-Levels / Abitur"),
                .init(value: Components.Schemas.Education.apprenticeship.rawValue, label: "Apprenticeship"),
                .init(value: Components.Schemas.Education.university_degree.rawValue, label: "University degree"),
                .init(value: Components.Schemas.Education.phd.rawValue, label: "PhD"),
            ]
        case .religion:
            // NINE: `Muslim` before `Jewish` (registry 1.4.14).
            return [
                .init(value: Components.Schemas.Religion.protestant.rawValue, label: "Protestant"),
                .init(value: Components.Schemas.Religion.catholic.rawValue, label: "Catholic"),
                .init(value: Components.Schemas.Religion.orthodox.rawValue, label: "Orthodox"),
                .init(value: Components.Schemas.Religion.muslim.rawValue, label: "Muslim"),
                .init(value: Components.Schemas.Religion.jewish.rawValue, label: "Jewish"),
                .init(value: Components.Schemas.Religion.buddhist.rawValue, label: "Buddhist"),
                .init(value: Components.Schemas.Religion.hindu.rawValue, label: "Hindu"),
                .init(value: Components.Schemas.Religion.atheist.rawValue, label: "Atheist"),
                .init(value: Components.Schemas.Religion.spiritual_other.rawValue, label: "Spiritual / other"),
            ]
        case .politics:
            // LINEAR: left -> right, then the three off-axis answers (registry 1.4.15).
            return [
                .init(value: Components.Schemas.Politics.left.rawValue, label: "Left"),
                .init(value: Components.Schemas.Politics.mid_left.rawValue, label: "Mid-left"),
                .init(value: Components.Schemas.Politics.middle.rawValue, label: "Middle"),
                .init(value: Components.Schemas.Politics.mid_right.rawValue, label: "Mid-right"),
                .init(value: Components.Schemas.Politics.right.rawValue, label: "Right"),
                .init(value: Components.Schemas.Politics.conservative.rawValue, label: "Conservative"),
                .init(value: Components.Schemas.Politics.libertarian.rawValue, label: "Libertarian"),
                .init(value: Components.Schemas.Politics.apolitical.rawValue, label: "Apolitical"),
            ]
        }
    }
}

// MARK: - height

/// The screen's own bounds (Profile 14), and the server's CHECK.
let heightCmMin = 120
let heightCmMax = 230

/// Digits only, at most three — typing and paste alike.
func filterHeightInput(_ raw: String) -> String {
    String(raw.filter { $0.isASCII && $0.isNumber }.prefix(3))
}

/// The height in cm when the text is a valid answer, else nil. Whole numbers 120-230 inclusive.
func parseHeight(_ text: String) -> Int? {
    guard let value = Int(text), (heightCmMin...heightCmMax).contains(value) else { return nil }
    return value
}

/// Why Continue was refused, as §8's `rule`, or nil. Empty is `required_missing`, else `impossible`.
func heightRefusal(_ text: String) -> String? {
    if text.isEmpty { return ValidationRule.requiredMissing }
    return parseHeight(text) == nil ? ValidationRule.impossible : nil
}

/// §1's height bucket, computed ON DEVICE — "the exact cm never goes to analytics".
func heightBucket(_ cm: Int) -> String {
    if cm < 150 { return "<150" }
    if cm < 160 { return "150_159" }
    if cm < 190 {
        let low = cm - cm % 5
        return "\(low)_\(low + 4)"
    }
    if cm < 200 { return "190_199" }
    return "200+"
}

// MARK: - the lists

/// The selection after a tap: a different row moves it; the same row keeps it on gender and
/// orientation and clears it on the skippable three.
func pickSingle(_ step: DetailStep, current: String?, tapped: String) -> String? {
    current == tapped && step.clearsOnReselect ? nil : tapped
}

/// A ticked row unticks, any other ticks. No limit.
func toggleMulti(_ current: Set<String>, tapped: String) -> Set<String> {
    var next = current
    if next.contains(tapped) { next.remove(tapped) } else { next.insert(tapped) }
    return next
}

/// The ticked languages in LIST order, never tap order.
func inListOrder(_ step: DetailStep, _ ticked: Set<String>) -> [String] {
    step.options.map(\.value).filter { ticked.contains($0) }
}

/// `value_bucketed` for dating language: list order, comma-joined, no spaces (registry 1.4.12).
func languagesBucketed(_ values: [String]) -> String { values.joined(separator: ",") }

// MARK: - what a press does

/// What the footer's Continue resolves to, before anything is sent.
enum DetailContinue: Equatable {
    /// Refused: show the toast and report the rule. Height, gender and orientation only.
    case refused(rule: String)
    /// "Continue with nothing selected is a Skip" — the four skippable lists.
    case skip
    /// Save this answer and advance.
    case answer
}

/// Everything the user has typed, picked or ticked across the group, plus the visibility boxes.
struct DetailDraft: Equatable, Sendable, Codable {
    var heightText = ""
    var gender: String?
    var orientation: String?
    var languages: Set<String> = []
    var education: String?
    var religion: String?
    var politics: String?
    /// `hidden_fields` entries ticked on the band. Unchecked by default on every step.
    var hidden: Set<String> = []

    func selection(_ step: DetailStep) -> String? {
        switch step {
        case .gender: return gender
        case .orientation: return orientation
        case .education: return education
        case .religion: return religion
        case .politics: return politics
        case .height, .datingLanguage: return nil
        }
    }

    func withSelection(_ step: DetailStep, _ value: String?) -> DetailDraft {
        var copy = self
        switch step {
        case .gender: copy.gender = value
        case .orientation: copy.orientation = value
        case .education: copy.education = value
        case .religion: copy.religion = value
        case .politics: copy.politics = value
        case .height, .datingLanguage: break
        }
        return copy
    }

    func isHidden(_ step: DetailStep) -> Bool { hidden.contains(step.hiddenField) }
}

/// The one decision behind every footer in the group — see the Kotlin twin for the table.
func resolveContinue(_ step: DetailStep, _ draft: DetailDraft) -> DetailContinue {
    if step == .height {
        if let rule = heightRefusal(draft.heightText) { return .refused(rule: rule) }
        return .answer
    }
    if step.multiSelect { return draft.languages.isEmpty ? .skip : .answer }
    if draft.selection(step) != nil { return .answer }
    return step.mandatory ? .refused(rule: ValidationRule.requiredMissing) : .skip
}

/// What the account already holds, from `GET /me/profile`. Strings, read tolerantly.
struct SavedDetails: Equatable, Sendable {
    var heightCm: Int?
    var gender: String?
    var orientation: String?
    var datingLanguages: [String] = []
    var education: String?
    var religion: String?
    var politics: String?
    var hiddenFields: Set<String> = []
}

/// The draft a screen opens with: the saved value and visibility for THAT step only.
func draftOnArrival(_ step: DetailStep, current: DetailDraft, saved: SavedDetails) -> DetailDraft {
    var reset = current
    if saved.hiddenFields.contains(step.hiddenField) {
        reset.hidden.insert(step.hiddenField)
    } else {
        reset.hidden.remove(step.hiddenField)
    }
    func known(_ value: String?) -> String? {
        guard let value, step.options.contains(where: { $0.value == value }) else { return nil }
        return value
    }
    switch step {
    case .height: reset.heightText = saved.heightCm.map(String.init) ?? ""
    case .datingLanguage:
        reset.languages = Set(saved.datingLanguages.filter { v in step.options.contains { $0.value == v } })
    case .gender: reset.gender = known(saved.gender)
    case .orientation: reset.orientation = known(saved.orientation)
    case .education: reset.education = known(saved.education)
    case .religion: reset.religion = known(saved.religion)
    case .politics: reset.politics = known(saved.politics)
    }
    return reset
}

/// Every step's part of `current` reset from `saved` — except `onScreen`, whose part is the user's
/// until they leave it. What keeps a returning step from drawing its old pick on the first frame;
/// see the Kotlin twin.
func draftFromSaved(_ current: DetailDraft, saved: SavedDetails, onScreen: DetailStep?) -> DetailDraft {
    DetailStep.allCases.filter { $0 != onScreen }.reduce(current) { draft, step in
        draftOnArrival(step, current: draft, saved: saved)
    }
}

/// The `hidden_fields` set to send with an accepted Continue: the account's set with this step's
/// entry added or removed, and nothing else touched — the server REPLACES what it is given.
func hiddenFieldsToSend(_ step: DetailStep, saved: Set<String>, hideThis: Bool) -> [String] {
    var next = saved
    if hideThis { next.insert(step.hiddenField) } else { next.remove(step.hiddenField) }
    return next.sorted()
}
