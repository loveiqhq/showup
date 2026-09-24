//
//  ProfileNotifications.swift
//  ShowUpWelcome · Profile creation 09 — Notifications permission ask (SHOWUP-162)
//
//  ───────────────────────────────────────────────────────────────────────────
//  ONE STATE, NO INPUT, NOTHING THAT CAN FAIL
//  ───────────────────────────────────────────────────────────────────────────
//
//  No denied state, no recovery banner, no "asking" state, no error card. Profile creation runs
//  once on a FRESH INSTALL and a fresh install has no notification permission, so the status here
//  is always `not determined` and the screen has exactly one path.
//
//  The two cases where it is not shown are decided BEFORE it is pushed and never after it mounts.
//  Neither is a state of this screen; both live in the host. See `NotificationAccess.swift`.
//
//  ───────────────────────────────────────────────────────────────────────────
//  TWO NAMED EXCEPTIONS, AND THEY ARE THE BRIDGE'S OWN
//  ───────────────────────────────────────────────────────────────────────────
//
//  RULE 5 SAYS NO AMBIENT BACKDROP on a profile screen, because every other one has the keyboard
//  open. This one has no keyboard and no input, so it carries the first-run atmosphere — orange
//  orb, violet orb, 360 peach wash — exactly as the embrace bridge does, which is why it uses
//  `WelcomeScaffold`.
//
//  RULE 7 SAYS THE CTA IS ORANGE. Granting push is a commitment beat, so it is the full-width
//  sunset button. ONE DIFFERENCE FROM THE BRIDGE: no trailing arrow. The label is the whole
//  button, and the ticket calls that out because it is the thing most likely to be carried over.
//
//  ───────────────────────────────────────────────────────────────────────────
//  THE ABSENCES ARE THE DESIGN
//  ───────────────────────────────────────────────────────────────────────────
//
//    · NO AppHeader — no title, no back chevron, no skip, no close
//    · NO StepProgress — it is in neither progress bar
//    · NO "Not now" — the OS sheet's own decline is the way past
//    · NO back of any kind — no chevron and no swipe-back
//
//  ───────────────────────────────────────────────────────────────────────────
//  IT DOES NOT FIT EVERYWHERE
//  ───────────────────────────────────────────────────────────────────────────
//
//  Measured on the Compose side across seventeen frames. At the DEFAULT font three come up short
//  — the Galaxy Fold cover screen by 169, a 360x640 Android by 98, the iPhone SE by 5. At 1.3x
//  type thirteen of the seventeen miss; at 2.0x all of them do.
//
//  The ticket's agreed order of sacrifice is worth about 15pt before its last rung, which closes
//  the SE and nothing else, and that last rung — cut a row — is a content decision the ticket
//  reserves. So the approved values are kept, THE CTA IS PINNED BELOW THE SCROLL, and only the
//  list moves. Nothing scrolls on the fourteen frames where it fits. See E20 in
//  `audit/CONFLICTS-2026-08-27.md`.
//

import SwiftUI

/// Every string on the screen, verbatim from the ticket.
///
/// The em dashes are SPACED EM DASHES, not hyphens, and `e.g.` is lower case with both points.
enum NotificationsCopy {
    static let headlineLead = "Never miss "
    static let headlineEm = "a date"
    static let headlineTail = " with Notifications!"

    static let lead =
        "No spam! Every notification is about your dates and helps you to never miss one."

    static let cta = "Enable notifications"

    /// Row 2's label. Always capitalised, always this one word, never an upsell.
    static let premium = "Premium"
}

/// One of the five things push is for.
///
/// FIVE, NOT SIX, and the order is content rather than layout: row 1 is the reason the user is in
/// the flow at all and row 5 is the one that protects their time. An earlier draft had a sixth and
/// it was cut — do not re-add it and do not re-order.
struct NotifyBenefit: Identifiable {
    let icon: BrandIcon
    let title: String
    let line: String
    var tag: String?

    var id: String { title }
}

let notifyBenefits: [NotifyBenefit] = [
    .init(
        icon: .heart,
        title: "Match alert",
        line: "Get instant notifications when you receive a match and never miss out on a date."
    ),
    .init(
        icon: .sparkles,
        title: "Like received",
        line: "Don't miss your chance to meet — we surface it the second it arrives.",
        tag: NotificationsCopy.premium
    ),
    .init(
        icon: .messageCircle,
        title: "Meeting details change",
        line: "Get notified if your date asks — e.g. meet time change, running late."
    ),
    .init(
        icon: .clock,
        title: "Date reminder",
        line: "A heads-up an hour out — never arrive late."
    ),
    .init(
        // The kit's `x`, which this project already draws as `close` at the kit's own geometry.
        icon: .close,
        title: "Date cancelled",
        line: "Never waste time waiting — get notified, look for someone else instead."
    ),
]

/// The sunset `Premium` pill on row 2.
///
/// A LABEL, NOT A CONTROL: not tappable, opens nothing, no paywall behind it. It reads as part of
/// row 2's title rather than as a thing of its own.
private struct PremiumTag: View {
    var body: some View {
        Text(NotificationsCopy.premium.uppercased())
            .font(F.manrope(10, .heavy))
            .tracking(0.06 * 10)
            .foregroundColor(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 3)
            .background(Gradients.sunset(), in: Capsule())
            // translateY(-1px) in the reference: a capsule reads a hair low against a serif
            // cap-height without it.
            .offset(y: -1)
    }
}

/// One benefit row: a lilac pip, a serif title, and a line underneath.
///
/// THE PIP IS THE MEDIA CARD'S RECIPE, not a second one — a lilac square with a primary-500 glyph,
/// which is what makes profile creation read as one system end to end. The numbers are this
/// screen's own (40 at radius 12 against the media card's 42 at 13): the reference wins on numbers
/// and "same recipe" is about the vocabulary.
private struct BenefitRow: View {
    let benefit: NotifyBenefit

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Gradients.lilac())
                BrandIconView(icon: benefit.icon, size: 20, stroke: 1.8, tint: .liqPurple)
            }
            .frame(width: 40, height: 40)

            // paddingTop 1: the serif title's cap-height sits a fraction below the pip's optical
            // centre without it.
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(benefit.title)
                        .font(F.lora(16, bold: true))
                        // `lineHeight: 1.2`, in the same size x (multiple - 1) spelling the lead
                        // and the row line use. Invisible on one line and not on two, and at 2.0x
                        // type every title is two lines.
                        .lineSpacing(16 * 0.2)
                        .tracking(-0.005 * 16)
                        .foregroundColor(.liqFg)
                    // THE PILL KEEPS ITS OWN WIDTH. The spec sheet has this row at
                    // `flex-wrap: wrap` and an HStack does not wrap; it shares the width. On the
                    // Compose side the unweighted title took everything and left the pill 25pt of
                    // the 123 it needed at 2.0x type, with PREMIUM ellipsised inside it, and
                    // nothing overflowed so no fit sweep could see it. Stated rather than left to
                    // SwiftUI's flexibility arithmetic, which cannot be measured on this machine.
                    if benefit.tag != nil {
                        PremiumTag().fixedSize(horizontal: true, vertical: false)
                    }
                }
                Text(benefit.line)
                    .font(F.manrope(13.5, .medium))
                    .lineSpacing(13.5 * 0.4)
                    .foregroundColor(.liqNeutral)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 3)
            }
            .padding(.top, 1)
        }
        // ONE GROUP FOR VOICEOVER: title and line read together, the icon is decorative, and
        // `Premium` is part of row 2's title rather than a control of its own.
        .accessibilityElement(children: .combine)
    }
}

/// The screen. One state and one callback.
struct ProfileNotificationsView: View {
    var onEnable: () -> Void = {}
    /// Swallows a second press for the lifetime of the OS sheet.
    ///
    /// The CTA is NEVER DISABLED — it never greys out and never changes — it simply stops
    /// answering. A second request is a silent platform no-op, so a double tap that looked like a
    /// hang would be a hang we invented.
    var busy: Bool = false

    var body: some View {
        // topPadding 40 and gutter 28 are the headline block's `padding: '40px 28px 0'`. The 28 is
        // the bridge's gutter, not the group's 24, for the same reason: no input field to align to.
        //
        // scrollWhenTight, and the ticket says "never let it scroll" — see the file header. It
        // changes nothing where the screen fits and stops it clipping where it does not.
        //
        // THE CTA IS IN `footer`, NOT IN THE SCROLL, and that is the half that matters. Scrolling
        // alone makes the button reachable and leaves it invisible: on the Galaxy Fold cover
        // screen this opened with the whole button below the fold and nothing on screen saying
        // so, which on a PERMISSION ASK reads as a broken screen rather than a scrollable one.
        // `testTheCTAIsOnScreenOnEveryDevice` caught it and was right to.
        //
        // Pinned, the band is the bottom of the same column — same gutter, same 18 above the home
        // indicator — so on the fourteen frames that fit it is exactly where it already was, and
        // on the three that do not the list scrolls beneath a button that never leaves.
        WelcomeScaffold(
            topPadding: 40,
            gutter: 28,
            scrollWhenTight: true,
            footer: {
                // Sunset and full width, rule 7's named exception. NO TRAILING ICON.
                // UNLABELLED FIRST ARGUMENT. `PrimaryButton` has two inits: the slotted one takes
                // `label:` and a `trailing:` closure, and the no-slots one takes the label
                // positionally. This button has NO trailing icon -- the one thing that differs
                // from the bridge's CTA -- so it is the second, and `label:` there is a compile
                // error.
                PrimaryButton(
                    NotificationsCopy.cta,
                    variant: .sunset,
                    action: { if !busy { onEnable() } }
                )
                .padding(.bottom, 18)
            }
        ) {
            WashHeadline(
                parts: [
                    (NotificationsCopy.headlineLead, false),
                    (NotificationsCopy.headlineEm, true),
                    (NotificationsCopy.headlineTail, false),
                ],
                fontSize: 32, lineHeightMultiple: 1.1, trackingEm: -0.015,
                // `text-wrap: balance`, which the reference sets and the criteria name. A greedy
                // wrap gives `Never miss a date with` / `Notifications!`; the artboard shows
                // `Never miss a date` / `with Notifications!`.
                balance: true
            )

            // The body block's own 20 top padding. The headline block has no bottom padding, so
            // this single value is the whole gap between them.
            Text(NotificationsCopy.lead)
                .font(F.manrope(15, .medium))
                .lineSpacing(15 * 0.55)
                .foregroundColor(.liqNeutral)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 20)

            VStack(alignment: .leading, spacing: 16) {
                ForEach(notifyBenefits) { BenefitRow(benefit: $0) }
            }
            .padding(.top, 22)

            // THE ONLY FLEXIBLE ELEMENT, with a 12 floor. Every device difference lands here and
            // nothing else moves. One spacer, never two.
            //
            // IT STAYS IN THE CONTENT even though the CTA no longer is: with slack it is the thing
            // that holds the button at the bottom of the screen, and with none it is the 12 points
            // of air between the last row and the pinned band once the user has scrolled down.
            Spacer(minLength: 12)
        }
        // No chevron, no swipe-back. Profile creation is mandatory once entered.
        .navigationBarBackButtonHidden(true)
    }
}

#Preview("Notifications · 375") {
    ProfileNotificationsView().frame(width: 375, height: 667)
}

#Preview("Notifications · 390") {
    ProfileNotificationsView().frame(width: 390, height: 844)
}

#Preview("Notifications · 430") {
    ProfileNotificationsView().frame(width: 430, height: 932)
}
