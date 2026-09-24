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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.showup.profile.WAVEFORM_TAG
import com.showup.profile.Waveform
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
        // Never advisory. Scrolling excuses where a thing sits, never whether it can be read, so
        // the scroll-aware branch must not have softened this one.
        assertTrue("clipping must fail a screen, not merely be noted: $v",
            v.filter { it.problem == "TEXT CLIPPED" }.none { it.advisory })
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
    fun `content below the fold of a scroll view is an advisory, not a failure`() {
        // The instrument for the scroll-aware branch added on 10 September. Without this, "zero
        // findings" on a scrolling screen is indistinguishable from a detector that never fires --
        // and the whole sweep would go quiet the moment a screen started scrolling.
        val v = measureFit(PHONE, "scrolled") {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("top")
                Text("far below", Modifier.padding(top = 2000.dp))
            }
        }
        val below = v.filter { it.problem == "BELOW THE FOLD" }
        assertTrue("content below a scroll fold was not reported at all: $v", below.isNotEmpty())
        assertTrue("below-the-fold content must not FAIL a screen: $below", below.all { it.advisory })
        assertTrue("it must not also be reported as lost off the bottom: $v",
            v.none { it.problem == "OFF THE BOTTOM" })
    }

    // NO SELF-TEST FOR "text clipped inside a scroll view is still a failure", and the absence
    // is deliberate rather than an oversight.
    //
    // The property holds structurally: the `scrollable` flag added on 10 September gates ONLY the
    // off-the-bottom branch, and the clipping detector above it is untouched. What could not be
    // built is a synthetic case to prove it, because Compose will not produce the condition. A
    // scroll container measures its content with an infinite maximum height, and every attempt to
    // squeeze a text node underneath one -- requiredHeight on the text, a fixed-height Box around
    // it, a fixed-height Column around it -- leaves the paragraph laid out in full and merely
    // overhanging its box, which is not clipping and is correctly not reported.
    //
    // Three constructions were tried and all three returned no findings at all. Writing a fourth
    // until something passed would have produced a test asserting whatever it happened to catch.
    // The clipping detector's own instrument is `text squeezed into too little height is caught
    // as clipped` above, which also asserts the finding is not advisory.


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

    // ── a drawing given no room to draw in ──────────────────────────────────────────────────
    //
    // The three below exist because the voice waveform shipped invisible for an afternoon and every
    // check in this file said the screen was clean. They reproduce the exact shape of that bug.

    @Test
    fun `a canvas given a height RANGE collapses, and is caught`() {
        // The bug, in four lines. `Canvas` is `Spacer(modifier.drawBehind {})`, and Spacer takes the
        // incoming maximum only on an axis whose constraint is FIXED -- zero on an axis given a
        // range. `heightIn(min, max)` is a range, so this canvas is 0dp tall and paints nothing.
        val v = measureFit(PHONE, "collapsed canvas") {
            Column {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 120.dp)
                        .testTag(WAVEFORM_TAG),
                ) { drawRect(Color.Red) }
            }
        }
        val hit = v.firstOrNull { it.problem == "CANVAS COLLAPSED" }
        assertTrue("a zero-height canvas must be caught: $v", hit != null)
        assertTrue("and must not be an advisory", !hit!!.advisory)
    }

    @Test
    fun `the same canvas with a FIXED height is not reported`() {
        val v = measureFit(PHONE, "healthy canvas") {
            Column {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 120.dp)
                        .fillMaxHeight()
                        .testTag(WAVEFORM_TAG),
                ) { drawRect(Color.Red) }
            }
        }
        assertTrue("the fix must read as clean: $v", v.none { it.problem.startsWith("CANVAS") })
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `every tag the harness measures is one a real component actually carries`() {
        // Keeps MEASURED_CANVASES honest. The set is spelled as strings so the harness does not
        // depend on the screen package; the cost of that is a tag rename silently matching nothing,
        // and this is what pays it. Every entry must be reachable by rendering a real component.
        val rendered = mutableSetOf<String>()
        measureFit(PHONE, "tag reachability") {
            Waveform(
                bars = List(24) { 0.5f },
                progress = 0.5f,
                playedColor = Color.Red,
                restColor = Color.Blue,
                barGap = 2.dp,
                minBarHeight = 3.dp,
                corner = 2.dp,
                modifier = Modifier.fillMaxWidth().height(36.dp),
            )
        }
        // measureFit reports only problems, so ask the tree directly.
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("root")) {
                    Waveform(
                        bars = List(24) { 0.5f },
                        progress = 0.5f,
                        playedColor = Color.Red,
                        restColor = Color.Blue,
                        barGap = 2.dp,
                        minBarHeight = 3.dp,
                        corner = 2.dp,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                    )
                }
            }
            fun walk(n: SemanticsNode) {
                n.config.getOrNull(SemanticsProperties.TestTag)?.let { rendered += it }
                n.children.forEach { walk(it) }
            }
            walk(onNodeWithTag("root").fetchSemanticsNode())
        }
        val missing = MEASURED_CANVASES - rendered
        assertTrue(
            "these tags are measured by the harness but no component carries them: $missing",
            missing.isEmpty(),
        )
    }
}
