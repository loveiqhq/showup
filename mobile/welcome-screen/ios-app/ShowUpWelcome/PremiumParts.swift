//
//  PremiumParts.swift
//  ShowUp · what the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186) both draw
//
//  The Swift twin of `search/PremiumParts.kt`: the `Liked you` badge (on a grid card and on the
//  profile's hero), the white label pill (a marked or closed card, the sorry sheet's reason, the list's
//  `Not now`), the undo toast, and the frame both screens stand in: top bar, the one scroll region, the
//  TabBar, and the toast 12 above it. Built once, here, because both stories consume them -- "one
//  implementation".
//

import SwiftUI

/// `PremiumLikedYouBadge`: a sunset pill, padding `4 9 4 7`, gap 4, the filled heart 11, `Liked you`
/// in Manrope 800 / 10.5 with `letter-spacing: 0.04` (px), lifted by `0 2px 8px rgba(0,0,0,.18)`. On a
/// grid card and on the profile's hero. Read as part of its card or hero, never on its own.
struct LikedYouBadge: View {
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        HStack(spacing: 4) {
            BrandIconView(icon: .heartFilled, size: 11, tint: .white)
            Text(PremiumCopy.likedYou)
                .font(F.manrope(10.5, .heavy))
                .tracking(0.04)
                .foregroundColor(.white)
                .lineLimit(1)
                // Shrinks rather than cuts where a large type outgrows a narrow card's corner.
                .minimumScaleFactor(0.6)
                .cssLineBox(10.5 * typeScale)
        }
        .padding(EdgeInsets(top: 4, leading: 7, bottom: 4, trailing: 9))
        .background(Capsule().fill(Gradients.sunset()).shadow(color: Color.black.opacity(0.18), radius: 4, y: 2))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(PremiumCopy.likedYou))
    }
}

/// The white label pill: `#fff`, 1px `--liq-border`, Manrope 800 / 11 in `--liq-fg-muted`, nowrap.
/// `5 10` on a grid card and the sorry sheet; `4 9` for the Likes list's `Not now` (`compact`).
struct PillLabel: View {
    let text: String
    var compact = false

    var body: some View {
        Text(text)
            .font(F.manrope(11, .heavy))
            .foregroundColor(.liqMuted)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, compact ? 9 : 10)
            .padding(.vertical, compact ? 4 : 5)
            .background(Capsule().fill(Color.liqElevated))
            .overlay(Capsule().strokeBorder(Color.liqBorder, lineWidth: 1))
    }
}

/// `LikesUndoToast` (Search 02 G, Search 03 D): min-height 52, padding `4 4 4 16`, radius 16,
/// `rgba(29,17,41,.92)`, gap 12, lifted by `0 8px 24px rgba(20,12,28,.22)`; `{name}'s like declined.`
/// in Manrope 600 / 14 / 1.35, white; `Undo` a 44-high button, padding `0 14`, 800 / 14 in orange.
///
/// Announced politely when it arrives ("role=status"). VoiceOver focus on `Undo` holds the window --
/// `onUndoFocus` tells the model, which owns the clock.
struct LikesUndoToast: View {
    let name: String
    let onUndo: () -> Void
    let onUndoFocus: (Bool) -> Void

    @AccessibilityFocusState private var undoFocused: Bool
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        HStack(spacing: 12) {
            Text(undoLine(name))
                .font(F.manrope(14, .semibold))
                .foregroundColor(.white)
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeSemi, size: 14, multiple: 1.35))
                .cssLineBox(14 * 1.35 * typeScale)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onUndo) {
                Text(PremiumCopy.undo)
                    .font(F.manrope(14, .heavy))
                    .foregroundColor(.liqOrange)
                    .lineLimit(1)
                    .fixedSize()
                    .padding(.horizontal, 14)
                    .frame(minHeight: ComponentSizes.minTapTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(PremiumCopy.undo))
            .accessibilityFocused($undoFocused)
            .onChange(of: undoFocused) { _, focused in onUndoFocus(focused) }
        }
        .padding(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 4))
        .frame(minHeight: 52)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.liqFg.opacity(0.92))
                .shadow(color: Color.liqPhotoInk.opacity(0.22), radius: 12, y: 8)
        )
        .accessibilityElement(children: .contain)
        .fitAnchor(FitAnchorID.undoToast)
        .onAppear {
            var announcement = AttributedString(undoLine(name))
            announcement.accessibilitySpeechAnnouncementPriority = .default
            AccessibilityNotification.Announcement(announcement).post()
        }
    }
}

/// The toast's arrival: `su-toast-in`, a 200 ms rise of 6 and fade on the brand curve, as the Check-In
/// toast arrives. Always one view, empty when there is no toast, so the frame always has its four
/// parts; the last toast stays drawn while it leaves, so its words do not blank mid-fade.
struct UndoToastSlot: View {
    let toast: UndoToast?
    let onUndo: () -> Void
    let onUndoFocus: (Bool) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if let toast {
                LikesUndoToast(name: toast.name, onUndo: onUndo, onUndoFocus: onUndoFocus)
                    .id(toast.userId)
                    .transition(.opacity.combined(with: .offset(y: 6)))
            }
        }
        .animation(reduceMotion ? nil : Motion.brand(Motion.reveal), value: toast)
    }
}

/// A Premium screen's frame: the top bar, the region between it and the TabBar (the list, the one
/// scroll container), the TabBar at the bottom, and the toast floating 16 from each side and 12 above
/// the TabBar -- "it does not scroll with the grid". No fixed Y: everything is measured.
struct PremiumFrameLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        proposal.replacingUnspecifiedDimensions()
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 4 else { return }
        let w = bounds.width
        let h = bounds.height
        let row = ProposedViewSize(width: w, height: nil)
        let top = subviews[0].sizeThatFits(row).height
        let tab = subviews[2].sizeThatFits(row).height
        let region = max(0, h - top - tab)
        subviews[0].place(at: bounds.origin, proposal: ProposedViewSize(width: w, height: top))
        subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.minY + top), proposal: ProposedViewSize(width: w, height: region))
        subviews[2].place(at: CGPoint(x: bounds.minX, y: bounds.maxY - tab), proposal: ProposedViewSize(width: w, height: tab))
        let side: CGFloat = 16
        let toastWidth = max(0, w - 2 * side)
        let toast = subviews[3].sizeThatFits(ProposedViewSize(width: toastWidth, height: region))
        subviews[3].place(at: CGPoint(x: bounds.minX + side, y: bounds.maxY - tab - 12 - toast.height),
                          proposal: ProposedViewSize(width: toastWidth, height: toast.height))
    }
}

/// A list being read: the spinner, named for VoiceOver.
struct ListLoading: View {
    let label: String

    var body: some View {
        Spinner(tint: .liqPurple, size: 24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(label))
    }
}

/// Space a list keeps clear above the toast: the Likes list's `88` bottom padding.
let toastClearance: CGFloat = 88
