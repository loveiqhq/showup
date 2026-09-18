//
//  MediaCaptureView.swift
//  ShowUp · the viewfinder and the review screen, for both media — states G to J (SHOWUP-161)
//
//  ─────────────────────────────────────────────────────────────────────────────
//  ONE BEHAVIOUR IN TWO MEDIA, NOT ONE COMPONENT AND NOT TWO SCREENS
//  ─────────────────────────────────────────────────────────────────────────────
//
//  The ticket is unusually specific about this, because both of the obvious builds are wrong:
//
//    "Video is a full-bleed viewfinder on a dark ground with a light status bar; voice is the app's
//     own light ground with the ambient orbs and a centred waveform. Same anatomy — Cancel + REC
//     row, prompt, progress bar, shutter — and the same `phase` prop switching to review. Build the
//     anatomy ONCE and let the medium supply the middle; do not fork them into two screens and do
//     not normalise voice onto the dark ground."
//
//  So `CaptureFrame` owns the anatomy and the two callers own their ground, their tones and their
//  middle. The one structural difference is where the prompt lives: video puts it in the lower
//  third over the viewfinder, voice makes it the hero. That is the "medium supplies the middle"
//  part, and it is why this is not one view with a colour parameter.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  THE CHROME FALLS AWAY
//  ─────────────────────────────────────────────────────────────────────────────
//
//  No AppHeader, no StepProgress, no SkipLink, on either. Full-bleed, and `Cancel` is the only way
//  out. These are their own §11 screens — `profile_media_record` and `profile_media_review` — and
//  they fire their own `screen_viewed`, because otherwise the two most abandonable views in the
//  whole flow are invisible.
//
//  STOP ALWAYS LANDS ON REVIEW. Stopping produces a take; only `Use this clip` produces an artefact.
//  Reaching the cap does exactly what Stop does — no alert, no truncated save.
//

import SwiftUI

/// `#0F0518` — darker than `liqFg`, because a viewfinder's ground must not compete with the scene.
private let viewfinderGround = Color(hex: 0x0F0518)

/// The one entry point. Which medium is a property of the take, not a separate screen.
struct MediaCaptureView: View {
    let take: MediaTake
    var camera: CameraSession?
    var onCancel: () -> Void = {}
    var onStop: () -> Void = {}
    var onPlay: () -> Void = {}
    var onRetake: () -> Void = {}
    var onAccept: () -> Void = {}

    var body: some View {
        switch take.kind {
        case .video:
            VideoCaptureScreen(take: take, camera: camera, onCancel: onCancel, onStop: onStop,
                               onPlay: onPlay, onRetake: onRetake, onAccept: onAccept)
        case .voice:
            VoiceCaptureScreen(take: take, onCancel: onCancel, onStop: onStop,
                               onPlay: onPlay, onRetake: onRetake, onAccept: onAccept)
        }
    }
}

// MARK: - the shared anatomy

/// Status bar → top row → flexible middle → lower third → home indicator.
///
/// NO ABSOLUTE Y ANYWHERE. Every region is a stack child, which is what keeps the shutter off the
/// home indicator on a tall phone and the prompt off the Dynamic Island on a short one. The safe
/// area supplies both insets; nothing here hardcodes a status bar height.
private struct CaptureFrame<Background: View, Top: View, Middle: View, Lower: View>: View {
    @ViewBuilder var background: () -> Background
    @ViewBuilder var topRow: () -> Top
    @ViewBuilder var middle: () -> Middle
    @ViewBuilder var lowerThird: () -> Lower

    var body: some View {
        ZStack {
            background()
            VStack(spacing: 0) {
                topRow()
                    .padding(.horizontal, 20)
                    .padding(.top, Spacing.xs)

                middle().frame(maxWidth: .infinity, maxHeight: .infinity)

                VStack(spacing: 18) { lowerThird() }
                    .padding(.horizontal, Spacing.screenGutter)
                    .padding(.bottom, 14)
            }
        }
    }
}

/// Cancel on the left, the state chip on the right. The only two controls above the fold.
private struct CaptureTopRow: View {
    let take: MediaTake
    let cancelBackground: Color
    let cancelContent: Color
    let recordedChipBackground: Color
    let recordedChipContent: Color
    let onCancel: () -> Void

    var body: some View {
        // CANCEL AND THE CHIP SHARE A LINE UNTIL THEY CANNOT.
        //
        // `0:14 RECORDED` is 13 characters with 0.08em of tracking on top. At the largest Dynamic
        // Type size on a 320 screen it does not fit beside Cancel, and the fallback is to break the
        // word: the chip read `RECORD` / `ED`, cut in half with no hyphen, with Cancel squeezed
        // under it. Stacked, Cancel keeps the line it must always have -- it is the ONLY way out of
        // this screen -- and the chip, a status rather than a control, drops beneath it.
        //
        // `ViewThatFits` is the measurement: it lays out the first child and uses it only if it
        // fits, which is the same rule the Compose side spells out by hand with a text measurer.
        ViewThatFits(in: .horizontal) {
            HStack {
                cancel
                Spacer()
                badge
            }
            VStack(alignment: .trailing, spacing: Spacing.sm) {
                HStack { cancel; Spacer() }
                badge
            }
        }
    }

    // Draws 36, answers at 44 -- the rule every tappable thing here is held to, and it matters
    // more on this screen than anywhere: Cancel is the ONLY way out.
    private var cancel: some View {
        Button(action: onCancel) {
            Text(MediaCopy.cancel)
                .font(F.manrope(13.5, .bold))
                .foregroundColor(cancelContent)
                .fixedSize()
                .padding(.horizontal, 14)
                // minHeight, not height: a fixed frame cuts the label's descenders at the largest
                // Dynamic Type size, which is what a fixed 30 did to the photo sheet's pill.
                .frame(minHeight: 36)
                .background(cancelBackground, in: Capsule())
                .minTapTarget(alignment: .leading)
        }
        .buttonStyle(PressScale())
    }

    @ViewBuilder
    private var badge: some View {
        if take.phase == .review {
            RecordedChip(elapsedMs: take.elapsedMs,
                         background: recordedChipBackground,
                         content: recordedChipContent)
        } else {
            RecChip()
        }
    }
}

/// The live indicator. OURS, not the OS's — we neither imitate nor compensate for those.
private struct RecChip: View {
    @State private var dim = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: Spacing.md) {
            Circle()
                .fill(Color.white)
                .frame(width: 8, height: 8)
                // `su-rec-pulse`: 1.2s ease-in-out, opacity 1 -> 0.35 -> 1.
                .opacity(dim ? 0.35 : 1)
                .animation(reduceMotion ? nil
                                        : .easeInOut(duration: 0.6).repeatForever(autoreverses: true),
                           value: dim)
            Text(MediaCopy.rec)
                .font(F.manrope(12, .heavy))
                .tracking(0.08 * 12)
                .foregroundColor(.white)
                // One line, never broken mid-word. The row above decides whether there is space
                // for this chip beside Cancel; this decides that it is never mangled to fit.
                .lineLimit(1)
                .fixedSize()
        }
        .padding(.leading, Spacing.xl)
        .padding(.trailing, 14)
        .frame(minHeight: 36)
        .background(Color.liqDanger.opacity(0.92), in: Capsule())
        .onAppear { if !reduceMotion { dim = true } }
    }
}

/// `0:09 recorded` — the take's real length, not the cap.
private struct RecordedChip: View {
    let elapsedMs: Int
    let background: Color
    let content: Color

    var body: some View {
        HStack(spacing: 7) {
            BrandIconView(icon: .check, size: 13, stroke: 2.6, tint: content)
            Text((formatTakeLength(elapsedMs) + MediaCopy.recordedSuffix).uppercased())
                .font(F.manrope(12, .heavy))
                .tracking(0.08 * 12)
                .foregroundColor(content)
                // See RecChip: one line, never broken mid-word.
                .lineLimit(1)
                .fixedSize()
        }
        .padding(.leading, Spacing.xl)
        .padding(.trailing, 14)
        .frame(minHeight: 36)
        .background(background, in: Capsule())
    }
}

/// The cap, as a bar.
///
/// It fills TO the limit and reaching the end stops the take — the bar is the only warning, which is
/// the design's choice and a good one: an alert at nine seconds would interrupt the sentence it was
/// warning about. In review the fill resets and the right-hand label becomes the take's length
/// rather than the cap.
private struct CaptureTimeBar: View {
    let take: MediaTake
    let trackColor: Color
    let labelColor: Color

    private var review: Bool { take.phase == .review }

    var body: some View {
        VStack(spacing: Spacing.md) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(trackColor)
                    Capsule()
                        .fill(Color.liqOrange)
                        .frame(width: geo.size.width * (review ? 0 : take.progress))
                }
            }
            .frame(height: 6)

            HStack {
                Text(review ? "0:00" : formatTakeLength(take.elapsedMs))
                Spacer()
                Text(formatTakeLength(review ? take.elapsedMs : take.maxMs))
            }
            .font(F.manrope(12, .bold))
            .monospacedDigit()
            .tracking(0.04 * 12)
            .foregroundColor(labelColor)
        }
    }
}

/// The shutter. One tap to stop.
///
/// 84 across, already well past the 44 floor, so no overflow trick is needed — the biggest control
/// in the app is the one the user has ten seconds to find.
private struct Shutter: View {
    let background: Color
    let ringColor: Color
    let onStop: () -> Void

    var body: some View {
        Button(action: onStop) {
            ZStack {
                Circle().fill(background)
                Circle().strokeBorder(ringColor, lineWidth: 4)
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.liqDanger)
                    .frame(width: 30, height: 30)
            }
            .frame(width: 84, height: 84)
        }
        .buttonStyle(PressScale())
        .accessibilityLabel(Text(MediaCopy.stop))
    }
}

/// `Retake` beside `Use this…`.
///
/// Retake is the quiet one on the left so the way forward stays obvious, and the row does not move
/// between plays — the user can watch the take four times and the buttons stay where they were.
private struct ReviewActions: View {
    let kind: MediaKind
    let retakeBackground: Color
    let retakeBorder: Color
    let retakeContent: Color
    let onRetake: () -> Void
    let onAccept: () -> Void

    var body: some View {
        // SIDE BY SIDE WHEN THEY FIT, STACKED WHEN THEY DO NOT.
        //
        // `Use this recording` beside `Retake` is comfortable at the default text size and does not
        // fit at the largest: the Compose side's fit sweep found the primary's label clipped on
        // every device at 2.0x type. Stacked, the primary goes FIRST -- it is the way forward, and
        // Retake stays the quiet one.
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Spacing.lg) {
                retake(fullWidth: false)
                accept
            }
            VStack(spacing: Spacing.lg) {
                accept
                retake(fullWidth: true)
            }
        }
    }

    private func retake(fullWidth: Bool) -> some View {
        Button(action: onRetake) {
            HStack(spacing: 7) {
                BrandIconView(icon: .refresh, size: 16, stroke: 2, tint: retakeContent)
                Text(MediaCopy.retake)
                    .font(F.manrope(14.5, .bold))
                    .foregroundColor(retakeContent)
                    .lineLimit(1)
            }
            .padding(.horizontal, 18)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            // minHeight, not height, for the same reason as the chips above.
            .frame(minHeight: 52)
            .background(retakeBackground, in: Capsule())
            .overlay(Capsule().strokeBorder(retakeBorder, lineWidth: 1))
        }
        .buttonStyle(PressScale())
    }

    private var accept: some View {
        PrimaryButton(
            label: MediaCopy.usePrimary(kind),
            variant: .sunset,
            action: onAccept,
            trailing: { BrandIconView(icon: .check, size: 17, stroke: 2.4, tint: .white) }
        )
    }
}

// MARK: - video

private struct VideoCaptureScreen: View {
    let take: MediaTake
    var camera: CameraSession?
    let onCancel: () -> Void
    let onStop: () -> Void
    let onPlay: () -> Void
    let onRetake: () -> Void
    let onAccept: () -> Void

    var body: some View {
        CaptureFrame(
            background: {
                ZStack {
                    viewfinderGround
                    // THE LIVE CAMERA ONLY WHILE FILMING. In review the ticket asks for "the 88px
                    // glass play button over the FROZEN FRAME" — leaving the preview running would
                    // show the user their own live face behind the controls for deciding whether
                    // to keep a recording of a different moment, which is the one thing that makes
                    // the review screen unreadable.
                    if take.phase == .recording {
                        if let camera {
                            CameraPreview(session: camera.session)
                        }
                    } else {
                        VideoFrame(path: take.path)
                    }
                    // The vignette, so white chrome reads against any scene.
                    RadialGradient(
                        colors: [.clear, viewfinderGround.opacity(0.45), viewfinderGround.opacity(0.65)],
                        center: .init(x: 0.5, y: 0.3), startRadius: 0, endRadius: 520
                    )
                }
                .ignoresSafeArea()
            },
            topRow: {
                CaptureTopRow(take: take,
                              cancelBackground: Color.white.opacity(0.16), cancelContent: .white,
                              recordedChipBackground: Color.white.opacity(0.18),
                              recordedChipContent: .white,
                              onCancel: onCancel)
            },
            middle: {
                // Empty while filming -- the viewfinder IS the middle. The play affordance appears
                // only in review.
                if take.phase == .review {
                    Button(action: onPlay) {
                        ZStack {
                            Circle().fill(Color.white.opacity(0.22))
                            Circle().strokeBorder(Color.white.opacity(0.55), lineWidth: 1.5)
                            BrandIconView(icon: .play, size: 34, stroke: 0, tint: .white)
                                .offset(x: 3)
                        }
                        .frame(width: 88, height: 88)
                    }
                    .buttonStyle(PressScale())
                    .accessibilityLabel(Text(MediaCopy.playVideo))
                }
            },
            lowerThird: {
                // THE PROMPT STAYS ON SCREEN FOR THE WHOLE TAKE. The user is answering a question,
                // not performing, and taking the question away is how a ten-second clip becomes a
                // stare.
                Text(take.prompt?.display ?? "")
                    .font(F.lora(18, italic: true))
                    .lineSpacing(18 * 0.35)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.white)
                    .frame(maxWidth: 280)
                    .shadow(color: .black.opacity(0.45), radius: 6, x: 0, y: 2)

                CaptureTimeBar(take: take,
                               trackColor: Color.white.opacity(0.20),
                               labelColor: Color.white.opacity(0.85))

                if take.phase == .review {
                    ReviewActions(kind: .video,
                                  retakeBackground: Color.white.opacity(0.18),
                                  retakeBorder: Color.white.opacity(0.40),
                                  retakeContent: .white,
                                  onRetake: onRetake, onAccept: onAccept)
                } else {
                    Shutter(background: Color.white.opacity(0.16), ringColor: .white,
                            onStop: onStop)
                }
            }
        )
    }
}

// MARK: - voice

private struct VoiceCaptureScreen: View {
    let take: MediaTake
    let onCancel: () -> Void
    let onStop: () -> Void
    let onPlay: () -> Void
    let onRetake: () -> Void
    let onAccept: () -> Void

    private var review: Bool { take.phase == .review }

    var body: some View {
        CaptureFrame(
            background: {
                // THE APP'S OWN LIGHT GROUND, not the viewfinder's. The ticket forbids normalising
                // voice onto the dark one: there is no camera here, so there is nothing to darken
                // for, and a black screen with a waveform on it looks like an error state.
                ZStack {
                    Color.liqCream
                    WelcomeBackdrop(peachWash: false, placement: .voiceCapture,
                                    orangeAlpha: 0.24, violetAlpha: 0.22)
                }
                .ignoresSafeArea()
            },
            topRow: {
                CaptureTopRow(take: take,
                              cancelBackground: Color.liqFg.opacity(0.06), cancelContent: .liqFg,
                              recordedChipBackground: .liqSuccess, recordedChipContent: .white,
                              onCancel: onCancel)
            },
            middle: {
                // The 28 gap is the first thing to give when the screen is short: the prompt is
                // the question being answered and the hint is how to answer it, so neither may be
                // cut, and 36 + 28 + 28 of air is the cheapest 92 points on the screen. See the
                // Compose side, where the same three-way priority is spelled out by subtraction.
                VStack(spacing: 28) {
                    // The prompt is the HERO here rather than a lower-third caption: there is
                    // nothing else on screen to look at, and the whole point is that the user is
                    // answering it.
                    Text(take.prompt?.display ?? "")
                        .font(F.lora(22, italic: true))
                        .lineSpacing(22 * 0.25)
                        .tracking(-0.015 * 22)
                        .multilineTextAlignment(.center)
                        .foregroundColor(.liqFg)
                        .frame(maxWidth: 300)

                    HStack(spacing: review ? 14 : Spacing.xs) {
                        if review {
                            Button(action: onPlay) {
                                ZStack {
                                    Circle().fill(Gradients.sunset())
                                    BrandIconView(icon: .play, size: 26, stroke: 0, tint: .white)
                                        .offset(x: 2)
                                }
                                .frame(width: 64, height: 64)
                                .shadow(color: Color.liqOrange.opacity(0.35), radius: 14, x: 0, y: 8)
                            }
                            .buttonStyle(PressScale())
                            .accessibilityLabel(Text(MediaCopy.playVoice))
                        }
                        Waveform(bars: MediaWaveform.live,
                                 progress: review ? 0 : take.progress,
                                 playedColor: .liqOrange, restColor: .liqPurple,
                                 barGap: Spacing.xs, minBarHeight: Spacing.sm, corner: 3)
                            // A FLOOR AND A CEILING, NOT A FIXED HEIGHT.
                            //
                            // 160 (120 in review) is what the design draws and what it gets
                            // whenever there is room. At the largest Dynamic Type size on a short
                            // screen the prompt above is three times taller and the hint below was
                            // pushed off the bottom; the waveform is the one thing in this column
                            // that can give way, being a decoration, where the question the user is
                            // answering is not.
                            //
                            // A range works here and does NOT on the Compose side, which is worth
                            // knowing rather than copying blindly: SwiftUI proposes a size and the
                            // child negotiates, so a flexible frame shrinks under pressure. Compose
                            // hands down constraints, and a Canvas given a height RANGE resolves to
                            // zero -- so the Kotlin waveform is sized by subtraction instead.
                            //
                            // The floor is 64 in review because the play button beside it is 64 and
                            // does not shrink.
                            .frame(minHeight: review ? 64 : 56,
                                   maxHeight: review ? 120 : 160)
                            // The finished take reads quieter than the live one: it is something to
                            // listen back to rather than something happening.
                            .opacity(review ? 0.5 : 0.85)
                    }

                    Text(review ? MediaCopy.voiceHintReview : MediaCopy.voiceHintRecording)
                        .font(F.manrope(13, .semibold))
                        .tracking(0.02 * 13)
                        .foregroundColor(.liqSubtle)
                }
                .padding(.horizontal, Spacing.screenGutter)
                .padding(.top, 36)
                .frame(maxHeight: .infinity, alignment: .top)
            },
            lowerThird: {
                CaptureTimeBar(take: take, trackColor: Color.liqFg.opacity(0.10),
                               labelColor: .liqSubtle)

                if review {
                    ReviewActions(kind: .voice, retakeBackground: .white,
                                  retakeBorder: .liqBorder, retakeContent: .liqFg,
                                  onRetake: onRetake, onAccept: onAccept)
                } else {
                    Shutter(background: .white, ringColor: .liqBorder, onStop: onStop)
                }
            }
        )
    }
}

// MARK: - previews

private let videoTake = MediaTake(kind: .video, promptId: "relaxed_and_happy", elapsedMs: 6_000)
private let voiceTake = MediaTake(kind: .voice, promptId: "relaxing_sound", elapsedMs: 4_000)
private let videoReviewTake = MediaTake(kind: .video, promptId: "relaxed_and_happy",
                                        phase: .review, elapsedMs: 9_000)
private let voiceReviewTake = MediaTake(kind: .voice, promptId: "relaxing_sound",
                                        phase: .review, elapsedMs: 14_000)

#Preview("G · video recording") { MediaCaptureView(take: videoTake) }
#Preview("H · video review") { MediaCaptureView(take: videoReviewTake) }
#Preview("I · voice recording") { MediaCaptureView(take: voiceTake) }
#Preview("J · voice review") { MediaCaptureView(take: voiceReviewTake) }
