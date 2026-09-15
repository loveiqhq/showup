//
//  ProfilePhotos.swift
//  ShowUp · Profile creation 06 — Photos (SHOWUP-156)
//
//  The Swift port of `profile/ProfilePhotosScreen.kt`. Read that file's header for the argument;
//  the seven states, the three rules and the permission surface are identical and are not restated
//  here. What follows is only what differs because this is SwiftUI.
//
//  EVERY SLOT IS 158 TALL IN EVERY STATE · THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD ·
//  CONTINUE IS NEVER DISABLED.
//
//  The permission alert, the system picker, the camera UI and the Settings page are the OS's. None
//  is drawn here, none is measured, and none has a copy string in `PhotosCopy`.
//

import SwiftUI

/// Copy — final strings. Every one is quoted by `audit/verify-profile.py`.
///
/// THE REQUIREMENT IS STATED THREE TIMES ON PURPOSE: as a sentence in `sub`, as a number in the
/// count row, and in `toastDefault` on a refused press. Each answers a different question — what am
/// I being asked, where am I up to, and why did nothing happen.
enum PhotosCopy {
    static let headlineLead = "The "
    static let headlineEm = "messy hair"
    static let headlineTail = ", the loud laugh."
    static let sub = "Show who you actually are — not a polished version. Four photos to continue, "
        + "six if you've got them."

    static let countLabel = "Photos · \(photosRequired) required, \(photosMax) max"
    static func countValue(_ filled: Int) -> String { "\(filled) of \(photosMax)" }

    /// What each slot asks for. Four categories, then two of `Anything you like`.
    static let slotHints = [
        "Portrait",
        "With friends",
        "Full body",
        "Favourite activity",
        "Anything you like",
        "Anything you like",
    ]
    static let firstSlotCtaHint = "Start with your face"

    static let optionalDivider = "Optional · slots 5 & 6"
    static let addMore = "Add more"
    static let reorderHint = "Drag to reorder · the first one is your main photo"
    static let mainBadge = "MAIN"
    static let cta = "Continue"

    static let uploading = "Uploading…"
    static let uploadFailed = "Upload failed"
    static let retry = "Retry"

    // MARK: the access card, one card and two modes
    static let accessTitle = "Photo access is needed to continue"
    /// `Photos` IS THE PLATFORM'S OWN LABEL FOR THE ROW, not a product term, and the ticket says to
    /// replace it with whatever the running version calls it rather than hard-coding this string.
    static let accessBlockedLead = "Show Up can't see your photos, so there is nothing to add. "
        + "Open Settings, turn on "
    static let accessBlockedRow = "Photos"
    static let accessBlockedTail = ", then come straight back — nothing you have entered is lost."
    static let accessAskBody =
        "Allow access so you can pick your photos. Nothing you have entered is lost."
    static let accessButtonBlocked = "Open Settings"
    static let accessButtonAsk = "Allow photo access"

    // MARK: the source sheet
    static let sheetTitle = "Add a photo"
    static let sheetLibrary = "Choose from library"
    static let sheetLibrarySub = "Pick one or more"
    static let sheetCamera = "Take a photo"
    static let sheetCameraSub = "Use the camera now"
    static let sheetCameraBlockedSub = "Camera access is off. Turn on Camera in Settings to use it."
    static let sheetCameraSettings = "Settings"

    // MARK: the toast, three variants
    static let toastDefault = "Upload at least \(photosRequired) photos to continue"
    static let toastBlocked = "Turn on Photos in Settings to continue"
    static let toastAsk = "Allow photo access to continue"

    /// Spoken by VoiceOver on a filled slot's remove pip, which is drawn and has no text.
    static func removeLabel(_ hint: String) -> String { "Remove \(hint)" }
}

// MARK: - shared drawing

/// The slot radius.
///
/// 20, and deliberately NOT `Radius.card`, which is 16 and means "a card in the sign-up flow". A
/// photo slot is a picture rather than a card, drawn a little softer so a face is not cropped by a
/// tight corner. Local because it means "a photo slot" and nothing else.
private let slotRadius: CGFloat = 20

/// A dashed rounded outline.
///
/// THE DASH PATTERN IS OURS AND THE REFERENCE DOES NOT SET IT. CSS `border: 1.5px dashed` leaves
/// the dash length and gap entirely to the user agent, so there is no number in the handoff to copy
/// and the PNG cannot be measured because the PNG wins on nothing. 6 on / 4 off at a 1.5 stroke
/// reads as dashed rather than dotted on a 158-tall card, and the same two numbers are used on
/// Android so the platforms agree by construction rather than by each guessing.
private struct DashedOutline: View {
    let color: Color
    let radius: CGFloat
    var width: CGFloat = 1.5

    var body: some View {
        RoundedRectangle(cornerRadius: radius)
            // strokeBorder, not stroke: it draws INSIDE the shape's bounds, so the dash does not
            // straddle the edge and the grid's 12 gap stays 12.
            .strokeBorder(color, style: StrokeStyle(lineWidth: width, dash: [6, 4]))
    }
}

/// A solid hairline at a given radius.
private struct SolidOutline: View {
    let color: Color
    let radius: CGFloat
    var width: CGFloat = 1.5

    var body: some View {
        RoundedRectangle(cornerRadius: radius)
            .strokeBorder(color, lineWidth: width)
    }
}

/// What a picked photo looks like while it is on screen.
///
/// A neutral lilac block rather than a decoded image, for now. The ticket is explicit that the
/// portrait art is a placeholder and that "the slots need real photography before any user-facing
/// build" — on a device this is where the picked image goes, cropped to fill.
///
/// Drawn rather than left blank because every treatment over it — the dimming, the ring, the remove
/// pip, the MAIN badge — is a treatment OVER an image, and a white rectangle would not show whether
/// any of them has enough contrast.
private struct PhotoFill: View {
    var body: some View {
        LinearGradient(colors: Color.suGradLilac, startPoint: .top, endPoint: .bottom)
    }
}

// MARK: - the slot

/// One tile in the grid. Four renderings, one height.
private struct PhotoSlotView: View {
    let photo: PickedPhoto?
    let hint: String
    let optional: Bool
    let cta: Bool
    var isMain = false
    var onTap: () -> Void = {}
    var onRemove: () -> Void = {}
    var onRetry: () -> Void = {}

    var body: some View {
        switch photo?.status {
        case .failed: FailedSlot(onRetry: onRetry)
        case .inFlight: UploadingSlot(progress: photo?.progress ?? 0)
        case .confirmed: FilledSlot(hint: hint, isMain: isMain, onTap: onTap, onRemove: onRemove)
        case nil: EmptySlot(hint: hint, optional: optional, cta: cta, onTap: onTap)
        }
    }
}

/// FAILED. Danger-tinted and never a solid red fill.
///
/// The same restraint as the flow's inline error card, for the same reason: a solid red tile reads
/// as damage rather than as one upload that has to be tried again. The slot KEEPS ITS 158 so the
/// grid does not reflow, and Retry is a real control rather than hypertext — it re-uploads into
/// this slot, so the user never re-picks.
private struct FailedSlot: View {
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 9) {
            ZStack {
                Circle().fill(Color.liqDanger).frame(width: 34, height: 34)
                // AN ICON, NOT READING TEXT. At 2x this became a 34pt "!" inside a 34pt circle
                // and clipped on every device in the matrix. See `UnscaledGlyph`.
                UnscaledGlyph(glyph: "!", size: 17)
            }
            Text(PhotosCopy.uploadFailed)
                .font(F.manrope(13, .bold))
                .foregroundColor(.liqDangerFg)
                .multilineTextAlignment(.center)
            // The pill DRAWS 32 and ANSWERS at 44 — `minTapTarget` grows the hit area without the
            // pill itself changing, which would open the 9 gaps either side of it.
            Button(action: onRetry) {
                Text(PhotosCopy.retry)
                    .font(F.manrope(12.5, .bold))
                    .foregroundColor(.liqDangerFg)
                    .padding(.horizontal, 15)
                    .padding(.vertical, Spacing.xs)
                    // A MINIMUM, not a fixed height. 32 is what the reference draws and what this
                    // is at every default font size; at 2x a 12.5pt label needs about 34, and a
                    // fixed 32 clipped it on all seventeen devices.
                    .frame(minHeight: 32)
                    .background(Capsule().fill(Color.liqElevated))
                    .overlay(Capsule().strokeBorder(Color.liqDanger.opacity(0.34), lineWidth: 1.5))
                    .minTapTarget(alignment: .center)
            }
            .buttonStyle(PressScale())
        }
        .padding(.horizontal, Spacing.xl)
        .frame(maxWidth: .infinity)
        .frame(height: photoSlotHeight)
        .background(RoundedRectangle(cornerRadius: slotRadius)
            .fill(Color.liqDanger.opacity(0.06)))
        .overlay(DashedOutline(color: .liqDanger.opacity(0.34), radius: slotRadius))
    }
}

/// UPLOADING. The picked image, dimmed, under a DETERMINATE ring.
///
/// Not a spinner on an empty slot, and that is the whole design of this state: the user has to see
/// WHICH photo is in flight, which an empty tile with a spinner cannot say, and whether it is
/// moving, which an indeterminate spinner cannot say either.
private struct UploadingSlot: View {
    let progress: Double

    var body: some View {
        ZStack {
            PhotoFill()
            Color.liqFg.opacity(0.44)
            VStack(spacing: Spacing.lg) {
                // A 38 disc swept to `progress` with a 28 disc over it — the conic gradient the
                // reference draws, expressed as the two arcs it actually is. Drawn rather than a
                // `ProgressView`, whose track, cap and stroke width are the system's and none of
                // which match.
                ZStack {
                    Circle().fill(Color.white.opacity(0.30))
                    Circle()
                        .trim(from: 0, to: max(0, min(1, progress)))
                        .fill(Color.white)
                        .rotationEffect(.degrees(-90))
                    Circle().fill(Color.liqFg.opacity(0.62)).frame(width: 28, height: 28)
                }
                .frame(width: 38, height: 38)
                Text(PhotosCopy.uploading)
                    .font(F.manrope(12, .bold))
                    .foregroundColor(.white)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: photoSlotHeight)
        .clipShape(RoundedRectangle(cornerRadius: slotRadius))
    }
}

/// FILLED. The image, a remove pip, and on slot 1 the MAIN badge.
private struct FilledSlot: View {
    let hint: String
    let isMain: Bool
    let onTap: () -> Void
    let onRemove: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Button(action: onTap) { PhotoFill() }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
                .frame(height: photoSlotHeight)

            // REMOVE IS IMMEDIATE AND HAS NO CONFIRMATION. Re-adding is one tap; a dialog here is
            // friction on the screen with the most taps in the flow.
            //
            // The pip DRAWS 28 at top 8 / right 8 and ANSWERS at 44. Because 44 = 28 + 8 + 8, a 44
            // hit area centred on the pip reaches exactly the slot's corner — the touch target and
            // the drawing agree without either being nudged.
            Button(action: onRemove) {
                ZStack {
                    Circle().fill(Color.liqMuted).frame(width: 28, height: 28)
                    BrandIconView(icon: .close, size: 14, stroke: 2.6, tint: .white)
                }
                .minTapTarget(alignment: .center)
            }
            .buttonStyle(PressScale())
            .accessibilityLabel(Text(PhotosCopy.removeLabel(hint)))
            .padding(8)

            if isMain {
                Text(PhotosCopy.mainBadge)
                    .font(F.manrope(10, .heavy))
                    .tracking(0.6)
                    .foregroundColor(.liqFg)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Color.white.opacity(0.92)))
                    .padding(8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            }
        }
        .frame(height: photoSlotHeight)
        .clipShape(RoundedRectangle(cornerRadius: slotRadius))
    }
}

/// EMPTY. Three tones: the first-slot call to action, a required slot, an optional one.
private struct EmptySlot: View {
    let hint: String
    let optional: Bool
    let cta: Bool
    let onTap: () -> Void

    private var borderColor: Color {
        if cta { return .liqPurple.opacity(0.46) }
        return optional ? .liqFg.opacity(0.18) : .liqPurple.opacity(0.36)
    }
    private var ink: Color { optional && !cta ? .liqSubtle : .liqPurple }
    private var pipInk: Color { cta ? .white : ink }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Spacing.lg) {
                ZStack {
                    // The CTA tone is a SOLID violet pip on the lilac wash, so a completely empty
                    // grid has one obvious place to start rather than four equal ones.
                    Circle().fill(cta ? Color.liqPurple : Color.liqElevated)
                    if !cta {
                        Circle().strokeBorder(
                            optional ? Color.liqFg.opacity(0.12) : Color.liqPurple.opacity(0.20),
                            lineWidth: 1)
                    }
                    BrandIconView(icon: .plus, size: 20, stroke: 2.4, tint: pipInk)
                }
                .frame(width: 40, height: 40)

                Text(hint)
                    .font(F.manrope(13, .semibold))
                    .foregroundColor(ink)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 130)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 14)
            .frame(maxWidth: .infinity)
            // A FLOOR, and this is the one place the "every slot is 158" rule bends — only for the
            // system font, and only upwards.
            //
            // The rule exists so the grid does not reflow BETWEEN STATES: a slot must not change
            // height when its photo starts uploading. It still cannot. What it may now do is be
            // taller than 158 on a phone whose type is at 2x, where "Start with your face" takes
            // four lines under a 40 pip and does not fit in 158. At every default font size this
            // resolves to exactly 158 and nothing moves.
            .frame(minHeight: photoSlotHeight)
            .background {
                if cta {
                    RoundedRectangle(cornerRadius: slotRadius)
                        .fill(LinearGradient(colors: Color.suGradLilac,
                                             startPoint: .top, endPoint: .bottom))
                } else {
                    RoundedRectangle(cornerRadius: slotRadius).fill(Color.liqRaised)
                }
            }
            .overlay(DashedOutline(color: borderColor, radius: slotRadius))
        }
        .buttonStyle(PressScale())
    }
}

// MARK: - the count row

/// The 0 → 6 scale, stated plainly: what is required, what is allowed, and where the user stands.
///
/// The numeral turning `liqSuccessFg` at four is THE ONLY SUCCESS AFFORDANCE ON THE SCREEN. There
/// is no tick, no banner and no colour change on the CTA — the CTA is never disabled and therefore
/// never changes state — so this one numeral carries the whole "you are done" signal.
private struct PhotoCount: View {
    let filled: Int

    var body: some View {
        HStack {
            // NO lineLimit. The ticket asks for one line at 390 (`white-space: nowrap`) and it is
            // one line at every width in the matrix at the default font size. At the largest
            // accessibility size it is not, and a line limit made that an ELLIPSIS on the
            // narrowest phone — "Photos · 4 required, 6 m…" — which cuts the maximum out of the
            // sentence that states it.
            Text(PhotosCopy.countLabel)
                .font(F.manrope(10.5, .heavy))
                .tracking(0.84)
                .foregroundColor(.liqSubtle)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: Spacing.md)
            Text(PhotosCopy.countValue(filled))
                .font(F.manrope(13, .bold))
                .monospacedDigit()
                .foregroundColor(filled >= photosRequired ? .liqSuccessFg : .liqFg)
        }
    }
}

// MARK: - the access card, one card and two modes

/// D and E. THE GRID IS REPLACED, not disabled.
///
/// Six tappable slots that cannot open anything is the worst state a mandatory step can be in, so
/// the count row, the grid, `Add more` and the reorder hint are all absent here.
///
/// LILAC, NOT DANGER. The user made a choice; they did not make a mistake.
///
/// ONE CARD, TWO MODES, and collapsing them is the specific failure the ticket names: "how a screen
/// ends up telling a user to open Settings when it could simply have asked".
///
/// UNREACHABLE ON THIS PLATFORM. `SystemPhotoAccess.library()` cannot return anything but
/// `.notNeeded`, so nothing can put this on an iPhone. It is built for parity with Android ≤ 12,
/// where it is real — see `PhotoAccess.swift`.
private struct AccessCard: View {
    let canAsk: Bool
    let onAllow: () -> Void
    let onOpenSettings: () -> Void

    private var body_: Text {
        if canAsk { return Text(PhotosCopy.accessAskBody) }
        // The row's name in bold, because the user has to FIND it: neither platform can deep-link
        // to a single permission toggle, so they land on a list and scan.
        return Text(PhotosCopy.accessBlockedLead)
            + Text(PhotosCopy.accessBlockedRow).font(F.manrope(13.5, .heavy))
            + Text(PhotosCopy.accessBlockedTail)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: Spacing.xl) {
                ZStack {
                    Circle().fill(Color.liqPurple).frame(width: 34, height: 34)
                    BrandIconView(icon: .lock, size: 17, stroke: 2.2, tint: .white)
                }
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(PhotosCopy.accessTitle)
                        .font(F.manrope(15, .heavy))
                        .tracking(-0.15)
                        .foregroundColor(.liqFg)
                        .fixedSize(horizontal: false, vertical: true)
                    body_
                        .font(F.manrope(13.5, .medium))
                        .foregroundColor(.liqNeutral)
                        .lineSpacing(13.5 * 0.45)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            // Violet and not sunset: this is the user getting back to where they already were, not
            // a commitment beat. 48 / 15 is the reference's `size="md"`.
            PrimaryButton(canAsk ? PhotosCopy.accessButtonAsk : PhotosCopy.accessButtonBlocked,
                          variant: .violet, height: 48, labelSize: 15,
                          action: canAsk ? onAllow : onOpenSettings)
        }
        .padding(.horizontal, Spacing.xxl)
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 18).fill(Color.liqLavender.opacity(0.12)))
        .overlay(SolidOutline(color: .liqPurple.opacity(0.16), radius: 18, width: 1))
        .padding(.top, 18)
    }
}

// MARK: - the source sheet

/// F and G. What "+" opens.
///
/// A SLOT TAP DOES NOT OPEN THE PICKER DIRECTLY. It asks where the photo comes from, and this sheet
/// is the only place the camera can be offered: the library path needs no permission and the camera
/// path always does, so the two cannot share one control.
///
/// THE SHEET IS OURS; WHAT IT LAUNCHES IS THE OS'S. The scrim dismisses it and the grid stays
/// visible behind it, because the user is choosing a source rather than leaving the screen.
///
/// A DENIED CAMERA BLOCKS NOTHING. In `.blocked` the camera row goes quiet and grows a `Settings`
/// pill; THE LIBRARY ROW IS UNTOUCHED and the step is still completable.
private struct PhotoSourceSheet: View {
    let camera: CameraAccess
    let onLibrary: () -> Void
    let onCamera: () -> Void
    let onCameraSettings: () -> Void
    let onDismiss: () -> Void

    private var blocked: Bool { camera == .blocked }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.liqFg.opacity(0.42)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
                .accessibilityLabel(Text("Dismiss"))
                .accessibilityAddTraits(.isButton)

            VStack(alignment: .leading, spacing: 0) {
                Capsule()
                    .fill(Color.liqFg.opacity(0.18))
                    .frame(width: 38, height: 4)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 14)
                    .accessibilityHidden(true)

                Text(PhotosCopy.sheetTitle)
                    .font(F.manrope(15, .heavy))
                    .tracking(-0.15)
                    .foregroundColor(.liqFg)
                    .padding(.bottom, Spacing.xl)

                SheetRow(icon: .image, iconSize: 18,
                         pipBg: .liqPurple, pipInk: .white,
                         title: PhotosCopy.sheetLibrary, subtitle: PhotosCopy.sheetLibrarySub,
                         onTap: onLibrary)

                SheetRow(icon: blocked ? .lock : .camera, iconSize: blocked ? 16 : 18,
                         pipBg: blocked ? Color.liqPurple.opacity(0.14) : .liqPurple,
                         pipInk: blocked ? .liqPurple : .white,
                         title: PhotosCopy.sheetCamera,
                         subtitle: blocked
                            ? PhotosCopy.sheetCameraBlockedSub
                            : PhotosCopy.sheetCameraSub,
                         quiet: blocked,
                         trailingPill: blocked ? PhotosCopy.sheetCameraSettings : nil,
                         onTap: blocked ? onCameraSettings : onCamera)
                    .padding(.top, Spacing.lg)
            }
            .padding(.horizontal, Spacing.xxl)
            .padding(.top, Spacing.lg)
            .padding(.bottom, Spacing.xxl)
            .frame(maxWidth: .infinity)
            .background(
                UnevenRoundedRectangle(topLeadingRadius: 22, topTrailingRadius: 22)
                    .fill(Color.liqCream)
            )
        }
    }
}

/// One row of the source sheet. 62 tall, radius 16, pip 38.
private struct SheetRow: View {
    let icon: BrandIcon
    let iconSize: CGFloat
    let pipBg: Color
    let pipInk: Color
    let title: String
    let subtitle: String
    var quiet = false
    var trailingPill: String? = nil
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 13) {
                ZStack {
                    Circle().fill(pipBg).frame(width: 38, height: 38)
                    BrandIconView(icon: icon, size: iconSize, stroke: 2.2, tint: pipInk)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .font(F.manrope(15, .bold))
                        .foregroundColor(.liqFg)
                    Text(subtitle)
                        .font(F.manrope(12.5, .medium))
                        .foregroundColor(.liqSubtle)
                        .lineSpacing(12.5 * 0.35)
                        .fixedSize(horizontal: false, vertical: true)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: Spacing.md)
                if let pill = trailingPill {
                    Text(pill)
                        .font(F.manrope(12.5, .bold))
                        .foregroundColor(.liqPurple)
                        .padding(.horizontal, Spacing.xl)
                        .frame(height: 30)
                        .background(Capsule().fill(Color.liqElevated))
                        .overlay(Capsule()
                            .strokeBorder(Color.liqPurple.opacity(0.32), lineWidth: 1.5))
                } else {
                    BrandIconView(icon: .chevronRight, size: 17, stroke: 2.2, tint: .liqFg)
                }
            }
            .padding(.leading, 14)
            .padding(.trailing, quiet ? Spacing.xl : 14)
            .padding(.vertical, quiet ? 11 : 0)
            // minHeight rather than height: the blocked camera row's subtitle is two lines on a
            // narrow phone, and a fixed 62 would clip it.
            .frame(minHeight: 62)
            .frame(maxWidth: .infinity)
            .background {
                if quiet {
                    RoundedRectangle(cornerRadius: Radius.card)
                        .fill(Color.liqLavender.opacity(0.10))
                        .overlay(SolidOutline(color: .liqPurple.opacity(0.16),
                                              radius: Radius.card, width: 1))
                } else {
                    RoundedRectangle(cornerRadius: Radius.card).fill(Color.liqRaised)
                }
            }
        }
        .buttonStyle(PressScale())
    }
}

// MARK: - the screen

struct ProfilePhotosView: View {
    var state: PhotoGridState = PhotoGridState()
    /// `.notNeeded` on iOS, always. See `PhotoAccess.swift`.
    var library: LibraryAccess = .notNeeded
    var camera: CameraAccess = .canAsk
    /// Whether the source sheet is up. Hoisted, so the screen stays a function of values.
    var sheetOpen = false
    var onBack: () -> Void = {}
    var onSlotTap: (Int) -> Void = { _ in }
    var onRemove: (Int) -> Void = { _ in }
    var onRetry: (Int) -> Void = { _ in }
    var onRevealOptional: () -> Void = {}
    var onReorder: (Int, Int) -> Void = { _, _ in }
    var onChooseLibrary: () -> Void = {}
    var onChooseCamera: () -> Void = {}
    var onCameraSettings: () -> Void = {}
    var onDismissSheet: () -> Void = {}
    var onAllowLibrary: () -> Void = {}
    var onOpenSettings: () -> Void = {}
    var onContinue: () -> Void = {}
    /// Fires on the REFUSED press, never on render.
    var onRefused: () -> Void = {}
    /// Artboard only — forces the refusal toast open. In the app it exists for 2.6 seconds after a
    /// refused Continue and never on arrival.
    var previewToast = false

    @State private var toast = RefusalToastState()

    private var denied: Bool { library != .notNeeded }
    private var canAsk: Bool { library == .canAsk }
    private var filled: Int { state.confirmedCount }

    private var toastMessage: String {
        if !denied { return PhotosCopy.toastDefault }
        return canAsk ? PhotosCopy.toastAsk : PhotosCopy.toastBlocked
    }

    var body: some View {
        ZStack {
            RealYouScaffold(step: .photos, onBack: onBack) {
                // Lora 700 / 32 / 1.08 / -0.018em, `messy hair` the single italic em.
                WashHeadline(
                    parts: [(PhotosCopy.headlineLead, false),
                            (PhotosCopy.headlineEm, true),
                            (PhotosCopy.headlineTail, false)],
                    fontSize: 32, lineHeightMultiple: 1.08, trackingEm: -0.018
                )
                .padding(.top, 2)

                Text(PhotosCopy.sub)
                    .font(F.manrope(14.5, .medium))
                    .foregroundColor(.liqNeutral)
                    .lineSpacing(14.5 * 0.45)
                    .frame(maxWidth: 320, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Spacing.md)

                if denied {
                    AccessCard(canAsk: canAsk, onAllow: onAllowLibrary,
                               onOpenSettings: onOpenSettings)
                } else {
                    PhotoCount(filled: filled)
                        .padding(.top, Spacing.xxl)

                    PhotoGridRows(state: state, first: 0, count: photosRequired,
                                  onSlotTap: onSlotTap, onRemove: onRemove, onRetry: onRetry,
                                  onReorder: onReorder)
                        .padding(.top, 14)

                    if state.optionalRevealed {
                        HStack(spacing: Spacing.md) {
                            Text(PhotosCopy.optionalDivider)
                                .font(F.manrope(10.5, .heavy))
                                .tracking(0.84)
                                .foregroundColor(.liqSubtle)
                            Rectangle().fill(Color.liqBorderSoft).frame(height: 1)
                        }
                        .padding(.top, 14)
                        .padding(.bottom, Spacing.md)

                        PhotoGridRows(state: state, first: photosRequired,
                                      count: photosMax - photosRequired,
                                      onSlotTap: onSlotTap, onRemove: onRemove, onRetry: onRetry,
                                      onReorder: onReorder)
                    } else {
                        // The ONLY way to reach slots 5–6 before the requirement is met. An
                        // outlined pill rather than hypertext, so it reads as a real control at
                        // the size of a tap target — and it is 44, which is the floor rather than
                        // a coincidence.
                        Button(action: onRevealOptional) {
                            HStack(spacing: Spacing.md) {
                                // 16, between IconSizes.sm (20) and nothing smaller. A glyph
                                // inside a pill next to a 14.5 label, which neither token sizes.
                                BrandIconView(icon: .plus, size: 16, stroke: 2.6, tint: .liqPurple)
                                Text(PhotosCopy.addMore)
                                    .font(F.manrope(14.5, .bold))
                                    .foregroundColor(.liqPurple)
                            }
                            .padding(.horizontal, 18)
                            .frame(height: ComponentSizes.minTapTarget)
                            .background(Capsule().fill(Color.liqElevated))
                            .overlay(Capsule()
                                .strokeBorder(Color.liqPurple.opacity(0.36), lineWidth: 1.5))
                        }
                        .buttonStyle(PressScale())
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, 14)
                    }

                    // FROM THE SECOND PHOTO, not the first: with one there is nothing to reorder.
                    if state.canReorder {
                        HStack(spacing: Spacing.sm) {
                            BrandIconView(icon: .sliders, size: 13, stroke: 2, tint: .liqSubtle)
                            Text(PhotosCopy.reorderHint)
                                .font(F.manrope(12, .semibold))
                                .foregroundColor(.liqSubtle)
                        }
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.top, Spacing.xl)
                    }
                }

                // The tail spacer. 24 so the last row clears the footer's gradient mask rather
                // than ending underneath it — the one place a fixed height inside the scroll is
                // correct, because it is a margin rather than a position.
                Color.clear.frame(height: Spacing.screenGutter)
            } footer: {
                footerView
            }

            if sheetOpen {
                PhotoSourceSheet(camera: camera, onLibrary: onChooseLibrary,
                                 onCamera: onChooseCamera, onCameraSettings: onCameraSettings,
                                 onDismiss: onDismissSheet)
            }
        }
    }

    /// The sticky footer: a gradient mask, and Continue.
    ///
    /// The mask lets the scroll region end underneath the CTA without a hard edge — content fades
    /// into the canvas colour rather than being cut by it. `Continue` is orange, never sunset:
    /// these are routine screens, and sunset is reserved for commitment beats.
    private var footerView: some View {
        HStack {
            Spacer(minLength: 0)
            // 52, not the 56 every other screen draws. Both "The real you" references pass
            // `size={52}`: this footer is a band over scrolling content rather than the end of a
            // column, and the larger circle reads heavier against it.
            NextButton(label: PhotosCopy.cta, arrowSize: 22, circleSize: 52) {
                if state.meetsMinimum {
                    onContinue()
                } else {
                    toast.show()
                    onRefused()
                }
            }
        }
        .padding(.vertical, Spacing.sm)
        .padding(.horizontal, 20)
        .padding(.top, Spacing.xl)
        .background(
            LinearGradient(
                stops: [
                    .init(color: Color.liqCream.opacity(0), location: 0.00),
                    .init(color: Color.liqCream.opacity(0.92), location: 0.22),
                    .init(color: Color.liqCream, location: 1.00),
                ],
                startPoint: .top, endPoint: .bottom)
        )
        .refusalToast(RefusalToast(visible: toast.visible || previewToast,
                                   icon: .camera, message: toastMessage))
    }
}

/// A 2-column run of slots, with drag-to-reorder.
///
/// `first` and `count` rather than a list, so the required four and the optional two are the same
/// component at two offsets rather than two grids that have to be kept in step.
private struct PhotoGridRows: View {
    let state: PhotoGridState
    let first: Int
    let count: Int
    let onSlotTap: (Int) -> Void
    let onRemove: (Int) -> Void
    let onRetry: (Int) -> Void
    let onReorder: (Int, Int) -> Void

    @State private var dragging: Int? = nil
    @State private var drag: CGSize = .zero
    @State private var cell: CGSize = .zero

    var body: some View {
        VStack(spacing: Spacing.xl) {
            ForEach(0..<((count + 1) / 2), id: \.self) { row in
                // `.fixedSize` on neither cell and equal frames on both: a slot may grow past
                // 158 at a large system font, and a row with one tall cell and one short one would
                // read as a broken grid rather than a roomy one. `HStack` with `.top` alignment
                // plus `maxHeight: .infinity` on each cell makes them match the taller.
                HStack(alignment: .top, spacing: Spacing.xl) {
                    ForEach(0..<2, id: \.self) { column in
                        let index = first + row * 2 + column
                        if index < first + count {
                            slot(index)
                        } else {
                            Color.clear.frame(maxWidth: .infinity)
                        }
                        // fixedSize on the row so it hugs the taller cell rather than filling the
                        // scroll view: `maxHeight: .infinity` on a cell would otherwise make each
                        // row take the whole viewport.
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder
    private func slot(_ index: Int) -> some View {
        let filled = state.confirmedCount
        PhotoSlotView(
            photo: state.at(index),
            hint: slotHint(index: index, confirmedCount: filled),
            optional: isOptionalSlot(index),
            cta: filled == 0 && index == 0,
            isMain: index == 0 && filled > 0,
            onTap: { onSlotTap(index) },
            onRemove: { onRemove(index) },
            onRetry: { onRetry(index) }
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(GeometryReader { geo in
            Color.clear.onAppear { cell = geo.size }.onChange(of: geo.size) { _, new in cell = new }
        })
        .offset(dragging == index ? drag : .zero)
        // Above its neighbours while it moves, so the tile the user is holding is the one on top.
        .zIndex(dragging == index ? 1 : 0)
        .gesture(reorderGesture(index))
    }

    /// Long press first, then drag.
    ///
    /// WHY NOT AN IMMEDIATE DRAG. The grid lives inside the screen's only scrolling region, and a
    /// slot that started dragging on the first point of movement would steal every vertical scroll
    /// that happened to begin on a photo — which, with six 158-tall tiles, is most of the screen.
    /// The long press is what lets the same finger both scroll the screen and move a photo.
    ///
    /// WHY THE ARITHMETIC IS NOT HERE. `reorderTarget` is a plain function over the cell size and
    /// the gap, so every offset can be checked in a test without a device.
    private func reorderGesture(_ index: Int) -> some Gesture {
        let enabled = state.canReorder && state.at(index) != nil
        return LongPressGesture(minimumDuration: 0.3)
            .sequenced(before: DragGesture())
            .onChanged { value in
                guard enabled else { return }
                if case .second(true, let drag?) = value {
                    dragging = index
                    self.drag = drag.translation
                }
            }
            .onEnded { _ in
                guard enabled, dragging == index else { return }
                let to = reorderTarget(from: index, dx: drag.width, dy: drag.height,
                                       cellWidth: cell.width, cellHeight: cell.height,
                                       gap: Spacing.xl, count: state.photos.count)
                dragging = nil
                drag = .zero
                if to != index { onReorder(index, to) }
            }
    }
}

// MARK: - Previews
//
// The ticket's device matrix. 375 × 667 is checked first and is where the grid, the count row and
// the footer compete for height; 430 × 932 is where the whole grid fits above the fold.

private func confirmedPhotos(_ n: Int) -> [PickedPhoto] {
    (0..<n).map { PickedPhoto(localId: Int64($0), uri: nil, status: .confirmed) }
}

#Preview("A · empty") { ProfilePhotosView() }

#Preview("B · partial") {
    ProfilePhotosView(state: PhotoGridState(photos: confirmedPhotos(2)))
}

#Preview("C · uploading + failed") {
    ProfilePhotosView(state: PhotoGridState(photos: [
        PickedPhoto(localId: 0, uri: nil, status: .confirmed),
        PickedPhoto(localId: 1, uri: nil, status: .inFlight, progress: 0.62),
        PickedPhoto(localId: 2, uri: nil, status: .failed),
    ]))
}

#Preview("D · library blocked") { ProfilePhotosView(library: .blocked) }

#Preview("E · library can ask") { ProfilePhotosView(library: .canAsk) }

#Preview("F · source sheet") {
    ProfilePhotosView(state: PhotoGridState(photos: confirmedPhotos(2)), sheetOpen: true)
}

#Preview("G · camera blocked") {
    ProfilePhotosView(state: PhotoGridState(photos: confirmedPhotos(2)),
                      camera: .blocked, sheetOpen: true)
}

#Preview("toast · refused") { ProfilePhotosView(previewToast: true) }

#Preview("six of six") {
    ProfilePhotosView(state: PhotoGridState(photos: confirmedPhotos(6), optionalRevealed: true))
}
