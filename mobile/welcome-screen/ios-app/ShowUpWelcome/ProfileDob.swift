//  ProfileDob.swift
//  ShowUp · Profile creation 04 — Date of birth (SHOWUP-154)
//
//  Mirrors `profile/ProfileDobScreen.kt`.
//
//  THE ONLY SCREEN IN THE FLOW THAT WRITES A VALUE THE USER CANNOT CHANGE
//
//  Age is locked after this step. That is why the confirmation is INLINE and leads with the
//  computed age rather than the typed date: a user re-reading "03/22/1998" checks their own input,
//  a user reading "28" checks the thing the app will actually use.
//
//  THE RESERVED REGION IS 84 AND MUST NOT SHRINK
//
//  84 is the height of the age card, the tallest of the four bodies. Holding it in every state is
//  what keeps the visibility band, the CTA and the keyboard at the identical Y whichever body is
//  showing.

import SwiftUI

/// Final strings, quoted from the ticket. The mono chip is generated from the locale's order.
enum DobCopy {
    static let section = "The basics"
    static let sub = "Be honest — it helps us find the right matches. You must be 18 or older."
    static let label = "Date of birth"
    static let cta = "Continue"
    static let lockNote = "Locked after this step."
    static let edit = "Edit"
    static let visibility = "Don't display on my profile"

    static let helperLead = "Your "
    static let helperBold = "age"
    static let helperTail = " appears on your profile — not your date of birth."

    static let under18 = "You must be at least 18 to use Show Up. Please check the date you entered."

    static func ageQuestion(_ age: Int) -> String { "You're \(age). Look right?" }

    /// Both format errors name the order the user is actually being asked for.
    static func incomplete(_ order: DateOrder) -> String {
        "Enter your full date of birth to continue — \(order.pattern)."
    }
    static func invalid(_ order: DateOrder) -> String {
        "That date doesn't exist. Please check the format — \(order.pattern)."
    }
}

struct ProfileDobView: View {
    @Binding var value: String
    var order: DateOrder = .forCurrentLocale()
    @Binding var hideAge: Bool
    /// Whether Continue has been pressed. The incomplete error appears only after a press —
    /// never mid-typing — which is why it is separate from `value`.
    var attempted: Bool = false
    /// A request is in flight. Continue stops accepting presses.
    var busy: Bool = false
    /// The server refused the age its own way — only reachable when the two 18 checks disagree.
    var serverRejectedAge: Bool = false
    var onContinue: (Int, String) -> Void = { _, _ in }
    var onRefused: () -> Void = {}
    var onEdit: () -> Void = {}
    var onBack: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shake: CGFloat = 0
    @State private var shakeTick = 0

    private var parsed: DobResult { parseDob(value, order: order) }

    private var age: Int? {
        if case let .valid(age, _) = parsed { return age }
        return nil
    }
    private var underAge: Bool { (age ?? minimumAge) < minimumAge }
    private var isValid: Bool { age != nil && !underAge }
    private var isError: Bool {
        if serverRejectedAge { return true }
        if parsed == .impossible { return true }
        if age != nil && underAge { return true }
        if attempted, parsed == .incomplete { return true }
        return false
    }

    private func submit() {
        // A second press while the first is in flight would write twice.
        if busy { return }
        if case let .valid(age, iso) = parsed, age >= minimumAge {
            onContinue(age, iso)
        } else {
            shakeTick += 1
            onRefused()
        }
    }

    var body: some View {
        BasicsScaffold(title: DobCopy.section, leading: .back, onBack: onBack, content: {
            // 3 of 3 — the only screen in the group where every segment is filled.
            StepProgress(steps: 3, current: BasicsStep.dob.progressSegment)
                .padding(.bottom, 18)

            WashHeadline(parts: [("What's your ", false), ("date of birth", true), ("?", false)],
                         fontSize: 32, lineHeightMultiple: 1.1)
                .padding(.bottom, 8)

            Text(DobCopy.sub)
                .font(F.manrope(14.5, .medium))
                .foregroundColor(.liqNeutral)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: 320, alignment: .leading)
                .padding(.bottom, 18)

            FloatingField(
                value: Binding(
                    get: { value },
                    // Digits only, re-formatted every time. Backspace therefore deletes a DIGIT
                    // and the slashes look after themselves.
                    set: { value = formatDob(dobDigits($0)) }
                ),
                label: DobCopy.label,
                placeholder: order.pattern,
                valid: isValid,
                error: isError,
                keyboard: .numberPad,
                capitalization: .never,
                onSubmit: submit
            )
            .offset(x: shake)
            .onChange(of: shakeTick) { _, _ in
                guard !reduceMotion else { return }
                let steps: [CGFloat] = [6, -6, 5, -5, 3, -3, 0]
                let start = Date()
                for (i, dx) in steps.enumerated() {
                    let deadline = start.addingTimeInterval(
                        Double(i) * Motion.shake / Double(steps.count))
                    Task { @MainActor in
                        try? await Task.sleep(for: .seconds(max(0, deadline.timeIntervalSinceNow)))
                        shake = dx
                    }
                }
            }

            // ⑨ reserved at 84 — the age card's height, the tallest of the four bodies.
            ZStack(alignment: .topLeading) {
                statusBody
            }
            .frame(maxWidth: .infinity, minHeight: 84, alignment: .topLeading)
            .padding(.top, 10)
            .accessibilityAddTraits(.updatesFrequently)
        }, cta: {
            VStack(spacing: 0) {
                ProfileVisibilityRow(hidden: $hideAge)
                Spacer().frame(height: 26)
                HStack {
                    Spacer(minLength: 0)
                    // Orange, not sunset — a routine step. Same silhouette as name and email.
                    NextButton(label: DobCopy.cta, arrowSize: 22, action: submit)
                }
                .padding(.bottom, 18)
            }
        })
    }

    @ViewBuilder
    private var statusBody: some View {
        if parsed == .impossible {
            InlineErrorCard(message: Text(DobCopy.invalid(order)))
        } else if (age != nil && underAge) || serverRejectedAge {
            // NO AGE NUMERAL IN STATE D. Printing "You're 15" back at a 15-year-old adds nothing
            // they do not know and hands a rejection a number.
            InlineErrorCard(message: Text(DobCopy.under18))
        } else if attempted, parsed == .incomplete {
            InlineErrorCard(message: Text(DobCopy.incomplete(order)))
        } else if let age {
            AgeCard(age: age, onEdit: onEdit)
        } else {
            // The reason the field is not scary: it says what is published and what is not.
            (Text(DobCopy.helperLead).font(F.manrope(13, .medium))
             + Text(DobCopy.helperBold).font(F.manrope(13, .bold))
             + Text(DobCopy.helperTail).font(F.manrope(13, .medium)))
                .foregroundColor(.liqMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// State B. The confirmation, inline.
private struct AgeCard: View {
    let age: Int
    let onEdit: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    var body: some View {
        HStack(spacing: 14) {
            Text("\(age)")
                .font(.custom(PS.loraBold, size: 44))
                .monospacedDigit()
                .foregroundColor(.liqPurple)
            VStack(alignment: .leading, spacing: 2) {
                Text(DobCopy.ageQuestion(age))
                    .font(F.manrope(14, .semibold))
                    .foregroundColor(.liqFg)
                Text(DobCopy.lockNote)
                    .font(F.manrope(12.5, .medium))
                    .foregroundColor(.liqMuted)
            }
            Spacer(minLength: 0)
            Button(action: onEdit) {
                Text(DobCopy.edit)
                    .font(F.manrope(14, .bold))
                    .foregroundColor(.liqPurple)
                    .underline()
            }
            .frame(minHeight: ComponentSizes.minTapTarget)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 16)
                // Lavender, not green — a question to answer, not a success message.
                .fill(LinearGradient(colors: [Color(hex: 0xF2EAFB), Color(hex: 0xF8F2FB)],
                                     startPoint: .top, endPoint: .bottom))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.liqPurple.opacity(0.22), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel(DobCopy.ageQuestion(age))
        // su-confirm-in: opacity 0->1 with a 6pt rise, once, on entering the state.
        .opacity(shown ? 1 : 0)
        .offset(y: shown ? 0 : 6)
        .onAppear {
            guard !reduceMotion else { shown = true; return }
            withAnimation(.easeOut(duration: Motion.confirm)) { shown = true }
        }
    }
}

/// The visibility band (callout ⑪).
///
/// NOT `MarketingOptIn`, and the five differences are why: transparent under a hairline divider
/// rather than a raised card, box on the LEFT, hit area capped to the switch and its label instead
/// of the full row, an eye-off mark, and a 700/14 label against that row's 500/13.
///
/// **Checked means the age is not DISPLAYED. It is still used for matching.** The choice hides a
/// value; it never excludes the user.
struct ProfileVisibilityRow: View {
    @Binding var hidden: Bool

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.liqBorderSoft).frame(height: 1)
            Button {
                hidden.toggle()
            } label: {
                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(hidden ? Color.liqPurple : Color.liqElevated)
                        RoundedRectangle(cornerRadius: 6)
                            .strokeBorder(hidden ? Color.liqPurple : Color.liqBorder, lineWidth: 1.5)
                        if hidden { CheckGlyph(size: 13, color: .liqElevated, lineWidth: 3) }
                    }
                    .frame(width: 22, height: 22)

                    HStack(spacing: 6) {
                        BrandIconView(icon: .eyeOff, size: 14, stroke: 2.1,
                                      tint: hidden ? .liqPurple : .liqSubtle)
                        Text(DobCopy.visibility)
                            .font(F.manrope(14, .bold))
                            .foregroundColor(.liqFg)
                    }
                }
                // Capped to the switch and its label, NOT the full row: an edge-to-edge target
                // above the CTA invites a mis-tap on the control the user actually meant.
                .frame(minHeight: 56)
                .padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(hidden ? [.isButton, .isSelected] : .isButton)
            .accessibilityLabel(DobCopy.visibility)
            .accessibilityValue(hidden ? "Hidden" : "Shown")
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

#Preview("A · empty") {
    ProfileDobView(value: .constant(""), hideAge: .constant(false))
}

#Preview("B · confirm") {
    ProfileDobView(value: .constant("03/22/1998"), hideAge: .constant(false))
}

#Preview("C · impossible") {
    ProfileDobView(value: .constant("02/30/1990"), hideAge: .constant(false))
}

#Preview("D · under 18") {
    ProfileDobView(value: .constant("05/19/2015"), hideAge: .constant(false))
}

#Preview("A + press") {
    ProfileDobView(value: .constant("03/22"), hideAge: .constant(false), attempted: true)
}

#Preview("B · age hidden") {
    ProfileDobView(value: .constant("03/22/1998"), hideAge: .constant(true))
}
