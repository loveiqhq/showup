/*
 * DateHandoffs.kt
 * ShowUp · the quick actions' hand-offs to the phone (SHOWUP-193 ⑪ and ④)
 *
 * Each one is an intent another app answers; none needs a permission. A phone with no app for one
 * must not crash the Date screen, so every start is guarded -- a quick action that cannot be handed
 * off simply does nothing, as an OS sheet the user dismisses would.
 */
package com.showup.date

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract

/**
 * Directions: the place in the phone's maps app, through `geo:` -- whichever one the user made their
 * default, with no chooser in the way. With no maps app, the same search on the web.
 */
fun openDirections(context: Context, place: DatePlace) {
    val query = Uri.encode(placeText(place))
    if (start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query")))) return
    start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")))
}

/** Share Date: the ticket's sentence as plain text, through the system share sheet. */
fun shareDate(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    start(context, Intent.createChooser(send, null))
}

/** Calendar: a new event in the calendar app, filled in -- the user saves it there. No permission. */
fun addToCalendar(context: Context, event: CalendarEvent) {
    val insert = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, event.title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
    event.location?.let { insert.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
    start(context, insert)
}

/** Copy: `{place}, {address}` on the clipboard. The pill's `Copied` is the confirmation -- no toast. */
fun copyAddress(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
}

/** What the clipboard calls the copied place (the system shows it on Android 13+). */
private const val CLIP_LABEL = "Meeting place"

/** Starts [intent]; false when no app answers it. From an Application context it needs a new task. */
private fun start(context: Context, intent: Intent): Boolean = try {
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
