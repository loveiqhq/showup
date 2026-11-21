// CancelCopy.swift
// ShowUp · Date 07's strings, exactly the ticket's (SHOWUP-198). Android's `CancelCopy.kt`.
//
// A template is a function of the match's first name, and the cost box a function of the rate config's drop
// (`ActiveDate.cancelPenaltyPoints`, 10 in MVP): the box names it from the value it is given and never carries a
// literal. The runs the reference draws as `<strong>` / `<em>` are split into `Run`s here so a screen styles them
// without re-typing a word; `plain` is the string with the marks removed, which the tests compare with the ticket.
// "her" stays (16 Nov). The apostrophes are the ticket's own and are not normalised.

import Foundation

/// How a run of copy is drawn. `danger` is the cost box's red `no-show`.
enum RunStyle: Equatable, Sendable { case plain, bold, italic, danger }

/// One styled stretch of a string.
struct Run: Equatable, Sendable {
    let text: String
    var style: RunStyle = .plain
}

private func plainRun(_ text: String) -> Run { Run(text: text) }
private func boldRun(_ text: String) -> Run { Run(text: text, style: .bold) }
private func italicRun(_ text: String) -> Run { Run(text: text, style: .italic) }

/// The string a list of runs reads as.
func plain(_ runs: [Run]) -> String { runs.map(\.text).joined() }

/// `(text, bold)` pairs for `PrettyText`, for copy whose only mark is `<strong>` (H's body and rows).
func boldPairs(_ runs: [Run]) -> [(String, Bool)] { runs.map { ($0.text, $0.style == .bold) } }

enum CancelCopy {
    // A
    static let link = "Cancel this date"

    // B: the portrait and the gut-punch
    static func counting(_ name: String) -> String { "\(name) is counting on you." }
    static let headlineLead = "Are you really ready to "
    static let headlineItalic = "disappoint her?"
    static let body: [Run] = [
        plainRun("This isn't a button — it's a real person who cleared her evening, picked something to wear, and is quietly looking forward to meeting "),
        italicRun("you"),
        plainRun(". Cancelling lands on her side of the screen."),
    ]

    /// The three reasons, in order.
    static let reasons: [[Run]] = [
        [plainRun("Almost every date leaves you "), boldRun("glad you went"), plainRun(". The hard part is only the front door.")],
        [plainRun("She chose "), italicRun("you"), plainRun(" out of everyone. That's rare — showing up honours it.")],
        [plainRun("You'll go to sleep "), boldRun("proud you stayed real"), plainRun(" and kept your word.")],
    ]

    // The solution box
    static func solutionTitle(_ name: String) -> String { "Sometimes life happens — find a solution with \(name)!" }
    static func solutionBody(_ name: String) -> String {
        "Would a later time or another place work? Request it — if \(name) agrees, your date goes ahead."
    }
    static let changeTime = "Change meet time"
    static let changePlace = "Change location"
    static func message(_ name: String) -> String { "Message \(name) instead" }

    /// D: `{value}` bold, then the line.
    static func sentTail(_ name: String) -> String { " requested. Waiting for \(name) to agree — until then your date stays as planned." }

    // The cost box
    static func cost(_ drop: Int) -> [Run] {
        [
            plainRun("Cancelling this date counts as a "),
            Run(text: "no-show", style: .danger),
            plainRun(" and lowers your "),
            boldRun("Show-up Rate by \(drop)"),
            plainRun(" — a lower rate means fewer likes and matches. Let it stay low and you'll eventually lose access to the app entirely."),
        ]
    }

    // The choices
    static let keep = "Don't cancel my date"
    static let confirm = "Cancel anyway and let her down"
    static func warning(_ name: String) -> String { "This can't be undone — your date is off and you'll never match \(name) again." }

    // E
    static let cancelFailed = "Couldn't cancel the date. Check your connection and try again."

    // H: what the match sees
    static func hHeadlineLead(_ name: String) -> String { "\(name) had to " }
    static let hHeadlineItalic = "cancel"
    static let hHeadlineRest = " — we're truly sorry."
    static let hBody: [Run] = [
        plainRun("You made room in your day and you were ready to show up. "),
        boldRun("Don't be discouraged - someone true as you is just a like away from meeting you."),
    ]
    static func hRows(_ name: String) -> [[Run]] {
        [
            [plainRun("Your "), boldRun("Show-up Rate stays untouched."), plainRun(" A date someone else cancels will "), boldRun("never"),
             plainRun(" count against you. You did everything right.")],
            [plainRun("Show Up is built on "), boldRun("reliability"), plainRun(" and "), boldRun("treasuring each other's time"),
             plainRun(" — real, warm, in-person connection. "), boldRun("This isn't how our community behaves.")],
            [plainRun("People who "), boldRun("repeatedly cancel or don't show up"), plainRun(" become less visible, and eventually "),
             boldRun("lose access."), plainRun(" \(name) carries that — not you.")],
        ]
    }
    static let hCta = "Find someone who shows up"
}
