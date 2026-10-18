//
//  CheckInUplift.swift
//  ShowUp · Check-In 06 (SHOWUP-183): Uplift + the commit
//
//  The Swift twin of `checkin/CheckInUpliftScreen.kt`. `UpliftStep` from
//  `screen-checkin-uplift-reference.jsx`, inside the step shell with the ambient backdrop -- the one
//  step that has it. Five states: A on entry (always empty), B typing with the keyboard open, C
//  contact info blocked, D saving, E save failed. D and E are `CheckInFooter`'s `loading` and
//  `error`, added for this story to the 02 component -- a variant, not a fork.
//
//  THE WHOLE STEP BODY IS ONE SCROLL CONTAINER, the headline included: "with the keyboard open there
//  is no room to pin it". `Find my date` is pinned below it and rides directly above the keyboard;
//  the system keyboard is used, with return inserting a new line and sentence capitalisation. A tap
//  outside the field closes the keyboard -- measured against the field's own frame, so a tap ON the
//  field never shuts the keyboard the user has just opened.
//

import SwiftUI

/// The field's geometry from `UpliftStep` -- local to this field, which no other screen draws.
private let fieldMinHeight: CGFloat = 116
private let fieldPadding: CGFloat = 14
private let fieldBorder: CGFloat = 1.5
private let fieldRadius: CGFloat = Radius.card
/// UITextView's own text inset under `TextEditor`: 5 of line-fragment padding at the sides, 8 of
/// container inset at the top and bottom. Taken off the field's padding so the first character
/// lands where the reference puts it, 15.5 in.
private let editorInsetX: CGFloat = 5
private let editorInsetY: CGFloat = 8

struct CheckInUpliftView: View {
    let text: String
    var blocked: Bool = false
    var saving: Bool = false
    var saveFailed: Bool = false
    var toast: String? = nil
    var toastTick: Int = 0
    var onText: (String) -> Void = { _ in }
    var onFindMyDate: () -> Void = {}
    var onBack: () -> Void = {}
    var onClose: () -> Void = {}

    /// Keyboard focus and where the field sits -- disposable view state the model never reads.
    @FocusState private var focused: Bool
    @State private var fieldFrame: CGRect = .zero

    /// The body's own space, so the field's frame and a tap's location are measured alike.
    private static func space() -> NamedCoordinateSpace { .named("uplift-body") }

    var body: some View {
        CheckInStepShell(step: .uplift, onBack: onBack, onClose: onClose, ambient: true, footer: {
            CheckInFooter(label: CheckInCopy.findMyDate, onClick: onFindMyDate, dimmed: blocked,
                          toast: toast, toastTick: toastTick, loading: saving,
                          error: saveFailed && toast == nil ? CheckInCopy.saveError : nil)
        }) {
            // THE STEP BODY -- one scroll container, the only flexible element.
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    Color.clear.frame(height: Spacing.md)
                    CheckInStepHeadline(parts: CheckInCopy.upliftHeadline)
                    CoachNote(text: CheckInCopy.upliftNote, warm: false)
                        .padding(.top, 14)
                    field
                        .padding(.top, 14)
                        .background(GeometryReader { g in
                            Color.clear.preference(key: UpliftFieldFrameKey.self, value: g.frame(in: Self.space()))
                        })
                    Text(upliftCounterLabel(text))
                        .font(F.manrope(12, .regular))
                        .foregroundColor(.liqSubtle)
                        .padding(.top, Spacing.sm)
                        .accessibilityAddTraits(.updatesFrequently)
                    if blocked {
                        // `role="alert"`.
                        InlineErrorCard(message: Text(CheckInCopy.contactError))
                            .padding(.top, Spacing.lg)
                            .onAppear {
                                var alert = AttributedString(CheckInCopy.contactError)
                                alert.accessibilitySpeechAnnouncementPriority = .high
                                AccessibilityNotification.Announcement(alert).post()
                            }
                    }
                    inspirations
                        .padding(.top, 22)
                }
                .padding(.bottom, Spacing.xl)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .coordinateSpace(Self.space())
                .onPreferenceChange(UpliftFieldFrameKey.self) { fieldFrame = $0 }
                .onTapGesture(coordinateSpace: Self.space()) { at in
                    if !fieldFrame.contains(at) { focused = false }
                }
            }
        }
    }

    /// The field: min 116, padding 14, radius 16, Manrope 14 / 1.5, growing with the text. A 1.5
    /// border that is violet when focused and danger while it holds contact info -- with a 4 ring of
    /// the same at .10 and a danger tint at .04 -- and `--liq-border` otherwise. At .7 while the
    /// commit is in flight, read-only. BORDER-BOX: the 116 includes the border and the padding.
    private var field: some View {
        let stroke: Color = blocked ? .liqDanger : focused ? .liqPurple : .liqBorder
        let ring: Color = blocked ? Color.liqDanger.opacity(0.10) : focused ? Color.liqPurple.opacity(0.10) : .clear
        return ZStack(alignment: .topLeading) {
            if text.isEmpty {
                Text(CheckInCopy.upliftPlaceholder)
                    .font(F.manrope(14, .regular))
                    .foregroundColor(.liqSubtle)
                    .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 14, multiple: 1.5))
                    .padding(.horizontal, editorInsetX)
                    .padding(.vertical, editorInsetY)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
            TextEditor(text: Binding(
                get: { text },
                // The cut at 100 graphemes is the model's (`upliftChanged`): input past it is not
                // accepted, a paste is cut. A setter, never a getter, doing the work.
                set: { onText($0) }
            ))
            .font(F.manrope(14, .regular))
            .foregroundColor(.liqFg)
            .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 14, multiple: 1.5))
            .scrollContentBackground(.hidden)
            .textInputAutocapitalization(.sentences)
            .tint(.liqPurple)
            .focused($focused)
            .disabled(saving)
            .frame(minHeight: fieldMinHeight - 2 * (fieldPadding + fieldBorder) + 2 * editorInsetY)
            .accessibilityLabel(Text(CheckInCopy.upliftFieldLabel))
            .accessibilityHint(blocked ? Text(CheckInCopy.contactError) : Text(""))
        }
        .padding(.horizontal, fieldPadding + fieldBorder - editorInsetX)
        .padding(.vertical, fieldPadding + fieldBorder - editorInsetY)
        .frame(maxWidth: .infinity, minHeight: fieldMinHeight, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: fieldRadius)
            .fill(blocked ? Color.liqDanger.opacity(0.04) : Color.liqElevated))
        .overlay(RoundedRectangle(cornerRadius: fieldRadius).strokeBorder(stroke, lineWidth: fieldBorder))
        // `box-shadow: 0 0 0 4px` -- a flat ring OUTSIDE the border box, with no direction.
        .background(RoundedRectangle(cornerRadius: fieldRadius + 4).fill(ring).padding(-4))
        .opacity(saving ? 0.7 : 1)
    }

    /// Inspirations -- plain text, read as a list. Not tappable, not buttons, never copied in.
    private var inspirations: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(CheckInCopy.inspirationsLabel)
                .font(F.manrope(13, .bold))
                .foregroundColor(.liqFg)
                .padding(.bottom, Spacing.lg)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: Spacing.md) {
                ForEach(CheckInCopy.inspirations, id: \.self) { line in
                    // `textWrap: 'pretty'`, as the reference sets on each line. Manrope has no italic:
                    // the browser's 0.2 skew of the upright, as elsewhere (`TypeMetrics.oblique`).
                    PrettyText("\u{201C}\(line)\u{201D}",
                               font: TypeMetrics.oblique(PS.manropeRegular, 14, fallback: TypeMetrics.italicSystem(14, weight: .regular)),
                               lineHeight: 14 * 1.45, color: .liqMuted)
                }
            }
        }
    }
}

/// Where the field sits in the body, for "a tap outside the field".
private struct UpliftFieldFrameKey: PreferenceKey {
    static let defaultValue: CGRect = .zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) { value = nextValue() }
}

#Preview("Check-In 06 · A empty") { CheckInUpliftView(text: "") }
#Preview("Check-In 06 · C blocked") {
    CheckInUpliftView(text: "Text me on 0176 2345 6789 :)", blocked: true, toast: CheckInCopy.contactError)
}
#Preview("Check-In 06 · D saving") { CheckInUpliftView(text: "will take you out on my boat on Wannsee", saving: true) }
#Preview("Check-In 06 · E failed") { CheckInUpliftView(text: "will take you out on my boat on Wannsee", saveFailed: true) }
