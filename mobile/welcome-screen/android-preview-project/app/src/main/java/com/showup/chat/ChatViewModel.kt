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

/** Everything the chat's screens render, in one value. */
data class ChatUi(
    /** The date, as the last read found it: the header's latest agreed time and place. */
    val date: ActiveDate? = null,
    val now: Instant,
    /** Oldest first: the server's thread, plus anything sent that a read has not returned yet. */
    val messages: List<ChatMessage> = emptyList(),
    /** The thread's first read has not landed. */
    val loading: Boolean = true,
    /** Why the first read failed, while there is no thread to show; null once one is. */
    val failed: CallFailure? = null,
    val selected: ChatReason? = null,
    val draft: String = "",
    val guide: Guide = Guide.None,
    val sending: Boolean = false,
    /** The last send did not land; the draft and the chip are still there. Cleared by any edit. */
    val sendFailed: Boolean = false,
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
) : ViewModel() {

    private val _ui = MutableStateFlow(ChatUi(now = clock()))
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()

    private var poll: Job? = null

    /** The match's messages already reported as received this session, by id: once per message. */
    private val received = mutableSetOf<String>()

    /** Sent this session and not yet in a read: a poll that left before the send must not drop them. */
    private val unconfirmed = mutableListOf<ChatMessage>()

    private var visit: Visit? = null
    private var guideShownAt: Instant? = null

    private class Visit(val start: Instant) {
        var sent = 0
    }

    /**
     * The chat is shown: a visit begins. [date] is what the opening screen read; the first poll
     * replaces it with the latest. A different date than the one held starts afresh.
     */
    fun open(date: ActiveDate, entry: ChatEntry) {
        if (_ui.value.date?.id.let { it != null && it != date.id }) reset()
        val now = clock()
        _ui.update { it.copy(date = date, now = now, left = false) }
        visit = Visit(now)
        // The tutorial comes before the first open of each date's chat, so arriving from it is the first.
        analytics?.report(ChatAnalytics.chatOpened(date, entry, isFirst = entry == ChatEntry.Tutorial, now))
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
                refresh(id)
                delay(CHAT_POLL_MS)
            }
        }
    }

    /** `Try again` on a failed first read. */
    fun retry() {
        val id = _ui.value.date?.id ?: return
        _ui.update { it.copy(loading = true, failed = null) }
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
     */
    fun pick(reason: ChatReason, surface: ChipSurface) {
        val date = _ui.value.date ?: return
        val now = clock()
        analytics?.report(ChatAnalytics.actionChipUsed(date, reason, surface, now))
        if (reason == ChatReason.CantMakeIt) {
            guideShownAt = now
            _ui.update { it.copy(selected = reason, draft = "", guide = Guide.Locked, sendFailed = false) }
        } else {
            _ui.update { it.copy(selected = reason, draft = reason.draft.orEmpty(), guide = Guide.None, sendFailed = false) }
        }
    }

    /** The field changed. A locked field takes nothing; the server's limit is the field's. */
    fun edit(text: String) {
        if (_ui.value.locked) return
        _ui.update { it.copy(draft = text.take(MESSAGE_MAX), sendFailed = false) }
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
        val isFirst = ui.messages.none { it.fromYou }
        _ui.update { it.copy(sending = true, sendFailed = false) }
        viewModelScope.launch {
            when (val sent = store.send(date.id, reason, body)) {
                is Outcome.Ok -> {
                    unconfirmed += sent.value
                    visit?.let { it.sent++ }
                    _ui.update { now ->
                        // Text typed while it was sending is the person's: only what was sent is cleared.
                        val untouched = now.draft == draft && now.selected == reason
                        now.copy(
                            messages = if (now.messages.any { it.id == sent.value.id }) now.messages else now.messages + sent.value,
                            sending = false,
                            selected = if (untouched) null else now.selected,
                            draft = if (untouched) "" else now.draft,
                            guide = if (untouched) Guide.None else now.guide,
                        )
                    }
                    val latest = _ui.value.date ?: date
                    analytics?.report(ChatAnalytics.messageSent(latest, reason, body.length, prefillEdited(reason, body), isFirst, clock()))
                    refresh(date.id)
                }
                is Outcome.Failed -> _ui.update { it.copy(sending = false, sendFailed = true) }
            }
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

    /** `Keep the date` or the scrim: the sheet closes and nothing changes. No event. */
    fun keepDate() = _ui.update { it.copy(endSheet = false, endFailed = null) }

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
                unconfirmed.removeAll { sent -> read.any { it.id == sent.id } }
                val messages = read + unconfirmed
                val arrived = messages.filter { !it.fromYou && received.add(it.id) }
                _ui.update { it.copy(messages = messages, loading = false, failed = null, now = clock()) }
                arrived.forEach { analytics?.report(ChatAnalytics.messageReceived(id, it.reason, it.body.length)) }
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
        _ui.update { it.copy(left = true, endSheet = false, ending = false) }
    }

    private fun reset() {
        poll?.cancel()
        poll = null
        received.clear()
        unconfirmed.clear()
        visit = null
        guideShownAt = null
        _ui.value = ChatUi(now = clock())
    }

    private fun seconds(from: Instant, to: Instant): Int = Duration.between(from, to).seconds.coerceAtLeast(0).toInt()
}
