/*
 * PickFitTest.kt
 * ShowUp · Interests, Life and Future, measured on every phone (SHOWUP-174 to SHOWUP-176)
 *
 * The general sweep (ScreenFitTest) proves nothing is clipped or squeezed. This proves the
 * geometry the three tickets promise:
 *
 *   · Continue and the footer are ALWAYS fully on screen -- every device, every state, three type
 *     sizes -- and nothing is laid out past the frame
 *   · the header, bar, headline, counter or sub, region and footer never move between a screen's
 *     states ("only the answer region moves between frames")
 *   · Interests' region scrolls on every device; Life fits at 390 and 430 and scrolls at 375;
 *     Future scrolls at 390 and 375 and fits at 430
 *   · a chip is 38 and a pill 34 on every device, never shortened to fit, and picking any chip --
 *     or every chip -- re-wraps no row on any device ("no element changes Y on selection")
 *
 * THE TICKETS' FRAMES ARE THE DEVICES' CONTENT COLUMNS, as in DetailsFitTest: 375 x 667 with the
 * SE's 20 status bar (647), and 390 x 844 / 430 x 932 at their devices' safe heights (763, 839).
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
import com.showup.profile.DETAILS_CTA_TAG
import com.showup.profile.DetailStep
import com.showup.profile.DetailsUiState
import com.showup.profile.FUTURE_B
import com.showup.profile.ProfileChoiceScreen
import com.showup.profile.ProfileDatingLanguageScreen
import com.showup.profile.ProfileHeightScreen
import com.showup.profile.INTERESTS_B
import com.showup.profile.INTERESTS_C
import com.showup.profile.INTERESTS_D
import com.showup.profile.InterestSection
import com.showup.profile.InterestsDraft
import com.showup.profile.LIFE_B
import com.showup.profile.LifeQuestion
import com.showup.profile.LifestyleField
import com.showup.profile.PICK_ABOVE_TAG
import com.showup.profile.PICK_CTA_TAG
import com.showup.profile.PICK_FOOTER_TAG
import com.showup.profile.PICK_HEADLINE_TAG
import com.showup.profile.PICK_REGION_TAG
import com.showup.profile.PickStep
import com.showup.profile.PickUiState
import com.showup.profile.ProfileInterestsScreen
import com.showup.profile.ProfileLifeAheadScreen
import com.showup.profile.ProfileLifeNowScreen
import com.showup.profile.interestChipTag
import com.showup.profile.lifePillTag
import com.showup.profile.questions
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class PickFitTest {

    private data class Rect(val top: Float, val bottom: Float, val left: Float = 0f, val width: Float = 0f) {
        val height: Float get() = bottom - top
    }

    private data class Shot(
        val frame: Float,
        val tags: Map<String, Rect>,
        /** The lowest edge anything is laid out to OUTSIDE the scrolling region. */
        val lowest: Float,
        /** The lowest edge laid out INSIDE the region -- below its bottom means it scrolls. */
        val regionContentBottom: Float,
        /** The CTA's own label: how many lines it was laid out on, and how wide it is. */
        val ctaLabelLines: Int = 0,
        val ctaLabelWidth: Float = 0f,
        /** The whole CTA's width: the label, the 14 gap and the 56 circle when nothing is squeezed. */
        val ctaWidth: Float = 0f,
    ) {
        fun tag(t: String): Rect = tags[t] ?: error("no node tagged $t")

        /** How far the region's content runs past its viewport; negative is slack. */
        val overflow: Float get() = regionContentBottom - tag(PICK_REGION_TAG).bottom
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
            var lowest = 0f
            var inner = 0f
            var labelLines = 0
            var labelWidth = 0f
            var ctaWidth = 0f
            fun walk(n: SemanticsNode, scrolled: Boolean) {
                // THE CTA'S LABEL, measured: a NextButton squeezed by its row breaks "Continue" over
                // two lines and crushes its circle, and no height check sees either.
                val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                if (text == "Continue") {
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)
                        ?.action?.invoke(results)
                    results.firstOrNull()?.let { labelLines = it.lineCount }
                    labelWidth = n.size.width / density
                }
                val ctaTag = n.config.getOrNull(SemanticsProperties.TestTag)
                if ((ctaTag == PICK_CTA_TAG || ctaTag == DETAILS_CTA_TAG) && n.layoutInfo.isPlaced) {
                    ctaWidth = n.size.width / density
                }
                val top = (n.positionInRoot.y - origin) / density
                val r = Rect(
                    top, top + n.size.height / density,
                    left = (n.positionInRoot.x - root.positionInRoot.x) / density,
                    width = n.size.width / density,
                )
                if (n.size.height > 0 && !scrolled) lowest = maxOf(lowest, r.bottom)
                if (n.size.height > 0 && scrolled) inner = maxOf(inner, r.bottom)
                val tag = n.config.getOrNull(SemanticsProperties.TestTag)
                tag?.let { if (n.layoutInfo.isPlaced) tags[it] = r }
                val inside = scrolled || tag == PICK_REGION_TAG
                n.children.forEach { walk(it, inside) }
            }
            walk(root, false)
            shot = Shot(height.toFloat(), tags, lowest, inner, labelLines, labelWidth, ctaWidth)
        }
        return shot!!
    }

    private val SCALES = listOf(1f, 1.3f, 2f)

    // ── the screens, in every state the tickets name ────────────────────────────

    private fun states(step: PickStep): List<Pair<String, PickUiState>> = when (step) {
        PickStep.Interests -> listOf(
            "A" to PickUiState(),
            "B" to INTERESTS_B,
            "C" to INTERESTS_C,
            "D" to INTERESTS_D,
            "save failed" to INTERESTS_B.copy(saveFailed = true),
        )
        PickStep.LifeNow -> listOf("A" to PickUiState(), "B" to LIFE_B, "save failed" to LIFE_B.copy(saveFailed = true))
        PickStep.LifeAhead -> listOf("A" to PickUiState(), "B" to FUTURE_B, "save failed" to FUTURE_B.copy(saveFailed = true))
    }

    @Composable
    private fun Screen(step: PickStep, state: PickUiState) = when (step) {
        PickStep.Interests -> ProfileInterestsScreen(state)
        PickStep.LifeNow -> ProfileLifeNowScreen(state)
        PickStep.LifeAhead -> ProfileLifeAheadScreen(state)
    }

    // ── 1 · Continue and the footer are always on screen ───────────────────────

    @Test
    fun `every state keeps the footer and Continue fully on screen, on every phone and type size`() {
        val failures = mutableListOf<String>()
        for (step in PickStep.entries) for ((label, state) in states(step)) {
            for (d in DEVICES) for (scale in SCALES) {
                val s = shoot(d.width, d.safeHeight, scale) { Screen(step, state) }
                val cta = s.tag(PICK_CTA_TAG)
                val footer = s.tag(PICK_FOOTER_TAG)
                val region = s.tag(PICK_REGION_TAG)
                if (cta.bottom > s.frame + 0.5f) failures += "$step/$label $d @$scale  CTA ends ${cta.bottom} > ${s.frame}"
                if (footer.bottom > s.frame + 0.5f) failures += "$step/$label $d @$scale  footer ends ${footer.bottom}"
                // At the default type the region ends where the footer starts. Larger type may engage the
                // floor, where the top part scrolls as one and the region is part of what scrolls.
                if (scale == 1f && region.bottom > footer.top + 0.5f) {
                    failures += "$step/$label $d @$scale  region runs under the footer"
                }
                if (s.lowest > s.frame + 0.5f) failures += "$step/$label $d @$scale  overflows to ${s.lowest}"
                if (region.height < 79.5f) failures += "$step/$label $d @$scale  region squeezed to ${region.height}"
            }
        }
        println(if (failures.isEmpty()) "OK   picks: footer and CTA on screen everywhere" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * CONTINUE IS NEVER SQUEEZED. At the largest system font on a 320 phone the footer row used to
     * measure `Skip for now` first and hand the CTA what was left: "Continue" broke over two lines
     * and its 56 circle was crushed to a sliver, while every height-based check stayed green. Now
     * the CTA is measured first and the skip link wraps instead -- so the label is one line and the
     * button is its full label + 14 + 56 wide, everywhere.
     */
    @Test
    fun `Continue keeps its one-line label and its whole circle, on every phone and type size`() {
        val failures = mutableListOf<String>()
        for (step in PickStep.entries) for (d in DEVICES) for (scale in SCALES) {
            val s = shoot(d.width, d.safeHeight, scale) { Screen(step, PickUiState()) }
            if (s.ctaLabelLines != 1) failures += "$step $d @$scale  Continue on ${s.ctaLabelLines} lines"
            if (s.ctaWidth < s.ctaLabelWidth + 14f + 56f - 1f) {
                failures += "$step $d @$scale  CTA ${s.ctaWidth} wide for a ${s.ctaLabelWidth} label"
            }
        }
        println(if (failures.isEmpty()) "OK   picks: Continue never squeezed" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The same promise for the seven detail steps, whose footer is the same two controls. They had
     * the defect before SkipContinueRow; this is what keeps it fixed there too.
     */
    @Test
    fun `the detail steps' Continue keeps its label and circle too`() {
        val failures = mutableListOf<String>()
        for (step in DetailStep.entries) for (d in DEVICES) for (scale in SCALES) {
            val s = shoot(d.width, d.safeHeight, scale) {
                when (step) {
                    DetailStep.Height -> ProfileHeightScreen(DetailsUiState(), autoFocus = false)
                    DetailStep.DatingLanguage -> ProfileDatingLanguageScreen()
                    else -> ProfileChoiceScreen(step)
                }
            }
            if (s.ctaLabelLines != 1) failures += "$step $d @$scale  Continue on ${s.ctaLabelLines} lines"
            if (s.ctaWidth < s.ctaLabelWidth + 14f + 56f - 1f) {
                failures += "$step $d @$scale  CTA ${s.ctaWidth} wide for a ${s.ctaLabelWidth} label"
            }
        }
        println(if (failures.isEmpty()) "OK   details: Continue never squeezed" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 2 · nothing outside the region moves between a screen's states ──────────

    @Test
    fun `nothing above or below the answer region moves between a screen's states`() {
        val failures = mutableListOf<String>()
        for (step in PickStep.entries) for (d in DEVICES) {
            val shots = states(step).map { (label, state) -> label to shoot(d.width, d.safeHeight) { Screen(step, state) } }
            val (firstLabel, first) = shots.first()
            for ((label, s) in shots.drop(1)) {
                for (t in listOf(PICK_HEADLINE_TAG, PICK_ABOVE_TAG, PICK_REGION_TAG, PICK_FOOTER_TAG, PICK_CTA_TAG)) {
                    val a = first.tag(t)
                    val b = s.tag(t)
                    if (abs(a.top - b.top) > 0.5f || abs(a.bottom - b.bottom) > 0.5f) {
                        failures += "$step $d  $t moved $firstLabel=${a.top}..${a.bottom} -> $label=${b.top}..${b.bottom}"
                    }
                }
            }
        }
        println(if (failures.isEmpty()) "OK   picks: no Y moves between states" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ── 3 · which screens fit and which scroll, at the tickets' frames ─────────

    private val SE = 375 to 647
    private val MID = 390 to 763
    private val MAX = 430 to 839

    private fun overflowAt(frame: Pair<Int, Int>, step: PickStep, state: PickUiState = PickUiState()): Float =
        shoot(frame.first, frame.second) { Screen(step, state) }.overflow

    @Test
    fun `Interests' region scrolls on every phone`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) {
            val over = overflowAt(d.width to d.safeHeight, PickStep.Interests)
            if (d.inAcceptanceCriteria) println("DIAG interests @$d: content runs ${over}dp past the region")
            if (over <= 0f) failures += "interests fits at $d (slack ${-over}) -- the ticket says it scrolls everywhere"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `Life fits at 390 and 430, and scrolls at 375 x 667`() {
        for (state in listOf(PickUiState(), LIFE_B)) {
            val se = overflowAt(SE, PickStep.LifeNow, state)
            val mid = overflowAt(MID, PickStep.LifeNow, state)
            val max = overflowAt(MAX, PickStep.LifeNow, state)
            println("DIAG life @SE overflow $se · @390 slack ${-mid} (ticket: about 12) · @430 slack ${-max}")
            assertTrue("life must scroll at the SE: $se", se > 0f)
            assertTrue("life must fit at 390: overflow $mid", mid <= 0.5f)
            assertTrue("life must fit at 430: overflow $max", max <= 0.5f)
        }
    }

    @Test
    fun `Future scrolls at 390 and 375, and fits at 430`() {
        for (state in listOf(PickUiState(), FUTURE_B)) {
            val se = overflowAt(SE, PickStep.LifeAhead, state)
            val mid = overflowAt(MID, PickStep.LifeAhead, state)
            val max = overflowAt(MAX, PickStep.LifeAhead, state)
            println("DIAG future @SE overflow $se · @390 overflow $mid (ticket: about 30) · @430 slack ${-max}")
            assertTrue("future must scroll at the SE: $se", se > 0f)
            assertTrue("future must scroll at 390: $mid", mid > 0f)
            assertTrue("future must fit at 430: overflow $max", max <= 0.5f)
        }
    }

    // ── 4 · chips and pills are never shortened, and selecting moves nothing ────

    @Test
    fun `a chip is 38 and a pill 34 on every phone, at the default type`() {
        val failures = mutableListOf<String>()
        for (d in DEVICES) {
            val interests = shoot(d.width, d.safeHeight) { ProfileInterestsScreen(INTERESTS_B) }
            LifestyleField.entries.flatMap { it.options }.forEach { option ->
                val h = interests.tag(interestChipTag(option.value)).height
                if (abs(h - 38f) > 0.5f) failures += "$d chip ${option.label} is $h"
            }
            val life = shoot(d.width, d.safeHeight) { ProfileLifeNowScreen(LIFE_B) }
            PickStep.LifeNow.questions.forEach { q ->
                q.options.forEach { option ->
                    val h = life.tag(lifePillTag(q, option.value)).height
                    if (abs(h - 34f) > 0.5f) failures += "$d pill ${option.label} is $h"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `picking How I live chips moves no chip at the three ticket sizes`() {
        val failures = mutableListOf<String>()
        for (f in listOf(SE, MID, MAX)) {
            val a = shoot(f.first, f.second) { ProfileInterestsScreen(PickUiState()) }
            val b = shoot(f.first, f.second) { ProfileInterestsScreen(INTERESTS_C) }
            LifestyleField.entries.flatMap { it.options }.forEach { option ->
                val t = interestChipTag(option.value)
                if (abs(a.tag(t).top - b.tag(t).top) > 0.5f) {
                    failures += "${f.first}x${f.second}  ${option.label} moved ${a.tag(t).top} -> ${b.tag(t).top}"
                }
            }
        }
        println(if (failures.isEmpty()) "OK   picks: How I live selection moves nothing" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * "NO ELEMENT CHANGES Y ON SELECTION" (ticket 21), for every chip on every phone at two type sizes.
     *
     * Found 13 October 2026: the picked weight and border widened a chip by up to 5, and a single pick
     * re-wrapped a row on 14 of 17 phones. The chip now reserves its picked width in both states, so
     * this measures every chip resting and picked, then lays each group out the way FlowRow does (a
     * chip goes on the line if it fits with the 8 gap) -- once per single pick, and once with every
     * chip picked at the same time, which covers any combination. The simulation is first checked
     * against the real resting layout, so a mismatch fails as the harness, not as a finding.
     */
    @Test
    fun `picking any chip, or every chip, re-wraps no row on any phone`() {
        val allTags = InterestSection.entries.flatMap { it.options }.map { it.value }.toSet()
        val open = InterestSection.entries.map { it.sectionId }.toSet()
        val groups: List<List<String>> =
            LifestyleField.entries.map { f -> f.options.map { it.value } } +
                InterestSection.entries.map { sec -> sec.options.map { it.value } }
        val maxOpts = LifestyleField.entries.maxOf { it.options.size }
        val findings = mutableListOf<String>()
        for (d in DEVICES) for (scale in listOf(1f, 1.3f)) {
            val rest = shoot(d.width, d.safeHeight, scale) {
                ProfileInterestsScreen(PickUiState(interests = InterestsDraft(expanded = open)))
            }
            val picked = mutableMapOf<String, Float>()
            for (i in 0 until maxOpts) {
                val life = LifestyleField.entries.mapNotNull { f -> f.options.getOrNull(i)?.let { f.fieldId to it.value } }.toMap()
                val shot = shoot(d.width, d.safeHeight, scale) {
                    ProfileInterestsScreen(PickUiState(interests = InterestsDraft(life, if (i == 0) allTags else emptySet(), open)))
                }
                life.values.forEach { v -> picked[v] = shot.tag(interestChipTag(v)).width }
                if (i == 0) allTags.forEach { v -> picked[v] = shot.tag(interestChipTag(v)).width }
            }
            val firstLeft = rest.tag(interestChipTag(groups[0][0])).left
            val maxWidth = d.width - 2 * firstLeft
            fun rows(widths: List<Float>): List<Int> {
                var row = 0
                var x = -1f
                return widths.map { w ->
                    if (x < 0f) { x = w } else if (x + 8f + w <= maxWidth + 0.01f) { x += 8f + w } else { row++; x = w }
                    row
                }
            }
            for (g in groups) {
                val restW = g.map { rest.tag(interestChipTag(it)).width }
                val base = rows(restW)
                // The harness must reproduce the real resting rows first.
                val realTops = g.map { rest.tag(interestChipTag(it)).top }
                val realRows = realTops.map { t -> realTops.distinct().sorted().indexOfFirst { abs(it - t) < 0.5f } }
                if (base != realRows) { findings += "HARNESS $d @$scale ${g.first()}: sim $base real $realRows"; continue }
                val allMax = rows(g.map { picked.getValue(it) })
                if (allMax.last() != base.last()) findings += "ALLMAX $d @$scale ${g.first()}: ${base.last() + 1} rows -> ${allMax.last() + 1}"
                g.forEachIndexed { i, v ->
                    val w = restW.toMutableList().also { it[i] = picked.getValue(v) }
                    if (rows(w) != base) findings += "$d @$scale  picking $v re-wraps (+${picked.getValue(v) - restW[i]}dp)"
                }
            }
        }
        println(if (findings.isEmpty()) "OK   picks: no pick re-wraps a row anywhere" else findings.joinToString("\n"))
        assertTrue(findings.joinToString("\n"), findings.isEmpty())
    }

    /**
     * "Two lines at 390." Counted in line boxes, not compared to the CSS height: the shared headline
     * keeps the font's full ascent on its first line and descent on its last where CSS lets a tight
     * 1.12 line box clip into them, so every headline in the flow is about 4.8 taller than its line
     * boxes (two lines measure 72, not 67.2). That is one component's rule for every screen, not
     * something this screen decides.
     */
    @Test
    fun `Future's headline is two lines at 390`() {
        val s = shoot(MID.first, MID.second) { ProfileLifeAheadScreen() }
        val h = s.tag(PICK_HEADLINE_TAG).height
        val lineBox = 30f * 1.12f
        println("DIAG future headline @390: $h tall, ${h / lineBox} line boxes")
        assertTrue("future headline @390 is $h -- not two lines", h > 1.5f * lineBox && h < 2.5f * lineBox)
    }

    @Test
    fun `the region sizes at the ticket frames, for the record`() {
        for (f in listOf(SE, MID, MAX)) for (step in PickStep.entries) {
            val s = shoot(f.first, f.second) { Screen(step, PickUiState()) }
            println(
                "DIAG $step @${f.first}x${f.second}: headline ${s.tag(PICK_HEADLINE_TAG).height} · " +
                    "above ${s.tag(PICK_ABOVE_TAG).height} · region ${s.tag(PICK_REGION_TAG).height} · " +
                    "content ${s.regionContentBottom - s.tag(PICK_REGION_TAG).top} · footer ${s.tag(PICK_FOOTER_TAG).height}",
            )
        }
    }

    @Test
    fun `the pill tags name the question, so the three find-out-together pills are distinct`() {
        val tags = LifeQuestion.entries.flatMap { q -> q.options.map { lifePillTag(q, it.value) } }
        assertTrue(tags.size == tags.toSet().size)
    }
}
