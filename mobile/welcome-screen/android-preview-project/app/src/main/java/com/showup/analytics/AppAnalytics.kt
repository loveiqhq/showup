package com.showup.analytics

import com.showup.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * THE app's tracker: every screen and view model reports through this one object.
 *
 * WHY ONE OBJECT, AND WHY NOW
 *
 * Every screen already builds its events, and the unit tests check them against each ticket's
 * table. But until 8 October 2026 no screen was handed a tracker at all -- each defaulted to
 * reporting nowhere -- so choosing an analytics tool would have meant wiring forty call sites by
 * hand, and nothing anyone could look at showed whether a single event fired. Now the host hands
 * this to every one of them, and two things follow:
 *
 *  - connecting a tool is ONE line: [destination].
 *  - in a debug build every event also lands in [log], which the developer menu shows live -- the
 *    "analytics debug log for one full pass" SHOWUP-163 asks for as evidence.
 *
 * A process-wide object rather than one per Activity: the log has to survive the restart a session
 * end makes (see `MainActivity.restartSignedOut`), and there is only ever one destination.
 */
object AppAnalytics : AnalyticsTracker {
    /**
     * Where events really go. Nowhere yet: no analytics tool has been chosen (Epic 11), and paid
     * providers are on hold. Connecting one is replacing this line, and nothing else.
     */
    private val destination: AnalyticsTracker = NoOpAnalytics

    /** Every event in this process, newest first -- filled in debug builds only. */
    val log = EventLog()

    override fun track(event: String, properties: Map<String, Any>) {
        // A compile-time constant, so R8 removes the branch -- and with it every reference to the
        // log -- from a release build.
        if (BuildConfig.DEBUG) log.record(event, properties)
        destination.track(event, properties)
    }
}

/**
 * The live event log the developer menu reads.
 *
 * In memory only, newest first, capped at [capacity] so a long session cannot grow it without
 * bound. Nothing here is written to logcat or to disk: the events carry no personal data by design
 * (events.json), and the menu still withholds any value whose key is on the prohibited list.
 *
 * Safe from any thread -- events are reported from view model coroutines and OkHttp callbacks
 * alike -- because every change is one atomic swap of an immutable list.
 */
class EventLog(
    private val capacity: Int = 300,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Entry(
        /** Increasing per log, so a list can key its rows on it. */
        val id: Long,
        val atMillis: Long,
        val event: String,
        val properties: Map<String, Any>,
    )

    private val nextId = AtomicLong(0)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    /** Newest first. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(event: String, properties: Map<String, Any>) {
        val entry = Entry(nextId.incrementAndGet(), clock(), event, properties.toMap())
        _entries.update { (listOf(entry) + it).take(capacity) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
