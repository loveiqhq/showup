//
//  ProfilePrompts.swift
//  ShowUp · Profile creation 07 — Prompts, the written answers (SHOWUP-158)
//
//  The Swift port of `profile/ProfilePromptsScreen.kt`. Read that file's header for the argument;
//  the eight states and the conversion pass are identical and are not restated here.
//
//  THE PROGRESS BAR SCROLLS ON THIS SCREEN, unlike photos. Deliberate: "this screen carries more
//  above the fold and the bar is not worth the 26px. Do not 'fix' it into a third fixed row."
//
//  SAVE IS SUNSET AND CONTINUE IS ORANGE, on the same screen. Saving an answer is the commitment
//  beat; moving to the next step is routine. The two are never on screen at once.
//

import SwiftUI

/// Copy — final strings. Every one is quoted by `audit/verify-profile.py`.
enum PromptsCopy {
    static let headlineLead = "Your space to share something "
    static let headlineEm = "personal"
    static let headlineTail = "."
    static let sub = "One is enough to continue. Add up to 3 if you're enjoying yourself."

    static let sectionNoneSaved = "Start with one of these"
    static let sectionSomeSaved = "Add another · optional"
    static let writeThis = "Write this"
    static let browseAll = "Browse all 15 topics"

    static func counter(_ count: Int) -> String {
        count >= promptsRequired
            ? "\(count)/\(promptsMax) prompts · enough to continue"
            : "\(count)/\(promptsMax) prompts"
    }

    static let cta = "Continue"
    static let toast = "Write 1 prompt to continue"

    // MARK: the topic sheet
    static let topicHeadlineLead = "Choose a "
    static let topicHeadlineEm = "topic"
    static let topicHeadlineTail = "."
    static let topicSub = "Pick something you'd want a match to actually know."
    static let topicUsed = "Used"

    // MARK: the write sheet
    static let exampleEyebrow = "FOR EXAMPLE"
    static let placeholder = "Say it like you'd tell it to a friend…"
    static let floor = "One good sentence is enough."
    static let atCap = "That's the full 160 — short and specific lands harder anyway."
    static let emptySubmit = "Write a few words to save this prompt."

    /// PROPOSED — not yet approved.
    ///
    /// SHOWUP-158 specifies three status messages and this is a fourth: the ticket was written
    /// before `/me/prompts` existed, so a save could not fail. Worded to match the two the flow
    /// already has for a transport failure, and marked with the same `_PROPOSED` convention the
    /// rest of the flow uses for copy design has not signed off.
    static let saveFailedProposed = "We couldn't save that just now. Please try again."
    static let save = "Save"

    static let dismiss = "Dismiss"
    static let hideExample = "Hide example"
    static let editPrompt = "Edit prompt"
}

/// Which sheet is up, if any.
enum PromptSheet: Equatable, Codable {
    /// All fifteen. Opened by `Browse all 15 topics` only — never the critical path.
    case topics
    /// Write or edit one answer.
    ///
    /// `editing` is what makes Save overwrite rather than append, and it is carried on the sheet
    /// rather than derived from whether the topic is already used — a user can open the topic
    /// sheet, pick a topic, and be editing nothing at all.
    ///
    /// `entryPoint` is carried for the same reason `editing` is, and a sharper one: the registry
    /// says `entry_point` is "never inferred from whether a sheet was open - pass it through from
    /// the control that was tapped". Two of its three values have the sheet open, so there is
    /// nothing to infer from. It rides on the sheet so that `prompt_saved` and
    /// `prompt_editor_dismissed`, which happen later, still report the control that started this.
    case write(topicId: String, editing: Bool, entryPoint: PromptEntryPoint = .suggestion)
}

/// Everything the prompts screen renders.
///
/// ONE VALUE, HOISTED. The screen owns no asynchronous work — there is no prompt endpoint to call —
/// so this lives in `@SceneStorage`-backed state at the host rather than in an `@Observable`, which
/// is the rule in CLAUDE.md applied rather than abandoned.
///
/// `drafts` is a map and not a string because "the draft survives dismissal… and is restored when
/// that topic is reopened": a draft belongs to a TOPIC, not to the sheet currently up.
struct PromptsState: Equatable, Codable {
    var prompts: [SavedPrompt] = []
    var sheet: PromptSheet? = nil
    var drafts: [String: String] = [:]
    /// Set by an empty Save. Cleared by the first character typed, never by blur or a re-press.
    var nudge = false
    /// The topic whose worked example the user has dismissed.
    ///
    /// "The example is per sheet, not per session. Dismissing it hides it for that sheet only; the
    /// next prompt shows it again." One id rather than a set, because only one sheet is ever open.
    var exampleHiddenFor: String? = nil
    /// A save is in flight.
    ///
    /// Save is NEVER DISABLED — the group rule holds — so this changes nothing on screen. What it
    /// does is stop a second press starting a second request while the first is open.
    var saving = false
    /// The last save did not land.
    ///
    /// NEW GROUND. SHOWUP-158 has no failure state for Save, because there was no endpoint when it
    /// was written. A fourth message goes in the same RESERVED row, so nothing about the layout
    /// changes, and the sheet stays open with the text still in it.
    var failed = false
    /// When the sheet now up was opened, as seconds since the reference date. 0 when none is.
    ///
    /// `time_on_sheet_s` needs a start, and one field serves both sheets because only one is ever
    /// open. An absolute instant rather than an uptime ON PURPOSE: it is the only one still true
    /// after the scene is torn down, and a duration that reset when iOS reclaimed the app would
    /// report the abandonment it is measuring as having taken no time at all.
    var sheetOpenedAt: Double = 0
    /// When this visit to the step began. Feeds `time_on_step_s` on the accepted Continue.
    var stepStartedAt: Double = 0
    /// How many topics have been chosen in this visit to the step.
    ///
    /// `selection_index` is this plus one, and the rule the registry is emphatic about is that it
    /// COUNTS PER VISIT TO THE STEP, NOT PER SHEET: it does not reset when a sheet closes. Reset
    /// it per sheet and "which topic did they reach for first" quietly becomes "which topic did
    /// they reach for first in this sheet", which is a question nobody asked. It lives in the
    /// persisted state so a scene teardown does not restart the count either. An EDIT does not
    /// increment it.
    var topicSelections = 0
    /// Whether `prompts_minimum_met` has fired. Once, on the FIRST save, never again.
    var minimumReported = false
    /// The topic whose 160-character cap has already been reported in this editor session.
    ///
    /// `prompt_char_limit_reached` fires ONCE PER EDITOR SESSION, not per keystroke - otherwise
    /// every character typed at the cap is another row saying the same thing. Cleared when a sheet
    /// opens, which is what makes it per session rather than per topic.
    var charLimitReportedFor: String? = nil

    var count: Int { prompts.count }
    var canContinue: Bool { count >= promptsRequired }
    var usedTopicIds: [String] { prompts.map(\.topicId) }

    func draftFor(_ topicId: String) -> String { drafts[topicId] ?? "" }

    // MARK: persistence
    //
    // `@SceneStorage` takes primitives, so the whole value travels as one JSON string. Ugly, and
    // the alternative is worse: the flow README's rule 4a says a resumed step behaves like a
    // freshly-reached one WITH EVERYTHING ALREADY ENTERED STILL PRESENT, and a half-written prompt
    // lost to a rotation is exactly the failure that rule exists to prevent.
    //
    // Scene-scoped rather than a `UserDefaults` key, deliberately: a properly closed scene should
    // not resurrect a half-finished draft weeks later, which is the same reasoning the sign-up
    // flow's position already follows.

    var encoded: String {
        guard let data = try? JSONEncoder().encode(self) else { return "" }
        return String(decoding: data, as: UTF8.self)
    }

    /// Anything that does not decode is an empty screen rather than a crash: a stored value from
    /// an older build is a shape this one has never seen, and losing a draft is survivable where
    /// refusing to launch is not.
    static func decode(_ raw: String) -> PromptsState {
        guard !raw.isEmpty, let data = raw.data(using: .utf8),
              let state = try? JSONDecoder().decode(PromptsState.self, from: data)
        else { return PromptsState() }
        return state
    }
}

// MARK: - pieces

/// The card radius in this group.
///
/// 18, and deliberately not `Radius.card`, which is 16 and belongs to the sign-up flow's chrome.
/// These are reading surfaces with a serif question on them, drawn a step softer.
private let promptCardRadius: CGFloat = 18

/// A topic, on the screen, tappable.
///
/// THE SINGLE BIGGEST CHANGE IN THIS REVISION. It is a topic first and a control second: the
/// question is set in Lora at reading size, and the affordance is a small violet `Write this` row
/// underneath rather than a chevron — so the card reads as an invitation rather than a menu item.
private struct SuggestionCard: View {
    let topic: PromptTopic
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(topic.text)
                    .font(F.lora(16.5, bold: true))
                    .foregroundColor(.liqFg)
                    .lineSpacing(16.5 * 0.25)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 7) {
                    ZStack {
                        Circle().fill(Color.liqLavenderWash).frame(width: 20, height: 20)
                        BrandIconView(icon: .plus, size: 13, stroke: 2.6, tint: .liqPurple)
                    }
                    Text(PromptsCopy.writeThis)
                        .font(F.manrope(12.5, .bold))
                        .foregroundColor(.liqPurple)
                }
            }
            .padding(.horizontal, Spacing.xxl)
            .padding(.top, 14)
            .padding(.bottom, Spacing.xl)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: promptCardRadius).fill(Color.liqElevated))
            .overlay(RoundedRectangle(cornerRadius: promptCardRadius)
                .strokeBorder(Color.liqPurple.opacity(0.28), lineWidth: 1.5))
        }
        .buttonStyle(PressScale())
    }
}

/// The escape hatch to the full fifteen.
///
/// DELIBERATELY QUIETER than a suggestion card: it is the slower path, and it should look like it.
private struct BrowseAllButton: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(PromptsCopy.browseAll)
                .font(F.manrope(14, .bold))
                .foregroundColor(.liqNeutral)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .overlay(Capsule().strokeBorder(Color.liqBorder, lineWidth: 1.5))
        }
        .buttonStyle(PressScale())
    }
}

/// A saved answer.
///
/// THE ANSWER IS NEVER TRUNCATED. The card grows to fit it — a profile answer the user wrote and
/// then cannot read back is the screen quietly disagreeing with the 160 it allowed.
private struct FilledPromptCard: View {
    let prompt: SavedPrompt
    let onEdit: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(topicText(prompt.topicId))
                    .font(F.lora(16, bold: true))
                    .foregroundColor(.liqFg)
                    .lineSpacing(16 * 0.25)
                    .padding(.trailing, 32)
                    .fixedSize(horizontal: false, vertical: true)
                Text(prompt.answer)
                    .font(F.manrope(13.5, .medium))
                    .foregroundColor(.liqFg)
                    .lineSpacing(13.5 * 0.5)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, Spacing.xxl)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: promptCardRadius)
                .fill(LinearGradient(colors: Color.suGradLilac,
                                     startPoint: .top, endPoint: .bottom)))
            .overlay(RoundedRectangle(cornerRadius: promptCardRadius)
                .strokeBorder(Color.liqPurple.opacity(0.10), lineWidth: 1))

            // The pip DRAWS 30 at top 12 / right 12 and ANSWERS at 44, by the same arithmetic as
            // the photo grid's remove control.
            Button(action: onEdit) {
                ZStack {
                    Circle().fill(Color.white.opacity(0.7)).frame(width: 30, height: 30)
                    BrandIconView(icon: .pen, size: 14, stroke: 1.8, tint: .liqPurple)
                }
                .minTapTarget(alignment: .center)
            }
            .buttonStyle(PressScale())
            .accessibilityLabel(Text(PromptsCopy.editPrompt))
            .padding(5)
        }
    }
}

/// A character used as an ICON, drawn at a fixed size whatever the system font is set to.
///
/// Icons in this app are drawn in points and do not scale; two of them happen to be characters
/// rather than paths — the danger "!" in the failed photo slot and the one in the empty-submit
/// row. Both live inside a circle whose size is fixed by the layout around it, so scaling the
/// glyph only clips it. `.dynamicTypeSize(.large)` pins the subtree to the default step, which is
/// the supported way for a glyph to opt out.
///
/// Reading text is NEVER drawn through this. Everything a user reads scales.
struct UnscaledGlyph: View {
    let glyph: String
    let size: CGFloat

    var body: some View {
        Text(glyph)
            .font(F.lora(size, bold: true))
            .foregroundColor(.white)
            .dynamicTypeSize(.large)
    }
}

/// The drag grabber. Decorative: a sheet is dismissed by the X or the scrim, not by this.
private struct SheetGrabber: View {
    var body: some View {
        Capsule()
            .fill(Color.liqFg.opacity(0.18))
            .frame(width: 40, height: 4)
            .accessibilityHidden(true)
    }
}

/// The close X.
///
/// DRAWS 36 AND ANSWERS AT 44. The reference specifies a 36 control; 36 fails the floor every
/// tappable thing in this app is held to, and the Android fit harness flagged it at all seventeen
/// sizes the first time it was built that way. `minTapTarget` grows the hit area and nothing
/// visible moves.
private struct SheetCloseButton: View {
    let onClose: () -> Void

    var body: some View {
        Button(action: onClose) {
            ZStack {
                Circle().fill(Color.liqFg.opacity(0.04)).frame(width: 36, height: 36)
                BrandIconView(icon: .close, size: 20, stroke: 1.8, tint: .liqFg)
            }
            .minTapTarget(alignment: .center)
        }
        .buttonStyle(PressScale())
        .accessibilityLabel(Text(PromptsCopy.dismiss))
    }
}

/// The scrim and the rising surface both sheets sit in.
///
/// `sheet-rise` is 28 up and 0.85 → 1 opacity over `Motion.sheet` — a shared keyframe, not a
/// per-sheet animation, and skipped entirely when the device asks for no motion.
private struct SheetScaffold<Content: View>: View {
    let onDismiss: (SheetDismissMethod) -> Void
    @ViewBuilder let content: () -> Content

    @State private var shown = false
    /// How far the sheet has been pushed down by the finger now on it.
    ///
    /// SWIPE-DOWN, which the ticket asks for twice - "closing the sheet by X, scrim tap or swipe
    /// keeps what was typed", and again in the tracking criteria - and which nothing here
    /// implemented. A bottom sheet that cannot be pushed down is wrong on a phone regardless of
    /// the ticket. It only counts as a dismissal past a threshold, so a small nudge springs back.
    @State private var drag: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// A thumb's travel: far enough that a scroll inside the sheet cannot trigger it by accident,
    /// near enough that the gesture does not feel resisted. Local, because it is this gesture's
    /// threshold and nothing else's.
    private static let swipeDismiss: CGFloat = 120

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.liqFg.opacity(0.42)
                .ignoresSafeArea()
                // THE SCRIM IS `backdrop`, NOT `close`. Four values, four different acts:
                // the registry unified them on 16 September 2026 precisely because three
                // spellings of the same four acts had drifted apart, and folding two of them
                // together here would put the drift back inside one screen.
                .onTapGesture { onDismiss(.backdrop) }
                .accessibilityLabel(Text(PromptsCopy.dismiss))
                .accessibilityAddTraits(.isButton)

            content()
                .offset(y: (shown ? 0 : 28) + drag)
                .opacity(shown ? 1 : 0.85)
                .gesture(
                    DragGesture()
                        // Downwards only. Dragging a bottom sheet UP would detach it from the
                        // edge it is docked to, and the ticket forbids positioning either sheet
                        // by a top offset.
                        .onChanged { drag = max(0, $0.translation.height) }
                        .onEnded { value in
                            if value.translation.height > Self.swipeDismiss {
                                onDismiss(.swipe)
                            }
                            drag = 0
                        }
                )
                // The keyboard is not ours, and its height is not knowable. The safe-area inset
                // for the keyboard is what keeps the field, the status row and Save above it
                // without anybody guessing — and SwiftUI applies it by default, which is why
                // there is no modifier here and no number anywhere.
                .onAppear {
                    if reduceMotion {
                        shown = true
                    } else {
                        withAnimation(.timingCurve(0.22, 1, 0.36, 1, duration: Motion.sheet)) {
                            shown = true
                        }
                    }
                }
        }
    }
}

// MARK: - the topic sheet

/// All fifteen, in three named groups.
///
/// A USED TOPIC IS DISABLED, NEVER HIDDEN. "The list never changes length or order between visits"
/// — a list that shortened as the user used it would move every remaining topic under their finger
/// between the first prompt and the second.
private struct TopicPickerSheet: View {
    let used: [String]
    let onPick: (String, Int) -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .top) {
                SheetGrabber().frame(maxWidth: .infinity)
                SheetCloseButton(onClose: onClose)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .padding(.top, Spacing.md)
            .padding(.trailing, Spacing.md)

            VStack(alignment: .leading, spacing: Spacing.sm) {
                WashHeadline(
                    parts: [(PromptsCopy.topicHeadlineLead, false),
                            (PromptsCopy.topicHeadlineEm, true),
                            (PromptsCopy.topicHeadlineTail, false)],
                    fontSize: 26, lineHeightMultiple: 1.1, trackingEm: -0.018
                )
                Text(PromptsCopy.topicSub)
                    .font(F.manrope(14, .medium))
                    .foregroundColor(.liqNeutral)
                    .lineSpacing(14 * 0.45)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 24)
            .padding(.top, 14)
            .padding(.bottom, Spacing.xl)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    ForEach(Array(topicGroups.enumerated()), id: \.element.id) { index, group in
                        Text(group.label)
                            .font(F.manrope(10.5, .heavy))
                            .tracking(0.84)
                            .foregroundColor(.liqSubtle)
                            .padding(.leading, 2)
                            .padding(.top, index == 0 ? 2 : Spacing.xl)
                        ForEach(group.topics) { topic in
                            topicRow(topic)
                        }
                    }
                    Color.clear.frame(height: 18)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, Spacing.xs)
            }
        }
        // maxHeight 600 in the reference. A cap rather than a height: on a short phone the list
        // scrolls inside what is left, and on a tall one the sheet stops well short of the header
        // so the screen is still visible behind it.
        .frame(maxHeight: 600)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32)
                .fill(Color.liqCream)
        )
    }

    @ViewBuilder
    private func topicRow(_ topic: PromptTopic) -> some View {
        let isUsed = used.contains(topic.id)
        // Flat across the whole sheet rather than within the group: "the row's index" is what a
        // person scanning the list sees, and the group boundaries are already carried by
        // `topic_group`.
        let position = promptTopics.firstIndex { $0.id == topic.id } ?? 0
        Button { if !isUsed { onPick(topic.id, position) } } label: {
            HStack(spacing: Spacing.xl) {
                Text(topic.text)
                    .font(F.manrope(14.5, .semibold))
                    .foregroundColor(.liqFg)
                    .lineSpacing(14.5 * 0.3)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                if isUsed {
                    Text(PromptsCopy.topicUsed)
                        .font(F.manrope(10.5, .heavy))
                        .tracking(0.63)
                        .foregroundColor(.liqSubtle)
                } else {
                    BrandIconView(icon: .plus, size: 16, stroke: 2.4, tint: .liqPurple)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, Spacing.lg)
            .frame(minHeight: 50)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Capsule().fill(isUsed ? Color.liqFg.opacity(0.03) : Color.liqElevated))
            .overlay(Capsule()
                .strokeBorder(isUsed ? Color.liqBorder : Color.liqPurple, lineWidth: 1.5))
            // A used topic is DISABLED, not hidden: it keeps its place and reads back at 55%.
            .opacity(isUsed ? 0.55 : 1)
        }
        .buttonStyle(PressScale())
        .disabled(isUsed)
    }
}

// MARK: - the write sheet

/// Write or edit one answer.
///
/// THE STATUS ROW IS RESERVED, NOT CONDITIONAL — `min-height: 34`, always present, carrying the
/// floor line in the calm state. It is what keeps the field, Save and the sheet's own height
/// identical between calm, at-cap and empty-submit.
///
/// THE CAP COLOUR IS THE ORANGE TOKEN, NOT THE DANGER TOKEN. Deliberate, and the thing in this
/// sheet most likely to be "corrected" later: red says you did something wrong, and writing to the
/// end of the box is not wrong.
private struct WritePromptSheet: View {
    let topicId: String
    let draft: String
    let nudge: Bool
    let failed: Bool
    let exampleHidden: Bool
    let onDraftChange: (String) -> Void
    let onHideExample: () -> Void
    let onSave: () -> Void
    let onClose: () -> Void

    @FocusState private var focused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var length: Int { draft.count }
    private var atCap: Bool { length >= promptMaxChars }
    private var isEmpty: Bool { promptAnswerIsEmpty(draft) }
    private var showCounter: Bool { length >= promptCounterFrom }
    private var showNudge: Bool { nudge && isEmpty }

    private var borderColor: Color {
        if atCap { return .liqOrange }
        if showNudge { return .liqDanger }
        return isEmpty ? .liqBorder : .liqPurple
    }
    private var haloColor: Color {
        if atCap { return Color.liqOrange.opacity(0.12) }
        if showNudge { return Color.liqDanger.opacity(0.10) }
        return isEmpty ? .clear : Color.liqPurple.opacity(0.10)
    }

    var body: some View {
        // THE SHEET ASKS FOR 560 AND TAKES LESS WHEN THERE IS LESS.
        //
        // `minHeight: 560` on its own was a bug, and the Android fit harness found it the first
        // time it was given a keyboard: on a 320 x 686 phone with a 300dp IME there are 362 of
        // screen left, the column demanded 560, and `Save` measured ZERO HEIGHT. A control the
        // user cannot see, on the only screen where it is the way out. The same arithmetic applies
        // here -- an iOS keyboard is shorter, which makes it the same bug one phone further down.
        //
        // The scroll is what makes the minimum a minimum rather than a demand: with room nothing
        // scrolls and the sheet is exactly what it was; without room it scrolls, which is the
        // answer `WelcomeScaffold(scrollWhenTight)` already reached -- "between a CTA the user
        // cannot reach and a few points of scroll, the scroll is the right failure."
        ScrollView(.vertical, showsIndicators: false) {
            content
        }
        // Bounce off, so a sheet that already fits does not rubber-band and read as a scroll view
        // that happens to be full.
        .scrollBounceBehavior(.basedOnSize)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32)
                .fill(Color.liqCream)
        )
        // The field takes focus as the sheet arrives, so the keyboard is already up and the user
        // types without a second tap.
        .onAppear { focused = true }
        .modifier(ShakeOnce(key: showNudge ? 1 : 0, active: showNudge && !reduceMotion))
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .top) {
                SheetGrabber().frame(maxWidth: .infinity)
                SheetCloseButton(onClose: onClose)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .padding(.top, Spacing.md)

            WashHeadline(parts: [(topicText(topicId), false)],
                         fontSize: 23, lineHeightMultiple: 1.18, trackingEm: -0.018)
                .padding(.top, 14)
                .padding(.bottom, Spacing.lg)
                .padding(.trailing, 40)

            // THE EXAMPLE IS A CARD ABOVE THE FIELD AND NOT A PLACEHOLDER. A placeholder disappears
            // at the first keystroke, which is exactly when the user still wants someone else's
            // sentence in front of them. Dismissible, because a user who already knows does not.
            if !exampleHidden {
                HStack(alignment: .top, spacing: Spacing.lg) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(PromptsCopy.exampleEyebrow)
                            .font(F.manrope(10, .heavy))
                            .tracking(0.8)
                            .foregroundColor(.liqPurple)
                        Text(exampleFor(topicId))
                            .font(F.manrope(13, .medium))
                            .foregroundColor(.liqNeutral)
                            .lineSpacing(13 * 0.45)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    // 22 drawn, 44 answered. The overflow reaches about 11 over the example's
                    // text, which is not itself tappable, so the only effect is that a near-miss
                    // dismisses the example rather than doing nothing.
                    Button(action: onHideExample) {
                        ZStack {
                            Circle().fill(Color.liqPurple.opacity(0.10))
                                .frame(width: 22, height: 22)
                            BrandIconView(icon: .close, size: 13, stroke: 2.2, tint: .liqPurple)
                        }
                        .minTapTarget(alignment: .center)
                    }
                    .buttonStyle(PressScale())
                    .accessibilityLabel(Text(PromptsCopy.hideExample))
                }
                .padding(.leading, 14)
                .padding(.trailing, Spacing.xl)
                .padding(.top, Spacing.lg)
                .padding(.bottom, 11)
                .background(RoundedRectangle(cornerRadius: 14).fill(Color.liqLavenderWash))
                .overlay(RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(Color.liqPurple.opacity(0.14), lineWidth: 1))
                .padding(.bottom, Spacing.xl)
            }

            field

            // RESERVED AT 34, in every state.
            HStack(alignment: .top, spacing: Spacing.md) {
                // The failure sits ABOVE the nudge in this order because it is the more recent
                // thing that happened: a user whose save failed has a non-empty field, so the two
                // cannot both be true anyway.
                if failed {
                    ZStack {
                        Circle().fill(Color.liqDanger).frame(width: 18, height: 18)
                        UnscaledGlyph(glyph: "!", size: 12)
                    }
                    .padding(.top, 1)
                    Text(PromptsCopy.saveFailedProposed)
                        .font(F.manrope(13.5, .medium))
                        .foregroundColor(.liqDangerFg)
                        .fixedSize(horizontal: false, vertical: true)
                } else if showNudge {
                    ZStack {
                        Circle().fill(Color.liqDanger).frame(width: 18, height: 18)
                        // AN ICON, NOT READING TEXT. See `UnscaledGlyph`.
                        UnscaledGlyph(glyph: "!", size: 12)
                    }
                    .padding(.top, 1)
                    Text(PromptsCopy.emptySubmit)
                        .font(F.manrope(13.5, .medium))
                        .foregroundColor(.liqDangerFg)
                        .fixedSize(horizontal: false, vertical: true)
                } else if atCap {
                    Text(PromptsCopy.atCap)
                        .font(F.manrope(13, .semibold))
                        .foregroundColor(.liqOrange)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Text(PromptsCopy.floor)
                        .font(F.manrope(13, .medium))
                        .foregroundColor(.liqSubtle)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .frame(minHeight: 34, alignment: .top)
            .padding(.top, Spacing.lg)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.updatesFrequently)

            // SUNSET, and NEVER DISABLED. An empty press produces the nudge above, not nothing.
            PrimaryButton(PromptsCopy.save, variant: .sunset, action: onSave)
                .padding(.top, Spacing.lg)
        }
        .padding(.horizontal, 24)
        .padding(.top, Spacing.md)
        .padding(.bottom, Spacing.xxl)
        .frame(minHeight: 560, alignment: .top)
    }

    private var field: some View {
        ZStack(alignment: .bottomTrailing) {
            ZStack(alignment: .topLeading) {
                if draft.isEmpty {
                    Text(PromptsCopy.placeholder)
                        .font(F.manrope(15.5, .medium))
                        .foregroundColor(.liqSubtle)
                        .lineSpacing(15.5 * 0.5)
                        // Matches TextEditor's own inset, so the placeholder sits exactly where
                        // the first character will.
                        .padding(.leading, 5)
                        .padding(.top, 8)
                        .allowsHitTesting(false)
                }
                TextEditor(text: Binding(
                    get: { draft },
                    // THE CAP IS APPLIED HERE, SILENTLY. A paste over the limit is sliced and
                    // nothing is said about it. This is the ONE place a binding's setter does
                    // work, and it is a setter rather than a getter for the reason CLAUDE.md
                    // gives: a getter runs during view evaluation and desynchronises the editing
                    // session.
                    set: { onDraftChange(cappedAnswer($0)) }
                ))
                .font(F.manrope(15.5, .medium))
                .foregroundColor(.liqFg)
                .lineSpacing(15.5 * 0.5)
                .scrollContentBackground(.hidden)
                .focused($focused)
                // rows={4} / min-height 116, so 160 characters fit without an inner scrollbar.
                .frame(minHeight: 116)
            }
            if showCounter {
                Text("\(length)/\(promptMaxChars)")
                    .font(F.manrope(12, .bold))
                    // tabular figures: 99/160 and 100/160 must not change the numeral's width, or
                    // the counter twitches sideways as the user types.
                    .monospacedDigit()
                    .foregroundColor(atCap ? .liqOrange : .liqSubtle)
                    .padding(.trailing, -2)
                    .padding(.bottom, -24)
            }
        }
        .padding(.horizontal, Spacing.xxl)
        .padding(.top, 14)
        // Bottom 34 rather than 16: the counter sits inside the box, and the text must not run
        // under it.
        .padding(.bottom, 34)
        .background(RoundedRectangle(cornerRadius: Spacing.xxl).fill(Color.liqElevated))
        .overlay(RoundedRectangle(cornerRadius: Spacing.xxl)
            .strokeBorder(borderColor, lineWidth: 1.5))
        // `box-shadow: 0 0 0 4px` — a flat ring OUTSIDE the border box, with no direction. Not a
        // `.shadow`, which is a blurred drop with an offset.
        .background(
            RoundedRectangle(cornerRadius: Spacing.xxl + 4)
                .fill(haloColor)
                .padding(-4)
        )
    }
}

// MARK: - the screen

struct ProfilePromptsView: View {
    var state: PromptsState = PromptsState()
    var onBack: () -> Void = {}
    var onOpenTopics: () -> Void = {}
    /// A suggestion card. The Int is which card, from 0 - `position` in the registry.
    var onWriteTopic: (String, Int) -> Void = { _, _ in }
    /// A row of the browse sheet. The Int is the row's index across the whole sheet.
    var onPickTopic: (String, Int) -> Void = { _, _ in }
    var onEditPrompt: (String) -> Void = { _ in }
    var onDraftChange: (String) -> Void = { _ in }
    var onHideExample: () -> Void = {}
    var onSave: () -> Void = {}
    var onDismissSheet: (SheetDismissMethod) -> Void = { _ in }
    var onContinue: () -> Void = {}
    /// Fires on the REFUSED press, never on render.
    var onRefused: () -> Void = {}
    /// Artboard only — forces the toast open.
    var previewToast = false

    @State private var toast = RefusalToastState()

    var body: some View {
        ZStack {
            RealYouScaffold(step: .prompts, onBack: onBack, progressFixed: false) {
                // THE BAR SCROLLS ON THIS SCREEN. Deliberate; see the file header.
                StepProgress(steps: RealYouStep.count, current: RealYouStep.prompts.progressSegment)
                    .padding(.bottom, 20)

                WashHeadline(
                    parts: [(PromptsCopy.headlineLead, false),
                            (PromptsCopy.headlineEm, true),
                            (PromptsCopy.headlineTail, false)],
                    fontSize: 30, lineHeightMultiple: 1.08, trackingEm: -0.018
                )
                .padding(.bottom, Spacing.md)

                Text(PromptsCopy.sub)
                    .font(F.manrope(14.5, .medium))
                    .foregroundColor(.liqNeutral)
                    .lineSpacing(14.5 * 0.45)
                    .frame(maxWidth: 330, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)

                if state.count > 0 {
                    VStack(alignment: .leading, spacing: Spacing.xl) {
                        // A NEW CARD APPEARS AT THE BOTTOM, not the top: the reading order stays
                        // chronological, so a user adding a second prompt does not find their
                        // first one has moved.
                        ForEach(state.prompts) { prompt in
                            FilledPromptCard(prompt: prompt) { onEditPrompt(prompt.topicId) }
                        }
                    }
                    .padding(.top, 18)
                }

                // AT 3 SAVED THE BLOCK IS ABSENT, not disabled. There is nothing left to suggest.
                if state.count < promptsMax {
                    Text(state.count == 0
                         ? PromptsCopy.sectionNoneSaved
                         : PromptsCopy.sectionSomeSaved)
                        .font(F.manrope(10.5, .heavy))
                        .tracking(0.84)
                        .foregroundColor(.liqSubtle)
                        .padding(.top, state.count == 0 ? 20 : 22)
                        .padding(.bottom, Spacing.lg)

                    VStack(alignment: .leading, spacing: Spacing.lg) {
                        ForEach(Array(suggestionsFor(used: state.usedTopicIds,
                                                     count: state.count == 0 ? 3 : 2)
                            .enumerated()), id: \.element.id) { position, topic in
                            SuggestionCard(topic: topic) {
                                onWriteTopic(topic.id, position)
                            }
                        }
                        BrowseAllButton(onTap: onOpenTopics)
                    }
                }

                // NO lineLimit, and that is a correction rather than an omission.
                //
                // The ticket asks for one line at 390 (`white-space: nowrap`) and it is, at every
                // width in the matrix, at the default font size. At the largest accessibility size
                // it is not, and a line limit turned that into an ELLIPSIS on all seventeen
                // devices -- "1/3 prompts · enough to c…" -- which loses the half of the sentence
                // that says the requirement is met. nowrap describes the 1x layout; it is not a
                // promise to somebody using large type.
                Text(PromptsCopy.counter(state.count))
                    .font(F.manrope(13, .semibold))
                    .foregroundColor(state.canContinue ? .liqSuccessFg : .liqSubtle)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 14)
                    .padding(.leading, 2)

                Color.clear.frame(height: 20)
            } footer: {
                footerView
            }

            switch state.sheet {
            case .topics:
                SheetScaffold(onDismiss: onDismissSheet) {
                    TopicPickerSheet(used: state.usedTopicIds,
                                     onPick: onPickTopic,
                                     onClose: { onDismissSheet(.close) })
                }
            case .write(let topicId, _, _):
                SheetScaffold(onDismiss: onDismissSheet) {
                    WritePromptSheet(
                        topicId: topicId,
                        draft: state.draftFor(topicId),
                        nudge: state.nudge,
                        failed: state.failed,
                        exampleHidden: state.exampleHiddenFor == topicId,
                        onDraftChange: onDraftChange,
                        onHideExample: onHideExample,
                        onSave: onSave,
                        onClose: { onDismissSheet(.close) }
                    )
                }
            case nil:
                EmptyView()
            }
        }
    }

    private var footerView: some View {
        HStack {
            Spacer(minLength: 0)
            NextButton(label: PromptsCopy.cta, arrowSize: 22, circleSize: 52) {
                if state.canContinue {
                    onContinue()
                } else {
                    toast.show()
                    onRefused()
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, Spacing.xl)
        .padding(.bottom, 14)
        .refusalToast(RefusalToast(visible: toast.visible || previewToast,
                                   icon: .edit, message: PromptsCopy.toast))
    }
}

// MARK: - Previews

private let onePrompt = [SavedPrompt(
    topicId: "first_date_usually",
    answer: "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on "
        + "your mind this week. Bring it. I'll listen."
)]
private let threePrompts = onePrompt + [
    SavedPrompt(topicId: "hill_to_die_on",
                answer: "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer."),
    SavedPrompt(topicId: "cross_town_for",
                answer: "A proper conversation. An old cinema. The 8pm walk after a long day."),
]
private let midDraft = "Talk about anything real. Not jobs, not pets, not the weather."
private let fullDraft = cappedAnswer(
    "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your "
    + "mind this week. Bring it. I will listen for the entire thirty minutes!")

#Preview("A · 0 of 3") { ProfilePromptsView() }

#Preview("B · 1 of 3") { ProfilePromptsView(state: PromptsState(prompts: onePrompt)) }

#Preview("C · 3 of 3") { ProfilePromptsView(state: PromptsState(prompts: threePrompts)) }

#Preview("D · topic sheet") {
    ProfilePromptsView(state: PromptsState(prompts: onePrompt, sheet: .topics))
}

#Preview("E · write empty") {
    ProfilePromptsView(state: PromptsState(sheet: .write(topicId: "first_date_usually", editing: false)))
}

#Preview("F · write mid") {
    ProfilePromptsView(state: PromptsState(
        sheet: .write(topicId: "first_date_usually", editing: false),
        drafts: ["first_date_usually": midDraft]))
}

#Preview("G · write at cap") {
    ProfilePromptsView(state: PromptsState(
        sheet: .write(topicId: "first_date_usually", editing: false),
        drafts: ["first_date_usually": fullDraft]))
}

#Preview("H · write nudge") {
    ProfilePromptsView(state: PromptsState(
        sheet: .write(topicId: "first_date_usually", editing: false), nudge: true))
}

#Preview("toast · refused") { ProfilePromptsView(previewToast: true) }

#Preview("I · save failed") {
    ProfilePromptsView(state: PromptsState(
        sheet: .write(topicId: "first_date_usually", editing: false),
        drafts: ["first_date_usually": midDraft],
        failed: true))
}
