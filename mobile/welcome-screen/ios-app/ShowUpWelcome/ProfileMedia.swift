//
//  ProfileMedia.swift
//  ShowUp · "Show your face. Let them hear you." — states A to F (SHOWUP-161)
//
//  Step 3 of 3 of "The real you", and the last screen of the group.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  OPTIONAL IS STATED STRUCTURALLY, ABOVE THE HEADLINE
//  ─────────────────────────────────────────────────────────────────────────────
//
//  This is the point of the 16 September pass, not a detail of it. The previous build put
//  "optional" in a subhead under a 32px display line, where it was the FOURTH thing read, and users
//  took the step as mandatory. It is now a violet pill above the headline, read first, and
//  `Skip for now` sits in the footer beside an always-enabled Continue.
//
//  CONTINUE IS NEVER DISABLED, in any state including empty. No colour change, no toast, no gate,
//  and no validation event when it is pressed with nothing recorded. "Gating Continue here would
//  punish users for not wanting to be on camera, and the brand's not that."
//
//  ─────────────────────────────────────────────────────────────────────────────
//  THE EMPTY STATE MUST FIT ABOVE THE FOLD AT 390 x 844
//  ─────────────────────────────────────────────────────────────────────────────
//
//  Pill, headline, both cards and both CTAs, with no scroll. That budget is why both cards are
//  compact, why their CTAs are the design's `md` height rather than `lg`, and why the removed
//  manifesto card is not coming back.
//
//  At 375 x 667 the second card's CTA may fall below the fold; what must not happen is the FIRST
//  card's CTA needing a scroll.
//

import AVFoundation
import SwiftUI

struct ProfileMediaView: View {
    var state: MediaState = MediaState()
    var onBack: () -> Void = {}
    var onOpenPrompts: (MediaKind, MediaEntryPoint) -> Void = { _, _ in }
    var onPickPrompt: (String) -> Void = { _ in }
    var onCommitPrompt: () -> Void = {}
    var onDismissSheet: (SheetDismissMethod) -> Void = { _ in }
    var onPlay: (MediaKind) -> Void = { _ in }
    var onRetake: (MediaKind) -> Void = { _ in }
    var onDelete: (MediaKind) -> Void = { _ in }
    var onRetryUpload: (MediaKind) -> Void = { _ in }
    var onPermissionAction: (MediaCapability, MediaPermission) -> Void = { _, _ in }
    var platformLabel: (MediaCapability) -> String = { _ in "" }
    /// The player the video surface draws from, or nil where there is none.
    ///
    /// Nil in every preview, every screenshot and all 17 fit sizes -- which is the point: the
    /// screen still renders completely without one, because a decoder is the only kind of thing a
    /// view in this project may be handed and it may never be required.
    var player: AVPlayer?
    var onSkip: () -> Void = {}
    var onContinue: () -> Void = {}

    var body: some View {
        ZStack {
            RealYouScaffold(
                step: .media,
                onBack: onBack,
                // THE PROGRESS BAR SCROLLS WITH THE CONTENT HERE, as on screen 07 and unlike screen
                // 06. A deliberate decision, not an oversight: this screen carries more above the
                // fold and the bar is not worth the fixed row. Do not "fix" it into a third fixed
                // region.
                progressFixed: false,
                content: {
                    StepProgress(steps: RealYouStep.count, current: RealYouStep.media.progressSegment)
                        .padding(.bottom, 22)

                    OptionalPill()
                        .padding(.bottom, Spacing.lg)

                    WashHeadline(
                        parts: [(MediaCopy.headlineBefore, false),
                                (MediaCopy.headlineEm, true),
                                (MediaCopy.headlineAfter, false)],
                        fontSize: 30, lineHeightMultiple: 1.08, trackingEm: -0.018
                    )
                    .padding(.bottom, Spacing.xs)

                    // Video sits above voice because it is the higher-signal artefact for a date:
                    // the user sees a person rather than only hearing one.
                    VStack(spacing: Spacing.xl) {
                        slot(.video)
                        slot(.voice)
                    }
                    .padding(.top, 14)

                    // The tail, so the last card clears the footer rather than ending under it.
                    Color.clear.frame(height: 18)
                },
                footer: {
                    HStack {
                        SkipLink(label: MediaCopy.skip, action: onSkip)
                        Spacer()
                        // ALWAYS ORANGE, in every state. The sunset gradient is reserved for the
                        // sheet's commit CTA and the two review primaries; the reference's own
                        // comment says this "pops to sunset when both slots are filled" and its
                        // code does not, and the ticket settles it: "It stays the orange 52px
                        // NextButton."
                        NextButton(label: MediaCopy.continueLabel, variant: .orange,
                                   arrowSize: 22, circleSize: 52, action: onContinue)
                    }
                    .padding(.horizontal, Spacing.screenGutter)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, 14)
                }
            )

            // The sheet, over the screen, with the screen still visible behind the scrim.
            if let sheet = state.sheet {
                SheetScaffold(onDismiss: onDismissSheet) {
                    MediaPromptSheet(kind: sheet.kind, selectedId: sheet.selectedId,
                                     onPick: onPickPrompt, onCommit: onCommitPrompt,
                                     onClose: { onDismissSheet(.close) })
                }
                .transition(.opacity)
            }
        }
    }

    /// One slot: the empty card, or whatever is recorded in it.
    @ViewBuilder
    private func slot(_ kind: MediaKind) -> some View {
        if let artefact = state.artefact(kind) {
            if kind == .video {
                RecordedVideoCard(artefact: artefact,
                                  isPlaying: state.isPlaying(kind),
                                  player: player,
                                  onPlay: { onPlay(kind) },
                                  onRetake: { onRetake(kind) },
                                  onDelete: { onDelete(kind) },
                                  onRetry: { onRetryUpload(kind) })
            } else {
                RecordedVoiceCard(artefact: artefact,
                                  // THE PLAYHEAD, not a zero. This was `0` with a comment saying
                                  // the position was not modelled, so the readout the ticket specs
                                  // as `0:08 / 0:14` read `0:00 / 0:14` for every user forever. The
                                  // model polls the player and puts the position in the state, so
                                  // the view stays a function of values with no player bound to it.
                                  playedMs: state.playedMs(kind),
                                  onPlay: { onPlay(kind) },
                                  onRetake: { onRetake(kind) },
                                  onDelete: { onDelete(kind) },
                                  onRetry: { onRetryUpload(kind) })
            }
        } else {
            let blocker = state.access.blocker(for: kind)
            MediaSlotCard(
                kind: kind,
                preview: state.preview(kind),
                blocker: blocker,
                platformLabel: blocker.map { platformLabel($0.capability) } ?? "",
                onChoose: { onOpenPrompts(kind, .seeThePrompts) },
                onPermissionAction: {
                    if let blocker { onPermissionAction(blocker.capability, blocker.status) }
                }
            )
        }
    }
}

/// `Optional · you can skip this`, above the headline.
///
/// ABOVE, not in the subhead, and that placement is an acceptance criterion in its own right. See
/// the file header for what it cost the last time it was fourth on the page.
private struct OptionalPill: View {
    var body: some View {
        HStack(spacing: 7) {
            BrandIconView(icon: .check, size: 12, stroke: 2.6, tint: .liqPurple)
            Text(MediaCopy.optionalPill.uppercased())
                .font(F.manrope(11, .heavy))
                .tracking(0.07 * 11)
                .foregroundColor(.liqPurple)
        }
        .padding(.leading, 9)
        .padding(.trailing, Spacing.xl)
        .padding(.vertical, 5)
        .background(Color.liqPurple.opacity(0.10), in: Capsule())
    }
}

// MARK: - previews, every state at the smallest and largest size

private let previewVideo = MediaArtefact(
    kind: .video, promptId: "relaxed_and_happy", durationMs: 9_400,
    localPath: nil, remoteId: "v1", url: nil, status: .confirmed
)
private let previewVoice = MediaArtefact(
    kind: .voice, promptId: "relaxing_sound", durationMs: 14_100,
    localPath: nil, remoteId: "a1", url: nil, status: .confirmed
)

#Preview("A · empty · 390") { ProfileMediaView() }

#Preview("B · video only") { ProfileMediaView(state: MediaState(video: previewVideo)) }

#Preview("C · voice only") { ProfileMediaView(state: MediaState(voice: previewVoice)) }

#Preview("D · both") {
    ProfileMediaView(state: MediaState(video: previewVideo, voice: previewVoice))
}

#Preview("E · prompts video") {
    ProfileMediaView(state: MediaState(
        sheet: MediaSheet(kind: .video, entryPoint: .seeThePrompts, openedAtMs: 0)
    ))
}

#Preview("F · prompts voice, picked") {
    ProfileMediaView(state: MediaState(
        sheet: MediaSheet(kind: .voice, entryPoint: .seeThePrompts,
                          selectedId: "relaxing_sound", openedAtMs: 0)
    ))
}

#Preview("Blocked microphone") {
    ProfileMediaView(
        state: MediaState(access: MediaAccess(camera: .granted, microphone: .blocked)),
        platformLabel: { _ in "Microphone" }
    )
}

/// The same artefact with a failed upload. A top-level constant rather than a `var` inside the
/// preview: a `#Preview` body is a ViewBuilder closure and cannot hold statements.
private let previewFailedVideo = MediaArtefact(
    kind: .video, promptId: "relaxed_and_happy", durationMs: 9_400,
    localPath: nil, remoteId: nil, url: nil, status: .failed
)

#Preview("Upload failed") { ProfileMediaView(state: MediaState(video: previewFailedVideo)) }
