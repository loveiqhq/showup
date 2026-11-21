/*
 * DevDateDemo.kt
 * ShowUp · an in-memory date server, so SHOWUP-196's frames open live on a phone with no backend (debug only)
 *
 * The States page draws every frame as a still picture: right pixels, nothing wired. Date 05's frames are a flow --
 * pick a time, suggest it, agree, check in -- and a picture of a flow reads as a broken app. This stands in for the
 * date, chat, change and arrival calls so each frame opens in its state with every tap working: the real host
 * routing, the real view models, only the server swapped, as [DevCheckInStore] does for the Check-In.
 *
 * The values are the kit's (ChangeSamples): Leonie, Cafe Krone, the slots, the five places. The TIMES are the kit's
 * moved to today: the sheet's clock (17:42, or 15:55 while the chat is locked) is now, so 19:00 is 1h18 away and
 * every rule that counts from the clock -- the unlock, the 30-minute cutoff, the expiry -- runs for real.
 *
 * Reached only through `MainActivity`'s debug launch extra (see [DevDateLaunch]); its construction sits behind
 * `BuildConfig.DEBUG`, a constant the compiler folds, so a release build never makes one. The rules are the server's
 * (`change-suggestions.service.ts`), mirrored loosely.
 */
package com.showup.dev

import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.arrival.ArrivalEntry
import com.showup.arrival.ArrivalStore
import com.showup.arrival.CheckInResult
import com.showup.arrival.LocationFix
import com.showup.arrival.LocationFixReader
import com.showup.change.AgreeResult
import com.showup.change.AgreeSurface
import com.showup.change.ChangeKind
import com.showup.change.ChangePlace
import com.showup.change.ChangeSamples
import com.showup.change.ChangeStatus
import com.showup.change.ChangeStore
import com.showup.change.ChangeSuggestion
import com.showup.change.DateChange
import com.showup.change.PlacesList
import com.showup.change.SuggestRequest
import com.showup.change.SuggestResult
import com.showup.change.placeTypeLabel
import com.showup.chat.ChatMessage
import com.showup.chat.ChatReason
import com.showup.chat.ChatStore
import com.showup.chat.EndResult
import com.showup.date.ActiveDate
import com.showup.date.CancelResult
import com.showup.date.CancelStore
import com.showup.date.DateArrival
import com.showup.date.DateEndStore
import com.showup.date.DatePlace
import com.showup.date.DateRead
import com.showup.date.DateSamples
import com.showup.date.DateStore
import com.showup.date.DateUnread
import com.showup.date.EndDateResult
import com.showup.date.EndEntry
import com.showup.date.EndReason
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
import com.showup.date.cancel.CancelSamples
import com.showup.date.end.DateEndSamples
import com.showup.search.SearchProfile
import com.showup.search.SearchSamples
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** Where a frame opens once its date is read: the screen the sheet draws it on. */
enum class DemoLanding { DateScreen, Chat, TimePicker, PlacePicker }

/**
 * What the host does after landing so a SHOWUP-197 or 198 frame shows its sheet at its step. Each is one public call
 * on the real view models (`DateEndViewModel`, `CancelViewModel`), the call the same tap makes: no second code path.
 */
enum class DemoStep { OpenWrap, OpenReport, PickMet, PickNoShow, PickUncomfortable, ConfirmWait, OpenCancel, CancelSent }

/** The steps a frame performs, in order, once its date is read; empty for a frame that is the Date screen itself. */
fun demoSteps(frame: String): List<DemoStep> = when (frame) {
    "197B" -> listOf(DemoStep.OpenWrap)
    "197C" -> listOf(DemoStep.OpenWrap, DemoStep.PickMet)
    // D is the gate (not checked in), E and F the guide (before and after the meet time): the same pick, a different date.
    "197D", "197E", "197F" -> listOf(DemoStep.OpenWrap, DemoStep.PickNoShow)
    "197G", "197J" -> listOf(DemoStep.OpenWrap, DemoStep.PickNoShow, DemoStep.ConfirmWait)
    "197H" -> listOf(DemoStep.OpenReport)
    "197I" -> listOf(DemoStep.OpenReport, DemoStep.PickUncomfortable)
    "198B", "198C", "198E", "198I" -> listOf(DemoStep.OpenCancel)
    "198D" -> listOf(DemoStep.OpenCancel, DemoStep.CancelSent)
    else -> emptyList()
}

/** Every frame id a launch can carry: Date 05's letters, and the two tickets' prefixed ids. */
val DEV_LIVE_FRAMES: Set<String> =
    (ChangeSamples.LETTERS + DateEndSamples.LETTERS.map { "197$it" } + CancelSamples.LETTERS.map { "198$it" }).toSet()

fun demoLanding(frame: String): DemoLanding = when (frame) {
    "A", "K" -> DemoLanding.TimePicker
    "B", "M", "N" -> DemoLanding.PlacePicker
    "C", "F", "H" -> DemoLanding.Chat
    else -> DemoLanding.DateScreen
}

/** The search a frame's picker opens on: N is drawn as a search for "sushi" that finds nothing. */
fun demoQuery(frame: String): String? = if (frame == "N") "sushi" else null

/** The launch extra that opens one of Date 05's frames live: the frame's letter, "A" to "N". */
data class DevDateLaunch(val frame: String) {
    companion object {
        const val EXTRA_FRAME = "dev_date_frame"

        fun from(intent: android.content.Intent?): DevDateLaunch? =
            intent?.getStringExtra(EXTRA_FRAME)?.takeIf { it in DEV_LIVE_FRAMES }?.let(::DevDateLaunch)
    }
}

/** Holds one launch's demo for the Activity's whole life, so a rebuild (dark mode, font size) keeps its state. */
class DevDateDemoHolder(val demo: DevDateDemo) : androidx.lifecycle.ViewModel()

class DevDateDemo(
    val frame: String,
    private val clock: () -> Instant = Instant::now,
    /** Milliseconds a write takes: long enough to see the sending state, short enough to walk. */
    private val latencyMs: Long = 600,
) : DateStore, ChatStore, ChangeStore, ArrivalStore, LocationFixReader, DateEndStore, CancelStore {

    private val start = clock()

    /** The sheet's clock for this frame: 15:55 while the chat is locked (I, J), past the 18:30 deadline in H. */
    private val kitNow: Instant = when (frame) {
        "I", "J", "198B" -> DateSamples.A_NOW
        "H" -> KIT_MEET.minusSeconds(29 * 60)
        // Ten minutes past the meet time: F, G and J need the end button open. 198I is 25 minutes before it, past the cutoff.
        "197F", "197G", "197J" -> KIT_MEET.plusSeconds(10 * 60)
        "198I" -> KIT_MEET.minusSeconds(25 * 60)
        else -> DateSamples.B_NOW
    }
    /** The frames drawn with the chat still locked (nothing unlocked it yet, nothing could have been sent). */
    private val chatLocked = frame in setOf("I", "J", "198B")
    private val shift: Duration = Duration.between(kitNow, start)

    /** A kit time, moved to today. */
    private fun kit(t: Instant): Instant = t.plus(shift)

    private var meetAt = kit(KIT_MEET)
    private var place: DatePlace = DateSamples.DATE.place!!
    private var timeWas: Instant? = null
    private var placeWas: String? = null
    /** Leonie's availability ends at the kit's 22:00, so the last slot is 21:30. */
    private val availableUntil = kit(KIT_MEET.plusSeconds(3 * 3600))

    private val suggestions = mutableListOf<ChangeSuggestion>()
    private val clientIds = mutableMapOf<String, String>()
    private val messages = mutableListOf<ChatMessage>()
    /** Read up to two minutes ago: Leonie's earlier message is read, the one E and L show under her request is not. */
    private var readAt: Instant = start.minusSeconds(120)
    /**
     * The server's `coordinate_unlocked_at`: once the chat has opened it stays open, even when a later time is agreed.
     * Every frame but I and J is drawn with the chat open, so it has opened already (G's at the original 19:00).
     */
    private var unlockStamped = !chatLocked
    private var tutorialSeen = true
    /** Date 06's frames except D (the gate) and Date 07's F are drawn after this person has checked in. */
    private var myCheckIn = if (frame.startsWith("197") && frame != "197D" || frame == "198F") MyCheckIn.Committed else MyCheckIn.None
    private var partnerArrivedAt: Instant? = null
    private var partnerArrivalSeen = false
    /** 198H: the match cancelled on this person, so the first read is the outcome. */
    private var ended: EndedOutcome? =
        if (frame == "198H") EndedOutcome(DEMO_DATE_ID, DateSamples.DATE.match.name, endedByYou = false, reason = OutcomeReason.Cancelled) else null
    private var endSeen = false
    private var gone = false
    private var discomfortFlagged = false
    private var nextId = 0

    // The failure frames fail once, so the retry is walked too.
    private var failSuggest = frame == "K"
    private var failAgree = frame == "L"
    private var failPlaces = frame == "M"
    /** J's end and E's cancel fail once, so the retry is walked too. */
    private var failEnd = frame == "197J"
    private var failCancel = frame == "198E"

    private enum class MyCheckIn { None, Stored, Committed }

    init {
        if (!chatLocked) messages += ChangeSamples.SEED.copy(sentAt = kit(ChangeSamples.SEED.sentAt))
        // E and L draw her running-late message, unread, under the request (ChangeSamples.E_DATE). J's chat is still
        // locked, so nothing could have been sent yet.
        if (frame in setOf("E", "L")) {
            messages += ChatMessage("demo-unread", ChatReason.RunningLate, DateSamples.UNREAD_BODY, fromYou = false, sentAt = start.minusSeconds(60), read = null)
        }
        when (frame) {
            "C" -> suggestions += ChangeSamples.MINE_TIME.moved()
            "D" -> suggestions += listOf(ChangeSamples.MINE_TIME.moved(), ChangeSamples.MINE_PLACE.moved())
            "E", "F", "J", "L" -> suggestions += ChangeSamples.THEIRS_TIME.moved()
            "G" -> {
                suggestions += ChangeSamples.MINE_TIME.moved().copy(status = ChangeStatus.Agreed, answeredAt = start.minusSeconds(60))
                timeWas = meetAt
                meetAt = kit(ChangeSamples.SLOTS[1])
            }
            "H" -> suggestions += ChangeSamples.EXPIRED_TIME.moved()
            // The request D's line reports, sent from the sheet: it is open on the server as well.
            "198D" -> suggestions += ChangeSamples.MINE_TIME.moved()
        }
    }

    private fun ChangeSuggestion.moved() = copy(
        time = time?.let(::kit),
        wasTime = kit(wasTime),
        deadline = kit(deadline),
        createdAt = kit(createdAt),
        answeredAt = answeredAt?.let(::kit),
        expiredAt = expiredAt?.let(::kit),
        expiredBindingTime = expiredBindingTime?.let(::kit),
    )

    private fun id(prefix: String) = "demo-$prefix-${nextId++}"

    // ── the rules the server applies on every read ──────────────────────────────────────────

    private val cutoff: Instant get() = meetAt.minusSeconds(30 * 60)
    private val unlockAt: Instant get() = meetAt.minusSeconds(90 * 60)
    private val committed: Boolean get() = myCheckIn == MyCheckIn.Committed

    /** New suggestions close at the cutoff and after either person's check-in. */
    private fun allowed(now: Instant) = now.isBefore(cutoff) && !committed && partnerArrivedAt == null

    /** Every 15 minutes after the binding time, until 30 minutes before Leonie's availability ends. */
    private fun slots(): List<Instant> =
        generateSequence(meetAt.plusSeconds(15 * 60)) { it.plusSeconds(15 * 60) }
            .takeWhile { !it.isAfter(availableUntil.minusSeconds(30 * 60)) }
            .toList()

    /** The expiry job: an open suggestion past its deadline expires, with the binding values then. */
    private fun expireDue(now: Instant) {
        suggestions.replaceAll {
            if (it.status == ChangeStatus.Pending && !now.isBefore(it.deadline)) expired(it, it.deadline) else it
        }
    }

    private fun expired(s: ChangeSuggestion, at: Instant) = s.copy(
        status = ChangeStatus.Expired, expiredAt = at, expiredBindingTime = meetAt, expiredBindingPlaceName = place.name,
    )

    private fun pending() = suggestions.filter { it.status == ChangeStatus.Pending }

    private fun dateNow(now: Instant): ActiveDate {
        val pending = pending()
        val theirs = messages.filter { !it.fromYou && it.sentAt.isAfter(readAt) }
        if (!now.isBefore(unlockAt)) unlockStamped = true
        return DateSamples.DATE.copy(
            id = DEMO_DATE_ID,
            meetAt = meetAt,
            meetDay = "today",
            unlockAt = unlockAt,
            coordinateOpen = unlockStamped || committed,
            place = place,
            unread = theirs.lastOrNull()?.let { DateUnread(theirs.size, it.reason, it.body, it.sentAt) },
            checkedIn = committed,
            chatTutorialSeen = tutorialSeen,
            endsEnabled = true,
            cancelPenaltyPoints = DEMO_CANCEL_PENALTY,
            arrival = partnerArrivedAt?.takeIf { !partnerArrivalSeen }?.let(::DateArrival),
            change = DateChange(cutoff, allowed(now), slots(), timeWas, placeWas, pending),
            changeRequest = pending.filter { !it.byYou }.minByOrNull { it.deadline },
        )
    }

    private fun agreed(s: ChangeSuggestion, now: Instant): ChangeSuggestion {
        s.time?.let {
            if (timeWas == null) timeWas = meetAt
            meetAt = it
        }
        s.place?.let {
            if (placeWas == null) placeWas = place.name
            place = DatePlace(it.name, it.address)
        }
        val done = s.copy(status = ChangeStatus.Agreed, answeredAt = now)
        suggestions.replaceAll { if (it.id == s.id) done else it }
        return done
    }

    /** A new suggestion replaces the same person's open one of that kind (one open per kind, the newest). */
    private fun add(kind: ChangeKind, byYou: Boolean, time: Instant?, placeOf: ChangePlace?, now: Instant): ChangeSuggestion {
        suggestions.replaceAll {
            if (it.status == ChangeStatus.Pending && it.kind == kind && it.byYou == byYou) it.copy(status = ChangeStatus.Replaced) else it
        }
        val s = ChangeSuggestion(
            id = id("s"), kind = kind, byYou = byYou, time = time, place = placeOf,
            wasTime = meetAt, wasPlaceName = if (kind == ChangeKind.Place) place.name else null,
            deadline = cutoff, createdAt = now,
        )
        suggestions += s
        return s
    }

    private fun <T> locked(block: (Instant) -> T): T = synchronized(this) {
        val now = clock()
        expireDue(now)
        block(now)
    }

    // ── DateStore ───────────────────────────────────────────────────────────────────────────

    override suspend fun active(zone: String): Outcome<DateRead> {
        delay(latencyMs / 3)
        return Outcome.Ok(
            locked { now ->
                when {
                    gone -> DateRead.None
                    ended != null -> if (endSeen) DateRead.None else DateRead.Ended(ended!!)
                    else -> DateRead.Active(dateNow(now))
                }
            },
        )
    }

    override suspend fun partnerProfile(zone: String): Outcome<SearchProfile> {
        delay(latencyMs / 3)
        return Outcome.Ok(SearchSamples.FULL)
    }

    override suspend fun createTestDate(minutes: Int): Result<Boolean> = Result.success(
        locked { now ->
            meetAt = now.plusSeconds(minutes * 60L)
            place = DateSamples.DATE.place!!
            timeWas = null
            placeWas = null
            suggestions.clear()
            messages.clear()
            myCheckIn = MyCheckIn.None
            partnerArrivedAt = null
            partnerArrivalSeen = false
            unlockStamped = false
            ended = null
            endSeen = false
            gone = false
            discomfortFlagged = false
            true
        },
    )

    override suspend fun messageFromMatch(reason: ChatReason?, body: String): Result<Boolean> = Result.success(
        locked { now ->
            messages += ChatMessage(id("m"), reason, body, fromYou = false, sentAt = now, read = null)
            true
        },
    )

    override suspend fun endTestDate(): Result<Boolean> = Result.success(locked { gone = true; true })

    override suspend fun matchEndsInChat(): Result<Boolean> = Result.success(
        locked {
            ended = EndedOutcome(DEMO_DATE_ID, DateSamples.DATE.match.name, endedByYou = false)
            endSeen = false
            true
        },
    )

    override suspend fun partnerCancels(): Result<Boolean> = Result.success(
        locked {
            if (over) return@locked false
            ended = EndedOutcome(DEMO_DATE_ID, DateSamples.DATE.match.name, endedByYou = false, reason = OutcomeReason.Cancelled)
            endSeen = false
            true
        },
    )

    override suspend fun partnerEnds(reason: EndReason): Result<Boolean> = Result.success(
        locked { now ->
            if (over) return@locked false
            // The server has her check in first for the two problem reasons, so her arrival card stands behind it.
            if (reason != EndReason.Met) partnerArrivedAt = partnerArrivedAt ?: now
            ended = EndedOutcome(
                DEMO_DATE_ID, DateSamples.DATE.match.name, endedByYou = false,
                reason = when (reason) {
                    EndReason.Met -> OutcomeReason.Met
                    EndReason.PartnerNoShow -> OutcomeReason.PartnerNoShow
                    EndReason.Uncomfortable -> OutcomeReason.Uncomfortable
                },
            )
            endSeen = false
            true
        },
    )

    override suspend fun markArrivalSeen(dateId: String) {
        locked { partnerArrivalSeen = true }
    }

    override suspend fun partnerChecksIn(): Result<Boolean> = Result.success(
        locked { now ->
            partnerArrivedAt = now
            partnerArrivalSeen = false
            true
        },
    )

    override suspend fun matchSuggests(kind: ChangeKind): Result<Boolean> = Result.success(
        locked { now ->
            if (!allowed(now)) return@locked false
            when (kind) {
                ChangeKind.Time -> {
                    val at = slots().getOrNull(1) ?: slots().firstOrNull() ?: return@locked false
                    add(ChangeKind.Time, byYou = false, time = at, placeOf = null, now = now)
                }
                ChangeKind.Place -> {
                    val o = ChangeSamples.PLACES.first { it.name != place.name }
                    add(ChangeKind.Place, byYou = false, time = null, placeOf = ChangePlace(o.placeId, o.name, o.type, o.address), now = now)
                }
            }
            true
        },
    )

    override suspend fun matchAgrees(): Result<Boolean> = Result.success(
        locked { now ->
            val mine = pending().filter { it.byYou }.minByOrNull { it.deadline } ?: return@locked false
            agreed(mine, now)
            true
        },
    )

    override suspend fun expireSuggestions(): Result<Boolean> = Result.success(
        locked { now ->
            suggestions.replaceAll { if (it.status == ChangeStatus.Pending) expired(it, now) else it }
            true
        },
    )

    // ── ChatStore ───────────────────────────────────────────────────────────────────────────

    override suspend fun thread(dateId: String): Outcome<List<ChatMessage>> {
        delay(latencyMs / 3)
        return Outcome.Ok(locked { messages.sortedBy { it.sentAt } })
    }

    override suspend fun send(dateId: String, reason: ChatReason?, body: String, clientId: String): Outcome<ChatMessage> {
        delay(latencyMs)
        return Outcome.Ok(
            locked { now ->
                messages.firstOrNull { it.clientId == clientId }
                    ?: ChatMessage(id("m"), reason, body.trim(), fromYou = true, sentAt = now, read = false, clientId = clientId)
                        .also { messages += it }
            },
        )
    }

    override suspend fun markRead(dateId: String) {
        locked { now -> readAt = now }
    }

    override suspend fun markTutorialSeen(dateId: String) {
        locked { tutorialSeen = true }
    }

    override suspend fun endInChat(dateId: String): EndResult {
        delay(latencyMs)
        return locked {
            val outcome = EndedOutcome(DEMO_DATE_ID, DateSamples.DATE.match.name, endedByYou = true)
            ended = outcome
            endSeen = false
            EndResult.Ended(outcome)
        }
    }

    override suspend fun markEndSeen(dateId: String) {
        locked { endSeen = true }
    }

    // ── DateEndStore and CancelStore: the server's refusals, then the outcome the next read reports ──

    /** The date is over for both, by [reason]; the next `active` read says how, until it is seen. */
    private fun finish(reason: OutcomeReason): EndedOutcome {
        val outcome = EndedOutcome(DEMO_DATE_ID, DateSamples.DATE.match.name, endedByYou = true, reason = reason)
        ended = outcome
        endSeen = false
        return outcome
    }

    private val over: Boolean get() = gone || ended != null

    override suspend fun end(dateId: String, reason: EndReason, entry: EndEntry): EndDateResult {
        delay(latencyMs)
        return locked { now ->
            when {
                over -> EndDateResult.Refused("date_not_active")
                failEnd -> {
                    failEnd = false
                    EndDateResult.Failed(CallFailure.Network)
                }
                reason == EndReason.PartnerNoShow && now.isBefore(meetAt) -> EndDateResult.Refused("before_meet")
                reason != EndReason.Met && !committed -> EndDateResult.Refused("not_checked_in")
                else -> EndDateResult.Ended(
                    finish(
                        when (reason) {
                            EndReason.Met -> OutcomeReason.Met
                            EndReason.PartnerNoShow -> OutcomeReason.PartnerNoShow
                            EndReason.Uncomfortable -> OutcomeReason.Uncomfortable
                        },
                    ),
                )
            }
        }
    }

    override suspend fun flagDiscomfort(dateId: String): Result<Boolean> = Result.success(
        locked { discomfortFlagged = true; true },
    )

    override suspend fun cancel(dateId: String): CancelResult {
        delay(latencyMs)
        return locked {
            when {
                over -> CancelResult.Refused("date_not_active")
                failCancel -> {
                    failCancel = false
                    CancelResult.Failed(CallFailure.Network)
                }
                committed -> CancelResult.Refused("checked_in")
                else -> CancelResult.Cancelled(finish(OutcomeReason.Cancelled))
            }
        }
    }

    // ── ChangeStore ─────────────────────────────────────────────────────────────────────────

    override suspend fun places(dateId: String, query: String?): Outcome<PlacesList> {
        delay(latencyMs)
        return locked {
            if (failPlaces) {
                failPlaces = false
                return@locked Outcome.Failed(CallFailure.Network)
            }
            val q = query?.trim().orEmpty().lowercase()
            val found = ChangeSamples.PLACES.filter { o ->
                o.name != place.name && (q.isEmpty() || listOf(o.name, placeTypeLabel(o.type), o.address).any { it.lowercase().contains(q) })
            }
            Outcome.Ok(PlacesList(google = true, places = found))
        }
    }

    override suspend fun suggest(dateId: String, request: SuggestRequest): SuggestResult {
        delay(latencyMs)
        return locked { now ->
            clientIds[request.clientId]?.let { sent -> suggestions.firstOrNull { it.id == sent }?.let { return@locked SuggestResult.Sent(it) } }
            if (!allowed(now)) return@locked SuggestResult.Stale
            if (failSuggest) {
                failSuggest = false
                return@locked SuggestResult.Failed(CallFailure.Network)
            }
            val s = when (request.kind) {
                ChangeKind.Time -> {
                    val at = request.time?.takeIf { it in slots() } ?: return@locked SuggestResult.Stale
                    add(ChangeKind.Time, byYou = true, time = at, placeOf = null, now = now)
                }
                ChangeKind.Place -> {
                    val o = ChangeSamples.PLACES.firstOrNull { it.placeId == request.placeId && it.name != place.name }
                        ?: return@locked SuggestResult.Stale
                    add(ChangeKind.Place, byYou = true, time = null, placeOf = ChangePlace(o.placeId, o.name, o.type, o.address), now = now)
                }
            }
            clientIds[request.clientId] = s.id
            SuggestResult.Sent(s)
        }
    }

    override suspend fun agree(dateId: String, suggestionId: String, surface: AgreeSurface): AgreeResult {
        delay(latencyMs)
        return locked { now ->
            val s = suggestions.firstOrNull { it.id == suggestionId }
            if (s?.status == ChangeStatus.Agreed && !s.byYou) return@locked AgreeResult.Agreed(s)
            if (s == null || s.status != ChangeStatus.Pending || s.byYou || committed || partnerArrivedAt != null) {
                return@locked AgreeResult.Stale
            }
            if (failAgree) {
                failAgree = false
                return@locked AgreeResult.Failed(CallFailure.Network)
            }
            AgreeResult.Agreed(agreed(s, now))
        }
    }

    override suspend fun suggestions(dateId: String): Outcome<List<ChangeSuggestion>> {
        delay(latencyMs / 3)
        return Outcome.Ok(locked { suggestions.sortedBy { it.createdAt } })
    }

    // ── ArrivalStore and the fix ────────────────────────────────────────────────────────────

    override suspend fun checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String): CheckInResult {
        delay(latencyMs)
        return locked {
            if (committed) return@locked CheckInResult.AlreadyCheckedIn
            myCheckIn = MyCheckIn.Stored
            CheckInResult.Stored
        }
    }

    override suspend fun undo(dateId: String): Outcome<Unit> {
        delay(latencyMs / 3)
        return Outcome.Ok(locked { if (myCheckIn == MyCheckIn.Stored) myCheckIn = MyCheckIn.None })
    }

    override suspend fun commit(dateId: String): Outcome<Unit> {
        delay(latencyMs / 3)
        return Outcome.Ok(locked { if (myCheckIn == MyCheckIn.Stored) myCheckIn = MyCheckIn.Committed })
    }

    /** Standing at the meeting place: the demo has no phone location to read. */
    override suspend fun fix(): LocationFix {
        delay(latencyMs)
        return LocationFix(CAFE_KRONE_LAT, CAFE_KRONE_LNG, 12.0)
    }

    companion object {
        const val DEMO_DATE_ID = "demo-date"

        /** What this stand-in server says a cancel costs; the apps only ever show the number a server sends. */
        private const val DEMO_CANCEL_PENALTY = 10

        /** The kit's binding meet time, 19:00 Berlin on the sample day. */
        private val KIT_MEET: Instant = DateSamples.DATE.meetAt

        private const val CAFE_KRONE_LAT = 52.5008
        private const val CAFE_KRONE_LNG = 13.4194
    }
}
