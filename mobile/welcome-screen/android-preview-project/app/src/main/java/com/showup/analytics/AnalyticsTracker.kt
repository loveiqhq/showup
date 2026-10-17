package com.showup.analytics

/**
 * The one way anything in the app reports an event.
 *
 * Moved here from `com.showup.tutorial` when the welcome and sign-up screens gained tracking: a
 * shared contract living inside one feature package meant `welcome` would have had to depend on
 * `tutorial` to report anything, which is the same package-seam problem that produced three
 * different primary buttons.
 *
 * No SDK behind it yet, deliberately. [NoOpAnalytics] is the default everywhere, so events are
 * defined, wired and tested without anything being sent, and without the app needing an analytics
 * key to build. Switching it on is one object, in one place.
 */
interface AnalyticsTracker {
    fun track(event: String, properties: Map<String, Any>)
}

/**
 * A property whose registry type is nullable and whose value is null -- JSON `null`, not an absent
 * key and not a stand-in number.
 *
 * Properties are `Map<String, Any>`, which cannot hold a null, and until the Check-In no registry
 * field needed one (`referrer_screen_id` reads "" by its own rule). `check_in_filters_changed`'s
 * `max_age` does: "null for 65+ (no upper limit), never 65" (SHOWUP-182). A destination writes this
 * as JSON null; the debug log shows `null`.
 */
object JsonNull {
    override fun toString() = "null"
}

/** Reports nothing. The default on every screen. */
object NoOpAnalytics : AnalyticsTracker {
    override fun track(event: String, properties: Map<String, Any>) = Unit
}

/**
 * Records what it was asked to report, for tests.
 *
 * Lives in main rather than test source because both the Android unit tests and, later, a debug
 * build's diagnostics want it. It holds events in memory and never sends anything.
 */
class RecordingAnalytics : AnalyticsTracker {
    data class Entry(val event: String, val properties: Map<String, Any>)

    private val entries = mutableListOf<Entry>()

    override fun track(event: String, properties: Map<String, Any>) {
        entries += Entry(event, properties)
    }

    fun all(): List<Entry> = entries.toList()

    fun names(): List<String> = entries.map { it.event }

    /** Every recording of one event name. */
    fun of(event: String): List<Entry> = entries.filter { it.event == event }

    fun clear() = entries.clear()
}
