/*
 * MediaCaptureScreen.kt
 * ShowUp · the viewfinder and the review screen, for both media — states G to J (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE BEHAVIOUR IN TWO MEDIA, NOT ONE COMPONENT AND NOT TWO SCREENS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The ticket is unusually specific about this, because both of the obvious builds are wrong:
 *
 *   "Video is a full-bleed viewfinder on a dark ground with a light status bar; voice is the app's
 *    own light ground with the ambient orbs and a centred waveform. Same anatomy — Cancel + REC
 *    row, prompt, progress bar, shutter — and the same `phase` prop switching to review. Build the
 *    anatomy ONCE and let the medium supply the middle; do not fork them into two screens and do
 *    not normalise voice onto the dark ground."
 *
 * So [CaptureFrame] owns the anatomy and the two callers own their ground, their tones and their
 * middle. The one structural difference is where the prompt lives: video puts it in the lower
 * third over the viewfinder, voice makes it the hero. That is the "medium supplies the middle"
 * part, and it is why this is not one component with a colour parameter.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE CHROME FALLS AWAY
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * No AppHeader, no StepProgress, no SkipLink, on either. Full-bleed, and `Cancel` is the only way
 * out. These are their own §11 screens -- `profile_media_record` and `profile_media_review` -- and
 * they fire their own `screen_viewed`, because otherwise the two most abandonable views in the
 * whole flow are invisible.
 *
 * STOP ALWAYS LANDS ON REVIEW. Stopping produces a take; only `Use this clip` produces an artefact.
 * Reaching the cap does exactly what Stop does -- no alert, no truncated save.
 */
package com.showup.profile

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.showup.designsystem.Border
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.Cream
import com.showup.designsystem.Danger
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import com.showup.designsystem.Subtle
import com.showup.designsystem.Success
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.eyebrowCase
import com.showup.designsystem.rememberMotion
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WelcomeBackdrop

/** The one entry point. Which medium is a property of the take, not a separate screen. */
@Composable
fun MediaCaptureScreen(
    take: MediaTake,
    onCancel: () -> Unit = {},
    onStop: () -> Unit = {},
    onPlay: () -> Unit = {},
    onRetake: () -> Unit = {},
    onAccept: () -> Unit = {},
    cameraController: LifecycleCameraController? = null,
) {
    when (take.kind) {
        MediaKind.Video ->
            VideoCapture(take, onCancel, onStop, onPlay, onRetake, onAccept, cameraController)
        MediaKind.Voice ->
            VoiceCapture(take, onCancel, onStop, onPlay, onRetake, onAccept)
    }
}

// ── the shared anatomy ───────────────────────────────────────────────────────

/**
 * Status bar → top row → flexible middle → lower third → home indicator.
 *
 * NO ABSOLUTE Y ANYWHERE. Every region is a flex child, which is what keeps the shutter off the
 * gesture bar on a tall phone and the prompt off the notch on a short one. `safeDrawing` supplies
 * both insets; nothing here hardcodes a status bar height.
 */
@Composable
private fun CaptureFrame(
    background: @Composable () -> Unit,
    topRow: @Composable () -> Unit,
    middle: @Composable ColumnScope.() -> Unit,
    lowerThird: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        background()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = Spacing.xs),
            ) { topRow() }

            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = middle,
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.screenGutter,
                        end = Spacing.screenGutter,
                        bottom = 14.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = lowerThird,
            )
        }
    }
}

/** Cancel on the left, the state chip on the right. The only two controls above the fold. */
@Composable
private fun CaptureTopRow(
    take: MediaTake,
    onCancel: () -> Unit,
    cancelBackground: Color,
    cancelContent: Color,
    recordedChipBackground: Color,
    recordedChipContent: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Draws 36, answers at 44 -- the rule every tappable thing here is held to, and it matters
        // more on this screen than anywhere: Cancel is the ONLY way out.
        Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    // requiredHeight, NOT height. `height` proposes a size and is clamped by the
                    // 36 above it, so this measured 36 on all seventeen sizes -- with a comment
                    // three lines up claiming otherwise. `requiredHeight` ignores the incoming
                    // constraint, which is the whole point of the overflow trick.
                    .requiredHeight(ComponentSizes.minTapTarget)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(cancelBackground)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        MediaCopy.CANCEL,
                        color = cancelContent, fontFamily = Manrope,
                        fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
                    )
                }
            }
        }

        if (take.phase == RecordingPhase.Review) {
            RecordedChip(
                take.elapsedMs,
                background = recordedChipBackground,
                content = recordedChipContent,
            )
        } else {
            RecChip()
        }
    }
}

/** The live indicator. OURS, not the OS's -- we neither imitate nor compensate for those. */
@Composable
private fun RecChip() {
    val motion = rememberMotion()
    val transition = rememberInfiniteTransition(label = "rec")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            // `su-rec-pulse`: 1.2s ease-in-out, opacity 1 -> 0.35 -> 1.
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recPulse",
    )
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Danger.copy(alpha = 0.92f))
            .padding(start = Spacing.xl, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (motion.enabled) pulse else 1f)
                .clip(CircleShape)
                .background(Color.White),
        )
        Text(
            MediaCopy.REC,
            color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp, letterSpacing = 0.08.em,
        )
    }
}

/** `0:09 recorded` — the take's real length, not the cap. */
@Composable
private fun RecordedChip(elapsedMs: Int, background: Color, content: Color) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(start = Spacing.xl, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BrandIcon.Check, 13.dp, tint = content, strokeWidth = 2.6f)
        Text(
            (formatTakeLength(elapsedMs) + MediaCopy.RECORDED_SUFFIX).eyebrowCase(),
            color = content, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp, letterSpacing = 0.08.em,
        )
    }
}

/**
 * The cap, as a bar.
 *
 * It fills TO the limit and reaching the end stops the take -- the bar is the only warning, which
 * is the design's choice and a good one: an alert at nine seconds would interrupt the sentence it
 * was warning about. In review the fill resets and the right-hand label becomes the take's length
 * rather than the cap.
 */
@Composable
private fun CaptureTimeBar(
    take: MediaTake,
    trackColor: Color,
    labelColor: Color,
) {
    val review = take.phase == RecordingPhase.Review
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(trackColor),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (review) 0f else take.progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Orange),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (review) "0:00" else formatTakeLength(take.elapsedMs),
                color = labelColor, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, letterSpacing = 0.04.em,
            )
            Text(
                formatTakeLength(if (review) take.elapsedMs else take.maxMs),
                color = labelColor, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, letterSpacing = 0.04.em,
            )
        }
    }
}

/**
 * The shutter. One tap to stop.
 *
 * 84 across, which is already well past the 48 floor, so no overflow trick is needed here -- the
 * biggest control in the app is the one the user has ten seconds to find.
 */
@Composable
private fun Shutter(
    onStop: () -> Unit,
    background: Color,
    ringColor: Color,
) {
    Box(
        Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(background)
            .border(4.dp, ringColor, CircleShape)
            .clickable(role = Role.Button, onClick = onStop)
            .semantics { contentDescription = MediaCopy.STOP },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Danger),
        )
    }
}

/**
 * `Retake` beside `Use this…`.
 *
 * Retake is the quiet one on the left so the way forward stays obvious, and the row does not move
 * between plays -- the user can watch the take four times and the buttons stay exactly where they
 * were the first time.
 */
@Composable
private fun ReviewActions(
    kind: MediaKind,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    retakeBackground: Color,
    retakeBorder: Color,
    retakeContent: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(retakeBackground)
                .border(1.dp, retakeBorder, RoundedCornerShape(percent = 50))
                .clickable(role = Role.Button, onClick = onRetake)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BrandIcon.Refresh, 16.dp, tint = retakeContent, strokeWidth = 2f)
            Text(
                MediaCopy.RETAKE,
                color = retakeContent, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
            )
        }
        Box(Modifier.weight(1f)) {
            PrimaryButton(
                label = MediaCopy.usePrimary(kind),
                onClick = onAccept,
                variant = PrimaryButtonVariant.Sunset,
                trailing = { Icon(BrandIcon.Check, 17.dp, tint = Color.White, strokeWidth = 2.4f) },
            )
        }
    }
}

// ── video ────────────────────────────────────────────────────────────────────

@Composable
private fun VideoCapture(
    take: MediaTake,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onPlay: () -> Unit,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    cameraController: LifecycleCameraController?,
) {
    CaptureFrame(
        background = {
            Box(Modifier.fillMaxSize().background(ViewfinderGround)) {
                if (cameraController != null) {
                    AndroidView(
                        factory = { context ->
                            PreviewView(context).apply {
                                // FILL_CENTER, because the viewfinder is full-bleed and a letterbox
                                // would put grey bars around the user's face. The prompt sits over
                                // the lower third either way.
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                controller = cameraController
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // The vignette, so white chrome reads against any scene.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.8f to ViewfinderGround.copy(alpha = 0.45f),
                            1.0f to ViewfinderGround.copy(alpha = 0.65f),
                        ),
                    ),
                )
            }
        },
        topRow = {
            CaptureTopRow(
                take = take,
                onCancel = onCancel,
                cancelBackground = Color.White.copy(alpha = 0.16f),
                cancelContent = Color.White,
                recordedChipBackground = Color.White.copy(alpha = 0.18f),
                recordedChipContent = Color.White,
            )
        },
        middle = {
            // Empty while filming -- the viewfinder IS the middle. The play affordance appears
            // only in review.
            Spacer(Modifier.weight(1f))
            if (take.phase == RecordingPhase.Review) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                        .clickable(role = Role.Button, onClick = onPlay)
                        .semantics { contentDescription = MediaCopy.PLAY_VIDEO },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.padding(start = Spacing.sm)) {
                        Icon(BrandIcon.Play, 34.dp, tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        },
        lowerThird = {
            // THE PROMPT STAYS ON SCREEN FOR THE WHOLE TAKE. The user is answering a question, not
            // performing, and taking the question away is how a ten-second clip becomes a stare.
            Text(
                take.prompt?.display.orEmpty(),
                modifier = Modifier.widthIn(max = 280.dp),
                color = Color.White, fontFamily = Lora, fontStyle = FontStyle.Italic,
                fontSize = 18.sp, lineHeight = (18f * 1.35f).sp,
                textAlign = TextAlign.Center,
            )
            CaptureTimeBar(
                take = take,
                trackColor = Color.White.copy(alpha = 0.20f),
                labelColor = Color.White.copy(alpha = 0.85f),
            )
            if (take.phase == RecordingPhase.Review) {
                ReviewActions(
                    kind = MediaKind.Video,
                    onRetake = onRetake,
                    onAccept = onAccept,
                    retakeBackground = Color.White.copy(alpha = 0.18f),
                    retakeBorder = Color.White.copy(alpha = 0.40f),
                    retakeContent = Color.White,
                )
            } else {
                Shutter(
                    onStop = onStop,
                    background = Color.White.copy(alpha = 0.16f),
                    ringColor = Color.White,
                )
            }
        },
    )
}

/** `#0F0518` — darker than [Fg], because a viewfinder's ground must not compete with the scene. */
private val ViewfinderGround = Color(0xFF0F0518)

// ── voice ────────────────────────────────────────────────────────────────────

@Composable
private fun VoiceCapture(
    take: MediaTake,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onPlay: () -> Unit,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
) {
    val review = take.phase == RecordingPhase.Review
    CaptureFrame(
        background = {
            // THE APP'S OWN LIGHT GROUND, not the viewfinder's. The ticket forbids normalising
            // voice onto the dark one: there is no camera here, so there is nothing to darken for,
            // and a black screen with a waveform on it looks like an error state.
            Box(Modifier.fillMaxSize().background(Cream)) {
                WelcomeBackdrop(
                    peachWash = false,
                    placement = OrbPlacement.VoiceCapture,
                    orangeAlpha = 0.24f,
                    violetAlpha = 0.22f,
                )
            }
        },
        topRow = {
            CaptureTopRow(
                take = take,
                onCancel = onCancel,
                cancelBackground = Fg.copy(alpha = 0.06f),
                cancelContent = Fg,
                recordedChipBackground = Success,
                recordedChipContent = Color.White,
            )
        },
        middle = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                // The prompt is the HERO here rather than a lower-third caption: there is nothing
                // else on screen to look at, and the whole point is that the user is answering it.
                Text(
                    take.prompt?.display.orEmpty(),
                    modifier = Modifier.widthIn(max = 300.dp),
                    color = Fg, fontFamily = Lora, fontStyle = FontStyle.Italic,
                    fontSize = 22.sp, lineHeight = (22f * 1.25f).sp,
                    letterSpacing = (-0.015).em,
                    textAlign = TextAlign.Center,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (review) 14.dp else Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (review) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()))
                                .clickable(role = Role.Button, onClick = onPlay)
                                .semantics { contentDescription = MediaCopy.PLAY_VOICE },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.padding(start = Spacing.xs)) {
                                Icon(BrandIcon.Play, 26.dp, tint = Color.White)
                            }
                        }
                    }
                    Waveform(
                        bars = MediaWaveform.live,
                        progress = if (review) 0f else take.progress,
                        playedColor = Orange,
                        restColor = Purple,
                        barGap = Spacing.xs,
                        minBarHeight = Spacing.sm,
                        corner = 3.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(if (review) 120.dp else 160.dp)
                            // The finished take reads quieter than the live one: it is something
                            // to listen back to rather than something happening.
                            .alpha(if (review) 0.5f else 0.85f),
                    )
                }
                Text(
                    if (review) MediaCopy.VOICE_HINT_REVIEW else MediaCopy.VOICE_HINT_RECORDING,
                    color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, letterSpacing = 0.02.em,
                )
            }
        },
        lowerThird = {
            CaptureTimeBar(
                take = take,
                trackColor = Fg.copy(alpha = 0.10f),
                labelColor = Subtle,
            )
            if (review) {
                ReviewActions(
                    kind = MediaKind.Voice,
                    onRetake = onRetake,
                    onAccept = onAccept,
                    retakeBackground = Color.White,
                    retakeBorder = Border,
                    retakeContent = Fg,
                )
            } else {
                Shutter(onStop = onStop, background = Color.White, ringColor = Border)
            }
        },
    )
}

// ── previews ─────────────────────────────────────────────────────────────────

private val VIDEO_TAKE = MediaTake(
    kind = MediaKind.Video, promptId = "relaxed_and_happy", elapsedMs = 6_000,
)
private val VOICE_TAKE = MediaTake(
    kind = MediaKind.Voice, promptId = "relaxing_sound", elapsedMs = 4_000,
)

@Preview(name = "G · video recording · 390", widthDp = 390, heightDp = 844)
@Composable
private fun CaptureG390() = MediaCaptureScreen(VIDEO_TAKE)

@Preview(name = "H · video review · 390", widthDp = 390, heightDp = 844)
@Composable
private fun CaptureH390() = MediaCaptureScreen(
    VIDEO_TAKE.copy(phase = RecordingPhase.Review, elapsedMs = 9_000),
)

@Preview(name = "I · voice recording · 390", widthDp = 390, heightDp = 844)
@Composable
private fun CaptureI390() = MediaCaptureScreen(VOICE_TAKE)

@Preview(name = "J · voice review · 390", widthDp = 390, heightDp = 844)
@Composable
private fun CaptureJ390() = MediaCaptureScreen(
    VOICE_TAKE.copy(phase = RecordingPhase.Review, elapsedMs = 14_000),
)

@Preview(name = "G · video recording · 320", widthDp = 320, heightDp = 686)
@Composable
private fun CaptureG320() = MediaCaptureScreen(VIDEO_TAKE)

@Preview(name = "J · voice review · 430", widthDp = 430, heightDp = 932)
@Composable
private fun CaptureJ430() = MediaCaptureScreen(
    VOICE_TAKE.copy(phase = RecordingPhase.Review, elapsedMs = 14_000),
)
