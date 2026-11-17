/*
 * ChangePickerViewModel.kt
 * ShowUp · the time and place picker's asynchronous work (SHOWUP-196, Date 05 A, B, K, M, N)
 *
 * The place picker loads, searches with a debounce and sends, which is the trigger CLAUDE.md names for a
 * ViewModel. The sheets stay functions from [ChangePickerUi] to pixels.
 *
 * THE RULES THIS FILE ENFORCES
 *
 * NOTHING CHANGES UNTIL THE SERVER CONFIRMS. A send that fails (K) leaves the picker open with the
 * selection kept; `done` is set only on the server's answer, and the host closes and re-reads on it.
 *
 * A RETRY IS THE SAME SEND. The clientId is made on the first Suggest and reused while the selected value
 * is the same, so a suggestion the server stored whose answer was lost is answered with that copy, never
 * stored twice (R14). Another value is another suggestion: a new id.
 *
 * ONE SELECTION SURVIVES A LIST CHANGE only while the selected place is still listed (R18).
 */
package com.showup.change

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.Outcome
import com.showup.date.ActiveDate
import com.showup.profile.report
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** What the place list area shows. */
sealed interface PlacesView {
    /** The first read is in flight: the area stays empty and says so to a screen reader only (R17). */
    data object Loading : PlacesView

    /** M: the request failed. The message and Try again take the list's place. */
    data object Failed : PlacesView

    /** [forQuery] is the trimmed query the list answers: empty is "nearby", otherwise "Results" (or N when empty). */
    data class Loaded(val places: List<PlaceOption>, val google: Boolean, val forQuery: String) : PlacesView
}

/** Everything a picker renders. */
data class ChangePickerUi(
    val kind: ChangeKind,
    val entry: ChangeEntry,
    val slot: Instant? = null,
    val place: PlaceOption? = null,
    /** The field as typed, untrimmed. */
    val query: String = "",
    val list: PlacesView = PlacesView.Loading,
    /** A send is in flight: both buttons are inert and the primary sits at alpha .6. */
    val sending: Boolean = false,
    /** K: the last send got no server OK. */
    val sendFailed: Boolean = false,
    /** The server confirmed: the host closes the picker and re-reads. */
    val done: Boolean = false,
    /** Cancel or back: the host closes the picker. */
    val closed: Boolean = false,
) {
    val hasSelection: Boolean get() = slot != null || place != null
}

/** The debounce on the trimmed query (ticket B, "debounced"; 300 ms is the design's). */
const val PLACES_DEBOUNCE_MS = 300L

class ChangePickerViewModel(
    private val kind: ChangeKind,
    private val entry: ChangeEntry,
    private val store: ChangeStore,
    private val analytics: AnalyticsTracker? = null,
    private val clock: () -> Instant = Instant::now,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChangePickerUi(kind = kind, entry = entry))
    val ui: StateFlow<ChangePickerUi> = _ui.asStateFlow()

    private var date: ActiveDate? = null
    private var loadJob: Job? = null

    /** The trimmed query the last read asked for. */
    private var requested: String? = null

    /** The selected place was picked while a non-empty query was showing. */
    private var pickedFromSearch = false

    /** The value last sent and the id it went with, kept for the retry. */
    private var attempt: Pair<Any, String>? = null

    /**
     * The picker is on screen: `change_picker_opened`, once however many times the host composes it, and
     * for the place picker the nearby list. Suggest another calls this too and fires only this.
     */
    fun open(date: ActiveDate) {
        if (this.date != null) return
        this.date = date
        analytics?.report(ChangeAnalytics.pickerOpened(date.id, kind, entry, date.meetAt, clock()))
        if (kind == ChangeKind.Place) fetch("", immediately = true)
    }

    fun selectSlot(slot: Instant) {
        _ui.update { it.copy(slot = slot, sendFailed = if (it.slot == slot) it.sendFailed else false) }
    }

    fun selectPlace(place: PlaceOption) {
        val list = _ui.value.list
        pickedFromSearch = list is PlacesView.Loaded && list.forQuery.isNotEmpty()
        _ui.update { it.copy(place = place, sendFailed = if (it.place == place) it.sendFailed else false) }
    }

    /** The field changed: the list follows 300 ms after the last key, on the trimmed text. */
    fun setQuery(query: String) {
        _ui.update { it.copy(query = query) }
        if (kind != ChangeKind.Place) return
        val trimmed = query.trim()
        // Same read as the last one, and it did not fail: nothing to ask. A failure is retried by typing.
        if (trimmed == requested && _ui.value.list !is PlacesView.Failed) {
            loadJob?.cancel()
            return
        }
        fetch(trimmed, immediately = false)
    }

    /** M's Try again: the same read again, for what is typed. */
    fun retryPlaces() {
        _ui.update { it.copy(list = PlacesView.Loading) }
        fetch(_ui.value.query.trim(), immediately = true)
    }

    private fun fetch(trimmed: String, immediately: Boolean) {
        val dateId = date?.id ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!immediately) delay(PLACES_DEBOUNCE_MS)
            requested = trimmed
            when (val answer = store.places(dateId, trimmed.ifEmpty { null })) {
                is Outcome.Ok -> _ui.update {
                    val listed = answer.value.places
                    it.copy(
                        list = PlacesView.Loaded(listed, answer.value.google, trimmed),
                        place = it.place?.takeIf { chosen -> listed.any { p -> p.placeId == chosen.placeId } },
                    )
                }
                // No selection to keep: the list it was picked from is gone, and Suggest waits for a new one.
                is Outcome.Failed -> _ui.update { it.copy(list = PlacesView.Failed, place = null) }
            }
        }
    }

    /** `Suggest {value}`, and K's retry. Inert while one is in flight, and with nothing selected. */
    fun suggest() {
        val state = _ui.value
        val dateId = date?.id ?: return
        if (state.sending || state.done) return
        val value: Any = (if (kind == ChangeKind.Time) state.slot else state.place?.placeId) ?: return
        val id = attempt?.takeIf { it.first == value }?.second ?: newId().also { attempt = value to it }
        _ui.update { it.copy(sending = true, sendFailed = false) }
        val request = SuggestRequest(
            kind = kind,
            time = state.slot.takeIf { kind == ChangeKind.Time },
            placeId = state.place?.placeId.takeIf { kind == ChangeKind.Place },
            entry = entry,
            fromSearch = if (kind == ChangeKind.Place) pickedFromSearch else null,
            clientId = id,
            tz = zone().id,
        )
        viewModelScope.launch {
            when (store.suggest(dateId, request)) {
                is Outcome.Ok -> _ui.update { it.copy(sending = false, done = true) }
                is Outcome.Failed -> _ui.update { it.copy(sending = false, sendFailed = true) }
            }
        }
    }

    /** Cancel or back: nothing was stored or sent. Ignored while a send is in flight (both buttons are inert). */
    fun cancel() {
        val state = _ui.value
        if (state.sending || state.closed || state.done) return
        date?.let { analytics?.report(ChangeAnalytics.pickerDismissed(it.id, kind, entry, state.hasSelection)) }
        _ui.update { it.copy(closed = true) }
    }
}
