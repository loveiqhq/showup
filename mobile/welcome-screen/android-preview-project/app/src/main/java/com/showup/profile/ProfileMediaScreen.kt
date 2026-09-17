/*
 * ProfileMediaScreen.kt
 * ShowUp · "Show your face. Let them hear you." — states A to F (SHOWUP-161)
 *
 * Step 3 of 3 of "The real you", and the last screen of the group.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * OPTIONAL IS STATED STRUCTURALLY, ABOVE THE HEADLINE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * This is the point of the 16 September pass, not a detail of it. The previous build put
 * "optional" in a subhead under a 32px display line, where it was the FOURTH thing read, and users
 * took the step as mandatory. It is now a violet pill above the headline, read first, and
 * `Skip for now` sits in the footer beside an always-enabled Continue.
 *
 * CONTINUE IS NEVER DISABLED, in any state including empty. No colour change, no toast, no gate,
 * and no validation event when it is pressed with nothing recorded. "Gating Continue here would
 * punish users for not wanting to be on camera, and the brand's not that."
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE EMPTY STATE MUST FIT ABOVE THE FOLD AT 390 x 844
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Pill, headline, both cards and both CTAs, with no scroll. That budget is why both cards are
 * compact, why their CTAs are the design's `md` height rather than `lg`, and why the removed
 * manifesto card is not coming back. `MediaFitTest` measures it rather than trusting it.
 *
 * At 375 x 667 the second card's CTA may fall below the fold; what must not happen is the FIRST
 * card's CTA needing a scroll.
 */
package com.showup.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.showup.designsystem.Cream
import com.showup.designsystem.Manrope
import com.showup.designsystem.Purple
import com.showup.designsystem.SheetScaffold
import com.showup.designsystem.SkipLink
import com.showup.designsystem.Spacing
import com.showup.designsystem.StepProgress
import com.showup.tutorial.NextButton
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/**
 * @param onOpenPrompts the medium whose list to open, and how it was reached.
 * @param onPermissionAction the row's button -- re-prompt in app, or open our app's settings page.
 */
@Composable
fun ProfileMediaScreen(
    state: MediaState = MediaState(),
    onBack: () -> Unit = {},
    onOpenPrompts: (MediaKind, MediaEntryPoint) -> Unit = { _, _ -> },
    onPickPrompt: (String) -> Unit = {},
    onCommitPrompt: () -> Unit = {},
    onDismissSheet: (SheetDismissMethod) -> Unit = {},
    onPlay: (MediaKind) -> Unit = {},
    onRetake: (MediaKind) -> Unit = {},
    onDelete: (MediaKind) -> Unit = {},
    onRetryUpload: (MediaKind) -> Unit = {},
    onPermissionAction: (MediaCapability, MediaPermission) -> Unit = { _, _ -> },
    platformLabel: (MediaCapability) -> String = { "" },
    onSkip: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Cream)) {
        RealYouScaffold(
            step = RealYouStep.Media,
            onBack = onBack,
            // THE PROGRESS BAR SCROLLS WITH THE CONTENT HERE, as on screen 07 and unlike screen 06.
            // A deliberate decision, not an oversight: this screen carries more above the fold and
            // the bar is not worth the fixed row. Do not "fix" it into a third fixed region.
            progressFixed = false,
            footer = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.screenGutter,
                            end = Spacing.screenGutter,
                            top = Spacing.xl,
                            bottom = 14.dp,
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkipLink(onClick = onSkip, label = MediaCopy.SKIP)
                    // ALWAYS ORANGE, in every state. The sunset gradient is reserved for the
                    // sheet's commit CTA and the two review primaries; the reference's own comment
                    // says this "pops to sunset when both slots are filled" and its code does not,
                    // and the ticket settles it: "It stays the orange 52px NextButton."
                    NextButton(
                        MediaCopy.CONTINUE,
                        onClick = onContinue,
                        arrowSize = 22.dp,
                        circleSize = 52.dp,
                    )
                }
            },
        ) {
            StepProgress(
                steps = RealYouStep.COUNT,
                current = RealYouStep.Media.progressSegment,
                modifier = Modifier.padding(bottom = 22.dp),
            )

            OptionalPill()

            WashHeadline(
                parts = listOf(
                    MediaCopy.HEADLINE_BEFORE to false,
                    MediaCopy.HEADLINE_EM to true,
                    MediaCopy.HEADLINE_AFTER to false,
                ),
                modifier = Modifier.padding(bottom = Spacing.xs),
                fontSize = 30.sp,
                lineHeight = (30f * 1.08f).sp,
                letterSpacing = (-0.018).em,
            )

            // Video sits above voice because it is the higher-signal artefact for a date: the
            // user sees a person rather than only hearing one.
            Column(
                Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                MediaSlot(
                    kind = MediaKind.Video,
                    state = state,
                    onOpenPrompts = onOpenPrompts,
                    onPlay = onPlay,
                    onRetake = onRetake,
                    onDelete = onDelete,
                    onRetryUpload = onRetryUpload,
                    onPermissionAction = onPermissionAction,
                    platformLabel = platformLabel,
                )
                MediaSlot(
                    kind = MediaKind.Voice,
                    state = state,
                    onOpenPrompts = onOpenPrompts,
                    onPlay = onPlay,
                    onRetake = onRetake,
                    onDelete = onDelete,
                    onRetryUpload = onRetryUpload,
                    onPermissionAction = onPermissionAction,
                    platformLabel = platformLabel,
                )
            }

            // The tail, so the last card clears the footer rather than ending under it.
            Spacer(Modifier.height(18.dp))
        }

        // The sheet, over the screen, with the screen still visible behind the scrim.
        state.sheet?.let { sheet ->
            SheetScaffold(onDismiss = onDismissSheet) {
                MediaPromptSheet(
                    kind = sheet.kind,
                    selectedId = sheet.selectedId,
                    onPick = onPickPrompt,
                    onCommit = onCommitPrompt,
                    onClose = { onDismissSheet(SheetDismissMethod.Close) },
                )
            }
        }
    }
}

/** One slot: the empty card, or whatever is recorded in it. */
@Composable
private fun MediaSlot(
    kind: MediaKind,
    state: MediaState,
    onOpenPrompts: (MediaKind, MediaEntryPoint) -> Unit,
    onPlay: (MediaKind) -> Unit,
    onRetake: (MediaKind) -> Unit,
    onDelete: (MediaKind) -> Unit,
    onRetryUpload: (MediaKind) -> Unit,
    onPermissionAction: (MediaCapability, MediaPermission) -> Unit,
    platformLabel: (MediaCapability) -> String,
) {
    val artefact = state.artefact(kind)
    when {
        artefact == null -> {
            val blocker = state.access.blockerFor(kind)
            MediaSlotCard(
                kind = kind,
                preview = state.preview(kind),
                onChoose = { onOpenPrompts(kind, MediaEntryPoint.SeeThePrompts) },
                blocker = blocker,
                platformLabel = blocker?.let { platformLabel(it.capability) }.orEmpty(),
                onPermissionAction = {
                    blocker?.let { onPermissionAction(it.capability, it.status) }
                },
            )
        }
        kind == MediaKind.Video -> RecordedVideoCard(
            artefact = artefact,
            onPlay = { onPlay(kind) },
            onRetake = { onRetake(kind) },
            onDelete = { onDelete(kind) },
            onRetry = { onRetryUpload(kind) },
        )
        else -> RecordedVoiceCard(
            artefact = artefact,
            // Playback position is not modelled on the card: the reference draws a static
            // readout, and a real scrub position would need a player bound to the composable.
            // The filled card plays from the start each time.
            playedMs = 0,
            onPlay = { onPlay(kind) },
            onRetake = { onRetake(kind) },
            onDelete = { onDelete(kind) },
            onRetry = { onRetryUpload(kind) },
        )
    }
}

/**
 * `Optional · you can skip this`, above the headline.
 *
 * ABOVE, not in the subhead, and that placement is an acceptance criterion in its own right. See
 * the file header for what it cost the last time it was fourth on the page.
 */
@Composable
private fun OptionalPill() {
    Row(
        Modifier
            .padding(bottom = Spacing.lg)
            .clip(RoundedCornerShape(percent = 50))
            .background(Purple.copy(alpha = 0.10f))
            .padding(start = 9.dp, end = Spacing.xl, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BrandIcon.Check, 12.dp, tint = Purple, strokeWidth = 2.6f)
        Text(
            MediaCopy.OPTIONAL_PILL.uppercase(java.util.Locale.ROOT),
            color = Purple,
            fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.07.em,
        )
    }
}

// ── previews, every state at the smallest and largest width ──────────────────

private val VIDEO = MediaArtefact(
    kind = MediaKind.Video, promptId = "relaxed_and_happy", durationMs = 9_400,
    localPath = null, remoteId = "v1", status = MediaUploadStatus.Confirmed,
)
private val VOICE = MediaArtefact(
    kind = MediaKind.Voice, promptId = "relaxing_sound", durationMs = 14_100,
    localPath = null, remoteId = "a1", status = MediaUploadStatus.Confirmed,
)

@Preview(name = "A · empty · 375", widthDp = 375, heightDp = 667)
@Composable
private fun MediaA375() = ProfileMediaScreen()

@Preview(name = "A · empty · 390", widthDp = 390, heightDp = 844)
@Composable
private fun MediaA390() = ProfileMediaScreen()

@Preview(name = "B · video only · 390", widthDp = 390, heightDp = 844)
@Composable
private fun MediaB390() = ProfileMediaScreen(MediaState(video = VIDEO))

@Preview(name = "C · voice only · 390", widthDp = 390, heightDp = 844)
@Composable
private fun MediaC390() = ProfileMediaScreen(MediaState(voice = VOICE))

@Preview(name = "D · both · 430", widthDp = 430, heightDp = 932)
@Composable
private fun MediaD430() = ProfileMediaScreen(MediaState(video = VIDEO, voice = VOICE))

@Preview(name = "E · prompts video · 390", widthDp = 390, heightDp = 844)
@Composable
private fun MediaE390() = ProfileMediaScreen(
    MediaState(sheet = MediaSheet(MediaKind.Video, MediaEntryPoint.SeeThePrompts, openedAtMs = 0L)),
)

@Preview(name = "F · prompts voice, picked · 390", widthDp = 390, heightDp = 844)
@Composable
private fun MediaF390() = ProfileMediaScreen(
    MediaState(
        sheet = MediaSheet(
            MediaKind.Voice,
            MediaEntryPoint.SeeThePrompts,
            selectedId = "relaxing_sound",
            openedAtMs = 0L,
        ),
    ),
)

@Preview(name = "Blocked microphone · 390", widthDp = 390, heightDp = 844)
@Composable
private fun MediaBlocked390() = ProfileMediaScreen(
    MediaState(access = MediaAccess(microphone = MediaPermission.Blocked)),
    platformLabel = { "Microphone" },
)

@Preview(name = "Upload failed · 390", widthDp = 390, heightDp = 844)
@Composable
private fun MediaFailed390() = ProfileMediaScreen(
    MediaState(video = VIDEO.copy(status = MediaUploadStatus.Failed)),
)
