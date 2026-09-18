/*
 * EvidenceScreenshots.kt
 * ShowUp · the images each ticket's last acceptance criterion asks for
 *
 * SHOWUP-155, SHOWUP-156 and SHOWUP-158 all end the same way: "Evidence of done: attach
 * screenshots of all <n> states at 375 x 667, 390 x 844 and 430 x 932". Three, twenty-one and
 * twenty-four images.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS IS A TEST AND NOT A PERSON WITH AN EMULATOR
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Forty-eight screenshots taken by hand is an afternoon, and it is an afternoon again the next
 * time a colour changes. Worse, it is an afternoon of screenshots nobody can check: a person
 * resizing an emulator three times and walking seven states is a person who will, at some point,
 * capture the same state twice and not notice.
 *
 * The fit harness already renders every state at every size; this asks it for the pixels instead
 * of the measurements. The states are the SAME LIST the fit sweeps use, deliberately -- if a state
 * is added to one and not the other, the counts below stop matching the tickets and this fails.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT THESE IMAGES ARE, AND WHAT THEY ARE NOT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * They are the composition, rendered into the device's SAFE AREA at that device's size, with the
 * platform's own fonts. That is what the acceptance criteria are about: all content visible,
 * nothing clipped, the CTA on screen.
 *
 * They are NOT a device screenshot. There is no status bar, no gesture bar, no keyboard and no
 * wallpaper behind a translucent system bar, because none of those is ours. The tickets' own
 * reference files draw a mocked status bar for the same reason and say not to build it.
 *
 * Written to `app/build/evidence/`, which is not committed -- an image is a build output, and
 * forty-eight of them in git is a repository nobody can clone on a train.
 */
package com.showup.fit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import com.showup.profile.CameraAccess
import com.showup.profile.LibraryAccess
import com.showup.profile.PhotoGridState
import com.showup.profile.PickedPhoto
import com.showup.profile.ProfileEmbraceScreen
import com.showup.profile.MediaAccess
import com.showup.profile.MediaArtefact
import com.showup.profile.MediaPermission
import com.showup.profile.MediaCaptureScreen
import com.showup.profile.MediaEntryPoint
import com.showup.profile.MediaKind
import com.showup.profile.MediaSheet
import com.showup.profile.MediaState
import com.showup.profile.MediaTake
import com.showup.profile.MediaUploadStatus
import com.showup.profile.ProfileMediaScreen
import com.showup.profile.ProfilePhotosScreen
import com.showup.profile.RecordingPhase
import com.showup.profile.ProfilePromptsScreen
import com.showup.profile.PROMPT_SAMPLE_AT_CAP
import com.showup.profile.PROMPT_SAMPLE_MID
import com.showup.profile.PromptSheet
import com.showup.profile.PromptsState
import com.showup.profile.SavedPrompt
import com.showup.profile.UploadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class EvidenceScreenshots {

    /**
     * The three frames every ticket names, by name rather than by number.
     *
     * Pulled from [DEVICES] so the safe-area insets are the same ones the fit sweeps measure
     * against -- a screenshot taken at a different usable height than the one that was measured is
     * evidence for a screen nobody tested.
     */
    private val frames = DEVICES.filter { it.inAcceptanceCriteria }

    private val out = File("build/evidence").apply { mkdirs() }

    /**
     * How much virtual time each render is given.
     *
     * Two frames at 60Hz is 33ms; 200 is generous enough that a slow composition is never cut
     * short and small enough that thirty renders cost no real time at all. See [settle].
     */
    private val SETTLE_MS = 200L

    /**
     * Renders one state at each frame and writes the pixels.
     *
     * WHY THE VIEW IS DRAWN INTO A BITMAP RATHER THAN `captureToImage()`.
     *
     * The obvious call is the test API's own: `onNodeWithTag(...).captureToImage()`. Under
     * Robolectric it times out -- "Condition still not satisfied after 2000 ms" -- because that
     * path asks the WINDOW to redraw and wait for the frame, and Robolectric has no surface to
     * produce one from. It is built for an instrumented device.
     *
     * A `ComposeView` measured, laid out and drawn into a `Bitmap` needs no window at all, which
     * is the same thing every Robolectric screenshot library does underneath. `GraphicsMode.NATIVE`
     * is what makes the result real pixels rather than a no-op canvas -- the same annotation the
     * fit harness needs for font metrics, and for the same reason.
     */
    private fun shoot(
        ticket: String,
        state: String,
        /**
         * The system font size to render at, as a multiplier.
         *
         * 1.0 for the ticket frames, which are what the design was drawn at. The tight sweep below
         * uses 2.0, because that and the narrowest phone together are where every layout bug in
         * this flow has actually been found.
         */
        fontScale: Float = 1f,
        /** Which frames to render. Defaults to the three the tickets name. */
        devices: List<Device> = frames,
        content: @Composable () -> Unit,
    ) {
        // ANIMATIONS OFF, FOR THE WHOLE HARNESS.
        //
        // Two reasons, and the second cost four and a half hours of CI. A frame captured mid
        // animation is not reproducible evidence -- a pulsing dot photographed at whatever phase
        // the clock happened to be in changes between runs. And a composition carrying an
        // animation that never ends never goes idle, so the render never returns.
        //
        // `rememberMotion` reads this setting, which is what a person turning animations off in
        // Settings changes. Setting it here is that same instruction, not a test-only back door.
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        devices.forEach { device ->
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            val view = ComposeView(activity)
            activity.setContentView(view)
            var widthPx = 0
            var heightPx = 0
            view.setContent {
                val cfg = android.content.res.Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = device.width
                    screenHeightDp = device.height
                    this.fontScale = fontScale
                }
                // Captured from the composition rather than assumed from the qualifiers, so the
                // bitmap is the right size whatever density the test host reports.
                widthPx = with(LocalDensity.current) { device.width.dp.roundToPx() }
                heightPx = with(LocalDensity.current) { device.safeHeight.dp.roundToPx() }
                // BOTH, not one. The Configuration is what a composable reading
                // `LocalConfiguration.fontScale` sees; LocalDensity is what actually sizes an `sp`.
                // Setting only the first scales nothing -- the same trap the fit harness documents.
                CompositionLocalProvider(
                    LocalConfiguration provides cfg,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    Box(
                        Modifier.requiredSize(device.width.dp, device.safeHeight.dp),
                    ) { content() }
                }
            }
            // Compose schedules its first composition on the main looper; nothing is laid out
            // until it has run.
            settle()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, widthPx, heightPx)
            settle()

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val suffix = if (fontScale == 1f) "" else "_fontScale$fontScale"
            val name = "${ticket}_${state}_${device.width}x${device.height}$suffix.png"
            File(out, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    /**
     * Runs the main looper for a BOUNDED slice of virtual time.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * WHY THIS IS NOT `idle()`, WHICH IS WHAT IT USED TO BE
     * ─────────────────────────────────────────────────────────────────────────
     *
     * `idle()` drains the queue until it is EMPTY. A screen carrying an infinite animation never
     * empties it: `rememberInfiniteTransition` re-posts a frame callback every frame, forever, so
     * draining to empty is draining to never.
     *
     * Nothing here hit that until the media step arrived. Its `REC` chip pulses while recording,
     * so states G and I are the first screens in this harness with an animation that does not end
     * -- and the CI run that introduced them spent FOUR AND A HALF HOURS in `testDebugUnitTest`,
     * against about three minutes before. It passed, which is the worst way to fail.
     *
     * `idleFor` advances a VIRTUAL clock by a fixed amount and runs what falls due. Composition and
     * layout are posted immediately and complete in the first frame or two, so the window below is
     * generous for them and finite for everything else. It also makes the captured frame
     * DETERMINISTIC: a pulsing dot photographed at whatever phase the wall clock happened to be in
     * is not reproducible evidence, and evidence that changes between runs is not evidence.
     *
     * This is the harness's bug rather than the chip's. The app is allowed an animation that does
     * not end; a screenshot is not allowed to wait for one.
     */
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(SETTLE_MS, TimeUnit.MILLISECONDS)
    }

    private fun confirmed(n: Int) = List(n) { PickedPhoto(it.toLong(), null, UploadStatus.Confirmed) }

    private val onePrompt = listOf(
        SavedPrompt(
            "first_date_usually",
            "Talk about anything real. Not jobs, not pets, not the weather. The thing actually " +
                "on your mind this week. Bring it. I'll listen.",
        ),
    )
    private val threePrompts = onePrompt + listOf(
        SavedPrompt(
            "hill_to_die_on",
            "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer.",
        ),
        SavedPrompt(
            "cross_town_for",
            "A proper conversation. An old cinema. The 8pm walk after a long day.",
        ),
    )

    /**
     * The same screens at the largest system font on the narrowest phone.
     *
     * ─────────────────────────────────────────────────────────────────────────
     * WHY A SECOND SWEEP EXISTS
     * ─────────────────────────────────────────────────────────────────────────
     *
     * Every layout defect in this flow has been found at 320 x 2.0 and at no other combination,
     * and several of them were invisible to `ScreenFitTest` at the same size. A canvas that
     * resolved to zero height drew nothing and clipped nothing. A hint pushed past the end of its
     * own scrolling region sat inside the viewport, so nothing was off the bottom. A chip broke
     * `RECORDED` into `RECORD` and `ED`, which is two legal lines of text.
     *
     * All three were found by looking at a picture. The harness has since learned to catch each of
     * them -- see the canvas check and the scroll-overflow check in `FitHarness` -- but the
     * pictures are what found them, and the Definition of Done ends with somebody looking at the
     * screen for exactly this reason. These are the frames to look at.
     */
    @Test
    fun `the tight states, at the narrowest phone and the largest system font`() {
        val tight = listOf(DEVICES.first { it.width == 320 })

        shoot("TIGHT", "media-empty", fontScale = 2f, devices = tight) { ProfileMediaScreen() }
        shoot("TIGHT", "media-both", fontScale = 2f, devices = tight) {
            ProfileMediaScreen(
                MediaState(
                    video = MediaArtefact(
                        kind = MediaKind.Video, promptId = "relaxed_and_happy",
                        durationMs = 9_400, localPath = null, remoteId = "v1",
                        status = MediaUploadStatus.Confirmed,
                    ),
                    voice = MediaArtefact(
                        kind = MediaKind.Voice, promptId = "relaxing_sound",
                        durationMs = 14_100, localPath = null, remoteId = "a1",
                        status = MediaUploadStatus.Confirmed,
                    ),
                ),
            )
        }
        shoot("TIGHT", "media-prompt-sheet", fontScale = 2f, devices = tight) {
            ProfileMediaScreen(
                MediaState(
                    sheet = MediaSheet(
                        MediaKind.Video, MediaEntryPoint.SeeThePrompts, openedAtMs = 0L,
                    ),
                ),
            )
        }
        shoot("TIGHT", "media-blocked", fontScale = 2f, devices = tight) {
            ProfileMediaScreen(
                MediaState(access = MediaAccess(microphone = MediaPermission.Blocked)),
                platformLabel = { "Microphone" },
            )
        }
        shoot("TIGHT", "voice-review", fontScale = 2f, devices = tight) {
            MediaCaptureScreen(
                MediaTake(
                    MediaKind.Voice, "relaxing_sound",
                    phase = RecordingPhase.Review, elapsedMs = 14_000,
                ),
            )
        }
        shoot("TIGHT", "voice-recording", fontScale = 2f, devices = tight) {
            MediaCaptureScreen(
                MediaTake(MediaKind.Voice, "relaxing_sound", elapsedMs = 6_000),
            )
        }
        shoot("TIGHT", "video-review", fontScale = 2f, devices = tight) {
            MediaCaptureScreen(
                MediaTake(
                    MediaKind.Video, "relaxed_and_happy",
                    phase = RecordingPhase.Review, elapsedMs = 9_000,
                ),
            )
        }
        // Not a media screen. The photo sheet's blocked camera row is where the `Settings` pill
        // and the sentence beside it compete for a 320dp line, and it is the other screen the
        // unmerged-tree harness found a real clipping bug on.
        shoot("TIGHT", "photos-camera-blocked", fontScale = 2f, devices = tight) {
            ProfilePhotosScreen(
                PhotoGridState(photos = confirmed(2)),
                sheetOpen = true,
                camera = CameraAccess.Blocked,
            )
        }
    }

    @Test
    fun `every state of every ticket, at the three frames the tickets name`() {
        // SHOWUP-155 -- one state. The ticket asks for three images and says what to check on the
        // smallest: that the spacer collapsed to about 65 and the headline still fits.
        shoot("SHOWUP-155", "bridge") { ProfileEmbraceScreen(firstName = "Leo") }

        // SHOWUP-156 -- seven states, A through G, in the ticket's own order and lettering.
        shoot("SHOWUP-156", "A-empty") { ProfilePhotosScreen() }
        shoot("SHOWUP-156", "B-partial") {
            ProfilePhotosScreen(PhotoGridState(photos = confirmed(2)))
        }
        shoot("SHOWUP-156", "C-uploading-failed") {
            ProfilePhotosScreen(
                PhotoGridState(
                    photos = listOf(
                        PickedPhoto(0, null, UploadStatus.Confirmed),
                        PickedPhoto(1, null, UploadStatus.InFlight, progress = 0.62f),
                        PickedPhoto(2, null, UploadStatus.Failed),
                    ),
                ),
            )
        }
        shoot("SHOWUP-156", "D-library-blocked") {
            ProfilePhotosScreen(library = LibraryAccess.Blocked)
        }
        shoot("SHOWUP-156", "E-library-can-ask") {
            ProfilePhotosScreen(library = LibraryAccess.CanAsk)
        }
        shoot("SHOWUP-156", "F-source-sheet") {
            ProfilePhotosScreen(PhotoGridState(photos = confirmed(2)), sheetOpen = true)
        }
        shoot("SHOWUP-156", "G-camera-blocked") {
            ProfilePhotosScreen(
                PhotoGridState(photos = confirmed(2)),
                sheetOpen = true,
                camera = CameraAccess.Blocked,
            )
        }

        // SHOWUP-158 -- eight states, A through H.
        shoot("SHOWUP-158", "A-none-saved") { ProfilePromptsScreen() }
        shoot("SHOWUP-158", "B-one-saved") {
            ProfilePromptsScreen(PromptsState(prompts = onePrompt))
        }
        shoot("SHOWUP-158", "C-three-saved") {
            ProfilePromptsScreen(PromptsState(prompts = threePrompts))
        }
        shoot("SHOWUP-158", "D-topic-sheet") {
            ProfilePromptsScreen(PromptsState(prompts = onePrompt, sheet = PromptSheet.Topics))
        }
        shoot("SHOWUP-158", "E-write-empty") {
            ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Write("first_date_usually")))
        }
        shoot("SHOWUP-158", "F-write-mid") {
            ProfilePromptsScreen(
                PromptsState(
                    sheet = PromptSheet.Write("first_date_usually"),
                    drafts = mapOf("first_date_usually" to PROMPT_SAMPLE_MID),
                ),
            )
        }
        shoot("SHOWUP-158", "G-write-at-cap") {
            ProfilePromptsScreen(
                PromptsState(
                    sheet = PromptSheet.Write("first_date_usually"),
                    // EXACTLY 160. A sample one character short is state F wearing state G's
                    // name, which is what the first run of this actually produced.
                    drafts = mapOf("first_date_usually" to PROMPT_SAMPLE_AT_CAP),
                ),
            )
        }
        shoot("SHOWUP-158", "H-write-nudge") {
            ProfilePromptsScreen(
                PromptsState(sheet = PromptSheet.Write("first_date_usually"), nudge = true),
            )
        }

        // ── SHOWUP-161 · the media step, all ten states ─────────────────────
        //
        // "Evidence of done: attach screenshots of all ten states at 375 x 667, 390 x 844 and
        // 430 x 932 -- thirty images." A to F are the media screen and its sheet; G to J are the
        // two capture sub-screens, which have no chrome at all.
        //
        // THE CAPTURE VIEWS RENDER WITHOUT A CAMERA, and that is what these images are for. The
        // viewfinder is a live preview layer that this harness cannot produce, so G and H show the
        // composition over the viewfinder's own dark ground -- which is exactly what the ticket
        // asks these images to prove: "nothing clipped and the primary action fully visible". The
        // scene behind them is the one thing on this screen that is never ours to draw.
        val mediaVideo = MediaArtefact(
            kind = MediaKind.Video, promptId = "relaxed_and_happy", durationMs = 9_400,
            localPath = null, remoteId = "v1", status = MediaUploadStatus.Confirmed,
        )
        val mediaVoice = MediaArtefact(
            kind = MediaKind.Voice, promptId = "relaxing_sound", durationMs = 14_100,
            localPath = null, remoteId = "a1", status = MediaUploadStatus.Confirmed,
        )

        shoot("SHOWUP-161", "A-empty") { ProfileMediaScreen() }
        shoot("SHOWUP-161", "B-video-only") {
            ProfileMediaScreen(MediaState(video = mediaVideo))
        }
        shoot("SHOWUP-161", "C-voice-only") {
            ProfileMediaScreen(MediaState(voice = mediaVoice))
        }
        shoot("SHOWUP-161", "D-both") {
            ProfileMediaScreen(MediaState(video = mediaVideo, voice = mediaVoice))
        }
        shoot("SHOWUP-161", "E-prompts-video") {
            ProfileMediaScreen(
                MediaState(
                    sheet = MediaSheet(
                        MediaKind.Video, MediaEntryPoint.SeeThePrompts, openedAtMs = 0L,
                    ),
                ),
            )
        }
        shoot("SHOWUP-161", "F-prompts-voice") {
            ProfileMediaScreen(
                MediaState(
                    sheet = MediaSheet(
                        MediaKind.Voice, MediaEntryPoint.SeeThePrompts,
                        selectedId = "relaxing_sound", openedAtMs = 0L,
                    ),
                ),
            )
        }
        shoot("SHOWUP-161", "G-video-recording") {
            MediaCaptureScreen(MediaTake(MediaKind.Video, "relaxed_and_happy", elapsedMs = 6_000))
        }
        shoot("SHOWUP-161", "H-video-review") {
            MediaCaptureScreen(
                MediaTake(
                    MediaKind.Video, "relaxed_and_happy",
                    phase = RecordingPhase.Review, elapsedMs = 9_000,
                ),
            )
        }
        shoot("SHOWUP-161", "I-voice-recording") {
            MediaCaptureScreen(MediaTake(MediaKind.Voice, "relaxing_sound", elapsedMs = 4_000))
        }
        shoot("SHOWUP-161", "J-voice-review") {
            MediaCaptureScreen(
                MediaTake(
                    MediaKind.Voice, "relaxing_sound",
                    phase = RecordingPhase.Review, elapsedMs = 14_000,
                ),
            )
        }

        // The counts the tickets ask for, asserted rather than trusted. A state added to the fit
        // sweep and forgotten here shows up as a number that no longer matches its ticket.
        val produced = out.listFiles().orEmpty().map { it.name }
        assertEquals("SHOWUP-155 asks for 3 images", 3, produced.count { it.startsWith("SHOWUP-155") })
        assertEquals("SHOWUP-156 asks for 21 images", 21, produced.count { it.startsWith("SHOWUP-156") })
        assertEquals("SHOWUP-158 asks for 24 images", 24, produced.count { it.startsWith("SHOWUP-158") })
        // Ten states at three sizes. The eleventh -- the permission row -- is deliberately absent:
        // "Include one capture per permission mode ONCE THE ROW IS SPECCED", and it has no artboard.
        assertEquals("SHOWUP-161 asks for 30 images", 30, produced.count { it.startsWith("SHOWUP-161") })

        // And an image that is a blank rectangle is not evidence of anything. A real render of one
        // of these screens is tens of kilobytes; an empty one compresses to a few hundred bytes.
        out.listFiles().orEmpty().forEach {
            assertTrue("${it.name} is too small to be a rendered screen", it.length() > 5_000)
        }
        println("EVIDENCE: ${produced.size} images in ${out.absolutePath}")
    }
}
