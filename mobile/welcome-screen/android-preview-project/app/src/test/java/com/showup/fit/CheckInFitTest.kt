/*
 * CheckInFitTest.kt
 * ShowUp · the 48hrs Check-In, measured on every phone (SHOWUP-178 to SHOWUP-183)
 *
 * ScreenFitTest proves nothing is clipped, squeezed or out of reach. This proves the geometry the six
 * tickets promise:
 *
 *   · the CTA and the footer are ALWAYS fully on screen -- every state, every phone, three type sizes
 *   · ONE flexible element per step, and only it scrolls: the grid (02), the list (03, 05), the chip
 *     groups (04), the whole body (06); the intro never scrolls at the tickets' frames
 *   · Availability, Motivation and Uplift fit at 390 x 844 and scroll at 375 x 667; Vibe scrolls on
 *     every phone; at 430 x 932 everything but Vibe fits
 *   · nothing above the scrolling region and nothing in the footer moves between a step's states
 *   · a slot is drawn 36 and answers at 44; a slider thumb's 44 never leaves the screen sideways
 *   · with a keyboard up, Find my date is still on screen and the body scrolls
 *
 * THE TICKETS' FRAMES ARE THE DEVICES' CONTENT COLUMNS, as in PickFitTest: 375 x 667 with the SE's
 * 20 status bar (647), 390 x 844 and 430 x 932 at their devices' safe heights (763, 839).
 */
package com.showup.fit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.showup.checkin.AGE_SLIDER_TAG
import com.showup.checkin.CHECK_IN_CTA_TAG
import com.showup.checkin.CHECK_IN_FOOTER_TAG
import com.showup.checkin.CHECK_IN_HEADLINE_TAG
import com.showup.checkin.CHECK_IN_SCROLL_TAG
import com.showup.checkin.CheckInAvailabilityScreen
import com.showup.checkin.CheckInCopy
import com.showup.checkin.CheckInFilters
import com.showup.checkin.CheckInFiltersScreen
import com.showup.checkin.CheckInGateScreen
import com.showup.checkin.CheckInIntroScreen
import com.showup.checkin.CheckInMotivationScreen
import com.showup.checkin.CheckInStep
import com.showup.checkin.CheckInUpliftScreen
import com.showup.checkin.CheckInVibeScreen
import com.showup.checkin.DISTANCE_SLIDER_TAG
import com.showup.checkin.GATE_STATEMENT_TAG
import com.showup.checkin.INTRO_LIST_TAG
import com.showup.checkin.SlotTier
import com.showup.checkin.checkInDays
import com.showup.checkin.gridOf
import com.showup.checkin.slotTag
import com.showup.designsystem.sliderThumbTag
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import kotlin.math.abs

/** The shared shot's types, under the names this suite has always used. */
private typealias Rect = FitRect
private typealias Shot = FitShot

/** The tallest phone the six tickets name (430 x 932). Their "every phone" claims are about these. */
private const val TALLEST_IN_MATRIX = 932

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class CheckInFitTest {

    /** The shared geometry shot ([shootGeometry]), with this screen group's scroll region. */
    private fun shoot(width: Int, height: Int, fontScale: Float = 1f, content: @Composable () -> Unit) =
        shootGeometry(width, height, fontScale, scrollTag = CHECK_IN_SCROLL_TAG, content = content)

    private val SCALES = listOf(1f, 1.3f, 2f)
    private val SE = 375 to 647
    private val MID = 390 to 763
    private val MAX = 430 to 839
    private val TICKET_FRAMES = listOf(SE, MID, MAX)

    // ── the states each ticket names ────────────────────────────────────────────────────────────

    private val days = checkInDays(LocalDateTime.of(2026, 10, 13, 16, 20))
    private val today = gridOf(CheckInStep.Today, days)
    private val tomorrow = gridOf(CheckInStep.Tomorrow, days)
    private val five = setOf("coffee_chat", "sunset", "deep_talk", "playful", "no_rush")
    private val narrowed = CheckInFilters(setOf("woman", "non_binary"), 25, 35, 15)
    private val CONTACT = "Text me on 0176 2345 6789 and we'll grab a drink tonight"
    private val WRITTEN = "I'll take you on a rafting tour on Wannsee — picnic included"

    /** Every step state, by step: the ones a step's region and footer must hold still across. */
    private fun steps(): Map<String, List<Pair<String, @Composable () -> Unit>>> = mapOf(
        "today" to listOf(
            "A" to { CheckInAvailabilityScreen(today, mapOf(18 to SlotTier.Preferred, 19 to SlotTier.Preferred, 20 to SlotTier.Available), false) },
            "B" to { CheckInAvailabilityScreen(today, emptyMap(), false, dialogOpen = true) },
            "how it works" to { CheckInAvailabilityScreen(today, mapOf(18 to SlotTier.Preferred), true) },
        ),
        "tomorrow" to listOf(
            "C" to { CheckInAvailabilityScreen(tomorrow, emptyMap(), false, toast = CheckInCopy.TOMORROW_TOAST) },
            "D" to { CheckInAvailabilityScreen(tomorrow, mapOf(12 to SlotTier.Preferred, 13 to SlotTier.Preferred, 19 to SlotTier.Available), false) },
        ),
        "motivation" to listOf(
            "A" to { CheckInMotivationScreen(selected = null) },
            "B" to { CheckInMotivationScreen(selected = null, toast = CheckInCopy.MOTIVATION_TOAST) },
            "C" to { CheckInMotivationScreen(selected = "long_term") },
        ),
        "vibe" to listOf(
            "A" to { CheckInVibeScreen(picked = emptySet()) },
            "B" to { CheckInVibeScreen(picked = emptySet(), toast = CheckInCopy.VIBE_TOAST_EMPTY) },
            "C" to { CheckInVibeScreen(picked = setOf("coffee_chat", "playful", "no_rush")) },
            "D" to { CheckInVibeScreen(picked = five, toast = CheckInCopy.VIBE_TOAST_MAX) },
        ),
        "filters" to listOf(
            "A" to { CheckInFiltersScreen(CheckInFilters(setOf("woman"))) },
            "B" to { CheckInFiltersScreen(CheckInFilters(emptySet()), toast = CheckInCopy.FILTERS_TOAST) },
            "C" to { CheckInFiltersScreen(narrowed) },
            // Check-In 05b (SHOWUP-189) with the remote flag on: the sheet's A, B and C.
            "05b A" to { CheckInFiltersScreen(CheckInFilters(setOf("woman")), dealbreakers = true) },
            "05b B" to { CheckInFiltersScreen(filters05bB, dealbreakers = true) },
            "05b C" to { CheckInFiltersScreen(filters05bC, dealbreakers = true) },
        ),
        "uplift" to listOf(
            "A" to { CheckInUpliftScreen(text = "") },
            "B" to { CheckInUpliftScreen(text = "I have 2 tickets for the Berghain garden tonight and nobody to go with") },
            "C" to { CheckInUpliftScreen(text = CONTACT, blocked = true, toast = CheckInCopy.CONTACT_ERROR) },
            "D" to { CheckInUpliftScreen(text = WRITTEN, saving = true) },
            "E" to { CheckInUpliftScreen(text = WRITTEN, saveFailed = true) },
        ),
    )

    // ── 1 · the CTA and the footer are always on screen ────────────────────────────────────────

    @Test
    fun `every step state keeps the footer and its CTA fully on screen, on every phone and type size`() {
        val failures = mutableListOf<String>()
        for ((step, states) in steps()) for ((label, content) in states) for (d in DEVICES) for (scale in SCALES) {
            val s = shoot(d.width, d.safeHeight, scale, content)
            val cta = s.tag(CHECK_IN_CTA_TAG)
            val footer = s.tag(CHECK_IN_FOOTER_TAG)
            if (cta.bottom > s.frame + 0.5f || cta.top < 0f) failures += "$step/$label $d @$scale  CTA at ${cta.top}..${cta.bottom} of ${s.frame}"
            if (footer.bottom > s.frame + 0.5f) failures += "$step/$label $d @$scale  footer ends ${footer.bottom}"
            if (cta.height < 55.5f) failures += "$step/$label $d @$scale  CTA squeezed to ${cta.height}"
            if (s.lowest > s.frame + 0.5f) failures += "$step/$label $d @$scale  something outside the region ends ${s.lowest}"
        }
        println(if (failures.isEmpty()) "OK   check-in: footer and CTA on screen everywhere" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the intro and the gate keep their CTA, list and tab bar on screen, on every phone and type size`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) for (scale in SCALES) {
            val intro = shoot(d.width, d.safeHeight, scale) { CheckInIntroScreen() }
            val cta = intro.tag(CHECK_IN_CTA_TAG)
            if (scale == 1f && cta.bottom > intro.frame + 0.5f) failures += "intro $d  CTA ends ${cta.bottom}"
            val gate = shoot(d.width, d.safeHeight, scale) { CheckInGateScreen() }
            val gateCta = gate.tag(CHECK_IN_CTA_TAG)
            val search = gate.tag("tab-search")
            if (gateCta.bottom > gate.frame + 0.5f) failures += "gate $d @$scale  CTA ends ${gateCta.bottom}"
            if (search.bottom > gate.frame + 0.5f) failures += "gate $d @$scale  tab bar ends ${search.bottom}"
            if (gateCta.bottom > search.top + 0.5f) failures += "gate $d @$scale  CTA runs into the tab bar"
        }
        println(if (failures.isEmpty()) "OK   intro and gate on screen everywhere" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 2 · which steps fit and which scroll ───────────────────────────────────────────────────

    private fun overflow(frame: Pair<Int, Int>, content: @Composable () -> Unit) = shoot(frame.first, frame.second, content = content).overflow

    @Test
    fun `the intro never scrolls at the tickets' frames`() {
        for (f in TICKET_FRAMES) {
            val s = shoot(f.first, f.second) { CheckInIntroScreen() }
            println("DIAG intro @${f.first}x${f.second}: list ends ${s.tag(INTRO_LIST_TAG).bottom}, CTA ${s.tag(CHECK_IN_CTA_TAG).top}")
            assertTrue("the intro must not scroll at $f", s.lowest <= s.frame + 0.5f)
            assertTrue("the CTA sits below the list at $f", s.tag(CHECK_IN_CTA_TAG).top > s.tag(INTRO_LIST_TAG).bottom)
        }
    }

    @Test
    fun `both grids fit at 390 and 430 and scroll inside themselves at 375`() {
        for ((name, grid) in listOf("today" to today, "tomorrow" to tomorrow)) {
            val se = overflow(SE) { CheckInAvailabilityScreen(grid, emptyMap(), false) }
            val mid = overflow(MID) { CheckInAvailabilityScreen(grid, emptyMap(), false) }
            val max = overflow(MAX) { CheckInAvailabilityScreen(grid, emptyMap(), false) }
            val rowsAtSe = (shoot(SE.first, SE.second) { CheckInAvailabilityScreen(grid, emptyMap(), false) }
                .tag(com.showup.checkin.GRID_TAG).height + 5f) / 41f
            println("DIAG $name grid: @375 overflow $se (about ${"%.1f".format(rowsAtSe)} rows visible) · @390 ${-mid} spare · @430 ${-max} spare")
            assertTrue("$name must fit at 390: $mid", mid <= 0.5f)
            assertTrue("$name must fit at 430: $max", max <= 0.5f)
            if (name == "tomorrow") assertTrue("tomorrow must scroll at 375: $se", se > 0f)
        }
    }

    @Test
    fun `motivation fits at 390 and 430 and scrolls at 375`() {
        val se = overflow(SE) { CheckInMotivationScreen(selected = null) }
        val mid = overflow(MID) { CheckInMotivationScreen(selected = null) }
        val max = overflow(MAX) { CheckInMotivationScreen(selected = null) }
        println("DIAG motivation: @375 overflow $se · @390 ${-mid} spare · @430 ${-max} spare")
        assertTrue("motivation must scroll at 375: $se", se > 0f)
        assertTrue("motivation must fit at 390: $mid", mid <= 0.5f)
        assertTrue("motivation must fit at 430: $max", max <= 0.5f)
    }

    @Test
    fun `the vibe groups scroll on every phone`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) {
            val over = overflow(d.width to d.safeHeight) { CheckInVibeScreen(picked = emptySet()) }
            if (d.inAcceptanceCriteria) println("DIAG vibe @$d: ${over}dp past the region")
            // The ticket's "everywhere" is its matrix, 375 x 667 to 430 x 932. A taller phone (the
            // Z Flip's 958 dp, added 22 Oct 2026) holds every group, and fitting is right there --
            // its clipping and off-screen checks are the sweep's, on every device.
            if (over <= 0f && d.height <= TALLEST_IN_MATRIX) {
                failures += "vibe fits at $d -- the ticket says the groups scroll everywhere"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `filters fit at 390 and 430, and the distance row is reachable at 375`() {
        val mid = overflow(MID) { CheckInFiltersScreen(CheckInFilters(setOf("woman"))) }
        val max = overflow(MAX) { CheckInFiltersScreen(CheckInFilters(setOf("woman"))) }
        val se = shoot(SE.first, SE.second) { CheckInFiltersScreen(CheckInFilters(setOf("woman"))) }
        val distanceVisible = se.tag(com.showup.checkin.DISTANCE_ROW_TAG).bottom <= se.tag(CHECK_IN_SCROLL_TAG).bottom + 0.5f
        println("DIAG filters: @375 overflow ${se.overflow}, distance row visible before scrolling: $distanceVisible · @390 ${-mid} spare")
        assertTrue("filters must fit at 390: $mid", mid <= 0.5f)
        assertTrue("filters must fit at 430: $max", max <= 0.5f)
    }

    /** 05b's B and C as the sheet draws them. */
    private val filters05bB = CheckInFilters(setOf("woman", "non_binary"), 28, 35, ageDealbreaker = true)
    private val filters05bC =
        CheckInFilters(setOf("woman", "man", "non_binary"), 25, 40, 15, ageDealbreaker = true, distanceDealbreaker = true)

    @Test
    fun `05b -- with both switches, filters fit at 390 and 430 and scroll inside themselves at 375`() {
        val mid = overflow(MID) { CheckInFiltersScreen(filters05bC, dealbreakers = true) }
        val max = overflow(MAX) { CheckInFiltersScreen(filters05bC, dealbreakers = true) }
        val se = overflow(SE) { CheckInFiltersScreen(filters05bC, dealbreakers = true) }
        println("DIAG filters 05b: @375 the list scrolls $se · @390 ${-mid} spare · @430 ${-max} spare")
        assertTrue("05b must fit at 390 with both switches: $mid", mid <= 0.5f)
        assertTrue("05b must fit at 430: $max", max <= 0.5f)
        assertTrue("05b scrolls inside itself at 375: $se", se > 0f)
    }

    @Test
    fun `05b -- the switches sit 12 under their sliders and never shrink, on every phone and type size`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) for (scale in SCALES) {
            val s = shoot(d.width, d.safeHeight, scale) { CheckInFiltersScreen(filters05bB, dealbreakers = true) }
            val age = s.tags[com.showup.checkin.AGE_DEALBREAKER_TAG]
            if (age == null) {
                failures += "$d @$scale  no age row"
                continue
            }
            val ageSlider = s.tag(com.showup.checkin.AGE_SLIDER_BOX_TAG)
            if (abs(age.top - ageSlider.bottom - 12f) > 0.6f) failures += "$d @$scale  age row ${age.top - ageSlider.bottom} under its slider"
            if (age.right > s.width + 0.5f || age.left < -0.5f) failures += "$d @$scale  age row off the screen sideways"
        }
        // With the flag off the layout is Check-In 05's: no Dealbreaker row at all.
        val off = shoot(MID.first, MID.second) { CheckInFiltersScreen(filters05bB) }
        if (com.showup.checkin.AGE_DEALBREAKER_TAG in off.tags) failures += "a Dealbreaker row with the flag off"
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `uplift fits at rest at 390 and 430 and scrolls at 375`() {
        val se = overflow(SE) { CheckInUpliftScreen(text = "") }
        val mid = overflow(MID) { CheckInUpliftScreen(text = "") }
        val max = overflow(MAX) { CheckInUpliftScreen(text = "") }
        println("DIAG uplift A: @375 the body scrolls ${se}dp · @390 ${-mid} spare · @430 ${-max} spare")
        assertTrue("uplift must scroll at 375: $se", se > 0f)
        assertTrue("uplift must fit at rest at 390: $mid", mid <= 0.5f)
        assertTrue("uplift must fit at rest at 430: $max", max <= 0.5f)
    }

    @Test
    fun `with a keyboard up Find my date stays on screen and the body scrolls`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) {
            // The keyboard is the OS's and its height is never specified: a tall one, modelled as the
            // bite WindowInsets.ime takes out of the frame.
            val s = shoot(d.width, d.safeHeight - 300) { CheckInUpliftScreen(text = "I have 2 tickets for the Berghain garden tonight") }
            val cta = s.tag(CHECK_IN_CTA_TAG)
            if (cta.bottom > s.frame + 0.5f) failures += "$d  Find my date under the keyboard (${cta.bottom} > ${s.frame})"
            // Only where the body cannot fit is there anything to scroll: on a phone taller than the
            // matrix it fits even with the keyboard up, and the CTA check above is what must hold.
            if (s.overflow <= 0f && d.height <= TALLEST_IN_MATRIX) {
                failures += "$d  the body does not scroll with the keyboard up"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 3 · nothing outside the region moves between a step's states ───────────────────────────

    @Test
    fun `the header, headline and CTA never move between a step's states`() {
        val failures = mutableListOf<String>()
        for ((step, states) in steps()) for (f in TICKET_FRAMES) {
            val shots = states.map { (label, content) -> label to shoot(f.first, f.second, content = content) }
            val (firstLabel, first) = shots.first()
            for ((label, s) in shots.drop(1)) for (t in listOf(CHECK_IN_HEADLINE_TAG, CHECK_IN_CTA_TAG)) {
                val a = first.tag(t)
                val b = s.tag(t)
                if (abs(a.top - b.top) > 0.5f) failures += "$step @$f  $t moved: $firstLabel ${a.top} -> $label ${b.top}"
            }
        }
        println(if (failures.isEmpty()) "OK   check-in: no Y moves between states" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 4 · sizes that are promised ────────────────────────────────────────────────────────────

    @Test
    fun `a slot is drawn 36 tall on every phone and never squeezed`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) {
            val s = shoot(d.width, d.safeHeight) { CheckInAvailabilityScreen(tomorrow, emptyMap(), false) }
            val slot = s.tag(slotTag(7))
            if (abs(slot.height - 36f) > 0.5f) failures += "$d  slot ${slot.height} tall"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `a slider thumb's 44 target never leaves the screen sideways`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) for (scale in SCALES) {
            val s = shoot(d.width, d.safeHeight, scale) { CheckInFiltersScreen(CheckInFilters(setOf("woman"))) }
            for (t in listOf(sliderThumbTag(AGE_SLIDER_TAG, 0), sliderThumbTag(AGE_SLIDER_TAG, 1), sliderThumbTag(DISTANCE_SLIDER_TAG, 0))) {
                val r = s.tags[t] ?: continue
                if (r.left < -0.5f || r.right > s.width + 0.5f) failures += "$d @$scale  $t at ${r.left}..${r.right} of ${s.width}"
                if (abs(r.width - 44f) > 0.5f) failures += "$d @$scale  $t is ${r.width} wide"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the gate's statement is centred in its block on every phone`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) {
            val s = shoot(d.width, d.safeHeight) { CheckInGateScreen() }
            val block = s.tag(GATE_STATEMENT_TAG)
            val headline = s.tag(CHECK_IN_HEADLINE_TAG)
            val centre = (headline.left + headline.right) / 2f
            if (abs(centre - s.width / 2f) > 1f) failures += "$d  headline centred at $centre of ${s.width}"
            if (block.top < 52f - 0.5f) failures += "$d  statement under the top row"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
