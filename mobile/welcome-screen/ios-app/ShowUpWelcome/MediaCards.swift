//
//  MediaCards.swift
//  ShowUp · the two slot cards, empty and filled (SHOWUP-161)
//
//  Every number here comes from `profile/screen-media-reference.jsx`. The ticket's own order of
//  authority says so: "reference file wins on numbers · ticket wins on behaviour, scope and copy ·
//  PNG wins on nothing".
//
//  ─────────────────────────────────────────────────────────────────────────────
//  THERE IS NO UPLOAD PATH AND NO CAPTION FIELD
//  ─────────────────────────────────────────────────────────────────────────────
//
//  Both were removed on 16 September 2026 and both removals are acceptance criteria, so they are
//  written down here rather than left as an absence somebody helpfully fills in later:
//
//    · Media is captured in the app or not at all. No library picker, no "upload instead" link, on
//      either card, in any state, on either platform.
//    · The chosen prompt IS the caption. There is no 50-character field after the take. It renders
//      read-only under `Prompt · shown on your profile` and cannot be left blank, which is more
//      than could be said for anything the user typed under time pressure.
//
//  The CTA is `See the prompts`, not `Record`. "Record asks for a performance with no brief; the
//  list IS the brief, so it comes first."
//

import AVFoundation
import SwiftUI

/// Copy for the media step, quoted from the ticket's "Copy — final strings".
enum MediaCopy {
    static let optionalPill = "Optional · you can skip this"
    static let headlineBefore = "Show your face. Let them "
    static let headlineEm = "hear you"
    static let headlineAfter = "."
    static let skip = "Skip for now"
    static let continueLabel = "Continue"

    static let videoTitle = "A 10-second video"
    static let videoHint = "Filmed here in the app. Ten seconds, one prompt."
    static let voiceTitle = "A 15-second voice note"
    static let voiceHint = "Just your voice, answering one prompt."
    static let previewEyebrow = "One of 11 prompts"
    static let seeThePrompts = "See the prompts"

    static let captionEyebrow = "Prompt · shown on your profile"
    static let videoRowLabel = "Your video"
    static let saved = "Saved"
    static let retake = "Retake"
    static let delete = "Delete"

    static let sheetHeadlineBefore = "Pick one to "
    static let sheetHeadlineEm = "answer"
    static let sheetHeadlineAfter = "."
    static let sheetSubVideo =
        "Ten seconds is short on purpose. Pick the easiest one — nobody is marking this."
    static let sheetSubVoice =
        "Fifteen seconds, just your voice. Pick the easiest one — nobody is marking this."
    static let ownIdeaSub = "Say or show whatever you like — your own words, your own idea."
    static let commitEmpty = "Choose a prompt to continue"
    static let commitVideo = "Film 10 seconds"
    static let commitVoice = "Record 15 seconds"

    static let cancel = "Cancel"
    static let rec = "REC"
    static let voiceHintRecording = "Listening · keep going"
    static let voiceHintReview = "Hear it back before you keep it"
    static let useClip = "Use this clip"
    static let useRecording = "Use this recording"
    static let recordedSuffix = " recorded"

    static let playVideo = "Play your clip"
    static let playVoice = "Play your recording"
    static let stop = "Stop recording"

    /// The card's own failure surface. Never a modal — it must not block leaving the screen.
    static let uploadFailed = "Upload failed"
    static let retry = "Retry"
    static let uploading = "Uploading"

    static func title(_ kind: MediaKind) -> String { kind == .video ? videoTitle : voiceTitle }
    static func hint(_ kind: MediaKind) -> String { kind == .video ? videoHint : voiceHint }
    static func sheetSub(_ kind: MediaKind) -> String {
        kind == .video ? sheetSubVideo : sheetSubVoice
    }
    static func commit(_ kind: MediaKind) -> String {
        kind == .video ? commitVideo : commitVoice
    }
    static func usePrimary(_ kind: MediaKind) -> String {
        kind == .video ? useClip : useRecording
    }
}

private let cardRadius: CGFloat = 20
private let innerRadius: CGFloat = 14

/// The shared card surface, so the elevation lives in one place.
private struct MediaCardSurface<Content: View>: View {
    var padding: EdgeInsets
    var spacing: CGFloat
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) { content() }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.liqElevated, in: RoundedRectangle(cornerRadius: cardRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cardRadius)
                    .strokeBorder(Color.liqBorderSoft, lineWidth: 1)
            )
            .shadow(color: Color.liqFg.opacity(0.06), radius: 10, x: 0, y: 4)
    }
}

/// The chosen prompt, read-only, under a recorded artefact.
///
/// Replaces the 50-character caption input removed on 16 September 2026: the prompt the user
/// answered is a better caption than anything they would type under time pressure, it is already in
/// the product's voice, and it can never be left blank.
struct PromptCaption: View {
    let prompt: String

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            // A DASHED rule, at the top edge only. `border` would box all four sides.
            Rectangle()
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                .foregroundColor(.liqBorderSoft)
                .frame(height: 1)
                .padding(.bottom, Spacing.lg)
            Text(MediaCopy.captionEyebrow.uppercased())
                .font(F.manrope(10.5, .bold))
                .tracking(0.08 * 10.5)
                .foregroundColor(.liqSubtle)
            Text(prompt)
                .font(F.lora(15, italic: true))
                .lineSpacing(15 * 0.35)
                .foregroundColor(.liqFg)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// An empty slot.
///
/// DELIBERATELY COMPACT. The whole empty state — pill, headline, both cards and both CTAs — has to
/// sit above the fold on a 390x844 with nothing cut off, and that budget is why the removed
/// manifesto card is not coming back.
struct MediaSlotCard: View {
    let kind: MediaKind
    let preview: MediaPrompt
    var blocker: MediaBlocker?
    var platformLabel: String = ""
    var onChoose: () -> Void = {}
    var onPermissionAction: () -> Void = {}

    var body: some View {
        MediaCardSurface(
            padding: .init(top: 14, leading: 14, bottom: 13, trailing: 14),
            spacing: Spacing.lg
        ) {
            HStack(alignment: .top, spacing: Spacing.xl) {
                ZStack {
                    RoundedRectangle(cornerRadius: 13).fill(Gradients.lilac())
                    BrandIconView(icon: kind == .video ? .video : .mic,
                                  size: 20, stroke: 1.8, tint: .liqPurple)
                }
                .frame(width: 42, height: 42)

                VStack(alignment: .leading, spacing: 3) {
                    Text(MediaCopy.title(kind))
                        .font(F.lora(17, bold: true))
                        .tracking(-0.01 * 17)
                        .foregroundColor(.liqFg)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(MediaCopy.hint(kind))
                        .font(F.manrope(13, .medium))
                        .lineSpacing(13 * 0.4)
                        .foregroundColor(.liqNeutral)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            // The previewed prompt — the answer to "where do I start". One real prompt, verbatim,
            // so the list is understood before it opens. Which prompt this is comes from the
            // server; see MediaPrompts.preview.
            VStack(alignment: .leading, spacing: 2) {
                Text(MediaCopy.previewEyebrow.uppercased())
                    .font(F.manrope(9.5, .heavy))
                    .tracking(0.08 * 9.5)
                    .foregroundColor(.liqPurple)
                Text(preview.display)
                    .font(F.lora(14.5, italic: true))
                    .lineSpacing(14.5 * 0.3)
                    .foregroundColor(.liqFg)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.init(top: Spacing.md, leading: Spacing.xl,
                           bottom: Spacing.lg, trailing: Spacing.xl))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Gradients.lilac(), in: RoundedRectangle(cornerRadius: innerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: innerRadius)
                    .strokeBorder(Color.liqPurple.opacity(0.10), lineWidth: 1)
            )

            // The permission row, in the RESERVED status region above the CTA — the same place, and
            // the same treatment, as screen 06's camera row. Nothing is drawn before the first
            // refusal: a user who has never been asked sees two clean cards.
            if let blocker {
                MediaPermissionRow(blocker: blocker, platformLabel: platformLabel,
                                   action: onPermissionAction)
            }

            // `md`, not the flow's full-width `lg`. See ComponentSizes.controlHeightMedium: this is
            // a button inside a card, and the 8pt it saves twice over is part of what makes the
            // empty state fit above the fold at 390x844.
            PrimaryButton(label: MediaCopy.seeThePrompts, variant: .sunset,
                          height: ComponentSizes.controlHeightMedium, labelSize: 15,
                          action: onChoose)
        }
    }
}

/// The blocked / can-still-ask row.
///
/// NO ARTBOARD EXISTS FOR THIS. Ten states are drawn and the two permission modes are not; the
/// ticket says the behaviour is binding and the treatment is not yet specified, and recommends
/// reusing the group's existing pattern rather than inventing a third. That is what this is: the
/// quiet lilac row with the violet lock glyph and a 30-tall action pill, as on screen 06's camera
/// row. Flagged back for a spec-sheet frame.
///
/// THE BUTTON SAYS WHAT IT WILL DO. `.canAsk` re-prompts in app and names no toggle; `.blocked`
/// opens our app's page and names the platform's own label for the row. One label for both is how a
/// screen ends up telling a user to open Settings when it could simply have asked.
struct MediaPermissionRow: View {
    let blocker: MediaBlocker
    let platformLabel: String
    let action: () -> Void

    private var canAsk: Bool { blocker.status == .canAsk }

    private var message: String {
        if canAsk {
            return blocker.capability == .camera
                ? "Allow camera access to film your 10 seconds."
                : "Allow microphone access to record."
        }
        return blocker.capability == .camera
            ? "\(platformLabel) access is off. Turn on \(platformLabel) in Settings to film."
            : "\(platformLabel) access is off. Turn on \(platformLabel) in Settings to record."
    }

    private var actionLabel: String {
        if canAsk {
            return blocker.capability == .camera ? "Allow camera access" : "Allow microphone access"
        }
        return "Settings"
    }

    var body: some View {
        HStack(spacing: Spacing.lg) {
            BrandIconView(icon: .lock, size: 15, stroke: 1.8, tint: .liqPurple)
            Text(message)
                .font(F.manrope(12, .medium))
                .lineSpacing(12 * 0.35)
                .foregroundColor(.liqNeutral)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: action) {
                Text(actionLabel)
                    .font(F.manrope(11.5, .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .padding(.horizontal, Spacing.xl)
                    .frame(height: 30)
                    .background(Color.liqPurple, in: Capsule())
                    // Draws 30 and answers at 44, like everything tappable here.
                    .minTapTarget(alignment: .center)
            }
            .buttonStyle(PressScale())
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Gradients.lilac(), in: RoundedRectangle(cornerRadius: innerRadius))
    }
}

/// A recorded video.
///
/// The thumbnail is a REAL FRAME from the take, pulled with `AVAssetImageGenerator`. A placeholder
/// tile would be the honest thing only if no frame were reachable; one is, and a card that does not
/// show what was filmed cannot be checked by the person who filmed it.
struct RecordedVideoCard: View {
    let artefact: MediaArtefact
    var onPlay: () -> Void = {}
    var onRetake: () -> Void = {}
    var onDelete: () -> Void = {}
    var onRetry: () -> Void = {}

    var body: some View {
        MediaCardSurface(padding: .init(top: Spacing.xl, leading: Spacing.xl,
                                        bottom: Spacing.xl, trailing: Spacing.xl),
                         spacing: Spacing.lg) {
            Button(action: onPlay) {
                ZStack {
                    VideoThumbnail(path: artefact.localPath)
                    // The scrim, so the play glyph reads on any frame.
                    LinearGradient(colors: [Color.liqFg.opacity(0.10), Color.liqFg.opacity(0.32)],
                                   startPoint: .top, endPoint: .bottom)
                    ZStack {
                        Circle().fill(Color.white.opacity(0.92))
                        // Nudged right, as the reference does: a triangle centred on its bounding
                        // box reads left of centre inside a circle, because its mass is on the left.
                        BrandIconView(icon: .play, size: 22, stroke: 0, tint: .liqFg)
                            .offset(x: 2)
                    }
                    .frame(width: 56, height: 56)

                    VStack {
                        HStack {
                            Spacer()
                            SavedChip()
                        }
                        Spacer()
                        HStack {
                            GlassPill(text: formatTakeLength(artefact.durationMs))
                            Spacer()
                        }
                    }
                    .padding(Spacing.lg)
                }
                .aspectRatio(16.0 / 10.0, contentMode: .fit)
                .background(Gradients.lilac())
                .clipShape(RoundedRectangle(cornerRadius: innerRadius))
            }
            .buttonStyle(PressScale())
            .accessibilityLabel(Text(MediaCopy.playVideo))

            if let prompt = artefact.prompt {
                PromptCaption(prompt: prompt.display)
            }

            RecordedControls(label: MediaCopy.videoRowLabel, status: artefact.status,
                             onRetake: onRetake, onDelete: onDelete, onRetry: onRetry)
        }
    }
}

/// One frame of the take, or the lilac ground while it is being read.
private struct VideoThumbnail: View {
    let path: String?
    @State private var frame: UIImage?

    var body: some View {
        Group {
            if let frame {
                Image(uiImage: frame).resizable().scaledToFill()
            } else {
                Color.clear
            }
        }
        .task(id: path) {
            guard let path else { return }
            frame = await Self.firstFrame(path)
        }
    }

    /// The FIRST frame rather than a time in the middle: the user pressed record and looked at the
    /// lens, so frame zero is the one they expect to see.
    private static func firstFrame(_ path: String) async -> UIImage? {
        await Task.detached(priority: .utility) {
            let asset = AVURLAsset(url: URL(fileURLWithPath: path))
            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            guard let cg = try? generator.copyCGImage(at: .zero, actualTime: nil) else { return nil }
            return UIImage(cgImage: cg)
        }.value
    }
}

/// The duration pill on the thumbnail. Dark glass, tabular figures.
private struct GlassPill: View {
    let text: String

    var body: some View {
        Text(text)
            .font(F.manrope(12, .bold))
            .monospacedDigit()
            .foregroundColor(.white)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.xs)
            .background(Color.liqFg.opacity(0.78), in: Capsule())
    }
}

/// `Saved`, top-right of the thumbnail.
private struct SavedChip: View {
    var body: some View {
        HStack(spacing: Spacing.xs) {
            CheckDot(diameter: 14, glyph: 8)
            Text(MediaCopy.saved)
                .font(F.manrope(11, .bold))
                .foregroundColor(.liqSuccessFg)
        }
        .padding(.leading, Spacing.sm)
        .padding(.trailing, Spacing.lg)
        .padding(.vertical, Spacing.xs)
        .background(Color.white.opacity(0.92), in: Capsule())
    }
}

/// A filled success circle with a white tick. Two sizes, both from the reference.
private struct CheckDot: View {
    let diameter: CGFloat
    let glyph: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(Color.liqSuccess)
            BrandIconView(icon: .check, size: glyph, stroke: 4, tint: .white)
        }
        .frame(width: diameter, height: diameter)
    }
}

/// The row under a filled artefact: what it is on the left, what you can do to it on the right.
///
/// ALSO THE CARD'S FAILURE SURFACE. An upload that did not land says so HERE, never as a modal, and
/// never in a way that blocks leaving the screen — "a dropped upload never blocks the flow".
private struct RecordedControls: View {
    let label: String
    let status: MediaUploadStatus
    let onRetake: () -> Void
    let onDelete: () -> Void
    let onRetry: () -> Void

    var body: some View {
        // A WRAPPING layout, so the controls drop to a second line on a narrow screen rather than
        // the label losing its end. The Android fit harness caught the truncation at 320 wide and
        // rejects ellipsised copy, which is right: the LAYOUT yields, not the words.
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Spacing.md) {
                statusGroup
                Spacer(minLength: Spacing.md)
                pips
            }
            VStack(alignment: .leading, spacing: Spacing.md) {
                statusGroup
                HStack { pips; Spacer() }
            }
        }
        .padding(.leading, Spacing.md)
        .padding(.trailing, Spacing.sm)
        .padding(.vertical, 2)
    }

    @ViewBuilder private var statusGroup: some View {
        HStack(spacing: Spacing.md) {
            switch status {
            case .failed:
                BrandIconView(icon: .close, size: 14, stroke: 2.4, tint: .liqDangerFg)
                Text(MediaCopy.uploadFailed)
                    .font(F.manrope(13.5, .semibold))
                    .foregroundColor(.liqDangerFg)
                RetryLink(fontSize: 13.5, action: onRetry)
            case .confirmed:
                CheckDot(diameter: 18, glyph: 11)
                Text(label).font(F.manrope(13.5, .semibold)).foregroundColor(.liqFg)
            default:
                // Queued or in flight. The card is already filled -- optimistically -- so this says
                // what is happening rather than pretending the artefact is not there.
                Text(MediaCopy.uploading)
                    .font(F.manrope(13.5, .semibold))
                    .foregroundColor(.liqSubtle)
            }
        }
        .lineLimit(1)
    }

    private var pips: some View {
        HStack(spacing: Spacing.md) {
            ControlPip(icon: .refresh, label: MediaCopy.retake, action: onRetake)
            ControlPip(icon: .trash, label: MediaCopy.delete, tone: .danger, action: onDelete)
        }
    }
}

/// `Retry`, on a card whose upload did not land.
///
/// DRAWS ITS TEXT AND ANSWERS AT 44. This is the one control on the screen a user reaches for when
/// something has already gone wrong, so it is the worst possible place for a hit area smaller than
/// it looks.
private struct RetryLink: View {
    let fontSize: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(MediaCopy.retry)
                .font(F.manrope(fontSize, .bold))
                .foregroundColor(.liqPurple)
                .lineLimit(1)
                .padding(.horizontal, Spacing.md)
                .minTapTarget(alignment: .center)
        }
        .buttonStyle(PressScale())
    }
}

/// A recorded voice note.
///
/// 48 DETERMINISTIC BARS whose heights do not change between renders — an acceptance criterion, and
/// the formula is `MediaWaveform.recorded`, ported from the reference rather than approximated. A
/// waveform re-rolled on redraw makes a finished recording appear to wobble while the user is
/// looking at it, and SwiftUI redraws for reasons that have nothing to do with audio.
struct RecordedVoiceCard: View {
    let artefact: MediaArtefact
    var playedMs: Int = 0
    var onPlay: () -> Void = {}
    var onRetake: () -> Void = {}
    var onDelete: () -> Void = {}
    var onRetry: () -> Void = {}

    private var progress: Double {
        guard artefact.durationMs > 0 else { return 0 }
        return min(1, max(0, Double(playedMs) / Double(artefact.durationMs)))
    }

    var body: some View {
        MediaCardSurface(padding: .init(top: 14, leading: 14, bottom: 14, trailing: 14),
                         spacing: Spacing.xl) {
            HStack(spacing: 14) {
                Button(action: onPlay) {
                    ZStack {
                        Circle().fill(Color.liqPurple)
                        BrandIconView(icon: .play, size: 18, stroke: 0, tint: .white)
                            .offset(x: 1.5)
                    }
                    .frame(width: 44, height: 44)
                    .shadow(color: Color.liqPurple.opacity(0.35), radius: 12, x: 0, y: 6)
                }
                .buttonStyle(PressScale())
                .accessibilityLabel(Text(MediaCopy.playVoice))

                Waveform(bars: MediaWaveform.recorded, progress: progress,
                         playedColor: .liqPurple, restColor: Color.liqPurple.opacity(0.28),
                         barGap: 2, minBarHeight: 3, corner: 2)
                    .frame(height: 36)
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .background(Gradients.lilac(), in: RoundedRectangle(cornerRadius: innerRadius))

            ViewThatFits(in: .horizontal) {
                HStack {
                    timeReadout
                    Spacer(minLength: Spacing.md)
                    pips
                }
                VStack(alignment: .leading, spacing: Spacing.md) {
                    timeReadout
                    HStack { pips; Spacer() }
                }
            }
            .padding(.horizontal, Spacing.xs)

            if artefact.status == .failed {
                HStack(spacing: Spacing.md) {
                    BrandIconView(icon: .close, size: 14, stroke: 2.4, tint: .liqDangerFg)
                    Text(MediaCopy.uploadFailed)
                        .font(F.manrope(13, .semibold))
                        .foregroundColor(.liqDangerFg)
                    RetryLink(fontSize: 13, action: onRetry)
                    Spacer()
                }
                .padding(.horizontal, Spacing.xs)
            }

            if let prompt = artefact.prompt {
                PromptCaption(prompt: prompt.display)
            }
        }
    }

    private var timeReadout: some View {
        HStack(spacing: 0) {
            Text(formatTakeLength(playedMs))
                .font(F.manrope(13, .bold)).monospacedDigit().foregroundColor(.liqFg)
            Text(" / \(formatTakeLength(artefact.durationMs))")
                .font(F.manrope(13, .bold)).monospacedDigit().foregroundColor(.liqSubtle)
        }
        .lineLimit(1)
    }

    private var pips: some View {
        HStack(spacing: Spacing.md) {
            ControlPip(icon: .refresh, label: MediaCopy.retake, action: onRetake)
            ControlPip(icon: .trash, label: MediaCopy.delete, tone: .danger, action: onDelete)
        }
    }
}

/// The bar strip, shared by the filled card and the capture screen.
///
/// Drawn on a Canvas rather than as 48 views: 48 nodes per waveform, two waveforms on screen,
/// redrawing on every playback tick is a lot of layout for a decoration. The arithmetic is
/// identical either way — each bar is `1/48` of the width minus the gap.
struct Waveform: View {
    let bars: [Double]
    let progress: Double
    let playedColor: Color
    let restColor: Color
    let barGap: CGFloat
    let minBarHeight: CGFloat
    let corner: CGFloat

    var body: some View {
        Canvas { ctx, size in
            let slot = (size.width + barGap) / CGFloat(bars.count)
            let width = max(1, slot - barGap)
            for (index, value) in bars.enumerated() {
                let height = max(minBarHeight, CGFloat(value) * size.height)
                let rect = CGRect(x: CGFloat(index) * slot, y: (size.height - height) / 2,
                                  width: width, height: height)
                let played = Double(index) < Double(bars.count) * progress
                ctx.fill(Path(roundedRect: rect, cornerRadius: corner),
                         with: .color(played ? playedColor : restColor))
            }
        }
        .accessibilityHidden(true)
    }
}
