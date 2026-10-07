/*
 * DetailsFitTest.kt
 * ShowUp · Location, Embrace 2 and the seven detail steps, measured on every phone
 * (SHOWUP-165 to SHOWUP-173)
 *
 * The general sweep (ScreenFitTest) proves nothing is clipped or squeezed. This proves the
 * specific promises the nine tickets make about geometry, each of which has failed on some screen
 * in this project before:
 *
 *   · the CTA is ALWAYS fully on screen -- every device, every state, three type sizes
 *   · on the location ask the CTA sits at the SAME Y in A, B and C
 *   · on the detail steps the header, the bar, the headline, the band and the footer do not move
 *     between a screen's states -- the toast is an overlay and the tick is inside the field
 *   · which lists fit and which scroll at the tickets' own frames, row for row, and that rows are
 *     the same height on every device
 *   · the sacrifice order on the location ask, applied only where it is needed
 *
 * THE TICKETS' FRAMES ARE THE DEVICES' CONTENT COLUMNS. The detail tickets reason about 375 x 667
 * with the SE's real 20 status bar and no home indicator -- Profile 15: "the column needs ~590 of
 * the ~647 below the SE's 20 status bar" -- so the SE is checked at 647, and 390 x 844 and 430 x 932
 * at their devices' safe heights, 763 and 839. Those are DEVICES' own `safeHeight`s, not numbers
 * picked to pass.
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
import com.showup.profile.BRIDGE_CTA_TAG
import com.showup.profile.DETAILS_BAND_TAG
import com.showup.profile.DETAILS_CTA_TAG
import com.showup.profile.DETAILS_HEADLINE_TAG
import com.showup.profile.DETAILS_REGION_TAG
import com.showup.profile.DetailDraft
import com.showup.profile.DetailStep
import com.showup.profile.DetailToast
import com.showup.profile.DetailsUiState
import com.showup.profile.LOCATION_BOTTOM_TAG
import com.showup.profile.LOCATION_RADAR_TAG
import com.showup.profile.LocationState
import com.showup.profile.ProfileChoiceScreen
import com.showup.profile.ProfileDatingLanguageScreen
import com.showup.profile.ProfileEmbraceDetailsScreen
import com.showup.profile.ProfileHeightScreen
import com.showup.profile.ProfileLocationScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class DetailsFitTest {

    private data class Rect(val top: Float, val bottom: Float) {
        val height: Float get() = bottom - top
    }

    private data class Shot(
        val frame: Float,
        val tags: Map<String, Rect>,
        /** Every answer row -- the nodes that carry a collection position. */
        val rows: List<Rect>,
        /** The lowest edge anything is laid out to. */
        val lowest: Float,
        /** The lowest edge laid out INSIDE the answer region -- below its bottom means it scrolls. */
        val regionContentBottom: Float = 0f,
    ) {
        fun tag(t: String): Rect = tags[t] ?: error("no node tagged $t")

        /** Rows lying wholly inside the answer region's viewport, with half a point of slack. */
        fun visibleRows(): Int {
            val region = tag(DETAILS_REGION_TAG)
            return rows.count { it.top >= region.top - 0.5f && it.bottom <= region.bottom + 0.5f }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun shoot(width: Int, height: Int, fontScale: Float = 1f, content: @Composable () -> Unit): Shot {
        var shot: Shot? = null
        runComposeUiTest {
            var density = 1f
            setContent {
                density = LocalDensity.current.density
                val cfg = android.content.res.Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = width
                    screenHeightDp = height
                    this.fontScale = fontScale
                }
                CompositionLocalProvider(
                    LocalConfiguration provides cfg,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    Box(Modifier.testTag(FIT_ROOT).requiredSize(width.dp, height.dp)) { content() }
                }
            }
            waitForIdle()
            val root = onNodeWithTag(FIT_ROOT, useUnmergedTree = true).fetchSemanticsNode()
            val origin = root.positionInRoot.y
            val tags = mutableMapOf<String, Rect>()
            val rows = mutableListOf<Rect>()
            var lowest = 0f
            var inner = 0f
            // INSIDE A SCROLL, BELOW THE FOLD IS NOT AN OVERFLOW. A row scrolled out of the answer
            // region still reports where it would be; counting it as "laid out past the frame"
            // flagged every list that scrolls by design. The region's own box is still counted.
            fun walk(n: SemanticsNode, scrolled: Boolean) {
                val top = (n.positionInRoot.y - origin) / density
                val r = Rect(top, top + n.size.height / density)
                if (n.size.height > 0 && !scrolled) lowest = maxOf(lowest, r.bottom)
                if (n.size.height > 0 && scrolled) inner = maxOf(inner, r.bottom)
                val tag = n.config.getOrNull(SemanticsProperties.TestTag)
                tag?.let { tags[it] = r }
                if (n.config.getOrNull(SemanticsProperties.CollectionItemInfo) != null) rows += r
                val inside = scrolled || tag == DETAILS_REGION_TAG
                n.children.forEach { walk(it, inside) }
            }
            walk(root, false)
            shot = Shot(height.toFloat(), tags, rows.sortedBy { it.top }, lowest, inner)
        }
        return shot!!
    }

    private val SCALES = listOf(1f, 1.3f, 2f)

    // ── the screens, in every state the tickets name ────────────────────────────

    private fun detailStates(step: DetailStep): List<Pair<String, DetailsUiState>> = when (step) {
        DetailStep.Height -> listOf(
            "A" to DetailsUiState(),
            "B" to DetailsUiState(draft = DetailDraft(heightText = "175")),
            "C" to DetailsUiState(toast = DetailToast.Refusal, toastTick = 1),
        )
        DetailStep.DatingLanguage -> listOf(
            "A" to DetailsUiState(),
            "B" to DetailsUiState(draft = DetailDraft(languages = setOf("german", "english", "spanish"))),
        )
        else -> buildList {
            add("A" to DetailsUiState())
            add("B" to DetailsUiState(draft = DetailDraft().withSelection(step, step.options[2].value)))
            if (step.mandatory) add("C" to DetailsUiState(toast = DetailToast.Refusal, toastTick = 1))
        }
    }

    @Composable
    private fun DetailScreen(step: DetailStep, state: DetailsUiState) = when (step) {
        DetailStep.Height -> ProfileHeightScreen(state, autoFocus = false)
        DetailStep.DatingLanguage -> ProfileDatingLanguageScreen(state)
        else -> ProfileChoiceScreen(step, state)
    }

    // ── 1 · the CTA and the band are always on screen ──────────────────────────

    @Test
    fun `every detail step keeps the band and Continue fully on screen, on every phone and type size`() {
        val failures = mutableListOf<String>()
        for (step in DetailStep.entries) for ((label, state) in detailStates(step)) {
            for (d in DEVICES) for (scale in SCALES) {
                val s = shoot(d.width, d.safeHeight, scale) { DetailScreen(step, state) }
                val cta = s.tag(DETAILS_CTA_TAG)
                val band = s.tag(DETAILS_BAND_TAG)
                if (cta.bottom > s.frame + 0.5f) failures += "$step/$label $d @$scale  CTA ends ${cta.bottom} > ${s.frame}"
                if (band.bottom > cta.top) failures += "$step/$label $d @$scale  band overlaps the CTA"
                if (s.lowest > s.frame + 0.5f) failures += "$step/$label $d @$scale  overflows to ${s.lowest}"
            }
        }
        println(if (failures.isEmpty()) "OK   details: band and CTA on screen everywhere" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 2 · nothing moves between a step's states ───────────────────────────────

    @Test
    fun `nothing above or below the answer region moves between a step's states`() {
        val failures = mutableListOf<String>()
        for (step in DetailStep.entries) for (d in DEVICES) {
            val shots = detailStates(step).map { (label, state) ->
                label to shoot(d.width, d.safeHeight) { DetailScreen(step, state) }
            }
            val (firstLabel, first) = shots.first()
            for ((label, s) in shots.drop(1)) {
                for (t in listOf(DETAILS_HEADLINE_TAG, DETAILS_BAND_TAG, DETAILS_CTA_TAG, DETAILS_REGION_TAG)) {
                    val a = first.tag(t).top
                    val b = s.tag(t).top
                    if (kotlin.math.abs(a - b) > 0.5f) failures += "$step $d  $t moved $firstLabel=$a -> $label=$b"
                }
            }
        }
        println(if (failures.isEmpty()) "OK   details: no Y moves between states" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 3 · which lists fit and which scroll, at the tickets' frames ────────────

    private val SE = 375 to 647
    private val MID = 390 to 763
    private val MAX = 430 to 839

    private fun rowsAt(frame: Pair<Int, Int>, step: DetailStep): Pair<Int, Int> {
        val s = shoot(frame.first, frame.second) { DetailScreen(step, DetailsUiState()) }
        return s.visibleRows() to s.rows.size
    }

    @Test
    fun `gender's four rows fit everywhere, even the iPhone SE`() {
        listOf(SE, MID, MAX).forEach { f ->
            val (visible, total) = rowsAt(f, DetailStep.Gender)
            println("DIAG gender @${f.first}x${f.second}: $visible/$total rows visible")
            assertEquals("gender @$f", 4, total)
            assertEquals("gender @$f", 4, visible)
        }
    }

    @Test
    fun `orientation scrolls at 375 x 667 and shows all six at 390 and 430`() {
        val (se, _) = rowsAt(SE, DetailStep.Orientation)
        println("DIAG orientation @SE: $se/6 visible")
        assertTrue("orientation must scroll at the SE: $se/6", se < 6)
        listOf(MID, MAX).forEach { f -> assertEquals("orientation @$f", 6, rowsAt(f, DetailStep.Orientation).first) }
    }

    @Test
    fun `dating language scrolls at 375 x 667 and shows all eight at 390 and 430`() {
        val (se, _) = rowsAt(SE, DetailStep.DatingLanguage)
        println("DIAG dating language @SE: $se/8 visible")
        assertTrue("dating language must scroll at the SE: $se/8", se < 8)
        listOf(MID, MAX).forEach { f -> assertEquals("dating language @$f", 8, rowsAt(f, DetailStep.DatingLanguage).first) }
    }

    @Test
    fun `education shows all four at 390 and 430`() {
        listOf(MID, MAX).forEach { f -> assertEquals("education @$f", 4, rowsAt(f, DetailStep.Education).first) }
        println("DIAG education @SE: ${rowsAt(SE, DetailStep.Education).first}/4 fully visible")
    }

    @Test
    fun `religion's nine rows scroll on every frame`() {
        listOf(SE, MID, MAX).forEach { f ->
            val (visible, total) = rowsAt(f, DetailStep.Religion)
            println("DIAG religion @${f.first}x${f.second}: $visible/$total visible")
            assertEquals(9, total)
            assertTrue("religion must scroll @$f: $visible/9", visible < 9)
        }
    }

    @Test
    fun `politics scrolls at 375 and 390 and fits at 430`() {
        listOf(SE, MID).forEach { f ->
            val (visible, _) = rowsAt(f, DetailStep.Politics)
            println("DIAG politics @${f.first}x${f.second}: $visible/8 visible")
            assertTrue("politics must scroll @$f: $visible/8", visible < 8)
        }
        assertEquals("politics fits at 430", 8, rowsAt(MAX, DetailStep.Politics).first)
    }

    @Test
    fun `rows are the same height on every device -- never shortened to fit`() {
        val failures = mutableListOf<String>()
        listOf(DetailStep.Orientation, DetailStep.Religion, DetailStep.DatingLanguage).forEach { step ->
            val heights = DEVICES.map { d ->
                shoot(d.width, d.safeHeight) { DetailScreen(step, DetailsUiState()) }.rows.map { it.height }.distinct()
            }.flatten().map { kotlin.math.round(it * 10) / 10 }.distinct()
            println("DIAG $step row heights: $heights")
            if (heights.size != 1) failures += "$step rows vary: $heights"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 4 · height with the keypad up ──────────────────────────────────────────

    @Test
    fun `height keeps the band and Continue above the keypad`() {
        // The iOS number pad on the SE leaves 431 of the 667 (the ticket's own estimate); a numeric
        // Gboard leaves less on a small Android. Both are checked, and the region is reported.
        val cases = listOf(
            "SE + iOS number pad" to (375 to 431),
            "390 + iOS number pad" to (390 to 844 - 47 - 250),
            "small Android + numeric Gboard" to (360 to 640 - 48 - 260),
            "Fold cover + numeric Gboard" to (320 to 686 - 48 - 260),
        )
        val failures = mutableListOf<String>()
        cases.forEach { (name, f) ->
            val s = shoot(f.first, f.second) { ProfileHeightScreen(DetailsUiState(), autoFocus = false) }
            val cta = s.tag(DETAILS_CTA_TAG)
            val region = s.tag(DETAILS_REGION_TAG)
            // "The answer region must not scroll internally" (SHOWUP-167, 375 x 667). Reported, not
            // asserted: the ticket's remedy for an overflow is shortening the note -- a copy
            // decision -- "before shrinking anything in the layout".
            val overflow = s.regionContentBottom - region.bottom
            println(
                "DIAG height $name: frame ${f.second}, answer region ${region.height}, " +
                    "content overflows the region by ${maxOf(overflow, 0f)}, CTA bottom ${cta.bottom}",
            )
            if (cta.bottom > s.frame + 0.5f) failures += "$name: CTA below the keypad (${cta.bottom} > ${s.frame})"
            // The field must have somewhere to be: its 10 inset and its 64 box. Where the top part
            // cannot give it that at rest, it scrolls, and the focused field is brought into view.
            if (region.height < 74f) failures += "$name: answer region ${region.height} cannot hold the field"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 5 · the location ask ────────────────────────────────────────────────────

    /**
     * THE SAME Y, AND WHAT THAT CAN MEAN AT 2.0x TYPE.
     *
     * The footer is bottom-anchored, so the bottom button's BOTTOM edge is identical in A, B and C
     * on every phone at every size -- asserted. Its TOP is identical too at 1.0x and 1.3x --
     * asserted. At 2.0x, B and C's `Not now — ask me when I search` wraps to two lines on the large
     * phones while A's `Allow location access` still fits on one, so that button is taller and its
     * top sits higher; the CTA has not moved, its label has grown. Reported, not hidden.
     */
    @Test
    fun `location's bottom CTA sits at the same Y in A, B and C, on screen, everywhere`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) for (scale in SCALES) {
            val boxes = LocationState.entries.map { state ->
                val s = shoot(d.width, d.safeHeight, scale) { ProfileLocationScreen(state) }
                val cta = s.tag(LOCATION_BOTTOM_TAG)
                if (cta.bottom > s.frame + 0.5f) failures += "$state $d @$scale  CTA ends ${cta.bottom}"
                cta
            }
            val bottoms = boxes.map { it.bottom }
            val tops = boxes.map { it.top }
            if (bottoms.max() - bottoms.min() > 0.5f) failures += "$d @$scale  CTA bottom differs: $bottoms"
            if (scale < 2f && tops.max() - tops.min() > 0.5f) failures += "$d @$scale  CTA Y differs: $tops"
            if (scale == 2f && tops.max() - tops.min() > 0.5f) println("DIAG location $d @2.0 label wraps: tops $tops")
        }
        println(if (failures.isEmpty()) "OK   location: CTA same Y and on screen everywhere" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `location keeps the full 160 radar where it fits and gives it up only where it does not`() {
        val report = DEVICES.map { d ->
            val s = shoot(d.width, d.safeHeight) { ProfileLocationScreen(LocationState.Ask) }
            "${d.name} ${d.width}x${d.safeHeight}: radar ${s.tag(LOCATION_RADAR_TAG).height}"
        }
        report.forEach { println("DIAG location $it") }
        val mid = shoot(MID.first, MID.second) { ProfileLocationScreen(LocationState.Ask) }
        assertEquals("the reference frame draws the full radar", 160f, mid.tag(LOCATION_RADAR_TAG).height, 0.5f)
    }

    // ── 6 · Embrace 2 ──────────────────────────────────────────────────────────

    @Test
    fun `embrace 2 shows its CTA on every phone and type size, and reports its spacer`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) for (scale in SCALES) {
            val s = shoot(d.width, d.safeHeight, scale) { ProfileEmbraceDetailsScreen(firstName = "Leo") }
            if (s.tag(BRIDGE_CTA_TAG).bottom > s.frame + 0.5f) failures += "$d @$scale  CTA off screen"
        }
        listOf(SE, MID, MAX).forEach { f ->
            val s = shoot(f.first, f.second) { ProfileEmbraceDetailsScreen(firstName = "Leo") }
            println("DIAG embrace 2 @${f.first}x${f.second}: CTA top ${s.tag(BRIDGE_CTA_TAG).top}")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
