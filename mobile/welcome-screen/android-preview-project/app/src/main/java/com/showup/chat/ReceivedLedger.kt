/*
 * ReceivedLedger.kt
 * ShowUp · which of the match's messages `message_received` has reported, kept on the phone (SHOWUP-194)
 *
 * The registry's rule is once per message. Held in memory alone, every launch reported the whole thread
 * again, and a date's "answered" count grew with each app open (the SHOWUP-194 review). Per date, the ids
 * already reported: a later launch reports only the rest. Ids, not the newest time: two messages in one
 * millisecond would have hidden the second. A date's chat is tens of messages, so the set stays small.
 * iOS's `ReceivedLedger.swift` is the twin.
 */
package com.showup.chat

import android.content.Context

interface ReceivedLedger {
    /** The match's messages already reported for [dateId], by id. */
    fun reported(dateId: String): Set<String>

    /** [ids] have been reported for [dateId], beside those already kept. */
    fun record(dateId: String, ids: Collection<String>)
}

/** The phone's ledger: a set of ids per date, so plain preferences are enough. */
class SharedPrefsReceivedLedger(context: Context) : ReceivedLedger {
    private val prefs = context.getSharedPreferences("showup_chat_received", Context.MODE_PRIVATE)

    override fun reported(dateId: String): Set<String> = prefs.getStringSet(dateId, null).orEmpty().toSet()

    override fun record(dateId: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        // A copy: the set preferences hand back must not be changed in place.
        prefs.edit().putStringSet(dateId, reported(dateId) + ids).apply()
    }
}

/** A ledger in memory. Previews and tests. */
class InMemoryReceivedLedger : ReceivedLedger {
    private val reported = mutableMapOf<String, Set<String>>()

    override fun reported(dateId: String): Set<String> = reported[dateId].orEmpty()

    override fun record(dateId: String, ids: Collection<String>) {
        reported[dateId] = reported(dateId) + ids
    }
}
