// PartnerEndedCopy.swift
// ShowUp · Date 08's strings, exactly the ticket's `## Copy` (SHOWUP-199). Android's `PartnerEndedCopy.kt`.
//
// A template is a function of A's (the match's) first name. The apostrophes are the ticket's own, straight, and are not
// normalised. The runs the reference draws as `<strong>` are split into `Run`s so the screen styles them without
// re-typing a word; the `<em>` of each headline is its own constant, the run `WashHeadline` washes. The literals sit in
// the ticket's order, so a verifier reading them in file order reads the ticket's sentences.

import Foundation

private func partnerPlain(_ text: String) -> Run { Run(text: text) }
private func partnerBold(_ text: String) -> Run { Run(text: text, style: .bold) }

enum PartnerEndedCopy {
    // A · You didn't show up
    static func aHeadlineLead(_ name: String) -> String { "\(name) ended the date, stating you " }
    static let aHeadlineItalic = "didn't show up."

    /// Two lines: the reference's `<br/>` falls after "waiting for you.".
    static func aSub(_ name: String) -> [Run] {
        [partnerPlain("\(name) "), partnerBold("ended the date"), partnerPlain(" after waiting for you.\nIt's now closed.")]
    }

    static let aRows: [[Run]] = [
        [partnerBold("Not showing up"), partnerPlain(" to a date — or being "), partnerBold("very late"),
         partnerPlain(" without reaching out to your date — shows "), partnerBold("no respect for each other's time.")],
        [partnerPlain("To clear this up, "), partnerBold("proceed to the review"), partnerPlain(" and "),
         partnerBold("give us your side.")],
        [partnerPlain("Once you've completed the review, we run an "), partnerBold("automated check"),
         partnerPlain(": did you both check in, and did you message each other? Your "), partnerBold("Show-up Rate only drops"),
         partnerPlain(" if the no-show is "), partnerBold("proven.")],
    ]

    // B · Uncomfortable
    static func bHeadlineLead(_ name: String) -> String { "\(name) ended the date, saying they " }
    static let bHeadlineItalic = "didn't feel comfortable"
    static let bHeadlineRest = " in the situation."
    static let bSub = "The date is now closed."

    static let bRows: [[Run]] = [
        [partnerBold("Respect is non-negotiable"), partnerPlain(" at Show Up. Behaviour that leaves someone feeling "),
         partnerBold("uncomfortable"), partnerPlain(" is not accepted here.")],
        [partnerPlain("So far we only have "), partnerBold("one side of the story."), partnerPlain(" In the next step, tell us "),
         partnerBold("what happened from your point of view.")],
        [partnerPlain("We "), partnerBold("review every report"), partnerPlain(" and "), partnerBold("act on it where it's needed.")],
    ]

    // C · We met
    static func cHeadlineLead(_ name: String) -> String { "\(name) ended the date, stating you both have " }
    static let cHeadlineItalic = "met."
    static let cSub = "Thank you for being real and showing up to your date."

    static let cRows: [[Run]] = [
        [partnerPlain("We hope it was a good one. "), partnerBold("Head into the review"),
         partnerPlain(" and tell us how it went — it takes a minute.")],
        [partnerPlain("Once the review is done, you're free to "), partnerBold("search for your next date.")],
    ]

    // CTA (all three)
    static let cta = "Proceed to review"
}
