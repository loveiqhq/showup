/*
 * DateProfileViewModel.kt
 * ShowUp · "See {name}'s full profile" from the Date screen, read-only (SHOWUP-193 ⑫)
 *
 * The match's profile, built by the server exactly as Search builds one (`GET /me/date/profile`),
 * drawn by Search's own profile screen in its read-only mode. Its state IS Search's state type, so
 * the screen needs nothing new -- but it is owned here, not by Search's view model: nothing on it can
 * be liked or passed, and it fires `screen_viewed` `search_profile` from `date_screen` and no other
 * Search event (spec §5). Photos open and page, clips play (ClipPlayback, shared with Search), and
 * none of that is reported.
 */
package com.showup.date

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.Outcome
import com.showup.profile.MediaKind
import com.showup.profile.MediaPlayerFactory
import com.showup.profile.ProfileAnalytics
import com.showup.profile.ProfileScreen
import com.showup.profile.report
import com.showup.search.ClipPlayback
import com.showup.search.SearchUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

class DateProfileViewModel(
    private val store: DateStore,
    private val analytics: AnalyticsTracker? = null,
    players: MediaPlayerFactory? = null,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    /** The open profile; null while none is open (the route is popped). */
    private val _state = MutableStateFlow<SearchUiState?>(null)
    val state: StateFlow<SearchUiState?> = _state.asStateFlow()

    private var readJob: Job? = null

    private val clips = ClipPlayback(
        viewModelScope, players,
        update = { change -> _state.update { s -> s?.copy(playback = change(s.playback)) } },
        ended = { stopPlayback() },
    )

    /** `See {name}'s full profile`: one visit, reported on the tap. A second tap while open is nothing. */
    fun open() {
        if (_state.value != null) return
        _state.value = SearchUiState(loading = true)
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.SearchProfile, ProfileScreen.DateScreen))
        read()
    }

    /** `Try again` on the failed state: the same visit, read again. */
    fun retry() {
        if (_state.value == null) return
        _state.update { it?.copy(loading = true, failure = null) }
        read()
    }

    /** The back pill, Android back, or the date going away under it. */
    fun close() {
        readJob?.cancel()
        stopPlayback()
        _state.value = null
    }

    fun openPhoto(index: Int) {
        stopPlayback()
        _state.update { it?.copy(viewerAt = index) }
    }

    fun viewerPage(index: Int) = _state.update { it?.copy(viewerAt = index) }

    fun closeViewer() = _state.update { it?.copy(viewerAt = null) }

    /** Play or pause [kind]; starting one stops the other. */
    fun togglePlay(kind: MediaKind) {
        val s = _state.value ?: return
        val profile = s.current ?: return
        val playing = s.playback
        stopPlayback()
        if (playing?.kind == kind) return
        val media = (if (kind == MediaKind.Voice) profile.voice else profile.video) ?: return
        clips.start(kind, media)
    }

    fun scrolledOut(kind: MediaKind) {
        if (_state.value?.playback?.kind == kind) stopPlayback()
    }

    private fun stopPlayback() {
        clips.stop()
        _state.update { it?.copy(playback = null) }
    }

    private fun read() {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            val answer = store.partnerProfile(zone().id)
            _state.update { s ->
                s ?: return@update null
                when (answer) {
                    is Outcome.Ok -> s.copy(loading = false, failure = null, queue = listOf(answer.value))
                    is Outcome.Failed -> s.copy(loading = false, failure = answer.why)
                }
            }
        }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
