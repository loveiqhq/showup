/*
 * ChatViewModel.kt
 * ShowUp · the coordinate chat's asynchronous work (SHOWUP-194)
 *
 * One per date, held by the host for the app session, so the thread, a typed draft, the selected chip
 * and the guide survive leaving the chat and coming back. The screens take [ChatUi] and return pixels.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE RULES THIS FILE ENFORCES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * THE DOCK IS THE PERSON'S. A poll replaces the thread and the date and nothing else: the draft, the
 * selected chip and the guide change only by what the person does. Text typed while a message was
 * sending is kept when it lands.
 *
 * A SEND THAT FAILS STAYS IN THE THREAD (Date 02 J, 9 Nov 2026), unsent, at its foot, and the field is
 * free for the next message. Tapping its line resends it with the same reason and the same id, which
 * the server answers with the first copy if it already has it -- the match receives it once. A poll
 * that finds it sent anyway takes it out of the outbox. `message_sent` fires once, when it is sent.
 *
 * NEW MESSAGES ARRIVE BY POLLING, while the chat is on screen: the date and the thread every
 * [CHAT_POLL_MS], and at once on showing, after a send and on retry. A read that brings the match's
 * messages marks the thread read; so does opening the chat.
 *
 * AN END FROM ANYWHERE SHOWS THE OUTCOME. This person's End date, the other person's (found by a poll),
 * or a second tap answered 409: the sheet closes, the draft goes, the poll stops and [ChatUi.outcome]
 * carries G or H. A date gone some other way sets [ChatUi.left].
 *
 * NO TEXT IN ANY EVENT. Payloads carry lengths, the chip and whether its draft was edited -- never the
 * text, the draft or the guide.
 */
package com.showup.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.date.ActiveDate
import com.showup.date.DateRead
import com.showup.date.DateStore
import com.showup.date.EndedInChat
import com.showup.profile.report
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Everything the chat's screens render, in one value. */
data class ChatUi(
    /** The date, as the last read found it: the header's latest agreed time and place. */
    val date: ActiveDate? = null,
    val now: Instant,
    /**
     * Oldest first: the server's thread, plus anything sent that a read has not returned yet, then the
     * messages whose send failed ([ChatMessage.failed]), at the foot.
     */
    val messages: List<ChatMessage> = emptyList(),
    /** The thread's first read has not landed. */
    val loading: Boolean = true,
    /** Why the first read failed, while there is no thread to show; null once one is. */
    val failed: CallFailure? = null,
    val selected: ChatReason? = null,
    val draft: String = "",
    val guide: Guide = Guide.None,
    val sending: Boolean = false,
    /** The failed messages being resent, by [ChatMessage.clientId]: their line waits. */
    val resending: Set<String> = emptySet(),
    /** The End the date sheet (F) is open. */
    val endSheet: Boolean = false,
    /** End date was tapped and has not answered. */
    val ending: Boolean = false,
    /** Why the last End date did not land; the sheet stays open. */
    val endFailed: CallFailure? = null,
    /** The date ended in the chat: G when [EndedInChat.endedByYou], H otherwise. The host shows it. */
    val outcome: EndedInChat? = null,
    /** The date is gone some other way: the host goes back to the Date screen. */
    val left: Boolean = false,
) {
    val locked: Boolean get() = fieldLocked(selected, guide)
    val canSend: Boolean get() = !sending && com.showup.chat.canSend(draft, locked)
    val endCard: Boolean get() = endCardVisible(messages, selected)

    /** B, the start grid: no message yet and nothing picked or typed. A chip tap moves to the thread. */
    val start: Boolean get() = messages.isEmpty() && selected == null && draft.isEmpty() && guide == Guide.None

    val countdown: String? get() = date?.let { com.showup.chat.countdown(now, it.meetAt) }
}

class ChatViewModel(
    private val store: ChatStore,
    private val dates: DateStore,
    private val analytics: AnalyticsTracker? = null,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val received: ReceivedLedger = InMemoryReceivedLedger(),
) : ViewModel() {

    private val _ui = MutableStateFlow(ChatUi(now = clock()))
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()

    private var poll: Job? = null

    /** The last read of the thread. */
    private var read: List<ChatMessage> = emptyList()

    /** Sent this session and not yet in a read: a poll that left before the send must not drop them. */
    private val unconfirmed = mutableListOf<ChatMessage>()

    /** Failed sends, oldest first, until one is resent or a read finds it sent after all. */
    private val outbox = mutableListOf<Unsent>()

    private var visit: Visit? = null
    private var guideShownAt: Instant? = null

    private class Visit(val start: Instant, val entry: ChatEntry) {
        var sent = 0

        /** `chat_opened` has fired: once per visit, once the chat has loaded. */
        var opened = false
    }

    /** A message whose send failed, with what `message_sent` will need once it is sent. */
    private class Unsent(
        val clientId: String,
        val reason: ChatReason?,
        val body: String,
        val prefillEdited: Boolean?,
        val at: Instant,
    ) {
        fun shown() = ChatMessage("unsent:$clientId", reason, body, fromYou = true, sentAt = at, read = null, clientId = clientId, failed = true)
    }

    /**
     * The chat is shown: a visit begins. [date] is what the opening screen read; the first poll
     * replaces it with the latest. A different date than the one held starts afresh.
     */
    fun open(date: ActiveDate, entry: ChatEntry) {
        if (_ui.value.date?.id.let { it != null && it != date.id }) reset()
        val now = clock()
        _ui.update { it.copy(date = date, now = now, left = false) }
        visit = Visit(now, entry)
        // A thread already on screen from an earlier visit is loaded; otherwise the first read reports it.
        if (!_ui.value.loading && _ui.value.failed == null) reportOpened()
        viewModelScope.launch { store.markRead(date.id) }
        onScreen(true)
    }

    /** The chat is on screen, or not (backgrounded, covered). The poll runs only while it is. */
    fun onScreen(visible: Boolean) {
        if (!visible) {
            poll?.cancel()
            poll = null
            return
        }
        val ui = _ui.value
        val id = ui.date?.id ?: return
        if (poll?.isActive == true || ui.outcome != null || ui.left) return
        poll = viewModelScope.launch {
            while (true) {
                // The countdown moves with the clock whether or not the reads land: offline near the
                // meet time it kept `in 0h 3m` after it (the SHOWUP-194 review).
                _ui.update { it.copy(now = clock()) }
                refresh(id)
                delay(CHAT_POLL_MS)
            }
        }
    }

    /** `Try again` on a failed first read. The failure stays on screen until the read answers. */
    fun retry() {
        val id = _ui.value.date?.id ?: return
        if (_ui.value.loading) return
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch { refresh(id) }
    }

    /** The person leaves the chat (back, or a hand-off that replaces it). The visit ends; the dock stays. */
    fun close() {
        onScreen(false)
        val visit = visit ?: return
        this.visit = null
        val id = _ui.value.date?.id ?: return
        analytics?.report(ChatAnalytics.chatClosed(id, visit.sent, seconds(visit.start, clock())))
    }

    /**
     * A chip tap, on the start grid or the dock: selects it and REPLACES the field with its draft.
     * "I can't make it today" has none: it empties the field and locks it behind the guide.
     * [gated]: only the event fires -- the arrival chip before check-in opens the gate instead.
     */
    fun pick(reason: ChatReason, surface: ChipSurface, gated: Boolean = false) {
        val date = _ui.value.date ?: return
        val now = clock()
        analytics?.report(ChatAnalytics.actionChipUsed(date, reason, surface, now))
        // Gated (Date 03): the arrival check-in answers the tap; nothing is selected, drafted or sent.
        if (gated) return
        if (reason == ChatReason.CantMakeIt) {
            guideShownAt = now
            _ui.update { it.copy(selected = reason, draft = "", guide = Guide.Locked) }
        } else {
            _ui.update { it.copy(selected = reason, draft = reason.draft.orEmpty(), guide = Guide.None) }
        }
    }

    /** The field changed. A locked field takes nothing; the server's limit is the field's. */
    fun edit(text: String) {
        if (_ui.value.locked) return
        _ui.update { it.copy(draft = text.take(MESSAGE_MAX)) }
    }

    /** `Got it`: the guide collapses to one line and the field opens. The screen focuses it. */
    fun ackGuide() {
        val ui = _ui.value
        val date = ui.date ?: return
        if (ui.guide != Guide.Locked) return
        val now = clock()
        _ui.update { it.copy(guide = Guide.Acked) }
        analytics?.report(ChatAnalytics.cancelGuideAcknowledged(date, seconds(guideShownAt ?: now, now), now))
    }

    /** Send: the trimmed text with the selected chip's reason, or none. */
    fun send() {
        val ui = _ui.value
        val date = ui.date ?: return
        if (!ui.canSend) return
        val draft = ui.draft
        val body = draft.trim()
        val reason = ui.selected
        val isFirst = sentNone(ui)
        val clientId = UUID.randomUUID().toString()
        _ui.update { it.copy(sending = true) }
        viewModelScope.launch {
            val sent = store.send(date.id, reason, body, clientId)
            var landed = sent is Outcome.Ok
            // Refused because the date ended meanwhile: the date read shows the outcome at once, not
            // `Couldn't send` until the next poll. Still on, it was the send that failed: it stays in the
            // thread, and the field is free for the next message -- as after a send that landed. Unless a
            // poll meanwhile brought it: the answer was lost, the message was not, and it shows once.
            if (sent is Outcome.Failed) {
                if (!readDate(date.id)) return@launch
                landed = read.any { it.clientId == clientId }
                if (!landed) outbox += Unsent(clientId, reason, body, prefillEdited(reason, body), clock())
            } else if (sent is Outcome.Ok) {
                unconfirmed += sent.value
            }
            _ui.update { now ->
                // Text typed while it was sending is the person's: only what was sent is cleared.
                val untouched = now.draft == draft && now.selected == reason
                now.copy(
                    messages = shown(),
                    sending = false,
                    selected = if (untouched) null else now.selected,
                    draft = if (untouched) "" else now.draft,
                    guide = if (untouched) Guide.None else now.guide,
                )
            }
            if (landed) reportSent(date, reason, body, prefillEdited(reason, body), isFirst)
            if (sent is Outcome.Ok) refresh(date.id)
        }
    }

    /**
     * A tap on a failed message's line: sends it again with the same reason and id. Once at a time --
     * a second tap while it is on its way sends nothing.
     */
    fun resend(clientId: String) {
        val ui = _ui.value
        val date = ui.date ?: return
        val unsent = outbox.firstOrNull { it.clientId == clientId } ?: return
        if (clientId in ui.resending) return
        val isFirst = sentNone(ui)
        _ui.update { it.copy(resending = it.resending + clientId) }
        viewModelScope.launch {
            val sent = store.send(date.id, unsent.reason, unsent.body, clientId)
            if (sent is Outcome.Failed && !readDate(date.id)) return@launch
            // Sent -- or failed, but a poll meanwhile brought it (see [send]).
            val landed = sent is Outcome.Ok || read.any { it.clientId == clientId }
            if (landed && outbox.remove(unsent)) {
                if (sent is Outcome.Ok) unconfirmed += sent.value
                reportSent(date, unsent.reason, unsent.body, unsent.prefillEdited, isFirst)
            }
            _ui.update { it.copy(messages = shown(), resending = it.resending - clientId) }
            if (sent is Outcome.Ok) refresh(date.id)
        }
    }

    /** `End date` on the card: the sheet opens. Only with the card, so only after a cant_make_it message. */
    fun openEndSheet() {
        val ui = _ui.value
        val date = ui.date ?: return
        if (!ui.endCard || ui.outcome != null) return
        val from = cantMakeItFrom(ui.messages) ?: return
        _ui.update { it.copy(endSheet = true, endFailed = null) }
        analytics?.report(ChatAnalytics.endDateSheetOpened(date, from, clock()))
    }

    /**
     * `Keep the date` or the scrim: the sheet closes and nothing changes. No event. Not while the end is
     * on its way: the sheet closed, the end landed anyway and the outcome replaced the chat.
     */
    fun keepDate() = _ui.update { if (it.ending) it else it.copy(endSheet = false, endFailed = null) }

    /** `End date` on the sheet: ends the date for both. */
    fun endDate() {
        val ui = _ui.value
        val date = ui.date ?: return
        if (!ui.endSheet || ui.ending) return
        _ui.update { it.copy(ending = true, endFailed = null) }
        viewModelScope.launch {
            when (val result = store.endInChat(date.id)) {
                is EndResult.Ended -> ended(result.outcome)
                // Already over: the other person ended it, or a second tap did. The date read says which.
                EndResult.AlreadyEnded -> if (readDate(date.id)) {
                    _ui.update { it.copy(ending = false, endFailed = CallFailure.Server) }
                }
                is EndResult.Failed -> _ui.update { it.copy(ending = false, endFailed = result.why) }
            }
        }
    }

    /** The date, then the thread. The dock is never touched. */
    private suspend fun refresh(id: String) {
        if (!readDate(id)) return
        when (val thread = store.thread(id)) {
            is Outcome.Ok -> {
                val read = thread.value
                this.read = read
                unconfirmed.removeAll { sent -> read.any { it.id == sent.id } }
                // A failed send the server has after all: its answer was lost, the message was not.
                val landed = outbox.filter { unsent -> read.any { it.clientId == unsent.clientId } }
                outbox -= landed.toSet()
                val messages = shown()
                // Not yet reported on this phone, in an earlier launch either: once per message.
                val reported = received.reported(id)
                val arrived = messages.filter { !it.fromYou && it.id !in reported }
                _ui.update { it.copy(messages = messages, loading = false, failed = null, now = clock()) }
                reportOpened()
                val date = _ui.value.date
                if (date != null) landed.forEach { reportSent(date, it.reason, it.body, it.prefillEdited, isFirst = null) }
                arrived.forEach { analytics?.report(ChatAnalytics.messageReceived(id, it.reason, it.body.length)) }
                received.record(id, arrived.map { it.id })
                if (arrived.isNotEmpty()) store.markRead(id)
            }
            // A failed re-read keeps the thread on screen: a dropped connection is not an empty chat.
            is Outcome.Failed -> _ui.update { if (it.loading) it.copy(loading = false, failed = thread.why) else it }
        }
    }

    /**
     * Reads the date: the latest agreed time and place, or its end. False once the chat is over -- ended
     * (the outcome shows) or gone some other way ([ChatUi.left]). A failed read changes nothing.
     */
    private suspend fun readDate(id: String): Boolean {
        val read = dates.active(zone().id)
        if (read is Outcome.Ok) {
            when (val found = read.value) {
                is DateRead.Active -> if (found.date.id == id) {
                    _ui.update { it.copy(date = found.date, now = clock()) }
                } else {
                    leave()
                }
                is DateRead.Ended -> if (found.outcome.dateId == id) ended(found.outcome) else leave()
                DateRead.None -> leave()
            }
        }
        return _ui.value.outcome == null && !_ui.value.left
    }

    private fun ended(outcome: EndedInChat) {
        poll?.cancel()
        poll = null
        _ui.update {
            it.copy(
                outcome = outcome, endSheet = false, ending = false, endFailed = null,
                draft = "", selected = null, guide = Guide.None, sending = false,
            )
        }
    }

    private fun leave() {
        poll?.cancel()
        poll = null
        _ui.update { it.copy(left = true, endSheet = false, ending = false, sending = false) }
    }

    /** What the thread shows: the last read, what was sent since, then the failed sends. */
    private fun shown(): List<ChatMessage> =
        read + unconfirmed.filter { sent -> read.none { it.id == sent.id } } + outbox.map { it.shown() }

    /** No message of this person's has been sent yet: `is_first` for the next. */
    private fun sentNone(ui: ChatUi): Boolean = ui.messages.none { it.fromYou && !it.failed }

    /**
     * `message_sent`, once per message, when it was actually sent. [isFirst] null: worked out now, for a
     * failed send a read found landed -- the first of this person's if it is the only one.
     */
    private fun reportSent(date: ActiveDate, reason: ChatReason?, body: String, edited: Boolean?, isFirst: Boolean?) {
        visit?.let { it.sent++ }
        val latest = _ui.value.date ?: date
        val first = isFirst ?: (read.count { it.fromYou } <= 1)
        analytics?.report(ChatAnalytics.messageSent(latest, reason, body.length, edited, first, clock()))
    }

    /** `chat_opened`, once per visit, once the chat has loaded -- never for a failed first load. */
    private fun reportOpened() {
        val visit = visit ?: return
        val date = _ui.value.date ?: return
        if (visit.opened) return
        visit.opened = true
        // The tutorial comes before the first open of each date's chat, so arriving from it is the first.
        analytics?.report(ChatAnalytics.chatOpened(date, visit.entry, isFirst = visit.entry == ChatEntry.Tutorial, clock()))
    }

    private fun reset() {
        poll?.cancel()
        poll = null
        read = emptyList()
        unconfirmed.clear()
        outbox.clear()
        visit = null
        guideShownAt = null
        _ui.value = ChatUi(now = clock())
    }

    private fun seconds(from: Instant, to: Instant): Int = Duration.between(from, to).seconds.coerceAtLeast(0).toInt()
}
