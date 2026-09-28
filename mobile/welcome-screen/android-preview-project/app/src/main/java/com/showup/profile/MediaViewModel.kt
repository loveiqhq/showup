/*
 * MediaViewModel.kt
 * ShowUp · every rule on the media step, in one testable place (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY SO MUCH IS HERE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The two things this screen actually does — point a camera at somebody and open a microphone —
 * cannot run in a unit test. Everything else can, and this file is where "everything else" was
 * deliberately put: the 10 and 15 second caps, the two-second interruption rule, the attempt
 * counter, the retake loop, the difference between stopping and keeping, and all thirteen tracking
 * events.
 *
 * The recorders sit behind [MediaCaptureFactory] and the permissions behind [MediaAccessReader],
 * so `MediaRulesTest` drives this whole class with no device, no camera and no microphone.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE ONE DISTINCTION THE REST OF THE FILE HANGS OFF
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * STOPPING PRODUCES A TAKE. ACCEPTING PRODUCES AN ARTEFACT.
 *
 * A [MediaTake] is a file and a length. A [MediaArtefact] is something on the profile. Stop, and
 * the cap, and an interruption all produce the first; only `Use this clip` produces the second.
 * Collapsing them is how a build ends up firing `video_prompt_recorded` for every abandoned take,
 * which would make the per-prompt completion rate — the number the whole server-side ranking is
 * built on — measure nothing.
 */
package com.showup.profile

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.BuildConfig
import com.showup.analytics.AnalyticsTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

open class MediaViewModel(
    private val repo: MediaRepository,
    private val access: MediaAccessReader,
    private val capture: MediaCaptureFactory,
    /**
     * Playback, behind the same kind of seam as [capture].
     *
     * Defaulted to the fake so every existing preview and test keeps working: this arrived after
     * the screens did, and a required parameter would have meant touching thirty call sites to say
     * "still no player here".
     */
    private val player: MediaPlayerFactory = FakeMediaPlayer(),
    private val analytics: AnalyticsTracker? = null,
    /**
     * The clock, injected.
     *
     * `time_on_sheet_s` and `time_on_step_s` cannot be asserted without one — a test reading the
     * real clock could only assert "about zero", which is the same as asserting nothing.
     */
    private val now: () -> Long = System::currentTimeMillis,
    /** How often the recording clock advances. Injected so a test can drive it instantly. */
    private val tickMs: Long = 50L,
    /**
     * MONOTONIC ELAPSED TIME, and a different thing from [now].
     *
     * [now] is a wall clock answering "when did this happen" for `time_on_step_s`. This one
     * answers "how long has this take been running", which is a duration, and durations are not
     * measured by counting how many times you slept.
     *
     * That is what this used to do: `delay(tickMs)` in a loop, adding `tickMs` per pass. `delay`
     * guarantees AT LEAST that long, never exactly -- and each pass also updated state, which
     * recomposed the capture screen and redrew a live waveform. On a real device the loop ran
     * around 70ms per 50ms tick, so a "ten second" video took about fourteen seconds of the
     * user's life and the recorder wrote fourteen seconds of footage while the bar said ten.
     * Reported from a device as "these seconds last much longer than real life seconds", which is
     * exactly what it was.
     *
     * `elapsedRealtime` rather than `currentTimeMillis`: it cannot jump backwards when the
     * network corrects the wall clock mid-take, and it keeps counting in deep sleep.
     *
     * THE iOS HALF IS NOT DONE. `MediaModel` still counts, because the same change turned most of
     * `MediaRulesTests` red -- its injected `tickWait` returns immediately, so the ticker is a hot
     * loop and the extra work per pass starved the executor the rest of the suite runs on. E28 has
     * both attempts and what it actually needs. The platforms are supposed to move together and
     * here they do not; that is recorded rather than quietly true.
     */
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state.asStateFlow()

    /**
     * When the step was entered, for `time_on_step_s`. Null until [arrived].
     *
     * NULLABLE RATHER THAN ZERO. A sentinel would mean "not arrived yet" AND "arrived at epoch",
     * and those are different facts: the second is a legal clock reading, so any test injecting a
     * clock that starts at zero could never observe a real duration -- and a build whose clock did
     * read zero would report `time_on_step_s: 0` for every user on the screen. Absence gets its own
     * type instead of a magic value.
     */
    private var arrivedAtMs: Long? = null

    /** The live session, and the clock driving it. */
    private var session: MediaCaptureSession? = null
    private var ticker: Job? = null

    /** The playing session, and the clock polling its playhead. Both null when nothing plays. */
    private var playing: MediaPlayerSession? = null
    private var playTicker: Job? = null

    /**
     * Bumped by every start and every stop, so a start that is still in flight can tell that it
     * has been superseded.
     *
     * WITHOUT THIS, STOPPING DURING A START DOES NOT STOP ANYTHING. Opening a file is suspending --
     * on a real player it is a disk read or a network reach -- so a user who presses play and
     * immediately presses Continue leaves a coroutine in mid-`start`. `stopPlayback` cancels the
     * TICKER, which does not exist yet, sets the state to null, and returns; the start then
     * completes and writes the playing state straight back over it, leaving sound playing on a
     * screen the user has left. Cancelling the job is not enough on its own either, because the
     * cancellation lands at the next suspension point and `start` may already have returned.
     */
    private var playGeneration: Int = 0

    /**
     * Attempts so far, per medium, within this visit.
     *
     * `attempt` starts at 1 and counts takes of the SAME prompt so a retake loop is readable
     * without diffing timestamps. Reset when the prompt changes, because attempt 3 of a prompt the
     * user has just switched to is not a third attempt at anything.
     */
    private val attempts = mutableMapOf<MediaKind, Int>()
    private val attemptPrompt = mutableMapOf<MediaKind, String>()

    // ── arriving ─────────────────────────────────────────────────────────────

    /**
     * Reads the account's recordings and the previewed prompts, then reports the entry state.
     *
     * `media_screen_viewed` FIRES ON EVERY MOUNT — including the return from an accepted take,
     * which is the case that makes it more than a duplicate of `screen_viewed`. It names the two
     * prompts that were on the cards, because the ranking moves and a view that does not record
     * what it showed cannot be attributed afterwards.
     *
     * The step views fire once per medium: one screen carries two §2 steps.
     */
    fun arrived() {
        arrivedAtMs = now()
        refreshAccess()
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.Media, ProfileScreen.Prompts))
        analytics?.report(ProfileAnalytics.mediaStepViewed(MediaKind.Video))
        analytics?.report(ProfileAnalytics.mediaStepViewed(MediaKind.Voice))
        viewModelScope.launch {
            val snapshot = repo.load()
            _state.update { current ->
                // A read that did not answer must not clear a card the user just filled. Null is
                // "do not touch what is on screen", which is not the same fact as "nothing stored".
                if (snapshot == null) current.copy(loaded = true) else current.adopt(snapshot)
            }
            analytics?.report(ProfileAnalytics.mediaScreenViewed(_state.value))
        }
    }

    /**
     * Re-reads both permission statuses.
     *
     * CALLED ON EVERY FOREGROUND. "A user who granted access and comes back to a blocked row will
     * not try twice" — so nothing is cached and the only thing the screen has to do is ask again
     * when it resumes.
     */
    fun refreshAccess() {
        _state.update { it.copy(access = access.read()) }
    }

    /** The platform's own name for a permission row. Never hard-coded — see [MediaAccessReader]. */
    fun platformLabel(capability: MediaCapability): String = access.platformLabel(capability)

    // ── the prompt list ──────────────────────────────────────────────────────

    /**
     * Opens the eleven for one medium.
     *
     * NOTHING IS PRESELECTED FROM AN EMPTY CARD — not even the previewed prompt. The card previews
     * it; the list does not pick it. Opening over a FILLED card preselects the answered prompt, so
     * keeping it is one tap and changing it is two.
     */
    fun openPrompts(kind: MediaKind, entryPoint: MediaEntryPoint) {
        val existing = _state.value.artefact(kind)
        _state.update {
            it.copy(
                sheet = MediaSheet(
                    kind = kind,
                    entryPoint = entryPoint,
                    selectedId = existing?.promptId,
                    openedAtMs = now(),
                ),
                // OPENING THE LIST CLEARS A FAILED TAKE'S NOTICE. The message belongs to a take
                // that is over, and choosing a prompt again is the user moving on from it --
                // waiting for the next take to BEGIN would leave it sitting under the sheet for
                // the whole time the sheet is open, which is a notice about something that is no
                // longer the state of the screen.
                captureFailed = null,
            )
        }
        analytics?.report(
            ProfileAnalytics.mediaPromptListOpened(kind, entryPoint, hasExisting = existing != null),
        )
    }

    /**
     * Ticks a row.
     *
     * ROWS ARE RADIO-SELECT, NOT TAP-TO-LAUNCH: the user can read all eleven and change their mind
     * before anything opens. Nothing is tracked here — `media_prompt_selected` fires on the commit
     * CTA, so the count of rows tried before committing is what `selections_before` carries.
     */
    fun pickPrompt(promptId: String) {
        _state.update { current ->
            val sheet = current.sheet ?: return@update current
            if (sheet.selectedId == promptId) return@update current
            current.copy(
                sheet = sheet.copy(
                    selectedId = promptId,
                    selectionsBefore = sheet.selectionsBefore + 1,
                ),
            )
        }
    }

    /**
     * Closes the list without committing.
     *
     * DISMISSING KEEPS NOTHING. There is no draft to lose: the selection is discarded and the card
     * is unchanged. `had_selection` is what splits "read it and left" from "picked one and lost
     * their nerve", which are two different problems with two different fixes.
     */
    fun dismissPrompts(method: SheetDismissMethod) {
        val sheet = _state.value.sheet ?: return
        _state.update { it.copy(sheet = null) }
        analytics?.report(
            ProfileAnalytics.mediaPromptListDismissed(
                kind = sheet.kind,
                method = method,
                hadSelection = sheet.selectedId != null,
                timeOnSheetSeconds = secondsSince(sheet.openedAtMs),
            ),
        )
    }

    /**
     * The commit CTA. Records the selection and asks for whatever is still missing.
     *
     * PERMISSIONS ARE REQUESTED HERE — on the commit CTA, not on screen entry and not on
     * `See the prompts`. By this point the user has chosen what they are about to say, which is the
     * moment the ask is least likely to read as an ambush.
     *
     * Returns what the caller must request, empty when it can go straight to the viewfinder. The
     * selection event fires either way: the user did choose a prompt, and whether the OS then
     * refused them is a different question the permission row answers.
     */
    fun commitPrompt(): List<MediaCapability> {
        val sheet = _state.value.sheet ?: return emptyList()
        val prompt = MediaPrompts.byId(sheet.selectedId) ?: return emptyList()
        val kind = sheet.kind

        analytics?.report(
            ProfileAnalytics.mediaPromptSelected(
                kind = kind,
                prompt = prompt,
                selectionsBefore = sheet.selectionsBefore,
                // THE FIELD THAT KEEPS THE RANKING HONEST. The previewed prompt is far more visible
                // than the other ten, so the job counts only takes where this is false.
                wasPreviewed = prompt.id == _state.value.preview(kind).id,
            ),
        )

        val missing = _state.value.access.missingFor(kind)
        if (missing.isNotEmpty()) return missing

        beginTake(kind, prompt.id)
        return emptyList()
    }

    /**
     * The OS answered the permission request.
     *
     * Granted for everything the medium needs → straight into the viewfinder, because that is what
     * the user asked for when they pressed the commit CTA. Refused → the sheet stays open with the
     * blocked row, which is where the question was asked.
     */
    fun permissionResult(kind: MediaKind, promptId: String) {
        refreshAccess()
        if (_state.value.access.isReady(kind)) beginTake(kind, promptId)
    }

    // ── recording ────────────────────────────────────────────────────────────

    /**
     * Opens the viewfinder on a prompt and starts the take.
     *
     * The attempt counter resets when the PROMPT changes: attempt 3 of a prompt the user has just
     * switched to is not a third attempt at anything.
     */
    private fun beginTake(kind: MediaKind, promptId: String) {
        if (attemptPrompt[kind] != promptId) {
            attemptPrompt[kind] = promptId
            attempts[kind] = 0
        }
        val attempt = (attempts[kind] ?: 0) + 1
        attempts[kind] = attempt
        // A NEW ATTEMPT CLEARS THE LAST FAILURE. The message belongs to one take, not to the
        // screen -- leaving it up while a fresh viewfinder opens would report a failure that is
        // no longer happening.
        _state.update { it.copy(captureFailed = null) }

        val isRetake = attempt > 1 || _state.value.artefact(kind) != null
        _state.update {
            it.copy(
                sheet = null,
                take = MediaTake(kind = kind, promptId = promptId, attempt = attempt),
            )
        }
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.MediaRecord, ProfileScreen.Media))
        MediaPrompts.byId(promptId)?.let { prompt ->
            analytics?.report(ProfileAnalytics.mediaRecordingStarted(kind, prompt, attempt, isRetake))
        }
        startCapture(kind)
    }

    private fun startCapture(kind: MediaKind) {
        val created = capture.create(kind)
        session = created
        viewModelScope.launch {
            val ok = created.start { reason -> onCaptureEnded(reason) }
            if (!ok) {
                // STOP MAY HAVE ARRIVED WHILE `start` WAS STILL WAITING for the recorder to roll.
                // The session exists from the moment the take does, so `stopTake` can reach it
                // first; when it does, `finish` owns the take and has already chosen between a
                // review screen and a failure. Reporting a start failure on top of that would
                // replace a perfectly good review with an error message.
                if (session !== created ||
                    _state.value.take?.phase != RecordingPhase.Recording
                ) {
                    return@launch
                }
                // The recorder would not start at all. Nothing was captured, so there is nothing to
                // review -- return to the card rather than showing an empty review screen, AND SAY
                // SO. A take that vanishes without a word is indistinguishable from one that was
                // never attempted.
                session = null
                _state.update {
                    it.copy(
                        take = null,
                        captureFailed = CaptureFailed(kind, CaptureFailure2.NeverStarted),
                    )
                }
                return@launch
            }
            runClock(kind)
        }
    }

    /**
     * The clock that drives the progress bar AND the cap.
     *
     * REACHING THE CAP STOPS THE TAKE AND SHOWS THE REVIEW SCREEN, exactly as Stop does. Never an
     * alert, never a truncated save: the bar fills to the limit and the take simply ends, which is
     * what "the cap is hard" means from the user's side.
     */
    private fun runClock(kind: MediaKind) {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            val max = MediaLimits.maxMs(kind)
            val startedAt = elapsedRealtimeMs()
            // BOUNDED, and not as a formality. Reading the clock means the loop's exit depends on
            // the clock MOVING, and a frozen one -- an injected stub, a platform quirk -- would
            // spin here forever. This project has already lost four and a half hours of CI to an
            // unbounded ticker; a take that ends early because the clock stopped is a bad take,
            // and one that never ends is a hung app.
            //
            // Ten times the passes the cap should need, so it cannot fire on a device merely
            // being slow -- which is the very thing the clock is here to tolerate.
            val maxPasses = (max / tickMs).toInt() * 10 + 100
            var passes = 0
            while (passes++ < maxPasses) {
                // SLEEP TO THE NEXT BOUNDARY, NOT FOR A FIXED SPAN.
                //
                // `delay(tickMs)` sleeps *at least* that long and the overshoot ACCUMULATES: on a
                // phone busy encoding video, fifty milliseconds asked for is seventy or ninety
                // delivered, so the redraws drift further behind real time with every pass. The
                // displayed number stayed CORRECT throughout -- it is read from the clock below,
                // not counted -- but it arrived late and then caught up, which is seen as a
                // counter that runs smoothly and then jumps a whole second. That was reported
                // from a device as "it accelerates, it skips a second every once in a while", and
                // the reporter was describing the cadence rather than the value.
                //
                // Anchoring each deadline to `startedAt` means a late pass is followed by a short
                // sleep rather than another full one, so the drift cannot compound.
                val deadline = startedAt + passes * tickMs
                val sleep = deadline - elapsedRealtimeMs()
                if (sleep > 0) delay(sleep) else yield()
                val take = _state.value.take ?: return@launch
                if (take.phase != RecordingPhase.Recording) return@launch
                // MEASURED, NOT COUNTED. The tick decides how often the bar is redrawn and
                // nothing else; how far it has got is read from the clock, so a slow frame
                // costs smoothness rather than making the recording longer.
                val elapsed = (elapsedRealtimeMs() - startedAt).toInt().coerceIn(0, max)
                _state.update { it.copy(take = it.take?.copy(elapsedMs = elapsed)) }
                if (elapsed >= max) break
            }
            stopTake(MediaStopReason.MaxLength)
        }
    }

    /** The shutter. */
    fun stopPressed() = stopTake(MediaStopReason.UserStop)

    private fun stopTake(reason: MediaStopReason) {
        val take = _state.value.take ?: return
        if (take.phase != RecordingPhase.Recording) return
        ticker?.cancel()
        val current = session ?: return
        viewModelScope.launch {
            val captured = current.finish(take.elapsedMs)
            if (captured == null) {
                // The recorder RAN and wrote nothing usable -- a take too short for the encoder, a
                // failure on close, or a camera session that was never really recording. A
                // different problem from "would not start", and reported as one.
                session = null
                _state.update {
                    it.copy(
                        take = null,
                        captureFailed = CaptureFailed(
                            take.kind,
                            CaptureFailure2.NothingRecorded,
                            // DEBUG ONLY. The platform's explanation is a diagnostic, not copy,
                            // and a release build must not show a user an error code.
                            detail = if (BuildConfig.DEBUG) current.failureDetail() else null,
                        ),
                    )
                }
                return@launch
            }
            showReview(take, captured, reason)
        }
    }

    /**
     * An interruption ended the take.
     *
     * A call, an alarm or another app taking the microphone. AT OR ABOVE [MediaLimits
     * .INTERRUPTION_KEEP_MS] the review screen is shown; below it the take is discarded and we
     * return to the card. NEVER A SILENT RESUME — the user was interrupted and did not choose to
     * stop, so pretending the recording continued would produce a take with a hole in it.
     *
     * The stop reason reported is `user_stop`, and that is the closed §8 set forcing a choice
     * between two values neither of which is "interrupted". `max_length` would be a lie that
     * corrupts the one measurement the field exists for — whether the caps are too short — so the
     * other value carries it. Raised as a registry gap rather than resolved silently.
     */
    private fun onCaptureEnded(reason: CaptureFailure) {
        if (reason != CaptureFailure.Interrupted) return
        val take = _state.value.take ?: return
        if (take.phase != RecordingPhase.Recording) return
        ticker?.cancel()
        val current = session ?: return
        viewModelScope.launch {
            if (take.elapsedMs < MediaLimits.INTERRUPTION_KEEP_MS) {
                current.discard()
                session = null
                _state.update { it.copy(take = null) }
                return@launch
            }
            val captured = current.finish(take.elapsedMs)
            if (captured == null) {
                session = null
                _state.update { it.copy(take = null) }
                return@launch
            }
            showReview(take, captured, MediaStopReason.UserStop)
        }
    }

    private fun showReview(take: MediaTake, captured: CaptureTake, reason: MediaStopReason) {
        _state.update {
            it.copy(
                take = take.copy(
                    phase = RecordingPhase.Review,
                    elapsedMs = captured.durationMs,
                    path = captured.path,
                    stopReason = reason,
                ),
            )
        }
        analytics?.report(
            ProfileAnalytics.screenViewed(ProfileScreen.MediaReview, ProfileScreen.MediaRecord),
        )
        MediaPrompts.byId(take.promptId)?.let { prompt ->
            analytics?.report(
                ProfileAnalytics.mediaReviewShown(
                    kind = take.kind,
                    prompt = prompt,
                    durationMs = captured.durationMs,
                    attempt = take.attempt,
                    stopReason = reason,
                ),
            )
        }
    }

    // ── review ───────────────────────────────────────────────────────────────

    /**
     * Playback on review is ON DEMAND and repeatable, and the CTA row does not move between plays.
     *
     * `play_count` is a running count rather than a boolean because "watched it once and kept it"
     * and "watched it four times and kept it" are different levels of confidence in the same
     * outcome.
     */
    /**
     * The play control on the review screen -- the 88px glass button on video, the 64px sunset pip
     * on voice.
     *
     * EVERY PRESS IS A PLAY, and a press during playback starts it again from the beginning.
     *
     * The first version of this made the second press a stop, which is a reasonable-sounding idea
     * and is not what the design draws or what the ticket describes. There is one glyph on that
     * button and it is a play triangle; the ticket says "multiple plays are expected and the CTA
     * never moves"; and `play_count` is specified as a count of plays that travels with the
     * recorded event. A toggle makes every other press count nothing, which quietly halves the one
     * number the caps will be judged on.
     */
    fun playPressed() {
        val take = _state.value.take ?: return
        if (take.phase != RecordingPhase.Review) return
        val path = take.path ?: return
        startPlayback(
            kind = take.kind,
            source = PlaybackSource.Review,
            path = path,
            durationMs = take.elapsedMs,
        ) {
            val count = take.playCount + 1
            _state.update { it.copy(take = it.take?.copy(playCount = count)) }
            analytics?.report(ProfileAnalytics.mediaPreviewPlayed(take.kind, take.attempt, count))
        }
    }

    /**
     * The play control on a FILLED CARD -- the 56px button over the video thumbnail, the 44px pip
     * beside the voice waveform.
     *
     * This had no implementation at all: the card's button reached [playPressed], which returned
     * early because there is no take on that screen, so the one control the design draws on a
     * saved card did nothing. The `0:08 / 0:14` readout beside it was a hardcoded zero.
     *
     * THE LOCAL FILE FIRST, THE UPLOADED COPY SECOND. Right after a take both exist and the local
     * one is instant; once the local copy has been cleaned up the URL is all there is. When there
     * is neither, nothing happens and no event fires -- see [MediaPlayerSession.start].
     *
     * As on review, every press is a play: pressing again restarts it. See [playPressed].
     */
    fun cardPlayPressed(kind: MediaKind) {
        val artefact = _state.value.artefact(kind) ?: return
        val path = artefact.localPath ?: artefact.url ?: return
        // NO TRACKING EVENT, and that is deliberate.
        //
        // `media_preview_played` is specified as "one play ON REVIEW, with a running count", and
        // `media_review_shown` is "the denominator for the whole review screen: of the takes that
        // reached it, how many were played". Firing it from here would count plays of a SAVED card
        // against a denominator of takes that reached review, which corrupts the exact ratio the
        // event exists to measure -- the same class of error as firing it for a play that never
        // happened, which is what this whole change is about.
        //
        // The registry has no event for playing back a finished artefact. That is a gap to raise,
        // not one to fill by reusing the nearest row: see E19 in audit/CONFLICTS-2026-08-27.md.
        startPlayback(kind, PlaybackSource.Card, path, artefact.durationMs) {}
    }

    /**
     * Stops whatever is playing, and puts the playhead back to the start.
     *
     * NOT A PAUSE. Nothing in this flow draws a resume affordance, and a 10-second clip stopped
     * three seconds in has nothing worth returning to; leaving a stranded playhead on the card
     * would also make the `0:08` half of the readout a number about a play that is over.
     */
    fun stopPlayback() {
        playGeneration++
        playTicker?.cancel()
        playTicker = null
        val session = playing
        playing = null
        _state.update { it.copy(playback = null) }
        viewModelScope.launch { session?.stop() }
    }

    /**
     * Shared by both controls: start, then poll.
     *
     * [onStarted] runs only if something actually played, which is what keeps the tracking honest.
     * `media_preview_played` used to fire on the tap itself, so a tap that played nothing -- every
     * tap, since there was no player -- still reported a preview into the dataset the ticket says
     * will decide whether 10 and 15 seconds are the right caps.
     */
    private fun startPlayback(
        kind: MediaKind,
        source: PlaybackSource,
        path: String,
        durationMs: Int,
        onStarted: () -> Unit,
    ) {
        // One thing plays at a time. Starting a second stops the first, which is also what makes
        // the single shared ExoPlayer instance safe.
        playTicker?.cancel()
        val previous = playing
        playing = null
        val generation = ++playGeneration
        viewModelScope.launch {
            previous?.stop()
            val session = player.create(kind)
            val started = session.start(path)
            // Superseded while the file was opening -- by another play, or by the user leaving.
            if (generation != playGeneration) {
                session.stop()
                return@launch
            }
            if (!started) {
                _state.update { it.copy(playback = null) }
                return@launch
            }
            playing = session
            _state.update {
                it.copy(
                    playback = MediaPlayback(
                        kind = kind,
                        source = source,
                        positionMs = 0,
                        durationMs = durationMs,
                    ),
                )
            }
            onStarted()
            runPlayClock(session, durationMs)
        }
    }

    /**
     * Polls the playhead, and notices the end.
     *
     * POLLED RATHER THAN PUSHED, for the same reason the recording bar is: one clock, running at
     * one rate, whose behaviour is identical in a test, in a preview and on a device. A player
     * callback would put the readout on the device's frame timing and leave the fake with nothing
     * to drive it.
     *
     * The guard on `playing` is what stops a superseded clock writing over a newer one's position.
     */
    private fun runPlayClock(session: MediaPlayerSession, durationMs: Int) {
        playTicker = viewModelScope.launch {
            // BOUNDED, like the recording clock next door, and for a reason this project has
            // already paid for once. `while (true) { delay }` never lets the dispatcher go idle:
            // in production it is a poll that outlives the clip it was following, and in a test it
            // is `advanceUntilIdle` advancing virtual time forever. The screenshot harness lost
            // four and a half hours of CI to exactly this shape -- and passed, which is the worst
            // way to fail. A clip of known length gets a clock of known length.
            //
            // The grace is because a decoder can run a little past its nominal duration; the end
            // is normally noticed by `hasFinished` well before the bound is reached, and the bound
            // is the backstop for a player that never reports one.
            // ANCHORED TO A START, NOT ACCUMULATED -- the same correction the recording clock
            // needed, and the reason playback was reported as "starts slow then speeds up".
            //
            // `delay(tickMs)` sleeps AT LEAST its argument, so on an emulator, or any device busy
            // decoding, each pass overshoots and the overshoot COMPOUNDS. The player itself never
            // changed rate -- ExoPlayer runs at 1.0 and `positionMs()` is read from it -- but the
            // POLL fell further and further behind, so the bar and the readout updated late and
            // then jumped to catch up. What a person sees is a clip that drags and then races,
            // which is exactly how it was described, and none of it is playback speed.
            //
            // `waited` was also the bound, so a late poll made the clock run PAST the clip: the
            // loop thought less time had passed than really had. Both faults come from counting
            // ticks, and both go away by reading the clock.
            val startedAt = elapsedRealtimeMs()
            val limit = durationMs + PLAY_CLOCK_GRACE_MS
            // BOUNDED BY BOTH, and the pass count is not belt-and-braces.
            //
            // Reading the clock for the exit condition means the loop ends when TIME passes -- and
            // a clock that does not move never ends it. That is not hypothetical: a test can hold
            // one still, and so can a stub. The first version of this anchoring hung the suite
            // for exactly that reason, which is the same trap the recording clock next door was
            // already bounded against.
            //
            // Ten times the passes the clip should need, so a slow device cannot trip it.
            val maxPasses = (limit / tickMs).toInt() * 10 + 100
            var passes = 0
            while (elapsedRealtimeMs() - startedAt < limit && passes < maxPasses) {
                val deadline = startedAt + (++passes) * tickMs
                val sleep = deadline - elapsedRealtimeMs()
                if (sleep > 0) delay(sleep) else yield()
                if (playing !== session) return@launch
                if (session.hasFinished()) {
                    stopPlayback()
                    return@launch
                }
                val position = session.positionMs()
                _state.update { state ->
                    val current = state.playback ?: return@update state
                    state.copy(playback = current.copy(positionMs = position))
                }
            }
            stopPlayback()
        }
    }

    private companion object {
        /**
         * How long the playback clock keeps polling past a clip's stated length.
         *
         * A backstop, not a timing rule: the end is normally reported by the player. It exists so
         * a player that never reports one cannot leave a clock running forever.
         */
        const val PLAY_CLOCK_GRACE_MS = 1_000L
    }

    /**
     * `Retake` on the review screen.
     *
     * RETURNS TO THE VIEWFINDER ON THE SAME PROMPT — it does not reopen the prompt list. The user
     * has already decided what to say; making them choose again would be asking a settled question.
     */
    fun retakeFromReview() {
        val take = _state.value.take ?: return
        val prompt = MediaPrompts.byId(take.promptId) ?: return
        analytics?.report(
            ProfileAnalytics.mediaRetaken(
                kind = take.kind,
                from = MediaActedFrom.Review,
                prompt = prompt,
                attempt = take.attempt,
                priorDurationMs = take.elapsedMs,
                state = _state.value,
            ),
        )
        viewModelScope.launch {
            session?.discard()
            session = null
            beginTake(take.kind, take.promptId)
        }
    }

    /**
     * `Use this clip` / `Use this recording` — the take becomes an artefact.
     *
     * THE CARD FILLS OPTIMISTICALLY and the upload runs in the background. A failure surfaces on
     * the card, never as a modal, and the user can leave the screen; an upload still running when
     * Continue is pressed keeps running.
     *
     * `*_prompt_recorded` fires HERE and never on Stop — see the file header.
     */
    fun acceptTake() {
        val take = _state.value.take ?: return
        val prompt = MediaPrompts.byId(take.promptId) ?: return
        val path = take.path ?: return

        analytics?.report(
            ProfileAnalytics.mediaPromptRecorded(
                kind = take.kind,
                prompt = prompt,
                durationMs = take.elapsedMs,
                // attempt counts from 1, so the number of RETAKES is one fewer.
                retakes = take.attempt - 1,
                playsBeforeAccept = take.playCount,
            ),
        )

        val artefact = MediaArtefact(
            kind = take.kind,
            promptId = take.promptId,
            durationMs = take.elapsedMs,
            localPath = path,
            status = MediaUploadStatus.Queued,
        )
        session = null
        _state.update { it.withArtefact(artefact, take.kind).copy(take = null) }
        // The screen is remounted by the return from the viewfinder, so the entry state is reported
        // again -- which is what "on every mount, including the return from an accepted take" asks.
        analytics?.report(ProfileAnalytics.mediaScreenViewed(_state.value))
        upload(artefact, take.kind)
    }

    /** `Cancel` in the viewfinder. Returns to the card with nothing saved. */
    fun cancelTake() {
        ticker?.cancel()
        val current = session
        session = null
        _state.update { it.copy(take = null) }
        viewModelScope.launch { current?.discard() }
    }

    private fun upload(artefact: MediaArtefact, kind: MediaKind) {
        viewModelScope.launch {
            _state.update {
                it.withArtefact(
                    it.artefact(kind)?.copy(status = MediaUploadStatus.InFlight),
                    kind,
                )
            }
            val bytes = readTake(artefact.localPath)
            if (bytes == null) {
                _state.update {
                    it.withArtefact(it.artefact(kind)?.copy(status = MediaUploadStatus.Failed), kind)
                }
                return@launch
            }
            val result = repo.upload(
                kind = kind,
                promptId = artefact.promptId,
                durationMs = artefact.durationMs,
                bytes = bytes,
                mimeType = mimeTypeFor(kind),
                fileName = fileNameFor(kind),
            )
            _state.update { current ->
                // Only touch the slot if it still holds the artefact this upload was for. A retake
                // that finished first must not be overwritten by the older upload's answer.
                val live = current.artefact(kind) ?: return@update current
                if (live.localPath != artefact.localPath) return@update current
                when (result) {
                    is UploadMediaResult.Stored -> current.withArtefact(
                        live.copy(
                            remoteId = result.media.id,
                            url = result.media.url,
                            status = MediaUploadStatus.Confirmed,
                        ),
                        kind,
                    )
                    is UploadMediaResult.Failed -> current.withArtefact(
                        live.copy(status = MediaUploadStatus.Failed),
                        kind,
                    )
                }
            }
        }
    }

    /** Re-sends a take whose upload failed. The bytes are still in the cache. */
    fun retryUpload(kind: MediaKind) {
        val artefact = _state.value.artefact(kind) ?: return
        if (artefact.status != MediaUploadStatus.Failed) return
        upload(artefact, kind)
    }

    // ── the cards ────────────────────────────────────────────────────────────

    /**
     * `Retake` on a FILLED card — reopens the prompt list with the answered prompt selected.
     *
     * The existing artefact stays on the profile until a new take is accepted, so a user who opens
     * the list and changes their mind has lost nothing.
     */
    fun retakeFromCard(kind: MediaKind) {
        val artefact = _state.value.artefact(kind) ?: return
        val prompt = MediaPrompts.byId(artefact.promptId)
        if (prompt != null) {
            analytics?.report(
                ProfileAnalytics.mediaRetaken(
                    kind = kind,
                    from = MediaActedFrom.MediaCard,
                    prompt = prompt,
                    attempt = (attempts[kind] ?: 0) + 1,
                    priorDurationMs = artefact.durationMs,
                    state = _state.value,
                ),
            )
        }
        openPrompts(kind, MediaEntryPoint.Retake)
    }

    /**
     * `Delete` — immediate, with NO confirmation dialog.
     *
     * The card returns to empty with its previewed prompt. DELETING IS NOT RETAKING: there is no
     * replacement take coming, which is why the two events must never be collapsed.
     *
     * The event carries the state BEFORE the removal, so `had_video` and `had_voice` describe what
     * the user was looking at when they decided.
     */
    fun delete(kind: MediaKind) {
        val artefact = _state.value.artefact(kind) ?: return
        analytics?.report(ProfileAnalytics.mediaDeleted(kind, artefact, _state.value))
        _state.update { it.withArtefact(null, kind) }
        // The attempt counter belongs to the prompt that is no longer answered.
        attempts.remove(kind)
        attemptPrompt.remove(kind)
        viewModelScope.launch {
            artefact.localPath?.let { path -> deleteTake(path) }
            artefact.remoteId?.let { id -> repo.remove(id) }
        }
    }

    // ── leaving ──────────────────────────────────────────────────────────────

    /**
     * Continue. Never disabled, never gated, in any state including empty.
     *
     * Per medium: a slot with a recording completed its step, a slot without skipped it. "An empty
     * Continue is a skip that the user did not call one" — so pressing Continue with nothing
     * records exactly what `Skip for now` records.
     *
     * NO VALIDATION EVENT EVER FIRES HERE. The step is optional, so there is no rule to fail.
     */
    fun continuePressed() {
        val state = _state.value
        val seconds = secondsSince(arrivedAtMs)
        for (kind in MediaKind.entries) {
            if (state.artefact(kind) != null) {
                analytics?.report(ProfileAnalytics.mediaStepCompleted(kind, seconds))
            } else {
                analytics?.report(ProfileAnalytics.mediaStepSkipped(kind, state))
            }
        }
    }

    /**
     * `Skip for now`. Same navigation as Continue; they differ only in what they record.
     *
     * An explicit skip is a skip for BOTH media, whatever is on the cards — the user said so.
     * Anything already recorded stays on the profile; the event describes the act, not the state,
     * and `has_video` / `has_voice` carry the state alongside it.
     */
    fun skipPressed() {
        val state = _state.value
        for (kind in MediaKind.entries) {
            analytics?.report(ProfileAnalytics.mediaStepSkipped(kind, state))
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    private fun secondsSince(startedAtMs: Long?): Int =
        if (startedAtMs == null) 0 else ((now() - startedAtMs) / 1000).toInt().coerceAtLeast(0)

    /**
     * Reads a take off disk.
     *
     * Overridable so tests need no filesystem. A ten-second video is a few megabytes, which is
     * small enough to hold once while it uploads and is the same approach the photo grid takes.
     */
    protected open fun readTake(path: String?): ByteArray? =
        path?.let { runCatching { File(it).readBytes() }.getOrNull() }

    protected open fun deleteTake(path: String) {
        runCatching { File(path).delete() }
    }
}

/** Folds a server read into the screen's state without disturbing anything in flight. */
private fun MediaState.adopt(snapshot: MediaSnapshot): MediaState {
    fun stored(kind: MediaKind): MediaArtefact? =
        snapshot.items.firstOrNull { it.kind == kind }?.let { item ->
            MediaArtefact(
                kind = kind,
                promptId = item.promptId,
                durationMs = item.durationMs,
                localPath = null,
                remoteId = item.id,
                url = item.url,
                status = MediaUploadStatus.Confirmed,
            )
        }

    // ANYTHING THIS SESSION IS STILL UPLOADING SURVIVES. A read that lands while a take is in
    // flight would otherwise replace the card the user is watching with the server's older answer
    // -- the same bug the photo grid's `load` had to be fixed for.
    fun keep(kind: MediaKind): MediaArtefact? {
        val live = artefact(kind)
        return if (live != null && live.status != MediaUploadStatus.Confirmed) live else stored(kind)
    }

    return copy(
        video = keep(MediaKind.Video),
        voice = keep(MediaKind.Voice),
        previewVideoId = snapshot.previewVideoId,
        previewVoiceId = snapshot.previewVoiceId,
        previewSource = snapshot.previewSource,
        loaded = true,
    )
}
