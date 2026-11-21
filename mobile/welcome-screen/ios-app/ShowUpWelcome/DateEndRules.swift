// DateEndRules.swift
// ShowUp · Date 06's plain rules (SHOWUP-197, R9, R11). Android's `DateEndRules.kt`.
//
// `minutesVsStart` is the arrival gate's (`ArrivalRules.swift`): the same whole minutes from the agreed meet time,
// negative before, so the end events and the gate's agree and the server's `date_ended` matches.

import Foundation

/// Which sheet is up: B (and its steps) from the End Date bar or the link, or H (and its steps) from the Report card.
enum EndRoute: String, CaseIterable, Codable, Sendable { case wrap, report }

/// The step inside the sheet: the menu (B or H), C, the guide (E or F), G, or I.
enum EndStep: String, CaseIterable, Codable, Sendable { case menu, met, guide, noShow, uncomfortable }

/// What a menu row picks. `We met` exists on B only, `I don't feel comfortable` on H only.
enum EndOption: String, CaseIterable, Sendable { case met, noShow, uncomfortable }

/// R11: the no-show end unlocks at the AGREED meet time, to the second.
func pastMeet(_ meetAt: Date, _ now: Date) -> Bool { now >= meetAt }

/// Where the picked option leads: with no check-in needed, or once the gate has stored one (R9).
func endStepAfterGate(_ option: EndOption) -> EndStep {
    switch option {
    case .met: return .met
    case .noShow: return .guide
    case .uncomfortable: return .uncomfortable
    }
}

/// `We met` needs no check-in (both confirming in the review counts); the two problem routes do.
func optionNeedsCheckIn(_ option: EndOption) -> Bool { option != .met }

/// What the scrim, back and a swipe-down do on `step`. The reference draws C inside `ModalShell onCancel={onClose}`:
/// a dismiss there CLOSES the sheet, and only C's own `Cancel` button goes back to B. E / F, G and I are
/// `onCancel={onBack}`: one step up.
func scrimClosesSheet(_ step: EndStep) -> Bool { step == .menu || step == .met }

/// Date 06 lands once per ended date: the host's guard, so a read that sees the end before navigation cannot route
/// twice. A value, held in the host's `@State`.
struct LandedOnce: Equatable, Sendable {
    private var last: String?

    /// True the first time `dateId` is seen, false for every later call with it.
    mutating func first(_ dateId: String) -> Bool {
        if last == dateId { return false }
        last = dateId
        return true
    }
}

/// The reason the CTA of `step` ends the date with; nil where no CTA ends it.
func endReasonFor(_ step: EndStep) -> EndReason? {
    switch step {
    case .met: return .met
    case .noShow: return .partnerNoShow
    case .uncomfortable: return .uncomfortable
    case .menu, .guide: return nil
    }
}

/// The guide's end button: its label, whether it answers a tap, and whether it leads with the clock icon.
struct GuideEnd: Equatable, Sendable {
    let label: String
    let enabled: Bool
    let clock: Bool
}

func guideEnd(pastMeet: Bool, meetTime: String) -> GuideEnd {
    pastMeet
        ? GuideEnd(label: DateEndCopy.endOpen, enabled: true, clock: false)
        : GuideEnd(label: DateEndCopy.endLocked(meetTime), enabled: false, clock: true)
}

/// `Message {name}` is hidden while the chat is locked; the end button moves up.
func messageShown(chatOpen: Bool) -> Bool { chatOpen }

/// The entry the gate reports: `wrapup` from B's route, `report` from H's.
func gateEntryFor(_ route: EndRoute) -> ArrivalEntry {
    switch route {
    case .wrap: return .wrapup
    case .report: return .report
    }
}
