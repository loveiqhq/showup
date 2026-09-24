/*
 * MediaCards.kt
 * ShowUp · the two slot cards, empty and filled (SHOWUP-161)
 *
 * Every number here comes from `profile/screen-media-reference.jsx`. The ticket's own order of
 * authority says so: "reference file wins on numbers · ticket wins on behaviour, scope and copy ·
 * PNG wins on nothing".
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THERE IS NO UPLOAD PATH AND NO CAPTION FIELD
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Both were removed on 16 September 2026 and both removals are acceptance criteria, so they are
 * written down here rather than left as an absence somebody helpfully fills in later:
 *
 *   · Media is captured in the app or not at all. No library picker, no "upload instead" link, on
 *     either card, in any state, on either platform.
 *   · The chosen prompt IS the caption. There is no 50-character field after the take. It renders
 *     read-only under `Prompt · shown on your profile` and cannot be left blank, which is more than
 *     could be said for anything the user typed under time pressure.
 *
 * The CTA is `See the prompts`, not `Record`. "Record asks for a performance with no brief; the
 * list IS the brief, so it comes first."
 */
package com.showup.profile

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.ControlPip
import com.showup.designsystem.ControlPipTone
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import com.showup.designsystem.Subtle
import com.showup.designsystem.Success
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.eyebrowCase
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copy for the media step, quoted from the ticket's "Copy — final strings". */
object MediaCopy {
    const val OPTIONAL_PILL = "Optional · you can skip this"
    const val HEADLINE_BEFORE = "Show your face. Let them "
    const val HEADLINE_EM = "hear you"
    const val HEADLINE_AFTER = "."
    const val SKIP = "Skip for now"
    const val CONTINUE = "Continue"

    const val VIDEO_TITLE = "A 10-second video"
    const val VIDEO_HINT = "Filmed here in the app. Ten seconds, one prompt."
    const val VOICE_TITLE = "A 15-second voice note"
    const val VOICE_HINT = "Just your voice, answering one prompt."
    const val PREVIEW_EYEBROW = "One of 11 prompts"
    const val SEE_THE_PROMPTS = "See the prompts"

    const val CAPTION_EYEBROW = "Prompt · shown on your profile"
    const val VIDEO_ROW_LABEL = "Your video"
    const val SAVED = "Saved"
    const val RETAKE = "Retake"
    const val DELETE = "Delete"

    const val SHEET_HEADLINE_BEFORE = "Pick one to "
    const val SHEET_HEADLINE_EM = "answer"
    const val SHEET_HEADLINE_AFTER = "."
    const val SHEET_SUB_VIDEO =
        "Ten seconds is short on purpose. Pick the easiest one — nobody is marking this."
    const val SHEET_SUB_VOICE =
        "Fifteen seconds, just your voice. Pick the easiest one — nobody is marking this."
    const val OWN_IDEA_SUB = "Say or show whatever you like — your own words, your own idea."
    const val COMMIT_EMPTY = "Choose a prompt to continue"
    const val COMMIT_VIDEO = "Film 10 seconds"
    const val COMMIT_VOICE = "Record 15 seconds"

    const val CANCEL = "Cancel"
    const val REC = "REC"
    const val VOICE_HINT_RECORDING = "Listening · keep going"
    const val VOICE_HINT_REVIEW = "Hear it back before you keep it"
    const val USE_CLIP = "Use this clip"
    const val USE_RECORDING = "Use this recording"
    const val RECORDED_SUFFIX = " recorded"

    const val PLAY_VIDEO = "Play your clip"
    const val PLAY_VOICE = "Play your recording"
    const val STOP = "Stop recording"

    /** The card's own failure surface. Never a modal -- it must not block leaving the screen. */
    const val UPLOAD_FAILED = "Upload failed"
    const val RETRY = "Retry"
    const val UPLOADING = "Uploading"

    fun title(kind: MediaKind) = if (kind == MediaKind.Video) VIDEO_TITLE else VOICE_TITLE
    fun hint(kind: MediaKind) = if (kind == MediaKind.Video) VIDEO_HINT else VOICE_HINT
    fun sheetSub(kind: MediaKind) = if (kind == MediaKind.Video) SHEET_SUB_VIDEO else SHEET_SUB_VOICE
    fun commit(kind: MediaKind) = if (kind == MediaKind.Video) COMMIT_VIDEO else COMMIT_VOICE
    fun usePrimary(kind: MediaKind) = if (kind == MediaKind.Video) USE_CLIP else USE_RECORDING
}

private val CardShape = RoundedCornerShape(20.dp)
private val InnerShape = RoundedCornerShape(14.dp)

/**
 * The shared card surface.
 *
 * `shadow` before `background`, because Compose's shadow is drawn by the modifier that clips the
 * shape and a background applied first would paint over nothing. Both cards use it, so the
 * elevation lives in one place.
 */
@Composable
private fun MediaCard(
    padding: PaddingValues,
    gap: Dp,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, CardShape, ambientColor = Fg, spotColor = Fg)
            .clip(CardShape)
            .background(Elevated)
            .border(1.dp, BorderSoft, CardShape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) { content() }
}

/**
 * The chosen prompt, read-only, under a recorded artefact.
 *
 * Replaces the 50-character caption input removed on 16 September 2026: the prompt the user
 * answered is a better caption than anything they would type under time pressure, it is already in
 * the product's voice, and it can never be left blank.
 */
@Composable
fun PromptCaption(prompt: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Column(
        modifier
            .fillMaxWidth()
            // A DASHED rule, which Compose has no border style for, so it is drawn. 1px at the top
            // edge only -- `border` would box all four sides.
            .drawBehind {
                val dash = with(density) { 3.dp.toPx() }
                drawLine(
                    color = BorderSoft,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = with(density) { 1.dp.toPx() },
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                )
            }
            .padding(top = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            MediaCopy.CAPTION_EYEBROW.eyebrowCase(),
            color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp, letterSpacing = 0.08.sp,
        )
        Text(
            prompt,
            color = Fg, fontFamily = Lora, fontStyle = FontStyle.Italic,
            fontSize = 15.sp, lineHeight = (15f * 1.35f).sp,
        )
    }
}

/**
 * An empty slot.
 *
 * DELIBERATELY COMPACT. The whole empty state -- pill, headline, both cards and both CTAs -- has to
 * sit above the fold on a 390x844 with nothing cut off, and that budget is why the removed
 * manifesto card is not coming back.
 */
@Composable
fun MediaSlotCard(
    kind: MediaKind,
    preview: MediaPrompt,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
    blocker: MediaBlocker? = null,
    platformLabel: String = "",
    onPermissionAction: () -> Unit = {},
) {
    Column(modifier) {
        MediaCard(
            padding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 13.dp),
            gap = Spacing.lg,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(LilacWash),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (kind == MediaKind.Video) BrandIcon.Video else BrandIcon.Mic,
                        20.dp, tint = Purple, strokeWidth = 1.8f,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        MediaCopy.title(kind),
                        color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                        fontSize = 17.sp, lineHeight = (17f * 1.2f).sp,
                        letterSpacing = (-0.17).sp,
                    )
                    Text(
                        MediaCopy.hint(kind),
                        modifier = Modifier.padding(top = 3.dp),
                        color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 13.sp, lineHeight = (13f * 1.4f).sp,
                    )
                }
            }

            // The previewed prompt -- the answer to "where do I start". One real prompt, verbatim,
            // so the list is understood before it opens. Which prompt this is comes from the
            // server; see MediaPrompts.preview.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(InnerShape)
                    .background(LilacWash)
                    .border(1.dp, Purple.copy(alpha = 0.10f), InnerShape)
                    .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.md, bottom = Spacing.lg),
            ) {
                Text(
                    MediaCopy.PREVIEW_EYEBROW.eyebrowCase(),
                    color = Purple, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.5.sp, letterSpacing = 0.08.sp,
                )
                Text(
                    preview.display,
                    modifier = Modifier.padding(top = 2.dp),
                    color = Fg, fontFamily = Lora, fontStyle = FontStyle.Italic,
                    fontSize = 14.5.sp, lineHeight = (14.5f * 1.3f).sp,
                )
            }

            // The permission row, in the RESERVED status region above the CTA -- the same place,
            // and the same treatment, as screen 06's camera row. Nothing is drawn before the first
            // refusal: a user who has never been asked sees two clean cards.
            if (blocker != null) {
                MediaPermissionRow(blocker, platformLabel, onPermissionAction)
            }

            PrimaryButton(
                label = MediaCopy.SEE_THE_PROMPTS,
                onClick = onChoose,
                variant = PrimaryButtonVariant.Sunset,
                // `md`, not the flow's full-width `lg`. See ComponentSizes.controlHeightMedium:
                // this is a button inside a card, and the 8dp it saves twice over is part of what
                // makes the empty state fit above the fold at 390x844.
                height = ComponentSizes.controlHeightMedium,
                labelSize = 15.sp,
            )
        }
    }
}

/**
 * The blocked / can-still-ask row.
 *
 * NO ARTBOARD EXISTS FOR THIS. Ten states are drawn and the two permission modes are not; the
 * ticket says the behaviour is binding and the treatment is not yet specified, and recommends
 * reusing the group's existing pattern rather than inventing a third. That is what this is: the
 * quiet lilac row with the violet lock glyph and a 30-tall action pill, as on screen 06's camera
 * row. Flagged back for a spec-sheet frame.
 *
 * THE BUTTON SAYS WHAT IT WILL DO. `CanAsk` re-prompts in app and names no toggle; `Blocked` opens
 * our app's page and names the platform's own label for the row. One label for both is how a
 * screen ends up telling a user to open Settings when it could simply have asked.
 */
@Composable
fun MediaPermissionRow(
    blocker: MediaBlocker,
    platformLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAsk = blocker.status == MediaPermission.CanAsk
    val message = when {
        canAsk && blocker.capability == MediaCapability.Camera ->
            "Allow camera access to film your 10 seconds."
        canAsk ->
            "Allow microphone access to record."
        blocker.capability == MediaCapability.Camera ->
            "$platformLabel access is off. Turn on $platformLabel in Settings to film."
        else ->
            "$platformLabel access is off. Turn on $platformLabel in Settings to record."
    }
    val action = when {
        canAsk && blocker.capability == MediaCapability.Camera -> "Allow camera access"
        canAsk -> "Allow microphone access"
        else -> "Settings"
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(LilacWash)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BrandIcon.Lock, 15.dp, tint = Purple, strokeWidth = 1.8f)
        Text(
            message,
            modifier = Modifier.weight(1f),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = (12f * 1.35f).sp,
        )
        // Draws 30 and answers at 44, the way everything tappable here does: the outer node
        // fixes the layout slot and the inner `requiredHeight` overflows it. The pill is wider
        // than 44 already, so only height needs it.
        // Minimums rather than fixed heights, for the reason in ControlPip: at 2.0x the label is
        // twice as tall and a 30dp box clips it on every device.
        Box(Modifier.heightIn(min = 30.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredHeightIn(min = ComponentSizes.minTapTarget)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(role = Role.Button, onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .heightIn(min = 30.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Purple)
                        .padding(horizontal = Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        action,
                        color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                    )
                }
            }
        }
    }
}

/**
 * A recorded video.
 *
 * The thumbnail is a REAL FRAME from the take, pulled with `MediaMetadataRetriever`. A placeholder
 * tile would be the honest thing only if no frame were reachable; one is, and a card that does not
 * show what was filmed cannot be checked by the person who filmed it.
 */
@Composable
fun RecordedVideoCard(
    artefact: MediaArtefact,
    onPlay: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether this card is the thing currently playing.
     *
     * Drives the surface below and hides the play glyph while it runs, so the control the user
     * just pressed is not still inviting the press.
     */
    isPlaying: Boolean = false,
    /**
     * The player to draw frames from, or null where there is none -- previews, the fit harness,
     * every screenshot. Same shape as the viewfinder taking a `LifecycleCameraController`: this is
     * the one kind of thing a screen in this project is allowed to be handed, because a decoder
     * cannot be a value.
     */
    player: Player? = null,
) {
    Column(modifier) {
        MediaCard(padding = PaddingValues(Spacing.xl), gap = Spacing.lg) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(InnerShape)
                    .background(LilacWash)
                    .clickable(role = Role.Button, onClick = onPlay)
                    .semantics { contentDescription = MediaCopy.PLAY_VIDEO },
            ) {
                if (isPlaying && player != null) {
                    VideoSurface(player, Modifier.fillMaxSize())
                } else {
                    VideoFrame(artefact.localPath, Modifier.fillMaxSize())
                }
                // The scrim, so the play glyph reads on any frame -- including a moving one, which
                // is why it is not lifted during playback: the glyph is still there to be pressed.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Fg.copy(alpha = 0.10f), Fg.copy(alpha = 0.32f)),
                            ),
                        ),
                )
                // The glyph stays while it plays: pressing it again is how a second play starts.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Nudged right, as the reference does: a triangle centred on its bounding
                    // box reads left of centre inside a circle, because its mass is on the left.
                    // `Icon` is a Canvas sized by its argument and takes no modifier, so the
                    // offset comes from a wrapper rather than from the glyph.
                    Box(Modifier.padding(start = Spacing.xs)) {
                        Icon(BrandIcon.Play, 22.dp, tint = Fg)
                    }
                }
                GlassPill(
                    text = formatTakeLength(artefact.durationMs),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Spacing.lg, bottom = Spacing.lg),
                )
                SavedChip(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = Spacing.lg, top = Spacing.lg),
                )
            }

            artefact.prompt?.let { PromptCaption(it.display) }

            RecordedControls(
                label = MediaCopy.VIDEO_ROW_LABEL,
                status = artefact.status,
                onRetake = onRetake,
                onDelete = onDelete,
                onRetry = onRetry,
            )
        }
    }
}

/**
 * The take's last frame, or nothing while it is being read.
 *
 * THE LAST FRAME, NOT THE FIRST, and the build inventory is specific about it: "a frozen LAST frame
 * for review and for the card thumbnail". One frame, two places -- so what the user approved on the
 * review screen is exactly what the card then shows, and a card that disagreed with the review it
 * came from would read as a different recording.
 *
 * Shared by the filled card and the review screen for the same reason.
 */
@Composable
internal fun VideoFrame(path: String?, modifier: Modifier = Modifier) {
    var frame by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        frame = path?.let { p ->
            withContext(Dispatchers.IO) {
                runCatching {
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(p)
                        val durationMs = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                        // OPTION_CLOSEST rather than the default sync-frame seek: a ten-second clip
                        // may have one keyframe, at zero, so asking for "the nearest sync frame to
                        // the end" would hand back the first frame and look like a bug in the seek
                        // rather than in the option.
                        val atUs = ((durationMs ?: 0L) * 1000L).coerceAtLeast(0L)
                        retriever.getFrameAtTime(
                            atUs, MediaMetadataRetriever.OPTION_CLOSEST,
                        )?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    frame?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

/** The duration pill on the thumbnail. Dark glass, tabular figures. */
@Composable
private fun GlassPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Fg.copy(alpha = 0.78f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Text(
            text,
            color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, letterSpacing = 0.02.sp,
        )
    }
}

/** `Saved`, top-right of the thumbnail. */
@Composable
private fun SavedChip(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(start = Spacing.sm, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckDot(14.dp, 8.dp)
        Text(
            MediaCopy.SAVED,
            color = SuccessFg, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, letterSpacing = 0.02.sp,
        )
    }
}

/** A filled success circle with a white tick. Two sizes, both from the reference. */
@Composable
private fun CheckDot(diameter: Dp, glyph: Dp) {
    Box(
        Modifier.size(diameter).clip(CircleShape).background(Success),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BrandIcon.Check, glyph, tint = Color.White, strokeWidth = 4f)
    }
}

/**
 * The row under a filled artefact: what it is on the left, what you can do to it on the right.
 *
 * ALSO THE CARD'S FAILURE SURFACE. An upload that did not land says so HERE, never as a modal, and
 * never in a way that blocks leaving the screen -- "a dropped upload never blocks the flow".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordedControls(
    label: String,
    status: MediaUploadStatus,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    // A FLOW ROW, so the controls drop to a second line rather than the label losing its end.
    //
    // On every screen 360 and wider this is exactly the Row the reference draws -- one line, label
    // left, pills right. At 320 the three do not fit, and the two honest answers are "truncate the
    // words" and "use another line". The fit harness rejects the first in as many words, and it is
    // right to: `Your video` ellipsised to `Your v...` on the narrowest phone is the copy giving
    // way to the layout, which is backwards.
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.sm, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        // No per-item cross-axis alignment in this Compose version, so each child centres its own
        // contents and the two land on the same line naturally -- the pills' 44 touch height is
        // the tallest thing in the row either way.
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                MediaUploadStatus.Failed -> {
                    Icon(BrandIcon.Close, 14.dp, tint = DangerFg, strokeWidth = 2.4f)
                    Text(
                        MediaCopy.UPLOAD_FAILED,
                        color = DangerFg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                    )
                    RetryLink(onRetry, 13.5)
                }
                MediaUploadStatus.Confirmed -> {
                    CheckDot(18.dp, 11.dp)
                    Text(
                        label,
                        color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                    )
                }
                else -> {
                    // Queued or in flight. The card is already filled -- optimistically -- so this
                    // says what is happening rather than pretending the artefact is not there.
                    Text(
                        MediaCopy.UPLOADING,
                        color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                    )
                }
            }
        }
        // DIRECT CHILDREN OF THE FLOW ROW, not a nested Row. Nested, the two pips were a single
        // item and could only move to the next line together -- and at 320 with the largest system
        // font there is no line they both fit on. As siblings they wrap one at a time.
        ControlPip(BrandIcon.Refresh, MediaCopy.RETAKE, onRetake)
        ControlPip(BrandIcon.Trash, MediaCopy.DELETE, onDelete, tone = ControlPipTone.Danger)
    }
}

/**
 * `Retry`, on a card whose upload did not land.
 *
 * DRAWS ITS TEXT AND ANSWERS AT 44. It was a bare Text with 4dp of vertical padding, which the fit
 * harness measured at 26.5 -- and this is the one control on the screen a user reaches for when
 * something has already gone wrong, so it is the worst possible place for a hit area smaller than
 * it looks.
 */
@Composable
private fun RetryLink(onRetry: () -> Unit, fontSize: Double) {
    Box(
        Modifier
            .requiredHeight(ComponentSizes.minTapTarget)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(role = Role.Button, onClick = onRetry)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            MediaCopy.RETRY,
            color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp, maxLines = 1, softWrap = false,
        )
    }
}

/**
 * A recorded voice note.
 *
 * 48 DETERMINISTIC BARS whose heights do not change between renders -- an acceptance criterion, and
 * the formula is [MediaWaveform.recorded], ported from the reference rather than approximated. A
 * waveform re-rolled on recomposition makes a finished recording appear to wobble while the user is
 * looking at it, and Compose recomposes for reasons that have nothing to do with audio.
 */
@Composable
fun RecordedVoiceCard(
    artefact: MediaArtefact,
    playedMs: Int,
    onPlay: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (artefact.durationMs <= 0) 0f else {
        (playedMs.toFloat() / artefact.durationMs).coerceIn(0f, 1f)
    }
    Column(modifier) {
        MediaCard(padding = PaddingValues(14.dp), gap = Spacing.xl) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(InnerShape)
                    .background(LilacWash)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Purple)
                        .clickable(role = Role.Button, onClick = onPlay)
                        .semantics { contentDescription = MediaCopy.PLAY_VOICE },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.padding(start = 3.dp)) {
                        Icon(BrandIcon.Play, 18.dp, tint = Color.White)
                    }
                }
                Waveform(
                    bars = MediaWaveform.recorded,
                    progress = progress,
                    playedColor = Purple,
                    restColor = Purple.copy(alpha = 0.28f),
                    barGap = 2.dp,
                    minBarHeight = 3.dp,
                    corner = 2.dp,
                    modifier = Modifier.weight(1f).height(36.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f, fill = false)) {
                    Text(
                        formatTakeLength(playedMs),
                        color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, letterSpacing = 0.02.sp,
                    )
                    Text(
                        " / ${formatTakeLength(artefact.durationMs)}",
                        color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, letterSpacing = 0.02.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ControlPip(BrandIcon.Refresh, MediaCopy.RETAKE, onRetake)
                    ControlPip(
                        BrandIcon.Trash, MediaCopy.DELETE, onDelete,
                        tone = ControlPipTone.Danger,
                    )
                }
            }

            if (artefact.status == MediaUploadStatus.Failed) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BrandIcon.Close, 14.dp, tint = DangerFg, strokeWidth = 2.4f)
                    Text(
                        MediaCopy.UPLOAD_FAILED,
                        color = DangerFg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    RetryLink(onRetry, 13.0)
                }
            }

            artefact.prompt?.let { PromptCaption(it.display) }
        }
    }
}

/**
 * The tag every waveform carries, so the fit harness can assert it was given room to draw in.
 *
 * A canvas that resolved to zero in either axis is invisible to every other check in this project:
 * nothing is clipped, nothing overflows, the node is present and reports a size. The only way to
 * catch it is to measure it, which is why this exists and why it is not a debug-only tag.
 */
internal const val WAVEFORM_TAG = "media:waveform"

/**
 * Frames, drawn by the player rather than decoded into a bitmap.
 *
 * A PLAYER WITH NOWHERE TO DRAW PLAYS THE AUDIO AND NOTHING ELSE, which on a video card is worse
 * than not playing: the user presses play on a picture of themselves and hears their own voice
 * coming from a still. This is the surface that makes the video a video.
 *
 * `useController = false` because the transport controls are ours -- the ticket draws a 56px play
 * button on the card and an 88px glass one on review, and ExoPlayer's own bar is neither.
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
 * The narrowest a waveform bar may be drawn.
 *
 * Below about this the bar lands inside a single device pixel at low densities and reads as nothing
 * at all. See [Waveform] for what that cost.
 */
private val MIN_BAR_WIDTH = 1.5.dp

/**
 * The bar strip, shared by the filled card and the capture screen.
 *
 * Drawn on a Canvas rather than as 48 composables: 48 nodes per waveform, two waveforms on screen,
 * recomposing on every playback tick is a lot of layout for a decoration. The arithmetic is
 * identical either way -- each bar is `1/48` of the width minus the gap.
 */
@Composable
internal fun Waveform(
    bars: List<Float>,
    progress: Float,
    playedColor: Color,
    restColor: Color,
    barGap: Dp,
    minBarHeight: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.testTag(WAVEFORM_TAG)) {
        val minH = minBarHeight.toPx()
        val radius = corner.toPx()
        val slot = (size.width + barGap.toPx()) / bars.size
        // THE GAP YIELDS BEFORE THE BAR DOES.
        //
        // 48 bars at a fixed 4dp gap spend 188dp on gaps alone. That is comfortable at the width
        // the design was drawn at -- 390, where the bars still get about 1.6dp each -- and it is
        // ruinous on a 320 screen with the review screen's play pip beside it: 194dp of room, 188
        // of it gap, leaving an eighth of a dp per bar. The waveform did not look thin, it looked
        // ABSENT, and nothing in the fit harness can see a canvas that drew nothing.
        //
        // So the gap is a preference and the bar width is a floor. Where the design's 4dp already
        // leaves a drawable bar nothing changes; where it does not, the gap gives way.
        val minWidth = MIN_BAR_WIDTH.toPx()
        val width = (slot - barGap.toPx()).coerceAtLeast(minWidth).coerceAtMost(slot)
        val gap = (slot - width).coerceAtLeast(0f)
        bars.forEachIndexed { index, value ->
            val height = (value * size.height).coerceAtLeast(minH)
            val left = index * slot + gap / 2f
            val top = (size.height - height) / 2f
            drawRoundRect(
                color = if (index < bars.size * progress) playedColor else restColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
        }
    }
}
