/*
 * ScreenFitTest.kt
 * ShowUp · every screen, every state, on every phone the app has to run on
 *
 * This is the answer to "does it actually fit". Seventeen device sizes from the 320x686 Galaxy Fold
 * cover screen up to the 440x956 iPhone 16 Pro Max, against every screen and every state either
 * flow can be in --
 * including the states a person only reaches by getting something wrong, which is exactly where a
 * layout is least likely to have been looked at.
 *
 * The measurements come from FitHarness, which is itself checked by HarnessSelfTest. Read that
 * first if you doubt a result here: a green run means nothing unless the instrument fires.
 *
 * Each test prints its full findings before asserting, so a failure tells you the device, the
 * element and the overshoot rather than only that something, somewhere, is wrong.
 */
package com.showup.fit

import com.showup.HomePlaceholderScreen
import com.showup.tutorial.MatchMeansMeetScreen
import com.showup.tutorial.MatchOnAvailabilityScreen
import com.showup.tutorial.MeetInRealLifeScreen
import com.showup.tutorial.ShowUpEveryTimeScreen
import com.showup.tutorial.ThirtyMinutesScreen
import com.showup.tutorial.WelcomeScreen
import com.showup.welcome.AuthMethod
import com.showup.welcome.ConnectAccountScreen
import com.showup.welcome.ConnectState
import com.showup.welcome.ErrorKind
import com.showup.welcome.PhoneError
import com.showup.welcome.PhoneNumberScreen
import com.showup.welcome.SignUpOutcome
import com.showup.profile.DateOrder
import com.showup.profile.ProfileDobScreen
import com.showup.profile.ProfileEmailScreen
import com.showup.profile.ProfileEmbraceScreen
import com.showup.profile.CameraAccess
import com.showup.profile.LibraryAccess
import com.showup.profile.PhotoGridState
import com.showup.profile.PickedPhoto
import com.showup.profile.ProfilePhotosScreen
import com.showup.profile.MediaAccess
import com.showup.profile.MediaArtefact
import com.showup.profile.MediaCaptureScreen
import com.showup.profile.MediaEntryPoint
import com.showup.profile.MediaKind
import com.showup.profile.MediaPermission
import com.showup.profile.MediaSheet
import com.showup.profile.MediaState
import com.showup.profile.MediaTake
import com.showup.profile.MediaUploadStatus
import com.showup.profile.ProfileMediaScreen
import com.showup.profile.ProfileNotificationsScreen
import com.showup.profile.ProfilePromptsScreen
import com.showup.profile.RecordingPhase
import com.showup.profile.PROMPT_SAMPLE_AT_CAP
import com.showup.profile.PromptSheet
import com.showup.profile.PromptsState
import com.showup.profile.SavedPrompt
import com.showup.profile.UploadStatus
import com.showup.profile.ProfileNameScreen
import com.showup.profile.ProfileVerifyEmailScreen
import com.showup.profile.VerifyState
import com.showup.welcome.StartupScreen
import com.showup.welcome.VerifyCodeScreen
import com.showup.welcome.WelcomeBackScreen
import com.showup.welcome.countryForRegion
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class ScreenFitTest {

    /** Runs one screen state across the whole device matrix and reports everything it found. */
    private fun sweep(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val found = DEVICES.flatMap { measureFit(it, name, content = content) }
        report(name, found)
    }

    /**
     * The same sweep at a system font size, and optionally with the keyboard up.
     *
     * TWO THINGS NOTHING HERE MEASURED UNTIL 15 SEPTEMBER 2026, both of them named as requirements
     * in `CLAUDE.md` and both of them left to a person:
     *
     *   · "usable at the largest system font". 1.3 is Android's largest non-accessibility step and
     *     2.0 its largest accessibility one. Fixed heights are what large type overflows, and
     *     "The real you" is the first group with several.
     *   · "the keyboard must not hide any required control... not yet automated on either
     *     platform -- check it by hand". By hand is not a thing seventeen devices get.
     */
    private fun sweep(
        name: String,
        fontScale: Float = 1f,
        keyboardDp: Int = 0,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val found = DEVICES.flatMap { measureFit(it, name, fontScale, keyboardDp, content) }
        report(name, found)
    }

    /**
     * EMPTY, since 10 September 2026, and that is the headline.
     *
     * This held eight element labels -- the findings recorded in audit/FIT-2026-08-31.md, all of
     * them the bottom band of a screen squeezed on a short phone. The comment here said the list
     * was meant to shrink and that adding to it was a defeat. It has shrunk to nothing: 117
     * findings across Connect and Welcome back are gone, fixed rather than accepted.
     *
     * Two changes did it. `WelcomeScaffold(scrollWhenTight = true)` on both screens lets the
     * layout ask for the height it needs instead of being clamped, and `PrimaryButton` takes a
     * minimum height rather than a fixed one so a label too wide for a 320dp phone wraps instead
     * of being cut. Neither changes anything on a device where the content already fitted.
     *
     * Keep it empty. A finding that appears here again is a real regression on a real phone, and
     * the honest response is to fix the screen rather than to write its name down.
     */
    private val known = emptyList<String>()

    /**
     * Nothing is known any more, so nothing is excused.
     *
     * The blanket passes for "Next" and "Back" went with the list. They were there because those
     * two words are short enough to appear inside an unrelated label, which mattered when the
     * list was doing real suppression; with nothing to suppress they only stood between this
     * suite and a genuine finding on the two most important controls in the flow.
     */
    private fun Violation.isKnown(): Boolean = known.any { element.contains(it) }

    private val collected = mutableListOf<Violation>()

    private fun report(name: String, found: List<Violation>) {
        collected += found
        val real = found.filterNot { it.advisory }
        val fresh = real.filterNot { it.isKnown() }
        val backlog = real.count { it.isKnown() }
        when {
            real.isEmpty() ->
                println("OK   $name -- clean on all ${DEVICES.size} devices")
            fresh.isEmpty() ->
                println("OK   $name -- clean apart from $backlog known finding(s); " +
                    "see audit/FIT-2026-08-31.md")
            else -> {
                println("FAIL $name -- ${fresh.size} NEW problem(s):")
                fresh.forEach { println("       $it") }
            }
        }

        // ADVISORIES ARE PRINTED, and they were not until 18 September 2026.
        //
        // They were collected, counted against nothing and discarded -- so `BELOW THE FOLD`, the
        // 56dp note and everything else non-fatal was invisible in the log as well as in the
        // assertion. The voice screen's hint was hidden behind its own scroll container on one
        // device and the report for that state said, in full, "clean on all 17 devices".
        //
        // Rolled up per element rather than per device, because the same advisory on 17 phones is
        // one thing to look at, not seventeen.
        found.filter { it.advisory }
            .groupBy { it.element to it.problem }
            .toList()
            .sortedByDescending { it.second.size }
            .forEach { (key, hits) ->
                val where = if (hits.size == DEVICES.size) {
                    "all ${DEVICES.size} devices"
                } else {
                    hits.joinToString(", ") { it.device.name }.take(70)
                }
                println("     note  ${key.first} -- ${key.second}: ${hits.first().detail}" +
                    "  [$where]")
            }
    }

    private fun assertClean() {
        val fresh = collected.filterNot { it.advisory }.filterNot { it.isKnown() }
        assertTrue(
            "NEW layout problems, not in audit/FIT-2026-08-31.md:\n" +
                fresh.joinToString("\n") { "  $it" },
            fresh.isEmpty(),
        )
    }

    // ── welcome and sign-up ─────────────────────────────────────────────────

    @Test
    fun `startup`() {
        sweep("Startup") { StartupScreen() }
        // The dates figure is off today but is a product decision, not a deleted feature, so the
        // layout has to hold with it on too.
        sweep("Startup + social proof") { StartupScreen(showSocialProof = true) }
        assertClean()
    }

    @Test
    fun `welcome back, every last-used value and every name length`() {
        // SHOWUP-142 names these cases outright: four methods, the unknown fallback, and headline
        // behaviour at 2 characters, 24 characters and no name at all.
        listOf(
            AuthMethod.Phone, AuthMethod.Apple, AuthMethod.Google,
            AuthMethod.Facebook, AuthMethod.Unknown,
        ).forEach { m ->
            sweep("WelcomeBack/$m") { WelcomeBackScreen(name = "Leo", lastUsed = m) }
        }
        sweep("WelcomeBack/no name") { WelcomeBackScreen(name = "", lastUsed = AuthMethod.Unknown) }
        sweep("WelcomeBack/2 chars") { WelcomeBackScreen(name = "Jo", lastUsed = AuthMethod.Phone) }
        sweep("WelcomeBack/24 chars") {
            WelcomeBackScreen(name = "Maximiliane Fürstenberg", lastUsed = AuthMethod.Phone)
        }
        assertClean()
    }

    @Test
    fun `phone number entry, empty and every error`() {
        sweep("Phone/empty") { PhoneNumberScreen() }
        sweep("Phone/typed") { PhoneNumberScreen(value = "17612345678") }
        // Every error string has to fit the reserved space, and the longest one is the test.
        PhoneError.values().forEach { e ->
            sweep("Phone/$e") { PhoneNumberScreen(value = "1761", error = e) }
        }
        // A long country name in the pill, and a long dial code.
        sweep("Phone/long country") {
            PhoneNumberScreen(value = "5551234", country = countryForRegion("GB"))
        }
        assertClean()
    }

    @Test
    fun `code entry, idle mismatch and cooldown`() {
        sweep("Code/empty") { VerifyCodeScreen() }
        sweep("Code/typed") { VerifyCodeScreen(digits = "4807") }
        sweep("Code/mismatch") { VerifyCodeScreen(digits = "480000", mismatch = true) }
        sweep("Code/resend ready") { VerifyCodeScreen(cooldownSeconds = 0) }
        // The attempt cap's own state. Its card carries a DIFFERENT string from the mismatch one,
        // and the region under the slots is a 42 floor sized for one line -- so a message that
        // wrapped would grow the region and take the CTA with it. This is the sweep that would
        // catch that, and the reason the copy was kept to the mismatch line's length.
        sweep("Code/locked out") {
            VerifyCodeScreen(digits = "480000", mismatch = true, lockedOut = true)
        }
        // The number is echoed back, so a long international one is the widest this can get.
        sweep("Code/long number") { VerifyCodeScreen(phone = "+880 1712 345678") }
        assertClean()
    }

    @Test
    fun `connect account, all ten states`() {
        sweep("Connect/idle") { ConnectAccountScreen(state = ConnectState.Idle) }
        sweep("Connect/tapped") { ConnectAccountScreen(state = ConnectState.Tapped) }
        sweep("Connect/handoff") { ConnectAccountScreen(state = ConnectState.Handoff) }
        sweep("Connect/linking") { ConnectAccountScreen(state = ConnectState.Linking) }
        sweep("Connect/success") { ConnectAccountScreen(state = ConnectState.Success) }
        sweep("Connect/success no name") {
            ConnectAccountScreen(state = ConnectState.Success, firstName = null)
        }
        sweep("Connect/success long name") {
            ConnectAccountScreen(state = ConnectState.Success, firstName = "Maximiliane")
        }
        sweep("Connect/cancelled") { ConnectAccountScreen(state = ConnectState.Cancelled) }
        sweep("Connect/error network") {
            ConnectAccountScreen(state = ConnectState.Error, kind = ErrorKind.Network)
        }
        sweep("Connect/error declined") {
            ConnectAccountScreen(state = ConnectState.Error, kind = ErrorKind.Declined)
        }
        sweep("Connect/conflict") { ConnectAccountScreen(state = ConnectState.Conflict) }
        sweep("Connect/conflict no email") {
            ConnectAccountScreen(state = ConnectState.Conflict, conflictEmail = null)
        }
        sweep("Connect/conflict long email") {
            ConnectAccountScreen(
                state = ConnectState.Conflict,
                conflictEmail = "leonhard.schwarzkopf@studio-mantis.example.com",
            )
        }
        assertClean()
    }

    @Test
    fun `the home placeholder, both outcomes`() {
        sweep("Home/new") { HomePlaceholderScreen(SignUpOutcome.NewAccount, {}) }
        sweep("Home/returning") { HomePlaceholderScreen(SignUpOutcome.ReturningMember, {}) }
        assertClean()
    }

    // ── profile creation · "The basics" ─────────────────────────────────────

    /**
     * SHOWUP-150 and SHOWUP-152, every state.
     *
     * The error states are the ones worth sweeping: on name the status region is RESERVED so
     * nothing should move, and on email it deliberately is not, so the consent row and CTA shift
     * ~18 lower. Both claims are about layout at every width, which is what this harness measures.
     */
    @Test
    fun `profile basics, all states`() {
        sweep("Profile/name empty") { ProfileNameScreen() }
        sweep("Profile/name typed") { ProfileNameScreen(value = "Leo") }
        sweep("Profile/name long") { ProfileNameScreen(value = "Maximiliane") }

        sweep("Profile/email valid") { ProfileEmailScreen(value = "leo@hey.com") }
        sweep("Profile/email invalid") { ProfileEmailScreen(value = "leo@hey") }
        sweep("Profile/email empty") { ProfileEmailScreen() }
        sweep("Profile/email consent on") {
            ProfileEmailScreen(value = "leo@hey.com", consent = true)
        }
        assertClean()
    }

    /**
     * SHOWUP-153 and SHOWUP-154, every state.
     *
     * The two reserved regions are what these are really measuring: 30 on the code screen and 84
     * on the date screen, both held in EVERY state so the CTA and the secondary rows do not move
     * between them. A failure here is almost always a region that was sized to its content.
     */
    @Test
    fun `profile verify email and date of birth, all states`() {
        sweep("Verify/arrival") { ProfileVerifyEmailScreen() }
        sweep("Verify/typed") { ProfileVerifyEmailScreen(digits = "4821") }
        sweep("Verify/mismatch") {
            ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.Mismatch)
        }
        sweep("Verify/expired") {
            ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.Expired)
        }
        sweep("Verify/locked out") {
            ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.LockedOut)
        }
        // Never truncated, wraps if long -- so the longest plausible address is swept too.
        sweep("Verify/long address") {
            ProfileVerifyEmailScreen(email = "leonardo.buonarroti@a-very-long-domain.example")
        }

        sweep("DoB/empty") { ProfileDobScreen() }
        sweep("DoB/confirm") { ProfileDobScreen(value = "03/22/1998") }
        sweep("DoB/impossible") { ProfileDobScreen(value = "02/30/1990") }
        sweep("DoB/under 18") { ProfileDobScreen(value = "05/19/2015") }
        sweep("DoB/incomplete after press") {
            ProfileDobScreen(value = "03/22", attempted = true)
        }
        sweep("DoB/age hidden") { ProfileDobScreen(value = "03/22/1998", hideAge = true) }
        sweep("DoB/day-first locale") {
            ProfileDobScreen(value = "22/03/1998", order = DateOrder.DayFirst)
        }
        assertClean()
    }

    // ── profile creation · the bridge ───────────────────────────────────────

    /**
     * SHOWUP-155, both greetings.
     *
     * One state and no input, so what this sweep is really measuring is the single flexible
     * spacer: the whole screen is a headline, three paragraphs and a full-width CTA, and the
     * spacer between them resolves to about 240 at 390 x 844 and nearly nothing at 320 x 686.
     * If the CTA is ever clipped or the closing line pushed off, it is this screen's spacer that
     * ran out, and 320 x 686 is where it runs out first.
     *
     * The long name is swept because the headline is the only thing here that can reflow: the
     * greeting owns its own line, so a long enough name takes two and everything below moves.
     */
    @Test
    fun `profile embrace bridge, both greetings`() {
        sweep("Embrace/named") { ProfileEmbraceScreen(firstName = "Leo") }
        sweep("Embrace/no name") { ProfileEmbraceScreen() }
        sweep("Embrace/long name") { ProfileEmbraceScreen(firstName = "Maximiliana-Rose") }
        assertClean()
    }

    // ── profile creation · "The real you" ───────────────────────────────────

    /**
     * SHOWUP-156, all seven states.
     *
     * This is the first screen in the flow that scrolls, so what the harness is measuring is
     * different from every sweep above it. On the non-scrolling screens a finding means content
     * ran off the bottom; here the middle region can always scroll, so a finding means something
     * FIXED has overflowed -- the header, the progress bar, the footer, or a slot whose 158 has
     * been compromised.
     *
     * The tight cases: 320 x 686 is where two 158 slots plus the count row plus the sub copy stop
     * fitting above the footer, and the camera-blocked sheet is where the tallest sheet content
     * (a two-line subtitle beside a Settings pill) meets the shortest frame.
     */
    @Test
    fun `profile photos, all seven states`() {
        fun confirmed(n: Int) = List(n) { PickedPhoto(it.toLong(), null, UploadStatus.Confirmed) }

        sweep("Photos/A empty") { ProfilePhotosScreen() }
        sweep("Photos/B partial") { ProfilePhotosScreen(PhotoGridState(photos = confirmed(2))) }
        sweep("Photos/C uploading") {
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
        sweep("Photos/D library blocked") { ProfilePhotosScreen(library = LibraryAccess.Blocked) }
        sweep("Photos/E library can ask") { ProfilePhotosScreen(library = LibraryAccess.CanAsk) }
        sweep("Photos/F source sheet") {
            ProfilePhotosScreen(PhotoGridState(photos = confirmed(2)), sheetOpen = true)
        }
        sweep("Photos/G camera blocked") {
            ProfilePhotosScreen(
                PhotoGridState(photos = confirmed(2)),
                sheetOpen = true,
                camera = CameraAccess.Blocked,
            )
        }
        // The refusal toast draws ABOVE the footer with a reported size of zero. If that ever
        // becomes a real row, the footer grows and this is the sweep that says so.
        sweep("Photos/toast") { ProfilePhotosScreen(previewToast = true) }
        // Six of six with the optional pair revealed: the tallest the scroll region ever gets.
        sweep("Photos/six revealed") {
            ProfilePhotosScreen(PhotoGridState(photos = confirmed(6), optionalRevealed = true))
        }
        assertClean()
    }

    /**
     * SHOWUP-158, all eight states.
     *
     * The two sheets are the point of this sweep. Both dock to the bottom edge and both are
     * measured in what is left after the header -- the topic sheet is capped at 600 and scrolls
     * inside that, the write sheet has a 560 minimum, and 375 x 667 is where those two numbers
     * meet the smallest frame. A write sheet that overflowed would put Save off the bottom on the
     * one screen where the CTA is the only way out.
     *
     * The previews render WITHOUT a keyboard, because the keyboard is not ours to draw. What this
     * harness therefore measures is the sheet's true content height, which is exactly what the
     * reference draws and what the ticket asks to be checked.
     */
    @Test
    fun `profile prompts, all eight states`() {
        val one = listOf(
            SavedPrompt(
                "first_date_usually",
                "Talk about anything real. Not jobs, not pets, not the weather. The thing " +
                    "actually on your mind this week. Bring it. I'll listen.",
            ),
        )
        val three = one + listOf(
            SavedPrompt(
                "hill_to_die_on",
                "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer.",
            ),
            SavedPrompt(
                "cross_town_for",
                "A proper conversation. An old cinema. The 8pm walk after a long day.",
            ),
        )

        sweep("Prompts/A none") { ProfilePromptsScreen() }
        sweep("Prompts/B one") { ProfilePromptsScreen(PromptsState(prompts = one)) }
        sweep("Prompts/C three") { ProfilePromptsScreen(PromptsState(prompts = three)) }
        sweep("Prompts/D topic sheet") {
            ProfilePromptsScreen(PromptsState(prompts = one, sheet = PromptSheet.Topics))
        }
        sweep("Prompts/E write empty") {
            ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Write("first_date_usually")))
        }
        sweep("Prompts/F write mid") {
            ProfilePromptsScreen(
                PromptsState(
                    sheet = PromptSheet.Write("first_date_usually"),
                    drafts = mapOf("first_date_usually" to "Talk about anything real. Not the weather."),
                ),
            )
        }
        sweep("Prompts/G write at cap") {
            ProfilePromptsScreen(
                PromptsState(
                    sheet = PromptSheet.Write("first_date_usually"),
                    drafts = mapOf("first_date_usually" to PROMPT_SAMPLE_AT_CAP),
                ),
            )
        }
        sweep("Prompts/H write nudge") {
            ProfilePromptsScreen(
                PromptsState(sheet = PromptSheet.Write("first_date_usually"), nudge = true),
            )
        }
        sweep("Prompts/toast") { ProfilePromptsScreen(previewToast = true) }
        assertClean()
    }

    /**
     * SHOWUP-161, all ten states.
     *
     * A-F are one screen; G-J are the two capture views, which have no chrome at all and are the
     * only screens in the app whose controls sit against a full-bleed ground. The capture views are
     * where a fit finding would be worst: `Cancel` is the ONLY way out of them, so a Cancel pushed
     * off a 320-wide screen is a user who cannot leave.
     */
    @Test
    fun `profile notifications, the one state`() {
        // ONE STATE, and the tightest non-scrolling content in the flow: a 32 headline, a lead
        // paragraph and five two-line rows with no scroll allowed. Swept at every size and every
        // font scale, because "it fits at 390" is the claim this screen is most likely to fail.
        sweep("Notifications") { ProfileNotificationsScreen() }
        sweep("Notifications @1.3", fontScale = 1.3f) { ProfileNotificationsScreen() }
        sweep("Notifications @2.0", fontScale = 2.0f) { ProfileNotificationsScreen() }
        assertClean()
    }

    @Test
    fun `profile media, all ten states`() {
        val video = MediaArtefact(
            kind = MediaKind.Video, promptId = "relaxed_and_happy", durationMs = 9_400,
            localPath = null, remoteId = "v1", status = MediaUploadStatus.Confirmed,
        )
        val voice = MediaArtefact(
            kind = MediaKind.Voice, promptId = "relaxing_sound", durationMs = 14_100,
            localPath = null, remoteId = "a1", status = MediaUploadStatus.Confirmed,
        )

        sweep("Media/A empty") { ProfileMediaScreen() }
        sweep("Media/B video only") { ProfileMediaScreen(MediaState(video = video)) }
        sweep("Media/C voice only") { ProfileMediaScreen(MediaState(voice = voice)) }
        sweep("Media/D both") { ProfileMediaScreen(MediaState(video = video, voice = voice)) }
        sweep("Media/E prompts video") {
            ProfileMediaScreen(
                MediaState(
                    sheet = MediaSheet(
                        MediaKind.Video, MediaEntryPoint.SeeThePrompts, openedAtMs = 0L,
                    ),
                ),
            )
        }
        sweep("Media/F prompts voice picked") {
            ProfileMediaScreen(
                MediaState(
                    sheet = MediaSheet(
                        MediaKind.Voice, MediaEntryPoint.SeeThePrompts,
                        selectedId = "relaxing_sound", openedAtMs = 0L,
                    ),
                ),
            )
        }
        sweep("Media/G video recording") {
            MediaCaptureScreen(
                MediaTake(MediaKind.Video, "relaxed_and_happy", elapsedMs = 6_000),
            )
        }
        sweep("Media/H video review") {
            MediaCaptureScreen(
                MediaTake(
                    MediaKind.Video, "relaxed_and_happy",
                    phase = RecordingPhase.Review, elapsedMs = 9_000,
                ),
            )
        }
        sweep("Media/I voice recording") {
            MediaCaptureScreen(MediaTake(MediaKind.Voice, "relaxing_sound", elapsedMs = 4_000))
        }
        sweep("Media/J voice review") {
            MediaCaptureScreen(
                MediaTake(
                    MediaKind.Voice, "relaxing_sound",
                    phase = RecordingPhase.Review, elapsedMs = 14_000,
                ),
            )
        }

        // The permission row has no artboard, so it has no state letter -- but it is real, it is
        // the longest copy on the screen, and it is the one thing that pushes a card taller.
        sweep("Media/blocked microphone") {
            ProfileMediaScreen(
                MediaState(access = MediaAccess(microphone = MediaPermission.Blocked)),
                platformLabel = { "Microphone" },
            )
        }
        sweep("Media/upload failed") {
            ProfileMediaScreen(
                MediaState(video = video.copy(status = MediaUploadStatus.Failed)),
            )
        }
        assertClean()
    }

    // ── "The real you" at the largest system font, and with the keyboard up ─

    /**
     * SHOWUP-156 and SHOWUP-158 at 1.3x and 2.0x type.
     *
     * 1.3 is Android's largest non-accessibility font step; 2.0 is its largest accessibility one,
     * and iOS's AX sizes go further still. `CLAUDE.md` has required "usable at the largest system
     * font" from the start and nothing measured it, which is a gap this group makes expensive:
     * these are the first screens in the flow with FIXED heights rather than minimums -- a 158
     * slot, a 44 pill, a 32 retry control -- and a fixed height is exactly what large type
     * overflows.
     */
    @Test
    fun `the real you, at the largest system font`() {
        fun confirmed(n: Int) = List(n) { PickedPhoto(it.toLong(), null, UploadStatus.Confirmed) }
        val onePrompt = listOf(
            SavedPrompt(
                "first_date_usually",
                "Talk about anything real. Not jobs, not pets, not the weather. The thing " +
                    "actually on your mind this week. Bring it. I'll listen.",
            ),
        )

        for (scale in listOf(1.3f, 2.0f)) {
            sweep("Photos/empty @$scale", fontScale = scale) { ProfilePhotosScreen() }
            sweep("Photos/partial @$scale", fontScale = scale) {
                ProfilePhotosScreen(PhotoGridState(photos = confirmed(2)))
            }
            sweep("Photos/failed @$scale", fontScale = scale) {
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
            sweep("Photos/blocked @$scale", fontScale = scale) {
                ProfilePhotosScreen(library = LibraryAccess.Blocked)
            }
            sweep("Photos/sheet @$scale", fontScale = scale) {
                ProfilePhotosScreen(PhotoGridState(photos = confirmed(2)), sheetOpen = true)
            }
            sweep("Photos/camera blocked @$scale", fontScale = scale) {
                ProfilePhotosScreen(
                    PhotoGridState(photos = confirmed(2)),
                    sheetOpen = true,
                    camera = CameraAccess.Blocked,
                )
            }
            sweep("Prompts/none @$scale", fontScale = scale) { ProfilePromptsScreen() }
            sweep("Prompts/one @$scale", fontScale = scale) {
                ProfilePromptsScreen(PromptsState(prompts = onePrompt))
            }
            sweep("Prompts/topics @$scale", fontScale = scale) {
                ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Topics))
            }
            sweep("Prompts/write @$scale", fontScale = scale) {
                ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Write("first_date_usually")))
            }

            // SHOWUP-161. This screen has MORE fixed heights than either of the two above -- a 36
            // chip, a 30 permission pill, a 32 control pip, a 52 review button, an 84 shutter --
            // and a fixed height is exactly what large type overflows. The capture views are the
            // sharpest case: `Cancel` is the only way out of them, so a Cancel pushed off the edge
            // at 2.0x is a user who cannot leave the screen.
            val mediaVideoFit = MediaArtefact(
                kind = MediaKind.Video, promptId = "relaxed_and_happy", durationMs = 9_400,
                localPath = null, remoteId = "v1", status = MediaUploadStatus.Confirmed,
            )
            val mediaVoiceFit = MediaArtefact(
                kind = MediaKind.Voice, promptId = "relaxing_sound", durationMs = 14_100,
                localPath = null, remoteId = "a1", status = MediaUploadStatus.Confirmed,
            )
            sweep("Media/empty @$scale", fontScale = scale) { ProfileMediaScreen() }
            sweep("Media/both @$scale", fontScale = scale) {
                ProfileMediaScreen(MediaState(video = mediaVideoFit, voice = mediaVoiceFit))
            }
            sweep("Media/prompts @$scale", fontScale = scale) {
                ProfileMediaScreen(
                    MediaState(
                        sheet = MediaSheet(
                            MediaKind.Video, MediaEntryPoint.SeeThePrompts, openedAtMs = 0L,
                        ),
                    ),
                )
            }
            sweep("Media/blocked @$scale", fontScale = scale) {
                ProfileMediaScreen(
                    MediaState(access = MediaAccess(microphone = MediaPermission.Blocked)),
                    platformLabel = { "Microphone" },
                )
            }
            sweep("Media/video recording @$scale", fontScale = scale) {
                MediaCaptureScreen(
                    MediaTake(MediaKind.Video, "relaxed_and_happy", elapsedMs = 6_000),
                )
            }
            sweep("Media/video review @$scale", fontScale = scale) {
                MediaCaptureScreen(
                    MediaTake(
                        MediaKind.Video, "relaxed_and_happy",
                        phase = RecordingPhase.Review, elapsedMs = 9_000,
                    ),
                )
            }
            sweep("Media/voice recording @$scale", fontScale = scale) {
                MediaCaptureScreen(MediaTake(MediaKind.Voice, "relaxing_sound", elapsedMs = 4_000))
            }
            sweep("Media/voice review @$scale", fontScale = scale) {
                MediaCaptureScreen(
                    MediaTake(
                        MediaKind.Voice, "relaxing_sound",
                        phase = RecordingPhase.Review, elapsedMs = 14_000,
                    ),
                )
            }
        }
        assertClean()
    }

    /**
     * The write sheet with the keyboard up, which is the only way it is ever seen.
     *
     * SHOWUP-158 asks for exactly this and leaves it to a person: "verify that the example, the
     * field, the status row and Save are all above the keyboard at 375 x 667". A person checks one
     * phone; this checks seventeen.
     *
     * 300dp is a realistic Android IME on a phone -- Gboard with a suggestion strip runs 260-320
     * depending on the device and the language -- so it is the tight end of plausible rather than
     * the average. The iOS keyboard is shorter, which makes this the harder case of the two.
     */
    @Test
    fun `the write sheet clears the keyboard`() {
        sweep("Prompts/write + keyboard", keyboardDp = 300) {
            ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Write("first_date_usually")))
        }
        sweep("Prompts/write + keyboard, at cap", keyboardDp = 300) {
            ProfilePromptsScreen(
                PromptsState(
                    sheet = PromptSheet.Write("first_date_usually"),
                    drafts = mapOf("first_date_usually" to PROMPT_SAMPLE_AT_CAP),
                ),
            )
        }
        sweep("Prompts/topics + keyboard", keyboardDp = 300) {
            ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Topics))
        }
        assertClean()
    }

    // ── the tutorial ────────────────────────────────────────────────────────

    @Test
    fun `all six tutorial cards`() {
        sweep("Tutorial/1 welcome") { WelcomeScreen() }
        sweep("Tutorial/2 meet in real life") { MeetInRealLifeScreen() }
        sweep("Tutorial/3 availability") { MatchOnAvailabilityScreen() }
        sweep("Tutorial/4 binding date") { MatchMeansMeetScreen() }
        sweep("Tutorial/5 thirty minutes") { ThirtyMinutesScreen() }
        sweep("Tutorial/6 show up every time") { ShowUpEveryTimeScreen() }
        assertClean()
    }
}
