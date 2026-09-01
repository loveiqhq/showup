/*
 * HarnessSelfTest.kt
 * ShowUp · proves the fit harness fires on real breakage, and stays quiet when there is none
 *
 * Both halves matter equally. A harness that never fires passes everything and is worthless; a
 * harness that always fires buries the real findings among noise. Neither failure is visible from
 * the results it produces -- only from feeding it cases whose answer is already known.
 *
 * The cases below are the four things the harness claims to detect, each induced deliberately. The
 * safe area here is 844 - 47 - 34 = 763dp, and every number is derived from that.
 */
package com.showup.fit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 844 - 47 - 34 = 763dp of usable height. */
private val PHONE = Device("self-test", 390, 844, 47, 34)

private const val LONG_TEXT =
    "This paragraph is far too long to survive being squeezed into a box only tall enough for a " +
        "single line of type, which is precisely the situation a cramped phone creates."

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class HarnessSelfTest {

    @Test
    fun `content that fits is reported as clean`() {
        val v = measureFit(PHONE, "fits") {
            Box(Modifier.fillMaxSize().padding(16.dp)) { Text("hello") }
        }
        assertTrue("a box that fits produced violations: $v", v.isEmpty())
    }

    @Test
    fun `text squeezed into too little height is caught as clipped`() {
        // The realistic failure: the text is not moved anywhere, it is simply not all drawn.
        val v = measureFit(PHONE, "squashed") {
            Box(Modifier.height(18.dp).width(200.dp)) { Text(LONG_TEXT, fontSize = 14.sp) }
        }
        assertTrue("clipped text was not detected: $v", v.any { it.problem == "TEXT CLIPPED" })
    }

    @Test
    fun `an element forced outside the safe area is caught`() {
        // requiredHeight ignores the parent's constraints, which is the one way to genuinely
        // escape the frame rather than be squashed inside it.
        val v = measureFit(PHONE, "overflowing") {
            Text("tall", Modifier.requiredHeight(900.dp))
        }
        assertTrue("an element past the bottom was not caught: $v",
            v.any { it.problem == "OFF THE BOTTOM" })
    }

    @Test
    fun `an element forced past the right edge is caught`() {
        val v = measureFit(PHONE, "too wide") {
            Text("wide", Modifier.requiredWidth(500.dp))
        }
        assertTrue("an element past the right edge was not caught: $v",
            v.any { it.problem == "OFF THE RIGHT" })
    }

    @Test
    fun `centred text is not mistaken for overflowing text`() {
        // The false positive that cost a whole run: a centred headline reported as clipped, by an
        // amount that grew with the screen. Nothing here is clipped, so nothing may be reported.
        val v = measureFit(PHONE, "centred") {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "The app starts here",
                    Modifier.align(androidx.compose.ui.Alignment.Center),
                    fontSize = 30.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        assertTrue("centred text was wrongly reported as clipped: $v",
            v.none { it.problem == "TEXT CLIPPED" })
    }

    @Test
    fun `a tap target below the 44dp minimum is a failure`() {
        val v = measureFit(PHONE, "small target") {
            Column { Text("tap me", Modifier.height(30.dp).clickable { }) }
        }
        val hit = v.firstOrNull { it.problem == "TAP TARGET TOO SMALL" }
        assertTrue("a 30dp tap target was not caught: $v", hit != null)
        assertTrue("it should be a failure, not an advisory", !hit!!.advisory)
    }

    @Test
    fun `a 48dp control is reported but not failed`() {
        // The band between Apple's 44 minimum and the ticket's 56: worth seeing, not a bug on its
        // own, because the design uses 44, 50, 52 and 54 deliberately for links and sheet buttons.
        val v = measureFit(PHONE, "mid target") {
            Column { Text("tap me", Modifier.height(48.dp).clickable { }) }
        }
        val hit = v.firstOrNull { it.problem.contains("56dp spec") }
        assertTrue("a 48dp control should be noted: $v", hit != null)
        assertTrue("...but only as an advisory", hit!!.advisory)
    }

    @Test
    fun `a healthy 56dp tap target is not reported at all`() {
        val v = measureFit(PHONE, "good target") {
            Column { Text("tap me", Modifier.height(56.dp).clickable { }) }
        }
        assertTrue("a compliant 56dp button was wrongly reported: $v",
            v.none { it.problem.contains("TAP TARGET") || it.problem.contains("56dp spec") })
    }
}
