/*
 * DateHandoffsTest.kt
 * ShowUp · the quick actions' hand-offs to the phone (SHOWUP-193)
 *
 * Each one is an intent another app answers, so what is tested is the intent: its action, its data
 * and its extras, exactly -- and that a phone with no app for it does not crash the screen.
 */
package com.showup.date

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DateHandoffsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `Directions opens the place in the phone's maps app`() {
        openDirections(app, DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"))
        val sent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, sent.action)
        assertEquals("geo:0,0?q=Cafe%20Krone%2C%20Oranienstrasse%2042%2C%20Kreuzberg", sent.dataString)
    }

    @Test
    fun `with no maps app Directions opens the map on the web, and with nothing at all it does not crash`() {
        shadowOf(app).checkActivities(true)
        openDirections(app, DatePlace("Cafe Krone", null))
        // Nothing resolves in the test runtime: both attempts are caught, and nothing was started.
        assertNull(shadowOf(app).nextStartedActivity)
        shadowOf(app).checkActivities(false)
    }

    @Test
    fun `Share sends the ticket's sentence as plain text through the share sheet`() {
        shareDate(app, "My date with Leonie today at 19:00 at Cafe Krone.")
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("text/plain", inner.type)
        assertEquals("My date with Leonie today at 19:00 at Cafe Krone.", inner.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `Calendar opens a new event with the title, the place and the hour, needing no permission`() {
        val start = Instant.parse("2026-11-03T18:00:00Z")
        addToCalendar(app, CalendarEvent("Date with Leonie", start, start.plusSeconds(3600), "Cafe Krone, Oranienstrasse 42"))
        val sent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_INSERT, sent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, sent.data)
        assertEquals("Date with Leonie", sent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("Cafe Krone, Oranienstrasse 42", sent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertEquals(start.toEpochMilli(), sent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(start.plusSeconds(3600).toEpochMilli(), sent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
    }

    @Test
    fun `Calendar with no place leaves the location out`() {
        val start = Instant.parse("2026-11-03T18:00:00Z")
        addToCalendar(app, CalendarEvent("Date with Leonie", start, start.plusSeconds(3600), null))
        assertNull(shadowOf(app).nextStartedActivity.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
    }

    @Test
    fun `Copy puts the place on the clipboard, labelled Meeting place`() {
        copyAddress(app, "Cafe Krone, Oranienstrasse 42, Kreuzberg")
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("Meeting place", clip.description.label)
        assertEquals("Cafe Krone, Oranienstrasse 42, Kreuzberg", clip.getItemAt(0).text)
    }
}
