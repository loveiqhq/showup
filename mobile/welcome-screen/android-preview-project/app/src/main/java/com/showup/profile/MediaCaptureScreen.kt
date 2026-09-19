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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
    /**
     * What is playing back, if anything.
     *
     * Carries the playhead as well as the fact, because the voice review's waveform shows the
     * position: a review screen that played with a waveform frozen at zero would be the same
     * half-drawn feedback the filled card had with its hardcoded `0:00 / 0:14`.
     */
    playback: MediaPlayback? = null,
    /** The player the video surface draws from, or null where there is none. */
    player: Player? = null,
) {
    when (take.kind) {
        MediaKind.Video ->
            VideoCapture(
                take, playback != null, player, onCancel, onStop, onPlay, onRetake, onAccept,
                cameraController,
            )
        MediaKind.Voice ->
            VoiceCapture(take, playback, onCancel, onStop, onPlay, onRetake, onAccept)
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
    /**
     * The flexible middle. Receives the height the region actually has, in dp.
     *
     * It is passed rather than discovered because the middle SCROLLS, and inside a scroll a
     * child's height constraint is unbounded -- `weight` resolves to zero and `BoxWithConstraints`
     * reports infinity. A decoration meant to give way when the screen is short therefore cannot
     * work it out for itself; this is the only place that knows.
     */
    middle: @Composable ColumnScope.(room: Dp) -> Unit,
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

            // THE MIDDLE SCROLLS, THE LOWER THIRD DOES NOT -- the same division as the media
            // screen's own scaffold, and for a sharper reason here. At the largest accessibility
            // font on the narrowest phone there is more content than screen: the prompt alone runs
            // to three lines. Something has to give, and it cannot be the lower third, because
            // that is where Stop lives and nobody can be asked to scroll to end a take.
            //
            // At every size and scale where it already fitted this changes nothing: a scroll
            // container whose content is shorter than its viewport does not scroll.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val room = maxHeight
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { middle(room) }
            }

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
    // CANCEL AND THE CHIP SHARE A LINE UNTIL THEY CANNOT, AND THAT IS MEASURED.
    //
    // `0:14 RECORDED` is 13 characters with 0.08em of tracking on top. At the largest system font
    // on a 320 phone it does not fit beside Cancel, and Compose's last resort for a single word too
    // wide for its line is to break it: the chip read `RECORD` / `ED`, a word cut in half with no
    // hyphen, while Cancel was pushed under it. Neither is something a person would ship.
    //
    // Stacked, Cancel keeps the line it must always have -- it is the ONLY way out of this screen
    // -- and the chip, which is a status rather than a control, drops beneath it on the right.
    //
    // Measured, not thresholded, for the reason this file keeps repeating: `fontScale > 1.3` is
    // right for English and wrong for the first translation.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val room = maxWidth
        val review = take.phase == RecordingPhase.Review
        val fits = with(density) {
            val cancel = measurer.measure(
                MediaCopy.CANCEL,
                TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
            ).size.width.toDp() + 28.dp
            val badge = measurer.measure(
                if (review) {
                    (formatTakeLength(take.elapsedMs) + MediaCopy.RECORDED_SUFFIX).eyebrowCase()
                } else {
                    MediaCopy.REC
                },
                TextStyle(
                    fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                    letterSpacing = 0.08.em,
                ),
            ).size.width.toDp() +
                // the glyph, its gap, and the chip's own horizontal padding
                13.dp + 7.dp + Spacing.xl + 14.dp
            cancel + badge + Spacing.md <= room
        }
        if (fits) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopRowContent(
                    take, onCancel, cancelBackground, cancelContent,
                    recordedChipBackground, recordedChipContent, stacked = false,
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TopRowContent(
                    take, onCancel, cancelBackground, cancelContent,
                    recordedChipBackground, recordedChipContent, stacked = true,
                )
            }
        }
    }
}

/**
 * Cancel and the status badge, in whichever container [CaptureTopRow] chose.
 *
 * One definition, so the two arrangements cannot drift apart: the order is the same either way and
 * only the container differs.
 */
@Composable
private fun TopRowContent(
    take: MediaTake,
    onCancel: () -> Unit,
    cancelBackground: Color,
    cancelContent: Color,
    recordedChipBackground: Color,
    recordedChipContent: Color,
    stacked: Boolean,
) {
    val cancel: @Composable () -> Unit = {
        // Draws 36, answers at 44 -- the rule every tappable thing here is held to, and it matters
        // more on this screen than anywhere: Cancel is the ONLY way out.
        Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    // requiredHeightIn, NOT height. `height` proposes a size and is clamped by the
                    // 36 above it, so this measured 36 on all seventeen sizes -- with a comment
                    // three lines up claiming otherwise. The `required` form ignores the incoming
                    // constraint, which is the whole point of the overflow trick; the `In` form
                    // then lets the label grow past it at the largest system font.
                    .requiredHeightIn(min = ComponentSizes.minTapTarget)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .heightIn(min = 36.dp)
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

    }
    if (stacked) {
        Box(Modifier.fillMaxWidth()) { cancel() }
    } else {
        cancel()
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

/**
 * Frames, drawn by the player.
 *
 * The same component the filled video card uses, for the same reason: a player with nowhere to
 * draw plays a clip's audio underneath a still picture, which on a video is worse than not playing
 * at all. `useController = false` because the transport controls are ours -- an 88px glass circle
 * here, a 56px button on the card, and ExoPlayer's own bar is neither.
 */
@Composable
private fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}

/**
 * The live indicator. OURS, not the OS's -- we neither imitate nor compensate for those.
 *
 * THE TRANSITION IS NOT CREATED WHEN MOTION IS OFF, and that is not a detail. The obvious shape --
 * create the transition, then write `if (motion.enabled) pulse else 1f` -- gates the VALUE and not
 * the ANIMATION. `rememberInfiniteTransition` goes on asking for a frame every frame for as long
 * as the composition lives, whatever is done with the number it produces. On a device that means a
 * screen told "no animations" still wakes the display pipeline sixty times a second; in a test it
 * means a composition that never goes idle.
 *
 * That is not theoretical. The screenshot harness renders this chip, and with the value-gated
 * version `testDebugUnitTest` went from about three minutes to FOUR AND A HALF HOURS -- and passed,
 * which is the worst way to fail.
 *
 * Three other components have the same shape (`Spinner`, the connect ring, the code caret). None
 * is in the screenshot harness, so none has bitten yet; they are recorded in
 * `audit/CONFLICTS-2026-08-27.md` rather than changed inside this ticket.
 */
@Composable
private fun RecChip() {
    val motion = rememberMotion()
    // Calling a composable conditionally is legal, and this condition is stable for the life of
    // the composition: it comes from a system setting whose change restarts the activity.
    val pulse = if (motion.enabled) recPulse() else 1f
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Danger.copy(alpha = 0.92f))
            .padding(start = Spacing.xl, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(pulse)
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

/**
 * `su-rec-pulse`: 1.2s ease-in-out, opacity 1 -> 0.35 -> 1.
 *
 * Its own composable so [RecChip] can decline to call it. See that function for why declining
 * matters rather than simply ignoring the result.
 */
@Composable
private fun recPulse(): Float {
    val transition = rememberInfiniteTransition(label = "rec")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recPulse",
    )
    return pulse
}

/** `0:09 recorded` — the take's real length, not the cap. */
@Composable
private fun RecordedChip(elapsedMs: Int, background: Color, content: Color) {
    Row(
        Modifier
            .heightIn(min = 36.dp)
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
    // SIDE BY SIDE WHEN THEY FIT, STACKED WHEN THEY DO NOT, and which it is is MEASURED.
    //
    // `Use this recording` beside `Retake` is comfortable at 1x and does not fit at the largest
    // system font: the fit harness found the primary's label clipped on every device at 2.0x, and
    // on the Galaxy Fold at 1.3x. A threshold on `fontScale` would be a guess that is right for
    // English and wrong for the first translation, so the label is laid out with the real font at
    // the real size and the row decides for itself. The same rule, and the same `rememberTextMeasurer`,
    // as the three SSO marks that had to share a width.
    //
    // Stacked, the primary goes FIRST: it is the way forward, and Retake stays the quiet one.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val room = maxWidth

        val fits = with(density) {
            val retakeStyle = TextStyle(
                fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.5.sp,
            )
            // 16 icon + 7 gap + 18 padding either side.
            val retakeWidth = measurer.measure(MediaCopy.RETAKE, retakeStyle).size.width.toDp() +
                16.dp + 7.dp + 36.dp
            // The primary's own label at its own size, plus the check, its gap and its padding.
            val primaryWidth = measurer
                .measure(MediaCopy.usePrimary(kind), retakeStyle.copy(fontSize = 16.sp))
                .size.width.toDp() + 17.dp + Spacing.md + 32.dp
            retakeWidth + Spacing.lg + primaryWidth <= room
        }

        val retake = @Composable { fullWidth: Boolean ->
            Row(
                (if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(retakeBackground)
                    .border(1.dp, retakeBorder, RoundedCornerShape(percent = 50))
                    .clickable(role = Role.Button, onClick = onRetake)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = if (fullWidth) {
                    Arrangement.Center
                } else {
                    Arrangement.spacedBy(7.dp)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BrandIcon.Refresh, 16.dp, tint = retakeContent, strokeWidth = 2f)
                Spacer(Modifier.size(7.dp))
                Text(
                    MediaCopy.RETAKE,
                    color = retakeContent, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                )
            }
        }
        val accept = @Composable {
            PrimaryButton(
                label = MediaCopy.usePrimary(kind),
                onClick = onAccept,
                variant = PrimaryButtonVariant.Sunset,
                trailing = { Icon(BrandIcon.Check, 17.dp, tint = Color.White, strokeWidth = 2.4f) },
            )
        }

        if (fits) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                retake(false)
                Box(Modifier.weight(1f)) { accept() }
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                accept()
                retake(true)
            }
        }
    }
}

// ── video ────────────────────────────────────────────────────────────────────

@Composable
private fun VideoCapture(
    take: MediaTake,
    /** Whether the take is playing back right now. */
    playing: Boolean,
    /** The player to draw frames from, null in previews and the fit harness. */
    player: Player?,
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
                // THE LIVE CAMERA ONLY WHILE FILMING. In review the ticket asks for "the 88px
                // glass play button over the FROZEN FRAME" -- leaving the preview running would
                // show the user their own live face behind the controls for deciding whether to
                // keep a recording of a different moment, which is the one thing that makes the
                // review screen unreadable.
                if (take.phase == RecordingPhase.Recording) {
                    if (cameraController != null) {
                        AndroidView(
                            factory = { context ->
                                PreviewView(context).apply {
                                    // FILL_CENTER, because the viewfinder is full-bleed and a
                                    // letterbox would put grey bars around the user's face. The
                                    // prompt sits over the lower third either way.
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    controller = cameraController
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else if (playing && player != null) {
                    // The frozen frame gives way to the clip itself. The ticket asks for the glass
                    // button "over the frozen frame"; pressing it is the moment that frame is
                    // meant to come alive, and audio over a still would be the opposite.
                    VideoSurface(player, Modifier.fillMaxSize())
                } else {
                    VideoFrame(take.path, Modifier.fillMaxSize())
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
        middle = { _ ->
            // Empty while filming -- the viewfinder IS the middle. The play affordance appears
            // only in review.
            Spacer(Modifier.weight(1f))
            // STAYS PUT WHILE IT PLAYS. It is not decoration during playback -- pressing it again
            // is how the user replays, which the ticket asks for in those words: "multiple plays
            // are expected and the CTA never moves". Hiding it would make the second play
            // impossible and move the layout while the first one ran.
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
    /** What is playing back, if anything -- the waveform shows its playhead. */
    playback: MediaPlayback?,
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
        middle = { room ->
            // HOW TALL THE WAVEFORM IS, DECIDED BY SUBTRACTION RATHER THAN BY A CONSTANT.
            //
            // The column below holds three things and only one of them may shrink. The prompt is
            // the question being answered and the hint is how to answer it; the waveform is a
            // decoration. So the two texts are laid out at the real font and the real width, and
            // the waveform is given what is left.
            //
            // Measured rather than thresholded, for the reason this file keeps repeating: a
            // `fontScale > 1.3` rule is right for English and wrong for the first translation. And
            // it cannot be a weight, which is the obvious answer and does not work -- inside the
            // scrolling middle the height constraint is unbounded and a weighted child resolves to
            // zero. That is why `room` is handed down.
            //
            // What it fixes: at 2.0x on a 320 phone this column overflowed its region by 94dp and
            // the hint -- `Hear it back before you keep it` -- sat behind the lower third, present
            // in the tree, invisible on the screen.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val hintCopy =
                if (review) MediaCopy.VOICE_HINT_REVIEW else MediaCopy.VOICE_HINT_RECORDING
            val promptCopy = take.prompt?.display.orEmpty()
            val (waveHeight, columnGap, columnTop) = with(density) {
                val textWidth = 300.dp.roundToPx()
                val promptHeight = measurer.measure(
                    promptCopy,
                    TextStyle(
                        fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 22.sp,
                        lineHeight = (22f * 1.25f).sp, letterSpacing = (-0.015).em,
                        textAlign = TextAlign.Center,
                    ),
                    constraints = Constraints(maxWidth = textWidth),
                ).size.height.toDp()
                val hintHeight = measurer.measure(
                    hintCopy,
                    TextStyle(
                        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        letterSpacing = 0.02.em,
                    ),
                    constraints = Constraints(maxWidth = textWidth),
                ).size.height.toDp()
                // WHITESPACE GIVES WAY FIRST, THEN THE WAVEFORM, AND THE TEXT NEVER DOES.
                //
                // Three things compete for `room` and they are not equal. The prompt is the
                // question being answered and the hint is how to answer it, so neither may be cut.
                // The waveform is a decoration with a floor -- in review the 64 play pip sits in
                // the same row and does not shrink. That leaves the gaps, and 36 + 28 + 28 of air
                // is the cheapest 92dp on the screen.
                //
                // At 2.0x on a 320 phone this is the difference between the hint being readable
                // and the hint being behind the lower third.
                val minWave = if (review) 64.dp else 56.dp
                val text = promptHeight + hintHeight
                // Two gaps and a top padding, all from one number so they stay in proportion.
                val gap = ((room - text - minWave) / 3).coerceIn(Spacing.sm, 28.dp)
                Triple(
                    (room - text - gap * 3).coerceIn(minWave, if (review) 120.dp else 160.dp),
                    gap,
                    gap,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.screenGutter,
                        end = Spacing.screenGutter,
                        top = columnTop,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(columnGap),
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
                    // The height comes from `waveHeight` above, not from a weight: the column this
                    // sits in scrolls, so its height constraint is unbounded and a weighted child
                    // resolves to zero. Not a theory -- it is what made the waveform vanish
                    // entirely at 2.0x, leaving a play button on a blank page.
                    Modifier.fillMaxWidth().height(waveHeight),
                    horizontalArrangement = Arrangement.spacedBy(if (review) 14.dp else Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (review) {
                        Box(
                            Modifier
                                // requiredSize, so a squeezed column cannot shrink the control.
                                // The row it sits in is flexible now, and `size` is a PROPOSAL --
                                // at 2.0x on a short phone the incoming height clamped this to
                                // 26.5dp, which is a play button smaller than a fingertip. The
                                // waveform beside it is what gives way; this does not.
                                .requiredSize(64.dp)
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
                        // THREE STATES, NOT TWO. Filming fills to the cap; playing back fills
                        // to the playhead; a finished take sitting still is empty. The middle one
                        // was missing, so pressing play moved nothing on screen.
                        progress = when {
                            playback != null -> playback.progress
                            review -> 0f
                            else -> take.progress
                        },
                        playedColor = Orange,
                        restColor = Purple,
                        barGap = Spacing.xs,
                        minBarHeight = Spacing.sm,
                        corner = 3.dp,
                        modifier = Modifier
                            .weight(1f)
                            // FILL, do not range. A Canvas IS a Spacer --
                            // `Spacer(modifier.drawBehind {})` -- and Spacer's measure policy takes
                            // the incoming maximum only on an axis whose constraint is FIXED, and
                            // ZERO on an axis given a range. `heightIn(min, max)` here produced a
                            // canvas nothing tall: constraints legal, node present and reporting a
                            // size, draw block painting into nothing, and not one check in the fit
                            // harness able to see it, because nothing was clipped. The row above
                            // owns the height; this fills it.
                            .fillMaxHeight()
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
