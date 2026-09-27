//
//  ProfileReachability.swift
//  ShowUpWelcome · Profile creation 10 — Stay reachable, MVP scope (SHOWUP-163)
//
//  ───────────────────────────────────────────────────────────────────────────
//  BUILT FROM `scope="mvp"`, AND NOTHING FROM `scope="full"`
//  ───────────────────────────────────────────────────────────────────────────
//
//  The reference file carries the later scope alongside the MVP behind a `scope` prop, and the
//  ticket's first acceptance criterion is that none of it ships: no concierge-call card, no
//  calendar card, no phone field, no email row, no WhatsApp/SMS toggles, no "Skip for now". Those
//  are absences, so nothing in this file can show them missing — `verify-profile.py` asserts each
//  one by name instead.
//
//  ───────────────────────────────────────────────────────────────────────────
//  THE BODY SCROLLS, THE CTA NEVER DOES
//  ───────────────────────────────────────────────────────────────────────────
//
//  The first screen in profile creation that is ALLOWED to scroll. 09 must not — it is one screen
//  of content and a scroll there means it did not fit — but this one has a card that Dynamic Type
//  can double, and the ticket says so outright: "The body scrolls; the CTA never does. The footer
//  is pinned, with its fade from transparent to `--liq-bg`. The scroll content needs bottom
//  padding at least equal to the footer, so the fine print can scroll clear of the CTA."
//
//  That last clause is `footerClearance`, and it is reserved rather than hoped for.
//
//  ONE DELIBERATE DIFFERENCE FROM THE REFERENCE, recorded as E31. The reference puts the headline
//  block at `flex: none` so only the card scrolls beneath it. Here the headline scrolls with the
//  body. At the reference's own 390 × 844 with default type the two are identical — nothing
//  scrolls at all — and they diverge only where the reference was never drawn: at 2.0x type on a
//  320-wide frame a pinned headline takes most of the screen and leaves the card a sliver. The
//  ticket's own warning is the argument for it: "Dynamic Type can double the card height."
//
//  ───────────────────────────────────────────────────────────────────────────
//  WHAT IS NOT OURS
//  ───────────────────────────────────────────────────────────────────────────
//
//  The OS notification dialog and the Settings app. We decide WHEN the dialog appears; its copy,
//  buttons and look belong to the system. iOS offers no custom explanation text for notifications
//  — this screen IS the explanation. Never draw it, restyle it or build a look-alike.
//

import SwiftUI

/// The CTA, for the fit harness. Found by identifier rather than by its label.
let reachCtaId = "reachability-cta"

/// The scrolling body's last element, so the harness can prove the fine print clears the CTA.
let reachFinePrintId = "reachability-fineprint"

/// The save-failure card, which exists on no other path and so cannot be found by its copy.
let reachErrorId = "reachability-save-failed"

/// How much room the pinned footer needs below the scrolling body.
///
/// 56 button + 10 top + 6 bottom from the reference's footer padding, plus the fade. The
/// acceptance criterion is that "the fine print scrolls fully clear of the CTA", and the only way
/// that is true at every Dynamic Type size is for the body to reserve the footer's height rather
/// than a number that looked right at 390.
private let footerClearance: CGFloat = 80

/// Every string on the screen, verbatim from the ticket. Typographic apostrophes throughout.
enum ReachCopy {
    static let headlineEm = "Never miss"
    static let headlineTail = " a date and avoid getting a penalty!"

    /// The lead, with the reference file's own bold runs.
    ///
    /// `Show-up Rate` — NEVER "Show-Up Rate". The ticket says so twice and it is a product term.
    static let leadPlain =
        "Activate notifications so you never miss a date! Missing a date lowers your Show-up "
        + "Rate, which will lead to a temporary ban or permanent suspension from the app."

    static let cardTitle = "Get notifications"
    static let cardLine =
        "We’ll let you know about new matches, meet time or location changes and date "
        + "cancellations."

    static let pushRow = "Push notifications"
    static let recommended = "Recommended"

    static let interestHeading = "More ways to reach you are coming soon."
    static let interestLine = "Tell us which ones you’d like."

    static let finePrint =
        "We use this only to coordinate your dates — never for marketing, and we never sell "
        + "your data. Turn any of these off whenever you like in Settings."
    /// The one word in the fine print that is dark rather than subtle. Plain text, never a link.
    static let settingsWord = "Settings"

    static let privacyLink = "Read our Privacy Policy"

    static let cta = "Save preferences"

    /// The save-failure message.
    ///
    /// COPY IS OPEN IN THE TICKET — "Open: The save-failure error copy. Use the flow's shared
    /// inline error card above the CTA." The card is specified; the words are not, so this is the
    /// flow's existing failure voice rather than a new register, and it is recorded in the
    /// conflicts file as needing a copy decision.
    static let saveFailed = "We couldn’t save your preferences. Please try again."

    // MARK: the deactivation confirm

    static let confirmTitle = "Are you sure?"
    static let confirmLead = "Please remember:"
    static let confirmBody =
        "Missing a date lowers your Show-up Rate, which will lead to a temporary ban or "
        + "permanent suspension from the app. Keep notifications active to avoid any chances "
        + "of missing a date."
    static let confirmKeep = "Keep active"
    static let confirmSettings = "Open Settings"
    static let confirmDeactivate = "Confirm deactivation"
}

/// The words the reference file sets in bold, in the lead and in the dialog body.
private let leadBold = [
    "Activate notifications", "never miss a date!", "Missing", "lowers", "Show-up Rate",
    "will", "temporary", "permanent", "suspension", "app",
]

/// The lead and the dialog body, with the reference's bold runs applied.
///
/// MATCHED ON THE STRING, not hand-split into fragments. The copy is one sentence in the ticket
/// and splitting it into a list in source is how a copy edit silently loses a word — the fragments
/// would still compile.
///
/// `private`, and it has to be: `leadBold` is a file-scope `private let`, and Swift refuses a
/// default argument that is less accessible than the function it is on. An internal `emphasised`
/// with a fileprivate default would not compile, and no checker in this repo can see that -- only
/// the Swift compiler can.
private func emphasised(_ text: String, bold: [String] = leadBold) -> Text {
    var out = Text("")
    var rest = Substring(text)
    while !rest.isEmpty {
        let hit = bold
            .compactMap { word -> (Range<Substring.Index>, String)? in
                rest.range(of: word).map { ($0, word) }
            }
            .min { $0.0.lowerBound < $1.0.lowerBound }

        guard let (range, word) = hit else {
            out = out + Text(String(rest))
            break
        }
        out = out + Text(String(rest[rest.startIndex..<range.lowerBound]))
        out = out + Text(word).fontWeight(.bold)
        rest = rest[range.upperBound...]
    }
    return out
}

// MARK: - the interest row

/// One interest row: pip, name, optional sub-line, checkbox.
///
/// THE WHOLE ROW IS THE TARGET, not the 22pt square. The ticket makes it an acceptance criterion
/// and the reference makes the row itself the `<button role="checkbox">`.
private struct InterestRow: View {
    let channel: InterestChannel
    let on: Bool
    let first: Bool
    var onToggle: () -> Void = {}

    private var title: String {
        switch channel {
        case .aiCall: return "Phone call from our AI assistant"
        case .whatsApp: return "WhatsApp"
        case .sms: return "SMS"
        }
    }

    private var sub: String? {
        channel == .aiCall ? "A short automated call when something changes." : nil
    }

    private var icon: BrandIcon {
        switch channel {
        case .aiCall: return .phoneCall
        case .whatsApp: return .whatsApp
        case .sms: return .messageCircle
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Gradients.lilac())
                BrandIconView(icon: icon, size: 17, stroke: 1.8, tint: .liqPurple)
            }
            .frame(width: 34, height: 34)

            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(F.manrope(15, .semibold))
                    .lineSpacing(15 * 0.3)
                    .foregroundColor(.liqFg)
                    .fixedSize(horizontal: false, vertical: true)
                if let sub {
                    Text(sub)
                        .font(F.manrope(12.5, .medium))
                        .lineSpacing(12.5 * 0.4)
                        .foregroundColor(.liqNeutral)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // THE BOX IS DECORATION: the row carries the role and the checked state, so a screen
            // reader announces one control rather than a row and a box that disagree.
            ZStack {
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .fill(on ? Color.liqPurple : Color.liqElevated)
                RoundedRectangle(cornerRadius: 7, style: .continuous)
                    .strokeBorder(on ? Color.liqPurple : Color.liqBorder, lineWidth: 1.5)
                if on { BrandIconView(icon: .check, size: 14, stroke: 3, tint: .white) }
            }
            .frame(width: 22, height: 22)
        }
        .padding(.vertical, 12)
        .frame(minHeight: ComponentSizes.minTapTarget)
        // THE WHOLE ROW. `contentShape` is what makes the padding tappable rather than only the
        // pip and the text — without it the gaps between them do nothing, which is a control
        // whose hit area is smaller than it looks.
        .contentShape(Rectangle())
        .onTapGesture(perform: onToggle)
        .overlay(alignment: .top) {
            // `borderTop: 1px solid var(--liq-border-soft)` — every row but the first. Drawn as an
            // overlay rather than a `Divider` between rows, because it has to sit on this row's
            // own top edge inside the same padding: a divider in the stack would take part in the
            // spacing and push the 12pt row padding apart by its own height.
            if !first { Rectangle().fill(Color.liqBorderSoft).frame(height: 1) }
        }
        // ONE CONTROL TO VOICEOVER, announcing whether it is checked. `.isButton` would say
        // nothing about the state, which for a checkbox is the whole content of the control.
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(on ? AccessibilityTraits([.isButton, .isSelected])
                                  : AccessibilityTraits.isButton)
        .accessibilityLabel(sub == nil ? title : "\(title). \(sub!)")
        .accessibilityValue(on ? "Checked" : "Unchecked")
        // A TRAILING CLOSURE, not `perform:`. `accessibilityAction` takes
        // `(_ actionKind: AccessibilityActionKind = .default, _ handler: () -> Void)` -- there is
        // no `perform:` label on it, unlike `onTapGesture(perform:)` and `Button(action:)` which
        // is where the habit comes from. `ConsentSwitch` two files over already spelled it this
        // way; this was the one call site that did not, and it was the ONE error in the whole of
        // this ticket's Swift.
        .accessibilityAction { onToggle() }
    }
}

// MARK: - the card

private struct ReachCard: View {
    let state: ReachabilityState
    var onPushChange: (Bool) -> Void = { _ in }
    var onInterestToggle: (InterestChannel) -> Void = { _ in }

    /// THE CARD LIFTS TO THE VIOLET TINT WHILE PUSH IS ON. `rgba(167,139,250,0.10)` over
    /// `rgba(129,42,236,0.28)`, and the elevated surface with its soft border when it is off.
    private var lifted: Bool { state.pushOn }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 13) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Gradients.lilac())
                    BrandIconView(icon: .bell, size: 20, stroke: 1.8, tint: .liqPurple)
                }
                .frame(width: 40, height: 40)

                VStack(alignment: .leading, spacing: 0) {
                    Text(ReachCopy.cardTitle)
                        .font(F.lora(16, bold: true))
                        .lineSpacing(16 * 0.2)
                        .foregroundColor(.liqFg)
                    Text(ReachCopy.cardLine)
                        .font(F.manrope(13, .medium))
                        .lineSpacing(13 * 0.42)
                        .foregroundColor(.liqNeutral)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 6)
                }
            }

            // ── the push row ────────────────────────────────────────────────
            HStack(spacing: 12) {
                // `recommended` FILLS the pip rather than tinting it — the one row on the screen
                // that is already the answer.
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color.liqPurple)
                    BrandIconView(icon: .bell, size: 17, stroke: 1.8, tint: .white)
                }
                .frame(width: 34, height: 34)

                PushRowLabel()
                    .frame(maxWidth: .infinity, alignment: .leading)

                ConsentSwitch(on: state.pushOn, label: ReachCopy.pushRow, onChange: onPushChange)
            }
            .padding(.top, 8)
            .padding(.vertical, 12)

            // ── the demand test ─────────────────────────────────────────────
            VStack(alignment: .leading, spacing: 0) {
                Text(ReachCopy.interestHeading)
                    .font(F.manrope(14, .bold))
                    .lineSpacing(14 * 0.35)
                    .foregroundColor(.liqFg)
                    .fixedSize(horizontal: false, vertical: true)
                Text(ReachCopy.interestLine)
                    .font(F.manrope(13, .medium))
                    .lineSpacing(13 * 0.42)
                    .foregroundColor(.liqNeutral)
                    .padding(.top, 3)

                VStack(spacing: 0) {
                    ForEach(Array(InterestChannel.order.enumerated()), id: \.element) { i, ch in
                        InterestRow(
                            channel: ch,
                            on: state.isInterested(ch),
                            first: i == 0,
                            onToggle: { onInterestToggle(ch) }
                        )
                    }
                }
                .padding(.top, 6)
            }
            .padding(.top, 18)
            .overlay(alignment: .top) {
                Rectangle().fill(Color.liqBorderSoft).frame(height: 1).offset(y: -10)
            }
            .padding(.top, 10)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(lifted ? Color(hex: 0xA78BFA).opacity(0.10) : Color.liqElevated)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(
                    lifted ? Color.liqPurple.opacity(0.28) : Color.liqBorderSoft,
                    lineWidth: 1
                )
        )
    }
}

/// The push row's label and its `Recommended` pill.
///
/// `flexWrap: 'wrap'` IN THE REFERENCE, AND IT REALLY WRAPS AT 390: the label is about 130 and the
/// pill about 110 in a 201-wide slot, so the design's OWN frame puts the pill on a second line
/// under the label. SwiftUI's `HStack` does not wrap — it shares the width — and the two things it
/// can do instead are both visibly wrong: squeeze the pill until RECOMMENDED breaks across lines
/// and the capsule draws as an ellipse, or squeeze the label until `Push notification/s` breaks.
/// Both were caught on the Compose side by looking at the render, not by a measurement.
///
/// So the label and the pill are laid out as a two-line block with the pill under the label, which
/// is what the reference's own frame produces, and neither can take width from the other.
private struct PushRowLabel: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(ReachCopy.pushRow)
                .font(F.manrope(15, .semibold))
                .foregroundColor(.liqFg)
                .fixedSize(horizontal: false, vertical: true)
            RecommendedPill()
        }
    }
}

/// THE ONE PLACE IN THIS FLOW WHERE TYPE STOPS GROWING.
///
/// RECOMMENDED is eleven characters of one unbreakable word, and at the largest accessibility
/// sizes it needs more width than this row can offer on the narrow frames. The Compose sweep found
/// that; looking at it found what the two obvious answers actually produce. Truncating spends the
/// shortfall on an ellipsis — `RECOMMEND…`, inside a decorative badge. Wrapping spends it on a
/// line break INSIDE the word: `RECOMMENDE` over `D`, which is worse. The reference has no answer
/// either: `flex: 'none'` means CSS would let the pill overflow its container.
///
/// So the badge is drawn at a FIXED size and does not scale. `.dynamicTypeSize(...(.large))` caps
/// it at the default step and lets everything around it grow. What is given up is real and is only
/// this: at larger settings the badge stays the size it was drawn. What is kept is that the word
/// is always whole — and it is defensible here and nowhere else on this screen, because the badge
/// repeats something the screen already says: the toggle beside it is on by default.
private struct RecommendedPill: View {
    var body: some View {
        Text(ReachCopy.recommended.uppercased())
            .font(F.manrope(10.5, .bold))
            .tracking(0.03 * 10.5)
            .lineLimit(1)
            .foregroundColor(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(Color.liqPurple, in: Capsule())
            .dynamicTypeSize(...DynamicTypeSize.large)
            .fixedSize(horizontal: true, vertical: false)
            .accessibilityLabel(ReachCopy.recommended)
    }
}

// MARK: - the fine print

/// The GDPR footer note.
///
/// `Privacy Policy` IS THE ONLY LINK. "Settings" is set in the foreground weight and is plain text
/// — the ticket says so outright, and a second link here would be a second thing to tap that goes
/// nowhere.
private struct FinePrint: View {
    var onPrivacy: () -> Void = {}

    var body: some View {
        // ONE PARAGRAPH, AND THE LINK IS INSIDE IT.
        //
        // This was a Text plus a Button in a VStack, with `Read our Privacy Policy` on its own
        // line behind a 44pt frame. Callout 12 and the spec sheet's scrolled frame both show ONE
        // FLOWING SENTENCE ending with the link and a full stop — "... whenever you like in
        // Settings. Read our Privacy Policy." — and a link on its own line read as a detached
        // button, which is how it came back reported as missing rather than as misplaced.
        //
        // `AttributedString` with a `link`, resolved by `OpenURLAction`, is how the welcome flow's
        // legal line already does exactly this. A run of text inside a paragraph cannot be a
        // Button without breaking the paragraph, and this is the mechanism SwiftUI offers for it.
        //
        // THE 44PT FLOOR CANNOT APPLY TO A WORD INSIDE A SENTENCE, and does not here — the same
        // trade the three sign-up legal links already make. Recorded as E35.
        Text(attributed)
            .font(F.manrope(11.5, .medium))
            .lineSpacing(11.5 * 0.5)
            .foregroundColor(.liqSubtle)
            .fixedSize(horizontal: false, vertical: true)
            .tint(.liqPurple)
            .environment(\.openURL, OpenURLAction { _ in
                onPrivacy()
                return .handled
            })
            .padding(.horizontal, 4)
            .padding(.top, 4)
            .accessibilityIdentifier(reachFinePrintId)
    }

    /// The sentence, with its two styled runs.
    ///
    /// `Settings` is `--liq-fg` AND 600 — DARK, not the subtle grey the rest is set in, and it is
    /// PLAIN TEXT: the spec says so outright, and a second link here would be a second thing to
    /// tap that goes nowhere.
    private var attributed: AttributedString {
        let text = ReachCopy.finePrint
        guard let range = text.range(of: ReachCopy.settingsWord) else {
            // The word is a constant of this file and cannot go missing, but a copy edit that
            // renamed it must not silently drop the whole paragraph.
            return AttributedString(text)
        }

        var out = AttributedString(text[text.startIndex..<range.lowerBound])

        var settings = AttributedString(ReachCopy.settingsWord)
        settings.font = F.manrope(11.5, .semibold)
        settings.foregroundColor = .liqFg
        out.append(settings)

        out.append(AttributedString(text[range.upperBound...]))
        out.append(AttributedString(" "))

        var link = AttributedString(ReachCopy.privacyLink)
        link.link = URL(string: "showup://privacy")
        link.font = F.manrope(11.5, .semibold)
        link.foregroundColor = .liqPurple
        link.underlineStyle = .single
        out.append(link)

        out.append(AttributedString("."))
        return out
    }
}

// MARK: - the deactivation confirm

private struct DeactivationDialog: View {
    let prompt: DeactivationPrompt
    var onKeepActive: () -> Void = {}
    var onOpenSettings: () -> Void = {}
    var onConfirm: () -> Void = {}

    /// Focus moves to the primary when the dialog opens — the ticket names it.
    @AccessibilityFocusState private var primaryFocused: Bool

    private var primaryLabel: String {
        prompt == .afterOsDenial ? ReachCopy.confirmSettings : ReachCopy.confirmKeep
    }

    /// The icon, title, body and channel chip — everything above the two actions.
    ///
    /// Hoisted into its own builder because `ViewThatFits` has to be handed it TWICE, once bare
    /// and once inside a `ScrollView`. Written once so the two can never disagree.
    @ViewBuilder private var explanation: some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.liqDanger.opacity(0.10))
                BrandIconView(icon: .alertCircle, size: 24, stroke: 1.9, tint: .liqDanger)
            }
            .frame(width: 52, height: 52)
            .accessibilityHidden(true)

            // ITALIC. The reference sets the whole title in an `<em>` — the same flourish the
            // headline uses on one word, here running the full line.
            Text(ReachCopy.confirmTitle)
                .font(F.lora(23, bold: true, italic: true))
                .lineSpacing(23 * 0.15)
                .tracking(-0.01 * 23)
                .foregroundColor(.liqFg)
                .padding(.top, 16)

            (
                Text(ReachCopy.confirmLead).fontWeight(.bold)
                + Text("\n")
                + emphasised(ReachCopy.confirmBody)
            )
            .font(F.manrope(14, .medium))
            .lineSpacing(14 * 0.5)
            .multilineTextAlignment(.center)
            .foregroundColor(.liqNeutral)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 10)

            // The channel chip: which consent this is about, so the dialog is never ambiguous
            // about what is being switched off.
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 9, style: .continuous)
                        .fill(Gradients.lilac())
                    BrandIconView(icon: .bell, size: 16, stroke: 1.8, tint: .liqPurple)
                }
                .frame(width: 30, height: 30)
                Text(ReachCopy.pushRow)
                    .font(F.manrope(13.5, .semibold))
                    .foregroundColor(.liqFg)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.liqRaised,
                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .padding(.top, 16)
            .accessibilityElement(children: .combine)
        }
    }

    var body: some View {
        ZStack {
            Color(hex: 0x1D1129).opacity(0.44)
                .ignoresSafeArea()
                .onTapGesture(perform: onKeepActive)
                .accessibilityHidden(true)

            VStack(spacing: 0) {
                // THE EXPLANATION SCROLLS, THE ACTIONS DO NOT — and only when it has to.
                //
                // At the largest Dynamic Type sizes on a narrow phone this dialog is taller than
                // the frame. A `VStack` centred in a `ZStack` simply lays its children out past
                // the bottom — on the Compose side that drew the title with 148px below the cut
                // and measured both buttons, INCLUDING the only destructive action on the screen,
                // at zero height. Scrolling everything was the first fix and it was still a dialog
                // whose destructive action you had to go looking for.
                //
                // `ViewThatFits` IS THE WHOLE MECHANISM, and a bare `ScrollView` is not: a
                // ScrollView accepts whatever height it is offered, so putting one in this stack
                // would make the card full-height on every phone at every type size — a dialog
                // that no longer hugs its content is a different design, not a safety net. This
                // offers the plain layout first and falls back to the scrolling one only when the
                // plain one does not fit, which is the SwiftUI spelling of Compose's
                // `weight(1f, fill = false)`.
                ViewThatFits(in: .vertical) {
                    explanation
                    ScrollView { explanation }
                }

                PrimaryButton(
                    primaryLabel,
                    variant: .sunset,
                    action: {
                        // `if`, not a ternary: both branches are Void, and a ternary whose result
                        // nobody uses is an expression pretending to be a statement.
                        if prompt == .afterOsDenial { onOpenSettings() } else { onKeepActive() }
                    }
                )
                .padding(.top, 18)
                .accessibilityFocused($primaryFocused)

                // `height: 50` in the reference, which is already over the 44 floor — as a
                // MINIMUM, not a fixed height: `Confirm deactivation` wraps to two lines at the
                // larger Dynamic Type sizes and a 50pt box has room for one.
                Button(action: onConfirm) {
                    Text(ReachCopy.confirmDeactivate)
                        .font(F.manrope(15, .bold))
                        .foregroundColor(.liqDanger)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.top, 10)
            }
            .padding(.horizontal, 24)
            .padding(.top, 26)
            .padding(.bottom, 20)
            .frame(maxWidth: 320)
            .background(Color.liqElevated,
                        in: RoundedRectangle(cornerRadius: 26, style: .continuous))
            .padding(.horizontal, 26)
            .padding(.vertical, 24)
        }
        // `aria-modal`: VoiceOver stays inside the dialog and the screen behind is gone from the
        // rotor, not merely covered. A scrim stops a finger and does nothing whatsoever to a
        // screen reader — without this a user could reach past the dialog and flip the very
        // switch it exists to ask about.
        .accessibilityAddTraits(.isModal)
        .onAppear { primaryFocused = true }
    }
}

// MARK: - the screen

/// Takes values and returns pixels.
///
/// Every callback is a closure and nothing here knows what a model is, which is what keeps the
/// states renderable at seventeen sizes with no device.
struct ProfileReachabilityView: View {
    var state = ReachabilityState()
    var onPushChange: (Bool) -> Void = { _ in }
    var onInterestToggle: (InterestChannel) -> Void = { _ in }
    var onKeepActive: () -> Void = {}
    var onOpenSettings: () -> Void = {}
    var onConfirmDeactivate: () -> Void = {}
    var onPrivacy: () -> Void = {}
    var onSave: () -> Void = {}

    var body: some View {
        ZStack {
            WelcomeScaffold(
                // THE TWO BLOCKS DO NOT SHARE A GUTTER. The reference has the headline block at
                // `padding: '32px 28px 0'` and the body at `'20px 24px 8px'` — the card is four
                // wider than the text above it, which is what stops it reading as an indent. The
                // scaffold carries the narrower of the two and the headline pads the difference.
                topPadding: 32,
                gutter: 24,
                scrollWhenTight: true,
                footer: {
                    // `padding: '10px 24px 6px'` with `background: linear-gradient(180deg,
                    // rgba(255,251,247,0) 0%, var(--liq-bg) 34%)`. FULL-BLEED, so the fade does
                    // not leave two strips down the sides where the backdrop orbs show through
                    // untouched.
                    VStack(spacing: 0) {
                        // ABOVE THE CTA, IN THE PINNED FOOTER, which is where the ticket puts it.
                        // In the footer rather than at the end of the scrolling body, and that is
                        // the point: the body may be scrolled anywhere when the save fails, so a
                        // card at the bottom of it would be off-screen exactly when it is needed.
                        if state.saveFailed {
                            InlineErrorCard(message: Text(ReachCopy.saveFailed))
                                .padding(.bottom, 10)
                                .accessibilityIdentifier(reachErrorId)
                        }
                        PrimaryButton(
                            ReachCopy.cta,
                            variant: .sunset,
                            // NEVER DISABLED, and not tappable twice: it stops answering while a
                            // save is in flight rather than greying out. A disabled CTA on a
                            // screen with no other exit is a dead end.
                            action: { if !state.saving { onSave() } }
                        )
                        .accessibilityIdentifier(reachCtaId)
                    }
                    .padding(.top, 10)
                    .padding(.bottom, 6)
                    // NO `.padding(.horizontal)` HERE. `WelcomeScaffold` already applies its own
                    // `gutter` to whatever it is handed as a footer, so a second one is 48 of
                    // inset and a visibly narrower button than every other CTA in the flow. The
                    // Compose side owns its gutter because that scaffold hands the footer the
                    // full frame on purpose, so the two files differ by one line and agree on
                    // the rendered result.
                    .background(
                        LinearGradient(
                            stops: [
                                .init(color: Color.liqCream.opacity(0), location: 0),
                                .init(color: Color.liqCream, location: 0.34),
                                .init(color: Color.liqCream, location: 1),
                            ],
                            startPoint: .top, endPoint: .bottom
                        )
                    )
                }
            ) {
                // The headline block's four of extra gutter — see the scaffold call above.
                WashHeadline(
                    parts: [
                        (ReachCopy.headlineEm, true),
                        (ReachCopy.headlineTail, false),
                    ],
                    fontSize: 31, lineHeightMultiple: 1.1, trackingEm: -0.015,
                    balance: true
                )
                .padding(.horizontal, 4)

                emphasised(ReachCopy.leadPlain)
                    .font(F.manrope(14.5, .medium))
                    .lineSpacing(14.5 * 0.5)
                    .foregroundColor(.liqNeutral)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 4)
                    .padding(.top, 14)

                VStack(spacing: 12) {
                    ReachCard(
                        state: state,
                        onPushChange: onPushChange,
                        onInterestToggle: onInterestToggle
                    )
                    FinePrint(onPrivacy: onPrivacy)
                }
                .padding(.top, 20)

                // The fine print must scroll CLEAR of the pinned CTA. Reserved, not hoped for.
                Spacer(minLength: footerClearance)
            }
            // The whole screen is removed from the accessibility tree while the dialog is up —
            // see the dialog's `.isModal`. Stated on this side too because the two mechanisms
            // cover different assistive technologies and neither one alone was enough on Compose.
            .accessibilityHidden(state.prompt != nil)

            if let prompt = state.prompt {
                DeactivationDialog(
                    prompt: prompt,
                    onKeepActive: onKeepActive,
                    onOpenSettings: onOpenSettings,
                    onConfirm: onConfirmDeactivate
                )
            }
        }
        // No chevron, no swipe-back. Profile creation is mandatory once entered.
        .navigationBarBackButtonHidden(true)
    }
}

// MARK: - previews
//
// 375 × 667 is where the body has to scroll and where the fine print must still clear the CTA;
// 430 × 932 is where the whole card fits above the fold.

#Preview("A · default · 375") {
    ProfileReachabilityView().frame(width: 375, height: 667)
}

#Preview("A · default · 390") {
    ProfileReachabilityView().frame(width: 390, height: 844)
}

#Preview("B · interest · 430") {
    ProfileReachabilityView(
        state: ReachabilityState(interest: [.aiCall, .whatsApp])
    )
    .frame(width: 430, height: 932)
}

#Preview("C · confirm · 390") {
    ProfileReachabilityView(state: ReachabilityState(prompt: .userTurnedItOff))
        .frame(width: 390, height: 844)
}

#Preview("D · after denial · 390") {
    ProfileReachabilityView(
        state: ReachabilityState(pushOn: false, prompt: .afterOsDenial)
    )
    .frame(width: 390, height: 844)
}

#Preview("F · save failed · 375") {
    ProfileReachabilityView(
        state: ReachabilityState(interest: [.aiCall, .whatsApp, .sms], saveFailed: true)
    )
    .frame(width: 375, height: 667)
}
