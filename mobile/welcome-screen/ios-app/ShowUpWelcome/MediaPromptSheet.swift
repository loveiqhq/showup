//
//  MediaPromptSheet.swift
//  ShowUp · the eleven prompts, for either medium (SHOWUP-161)
//
//  ─────────────────────────────────────────────────────────────────────────────
//  ONE COMPONENT WITH A `kind` PROP. TWO COPIES IS A BUG.
//  ─────────────────────────────────────────────────────────────────────────────
//
//  The ticket says exactly that, and it is worth reading twice because the two sheets differ in
//  three strings and nothing else: the sub line, the commit label, and the `type` on every event
//  they fire. Video and voice open THE SAME eleven, in THE SAME order.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  ROWS ARE RADIO-SELECT, NOT TAP-TO-LAUNCH
//  ─────────────────────────────────────────────────────────────────────────────
//
//  Tapping a row never opens the camera. The user can read all eleven and change their mind before
//  anything happens, and only the commit CTA leaves the sheet. That is also why
//  `media_prompt_selected` fires on the CTA rather than on a row: a tap is a considered look, not a
//  choice, and counting looks as choices would make the per-prompt numbers meaningless.
//
//  NOTHING IS PRESELECTED when this opens from an empty card — not even the previewed prompt. The
//  card previews it; the list does not pick it. Opening from a filled card's `Retake` DOES preselect
//  the answered prompt, so keeping it is one tap and changing it is two.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  THE DISABLED CTA IS THE SECOND DELIBERATE EXCEPTION TO FLOW RULE 2c
//  ─────────────────────────────────────────────────────────────────────────────
//
//  With nothing selected it is the quiet ghost variant reading `Choose a prompt to continue`, and it
//  is disabled — the same shape as `Verify code` on screen 03 and for the same reason: there is
//  nothing to validate and the label already names the requirement.
//
//  DO NOT GENERALISE THIS TO THE SCREEN'S OWN CONTINUE, which is never disabled in any state.
//

import SwiftUI

struct MediaPromptSheet: View {
    let kind: MediaKind
    var selectedId: String?
    var onPick: (String) -> Void = { _ in }
    var onCommit: () -> Void = {}
    var onClose: () -> Void = {}

    private var picked: MediaPrompt? { MediaPrompts.byId(selectedId) }

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .topTrailing) {
                SheetGrabber().frame(maxWidth: .infinity)
                SheetCloseButton(onClose: onClose)
                    .padding(.trailing, Spacing.md)
                    .padding(.top, Spacing.md)
            }
            .padding(.top, Spacing.md)

            VStack(alignment: .leading, spacing: Spacing.sm) {
                WashHeadline(
                    parts: [(MediaCopy.sheetHeadlineBefore, false),
                            (MediaCopy.sheetHeadlineEm, true),
                            (MediaCopy.sheetHeadlineAfter, false)],
                    fontSize: 25, lineHeightMultiple: 1.12, trackingEm: -0.018
                )
                // 36 of clearance on the trailing edge so the headline never runs under the X.
                .padding(.trailing, 36)
                Text(MediaCopy.sheetSub(kind))
                    .font(F.manrope(13.5, .medium))
                    .lineSpacing(13.5 * 0.45)
                    .foregroundColor(.liqNeutral)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.screenGutter)
            .padding(.top, 14)
            .padding(.bottom, Spacing.xl)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: Spacing.md) {
                    ForEach(MediaPrompts.all, id: \.id) { prompt in
                        PromptRow(prompt: prompt,
                                  selected: prompt.id == selectedId,
                                  onTap: { onPick(prompt.id) })
                    }
                    Color.clear.frame(height: Spacing.md)
                }
                .padding(.horizontal, Spacing.screenGutter)
            }
            // THE SCROLL MASK, from the reference's `maskImage`: opaque to 94% then fading out, so
            // a list that continues past the fold says so instead of being guillotined by the
            // commit row's top border.
            .mask(
                LinearGradient(
                    stops: [.init(color: .black, location: 0),
                            .init(color: .black, location: 0.94),
                            .init(color: .clear, location: 1)],
                    startPoint: .top, endPoint: .bottom
                )
            )

            VStack(spacing: 0) {
                // Argument order mirrors the stored properties: Swift's memberwise init is
                // positional and `action` is declared before `leading`. See
                // audit/check-swift-arg-order.py.
                PrimaryButton(
                    label: picked == nil ? MediaCopy.commitEmpty : MediaCopy.commit(kind),
                    variant: picked == nil ? .ghost : .sunset,
                    enabled: picked != nil,
                    action: onCommit,
                    leading: {
                        // The 8pt white dot the reference puts ahead of the committed label -- a
                        // small "you have chosen something" marker, not an icon.
                        if picked != nil {
                            Circle().fill(Color.white).frame(width: 8, height: 8)
                        }
                    }
                )
            }
            .padding(.horizontal, Spacing.screenGutter)
            .padding(.top, Spacing.xl)
            .padding(.bottom, 14)
            .background(Color.liqCream)
        }
        .frame(maxHeight: sheetMaxHeight)
        .background(Color.liqCream)
        .clipShape(
            UnevenRoundedRectangle(topLeadingRadius: sheetCornerRadius,
                                   topTrailingRadius: sheetCornerRadius)
        )
    }
}

/// One row.
///
/// THE OWN-IDEA ROW IS ALWAYS LAST AND ALWAYS VISUALLY DISTINCT — dashed border, sans-serif, its own
/// sub line. It never ranks and is never removed: a list of eleven is a help, not a cage.
private struct PromptRow: View {
    let prompt: MediaPrompt
    let selected: Bool
    let onTap: () -> Void

    private var borderColor: Color {
        if selected { return .liqPurple }
        return prompt.isOwn ? .liqBorder : .liqBorderSoft
    }

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .center, spacing: Spacing.xl) {
                ZStack {
                    if selected {
                        Circle().fill(Color.liqPurple)
                        BrandIconView(icon: .check, size: 12, stroke: 3.5, tint: .white)
                    } else {
                        Circle().strokeBorder(Color.liqFg.opacity(0.20), lineWidth: 1.5)
                    }
                }
                .frame(width: 22, height: 22)

                VStack(alignment: .leading, spacing: 2) {
                    Text(prompt.display)
                        .font(prompt.isOwn ? F.manrope(14, .bold) : F.lora(15, italic: true))
                        .lineSpacing((prompt.isOwn ? 14 : 15) * 0.3)
                        .foregroundColor(.liqFg)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if prompt.isOwn {
                        Text(MediaCopy.ownIdeaSub)
                            .font(F.manrope(12, .medium))
                            .lineSpacing(12 * 0.35)
                            .foregroundColor(.liqSubtle)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding(.init(top: 11, leading: 14, bottom: Spacing.xl, trailing: 14))
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: ComponentSizes.minTapTarget)
            // AnyShapeStyle, not a Group. `background(_:in:)` takes a ShapeStyle, and a Group of
            // two different styles is a View -- which is what the two branches would otherwise
            // build. Erasing them to one style is the difference between a fill and a subview.
            .background(
                selected ? AnyShapeStyle(Gradients.lilac()) : AnyShapeStyle(Color.white),
                in: RoundedRectangle(cornerRadius: 16)
            )
            .overlay(
                Group {
                    if prompt.isOwn && !selected {
                        // The dashed outline is the one thing that makes this row read as an escape
                        // hatch rather than a twelfth prompt.
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(borderColor,
                                          style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                    } else {
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(borderColor, lineWidth: 1.5)
                    }
                }
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale())
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
