//
//  ProfileLife.swift
//  ShowUp · Life and Future (Profiles 22 and 23, SHOWUP-175 / 176) — the six questions and the one
//  rule they share
//
//  The Swift twin of `profile/ProfileLife.kt`. ONE TOGGLE, TWO MAPS: both screens are three
//  multi-select questions, and what differs is which answers stand alone — `No children` on Life;
//  all three family answers and `I want to find out together` on Future. `toggleLifePick` is the one
//  function both use ("no copy, no fork").
//
//  MATCHING ONLY: never on a profile, never returned to anyone but their owner. Partnership and
//  partnership vision are Class 2.
//

import Foundation
import ShowUpAPI

/// One §4 question. Mirrors `LifeQuestion` in Kotlin case for case, in the same order.
enum LifeQuestion: String, CaseIterable, Sendable {
    // Life (Profile 22): Partnership · Family · Living.
    case partnership, family, living
    // Future (Profile 23): Family · Partnership · Living — family FIRST there.
    case familyAhead = "family_ahead"
    case partnershipAhead = "partnership_ahead"
    case livingAhead = "living_ahead"

    /// §4 `question_id`.
    var questionId: String { rawValue }

    var label: String {
        switch self {
        case .partnership: return "Partnership situation"
        case .family: return "Family situation"
        case .living: return "Living situation"
        case .familyAhead: return "Family vision"
        case .partnershipAhead: return "Partnership vision"
        case .livingAhead: return "Living style vision"
        }
    }

    var icon: BrandIcon {
        switch self {
        case .partnership, .partnershipAhead: return .heart
        case .family, .familyAhead: return .users
        case .living: return .home
        case .livingAhead: return .compass
        }
    }

    var sensitivityClass: Int {
        switch self {
        case .partnership, .partnershipAhead: return 2
        case .family, .living, .familyAhead, .livingAhead: return 1
        }
    }

    var options: [DetailOption] {
        switch self {
        case .partnership:
            typealias V = Components.Schemas.LifePartnership
            return [
                .init(value: V.single.rawValue, label: "Single"),
                .init(value: V.open_relationship.rawValue, label: "Open relationship"),
                .init(value: V.ethically_non_monogamous.rawValue, label: "Ethically non-monogamous"),
                .init(value: V.divorced.rawValue, label: "Divorced"),
            ]
        case .family:
            typealias V = Components.Schemas.LifeFamily
            return [
                .init(value: V.no_children.rawValue, label: "No children"),
                .init(value: V.has_children.rawValue, label: "I have children"),
                .init(value: V.single_parent.rawValue, label: "Single parent"),
                .init(value: V.co_parenting.rawValue, label: "Co-parenting"),
                .init(value: V.empty_nester.rawValue, label: "Empty nester"),
            ]
        case .living:
            typealias V = Components.Schemas.LifeLiving
            return [
                .init(value: V.living_alone.rawValue, label: "Living alone"),
                .init(value: V.living_with_roommates.rawValue, label: "Living with roommates"),
                .init(value: V.living_with_family.rawValue, label: "Living with family"),
                .init(value: V.living_with_children.rawValue, label: "Living with children"),
            ]
        case .familyAhead:
            typealias V = Components.Schemas.FutureFamily
            return [
                .init(value: V.want_kids.rawValue, label: "I want kids"),
                .init(value: V.dont_want_kids.rawValue, label: "I don't want kids"),
                .init(value: V.find_out_together.rawValue, label: "I want to find out together"),
            ]
        case .partnershipAhead:
            typealias V = Components.Schemas.FuturePartnership
            return [
                .init(value: V.monogamous_relationship.rawValue, label: "Monogamous relationship"),
                .init(value: V.open_relationship.rawValue, label: "Open relationship"),
                .init(value: V.ethically_non_monogamous.rawValue, label: "Ethically non-monogamous"),
                .init(value: V.find_out_together.rawValue, label: "I want to find out together"),
            ]
        case .livingAhead:
            typealias V = Components.Schemas.FutureLiving
            return [
                .init(value: V.stay_local.rawValue, label: "Stay local"),
                .init(value: V.expat_life.rawValue, label: "Expat life"),
                .init(value: V.move_abroad.rawValue, label: "Move abroad"),
                .init(value: V.find_out_together.rawValue, label: "I want to find out together"),
            ]
        }
    }

    /// The values that cannot share an answer with anything else (§4 `exclusive`).
    var exclusive: Set<String> {
        switch self {
        case .partnership, .living: return []
        case .family: return [Components.Schemas.LifeFamily.no_children.rawValue]
        // ALL THREE: wanting and not wanting kids cannot both hold, and either replaces "find out
        // together". Single-select in effect — but a tap on the picked one clears it.
        case .familyAhead: return Set(options.map(\.value))
        case .partnershipAhead: return [Components.Schemas.FuturePartnership.find_out_together.rawValue]
        case .livingAhead: return [Components.Schemas.FutureLiving.find_out_together.rawValue]
        }
    }
}

extension PickStep {
    /// The questions a step asks, in screen order. Interests asks none of these.
    var questions: [LifeQuestion] {
        switch self {
        case .interests: return []
        case .lifeNow: return [.partnership, .family, .living]
        case .lifeAhead: return [.familyAhead, .partnershipAhead, .livingAhead]
        }
    }
}

/// The answer to `question` after a tap on `tapped` — `toggleLifePick` in the reference. A picked
/// value unpicks; an exclusive value replaces the answer; any other is added and drops the exclusive
/// ones. No other question is touched.
func toggleLifePick(_ question: LifeQuestion, current: Set<String>, tapped: String) -> Set<String> {
    if current.contains(tapped) { return current.subtracting([tapped]) }
    if question.exclusive.contains(tapped) { return [tapped] }
    return current.subtracting(question.exclusive).union([tapped])
}

/// The picked values in LIST order: what is saved, and what `value_bucketed` joins.
func lifeInListOrder(_ question: LifeQuestion, _ picked: Set<String>) -> [String] {
    question.options.map(\.value).filter { picked.contains($0) }
}

/// `value_bucketed`: list order, comma-joined, no spaces — e.g. `single,divorced`.
func lifeBucketed(_ question: LifeQuestion, _ picked: Set<String>) -> String {
    lifeInListOrder(question, picked).joined(separator: ",")
}

/// Everything picked on Life and Future, by §4 `question_id`. ONE DRAFT FOR BOTH SCREENS: Future's
/// back shows Life's saved answers, and arriving on either resets only its own three.
struct LifeDraft: Equatable, Sendable, Codable {
    var answers: [String: Set<String>] = [:]

    func picked(_ question: LifeQuestion) -> Set<String> { answers[question.questionId] ?? [] }

    func with(_ question: LifeQuestion, _ picked: Set<String>) -> LifeDraft {
        var copy = self
        copy.answers[question.questionId] = picked
        return copy
    }

    /// Whether Continue on `step` saves anything: a pick in any of its questions.
    func anything(_ step: PickStep) -> Bool { step.questions.contains { !picked($0).isEmpty } }

    /// `question`'s picks in list order, or nil when it has none — "not supplied" on the wire.
    func answered(_ question: LifeQuestion) -> [String]? {
        let values = lifeInListOrder(question, picked(question))
        return values.isEmpty ? nil : values
    }
}

/// Whether `question`'s saved answer holds a value this build cannot draw — one a newer server
/// added. An untouched question holding one is not sent (`interestsHoldUnknown`'s rule).
func lifeHoldsUnknown(_ question: LifeQuestion, saved: SavedDetails) -> Bool {
    (saved.life[question.questionId] ?? []).contains { v in !question.options.contains { $0.value == v } }
}

/// `current` once the account's answers land LATE — after the user started on `step`. Each
/// question is one part: a touched one keeps the user's answer, every other one takes the saved
/// answer (the Kotlin twin, `lifeOnLateRead`).
func lifeOnLateRead(_ step: PickStep, current: LifeDraft, saved: SavedDetails, touched: Set<String>) -> LifeDraft {
    let fresh = lifeOnArrival(step, current: current, saved: saved)
    return step.questions.reduce(current) { draft, question in
        touched.contains(question.questionId) ? draft : draft.with(question, fresh.picked(question))
    }
}

/// `current` with `step`'s questions reset from what the account holds — values this build knows,
/// read through `toggleLifePick` so an old set that breaks its own exclusive rule arrives as one the
/// screen could have produced.
func lifeOnArrival(_ step: PickStep, current: LifeDraft, saved: SavedDetails) -> LifeDraft {
    step.questions.reduce(current) { draft, question in
        var seen = Set<String>()
        let known = (saved.life[question.questionId] ?? [])
            .filter { v in question.options.contains { $0.value == v } && seen.insert(v).inserted }
        let picked = known.reduce(Set<String>()) { toggleLifePick(question, current: $0, tapped: $1) }
        return draft.with(question, picked)
    }
}
