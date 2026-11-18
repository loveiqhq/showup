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
 *
 * A 409 IS NOT K. The server refuses a suggestion that can no longer be made, and a retry cannot change that:
 * [ChangePickerUi.stale] closes the picker and the host reads the date again. No error, no event.
 *
 * THE SELECTION, THE QUERY AND THE SEND'S ID SURVIVE PROCESS DEATH, in the [SavedStateHandle] -- the Definition of
 * Done's "survives process death". A restored place is kept only if the reloaded list still has it (R18).
 */
package com.showup.change

import androidx.lifecycle.SavedStateHandle
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
    /** 409: the suggestion can no longer be made. The host closes the picker and reads the date again; no K. */
    val stale: Boolean = false,
) {
    val hasSelection: Boolean get() = slot != null || place != null
}

/** The debounce on the trimmed query (ticket B, "debounced"; 300 ms is the design's). */
const val PLACES_DEBOUNCE_MS = 300L

/** The places route's `q` allows 100 characters; the field stops there so a long query never 400s into M. */
const val PLACES_QUERY_MAX = 100

private const val KEY_SLOT = "slot"
private const val KEY_QUERY = "query"
private const val KEY_PLACE_ID = "place_id"
private const val KEY_PLACE_NAME = "place_name"
private const val KEY_PLACE_TYPE = "place_type"
private const val KEY_PLACE_ADDRESS = "place_address"
private const val KEY_PLACE_DISTANCE = "place_distance"
private const val KEY_FROM_SEARCH = "from_search"
private const val KEY_ATTEMPT_VALUE = "attempt_value"
private const val KEY_ATTEMPT_ID = "attempt_id"

class ChangePickerViewModel(
    private val kind: ChangeKind,
    private val entry: ChangeEntry,
    private val store: ChangeStore,
    private val analytics: AnalyticsTracker? = null,
    private val clock: () -> Instant = Instant::now,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _ui = MutableStateFlow(
        ChangePickerUi(
            kind = kind,
            entry = entry,
            slot = saved.get<Long>(KEY_SLOT)?.let(Instant::ofEpochSecond),
            place = restoredPlace(),
            query = saved.get<String>(KEY_QUERY).orEmpty(),
        ),
    )
    val ui: StateFlow<ChangePickerUi> = _ui.asStateFlow()

    private var date: ActiveDate? = null
    private var loadJob: Job? = null

    /** The trimmed query the last read asked for. */
    private var requested: String? = null

    /** The selected place was picked while a non-empty query was showing. */
    private var pickedFromSearch = saved.get<Boolean>(KEY_FROM_SEARCH) ?: false

    /** The value last sent and the id it went with, kept for the retry -- and for a retry after process death. */
    private var attempt: Pair<String, String>? =
        saved.get<String>(KEY_ATTEMPT_VALUE)?.let { value -> saved.get<String>(KEY_ATTEMPT_ID)?.let { value to it } }

    private fun restoredPlace(): PlaceOption? {
        val id = saved.get<String>(KEY_PLACE_ID) ?: return null
        val type = PlaceType.entries.firstOrNull { it.name == saved.get<String>(KEY_PLACE_TYPE) } ?: return null
        return PlaceOption(
            id, saved.get<String>(KEY_PLACE_NAME).orEmpty(), type,
            saved.get<String>(KEY_PLACE_ADDRESS).orEmpty(), saved.get<Int>(KEY_PLACE_DISTANCE) ?: 0,
        )
    }

    /** What must outlive the process: the selection, the field, and the id of the send in progress. */
    private fun persist() {
        val u = _ui.value
        // A picker that has ended writes nothing more: a late answer must not bring a finished send back.
        if (u.closed || u.done || u.stale) return
        saved[KEY_SLOT] = u.slot?.epochSecond
        saved[KEY_QUERY] = u.query
        saved[KEY_PLACE_ID] = u.place?.placeId
        saved[KEY_PLACE_NAME] = u.place?.name
        saved[KEY_PLACE_TYPE] = u.place?.type?.name
        saved[KEY_PLACE_ADDRESS] = u.place?.address
        saved[KEY_PLACE_DISTANCE] = u.place?.distanceMeters
        saved[KEY_FROM_SEARCH] = pickedFromSearch
        saved[KEY_ATTEMPT_VALUE] = attempt?.first
        saved[KEY_ATTEMPT_ID] = attempt?.second
    }

    /**
     * The picker is on screen: `change_picker_opened`, once however many times the host composes it, and
     * for the place picker the nearby list. Suggest another calls this too and fires only this.
     */
    fun open(date: ActiveDate) {
        if (this.date != null) return
        this.date = date
        // A restored slot is only as good as the list the server offers now: it may have been taken, or the cutoff
        // may have moved it out. Not offered: dropped, so the primary reads `Pick a time` and no slot is lit (R18's
        // time half).
        _ui.value.slot?.let { restored ->
            if (restored !in date.change?.slots.orEmpty()) {
                _ui.update { it.copy(slot = null) }
                persist()
            }
        }
        analytics?.report(ChangeAnalytics.pickerOpened(date.id, kind, entry, date.meetAt, clock()))
        // After process death the field comes back with its text: the list answers THAT, not the nearby default.
        if (kind == ChangeKind.Place) fetch(_ui.value.query.trim(), immediately = true)
    }

    fun selectSlot(slot: Instant) {
        _ui.update { it.copy(slot = slot, sendFailed = if (it.slot == slot) it.sendFailed else false) }
        persist()
    }

    fun selectPlace(place: PlaceOption) {
        val list = _ui.value.list
        pickedFromSearch = list is PlacesView.Loaded && list.forQuery.isNotEmpty()
        _ui.update { it.copy(place = place, sendFailed = if (it.place == place) it.sendFailed else false) }
        persist()
    }

    /** The field changed: the list follows 300 ms after the last key, on the trimmed text. */
    fun setQuery(raw: String) {
        val query = raw.take(PLACES_QUERY_MAX)
        _ui.update { it.copy(query = query) }
        persist()
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
        // An ended picker starts no read: it would only write state nobody is looking at.
        if (_ui.value.closed || _ui.value.done || _ui.value.stale) return
        loadJob = viewModelScope.launch {
            if (!immediately) delay(PLACES_DEBOUNCE_MS)
            requested = trimmed
            when (val answer = store.places(dateId, trimmed.ifEmpty { null })) {
                is Outcome.Ok -> {
                    _ui.update {
                        val listed = answer.value.places
                        it.copy(
                            list = PlacesView.Loaded(listed, answer.value.google, trimmed),
                            place = it.place?.takeIf { chosen -> listed.any { p -> p.placeId == chosen.placeId } },
                        )
                    }
                    persist()
                }
                // No selection to keep: the list it was picked from is gone, and Suggest waits for a new one.
                is Outcome.Failed -> {
                    _ui.update { it.copy(list = PlacesView.Failed, place = null) }
                    persist()
                }
            }
        }
    }

    /** `Suggest {value}`, and K's retry. Inert while one is in flight, and with nothing selected. */
    fun suggest() {
        val state = _ui.value
        val dateId = date?.id ?: return
        if (state.sending || state.done || state.stale) return
        val value: String = (if (kind == ChangeKind.Time) state.slot?.epochSecond?.toString() else state.place?.placeId) ?: return
        val id = attempt?.takeIf { it.first == value }?.second ?: newId().also { attempt = value to it }
        persist()
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
                is SuggestResult.Sent -> {
                    loadJob?.cancel()
                    _ui.update { it.copy(sending = false, done = true) }
                }
                // 409: nothing a retry can fix. No K, no event: the host closes and reads the date again.
                SuggestResult.Stale -> {
                    loadJob?.cancel()
                    _ui.update { it.copy(sending = false, stale = true) }
                }
                is SuggestResult.Failed -> _ui.update { it.copy(sending = false, sendFailed = true) }
            }
        }
    }

    /** Cancel or back: nothing was stored or sent. Ignored while a send is in flight (both buttons are inert). */
    fun cancel() {
        val state = _ui.value
        if (state.sending || state.closed || state.done || state.stale) return
        date?.let { analytics?.report(ChangeAnalytics.pickerDismissed(it.id, kind, entry, state.hasSelection)) }
        // The picker is over: a places answer still in flight (or a debounced search not yet sent) is dropped.
        loadJob?.cancel()
        _ui.update { it.copy(closed = true) }
    }
}
