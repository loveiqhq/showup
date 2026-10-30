/*
 * ProfileLocationScreen.kt
 * ShowUp · Profile 12 — Location permission ask (SHOWUP-165)
 *
 * THREE STATES, ONE COMPONENT, the ticket's own rule: A (ask), B (denied) and C (the phone's
 * Location switch off) come from one [LocationState] prop and share one column. B and C drop the
 * radar, the rows and the privacy note, and the spacer takes up the difference.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE SAME NAMED EXCEPTIONS AS 09, AND NOTHING NEW
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The ambient backdrop and the full-width sunset CTA are 09's recipe -- callouts ① and ⑧, violet on
 * the sheet -- so this is [WelcomeScaffold] exactly as 09 uses it: the Startup orbs at 0.32 / 0.28,
 * the 360 peach wash, gutter 28. "Do not carry either into any other screen."
 *
 * THE ABSENCES ARE THE DESIGN: no AppHeader, no StepProgress, no chevron, no swipe-back, no system
 * back, and on A no skip -- "the OS dialog's own decline is the way past, and it leads to B, which
 * has one". `audit/verify-profile.py` asserts them.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE CTA SITS AT THE SAME Y IN ALL THREE STATES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The footer is a band pinned to the bottom of the column, 6 above the gesture bar, in every state:
 * A's one button and B/C's bottom button occupy the same rectangle. Above it the content is
 * top-anchored with ONE flexible spacer (min 12) between it and the band.
 *
 * IT DOES NOT SCROLL, AND WHEN IT DOES NOT FIT IT SACRIFICES IN THE TICKET'S ORDER: "spacer ->
 * radar block 160 -> 120 -> row gap 14 -> 10 -> then come back to this ticket. Never shrink the
 * headline and never let it scroll." [LocationFit] is that order, chosen by measuring the content
 * against the space actually there. Past the last step -- a 320-wide fold at accessibility type --
 * the content scrolls under the pinned band rather than push the CTA off screen, the precedent 09
 * set and recorded (E20); the fit test reports every frame where that happens.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.designsystem.IconSizes
import com.showup.designsystem.Lavender
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.PrivacyNoteWash
import com.showup.designsystem.Purple
import com.showup.designsystem.RadarSlate
import com.showup.designsystem.RadarSlateDot
import com.showup.designsystem.Spacing
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.Tab
import com.showup.designsystem.TabBar
import com.showup.search.BackPill
import com.showup.search.SEARCH_TOP_BAR_TAG
import com.showup.search.SearchTopBar
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeBackdrop
import com.showup.welcome.WelcomeScaffold

/** Fit-harness anchors. */
internal const val LOCATION_FOOTER_TAG = "location-footer"
internal const val LOCATION_PRIMARY_TAG = "location-primary"
internal const val LOCATION_BOTTOM_TAG = "location-bottom"
internal const val LOCATION_CONTENT_TAG = "location-content"
internal const val LOCATION_RADAR_TAG = "location-radar"

/** A benefit row's parts, for the fit test that holds each pip level with its title. */
internal fun locationRowTag(index: Int, part: String) = "location-row-$index-$part"

/**
 * Every string on the screen, verbatim from the ticket -- the Android column of its platform table.
 * `›` is U+203A, the single right-pointing angle quote, never `>`; em dashes are spaced.
 */
internal object LocationCopy {
    const val A_LEAD = "Find people "
    const val A_EM = "nearby"
    const val A_TAIL = "."

    const val B_LEAD = "We can't find dates "
    const val B_EM = "without"
    const val B_TAIL = " location."

    const val C_LEAD = "Your phone has location "
    const val C_EM = "switched off"
    const val C_TAIL = "."

    const val ROW1 = "Set a search radius around your location"
    const val ROW2 = "We pick a fair halfway venue for you both"
    const val ROW2_SUB =
        "No planning, no home advantage — a busy public spot that's neutral ground for both of you."
    const val ROW3 = "Walking directions on the day"

    const val PRIVACY =
        "Your exact location is never shown on your profile. Other people only see the city you're " +
            "in and an approximate distance from themselves e.g. 1.5km."

    const val ALLOW = "Allow location access"
    const val OPEN_SETTINGS = "Open Settings"
    const val NOT_NOW = "Not now — ask me when I search"

    /** B's body: the shared sentence, then Android's platform sentence with the path in bold. */
    const val B_BODY =
        "Location access is off for Show Up. Without it we can't show you anyone nearby — every " +
            "date happens in the real world. "
    const val B_PATH_LEAD = "Turn it on in "
    const val B_PATH = "Permissions › Location"
    const val B_PATH_TAIL = ", or finish your profile first and we'll ask again when you start searching."

    /** C's body on Android: "the path sentence is dropped" -- Settings opens the Location page itself. */
    const val C_BODY =
        "Location is off for every app on this phone, not just Show Up. Turn it on in Settings now, " +
            "or finish your profile first and we'll ask again when you start searching."

    /**
     * The Search location gate's strings (SHOWUP-188, `LOCATION_COPY.app`): identical on iOS and
     * Android, and none of them names a Settings path -- the button routes instead. Apostrophes are
     * U+2019, as the ticket's acceptance criteria and the reference render them. The privacy note
     * and the two CTAs are Profile 12's, unchanged.
     */
    object App {
        const val A_LEAD = "Allow location to see people "
        const val A_EM = "nearby"
        const val A_TAIL = "."
        const val A_BODY = "We use your location to show you people within your distance, pick a fair " +
            "halfway venue and guide you there on the day."

        const val B_LEAD = "Location is off for "
        const val B_EM = "Show Up"
        const val B_TAIL = "."
        const val B_BODY = "You turned off location access for Show Up, so we can’t show you anyone " +
            "nearby or plan a date. Open Settings and allow location — you’ll pick up right where " +
            "you left off."

        const val C_LEAD = "Location is off on your "
        const val C_EM = "phone"
        const val C_TAIL = "."
        const val C_BODY = "Location is switched off for every app on this phone, not just Show Up. Open " +
            "Settings and turn it on — you’ll pick up right where you left off."

        /** D's back pill. */
        const val BACK = "Back"
    }
}

/** One benefit row: a lilac pip, a Lora title, and on row 2 a sub-line. */
private data class LocationBenefit(val icon: BrandIcon, val title: String, val sub: String? = null)

/** "Rows, in this order" -- discovery, meet point, on-the-day routing. */
private val LOCATION_BENEFITS = listOf(
    LocationBenefit(BrandIcon.Compass, LocationCopy.ROW1),
    LocationBenefit(BrandIcon.MapPin, LocationCopy.ROW2, LocationCopy.ROW2_SUB),
    LocationBenefit(BrandIcon.Navigation, LocationCopy.ROW3),
)

/**
 * The ticket's order of sacrifice, as levels. Each is tried in turn and the first that fits is
 * drawn: the full design, then the 120 radar, then the 10 row gap as well.
 */
internal enum class LocationFit(val radar: Dp, val rowGap: Dp) {
    Full(160.dp, 14.dp),
    CompactRadar(120.dp, 14.dp),
    Tight(120.dp, 10.dp),
}

/**
 * Which context the ONE location screen is drawn in (SHOWUP-188 ①, `context: "profile" | "app"`).
 *
 * [Profile] is Profile 12 inside sign-up, unchanged. [App] is the Search location gate: the Search
 * top bar (no filter button) and the TabBar, Likes and Settings still reachable, and in D -- pushed
 * over a profile opened from the Likes tab -- the Premium bar with no chip and a back pill.
 */
sealed interface LocationScreenContext {
    data object Profile : LocationScreenContext

    data class App(
        /** The active tab: [Tab.Search] for A-C, [Tab.Likes] for D. */
        val tab: Tab,
        /** The open received likes -- the chip's count and the TabBar badge. */
        val likes: Int,
        val onTab: (Tab) -> Unit,
        val onLikes: () -> Unit,
        /** D only: back to the profile. Null on the tab, which is a root with no back. */
        val onBack: (() -> Unit)? = null,
        val backLabel: String = LocationCopy.App.BACK,
    ) : LocationScreenContext
}

@Composable
fun ProfileLocationScreen(
    state: LocationState = LocationState.Ask,
    onAllow: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNotNow: () -> Unit = {},
    /** The OS dialog is up. The CTA stops answering; it is never disabled and never changes. */
    requesting: Boolean = false,
    context: LocationScreenContext = LocationScreenContext.Profile,
) {
    if (context is LocationScreenContext.App) {
        LocationGateLayout(state, requesting, onAllow, onOpenSettings, context)
        return
    }

    // No chevron, no gesture, no hardware key -- as on 09 and 10.
    BackHandler(enabled = true) { /* deliberately nothing */ }

    WelcomeScaffold(
        topPadding = 0.dp,
        gutter = 28.dp,
        footer = {
            Column(
                Modifier
                    .padding(horizontal = 28.dp)
                    .padding(bottom = Spacing.sm)
                    .testTag(LOCATION_FOOTER_TAG),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (state == LocationState.Ask) {
                    PrimaryButton(
                        label = LocationCopy.ALLOW,
                        onClick = { if (!requesting) onAllow() },
                        // A's one button IS its bottom button -- the rectangle B and C's `Not now`
                        // occupies, which is the "same Y" the fit test measures.
                        modifier = Modifier.testTag(LOCATION_BOTTOM_TAG),
                        variant = PrimaryButtonVariant.Sunset,
                    )
                } else {
                    PrimaryButton(
                        label = LocationCopy.OPEN_SETTINGS,
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(LOCATION_PRIMARY_TAG),
                        variant = PrimaryButtonVariant.Sunset,
                    )
                    PrimaryButton(
                        label = LocationCopy.NOT_NOW,
                        onClick = onNotNow,
                        modifier = Modifier.testTag(LOCATION_BOTTOM_TAG),
                        variant = PrimaryButtonVariant.Ghost,
                        // The longest label on any button in the app. At 2.0x type on the Galaxy
                        // Fold cover it needs a third line, and two cut it off mid-sentence
                        // (ScreenFitTest). A taller button beats a clipped promise.
                        labelMaxLines = 3,
                    )
                }
            }
        },
    ) {
        // The region above the band, measured ONCE here and handed down: the content picks its
        // sacrifice level against it, and the spacer takes what is left.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val region = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.heightIn(min = region)) {
                    LocationContent(state = state, available = region)
                    // THE ONE FLEXIBLE ELEMENT, with a 12 floor -- `requiredHeightIn` so the floor
                    // holds when the content is tight, for the reason 09 records.
                    Spacer(Modifier.weight(1f).requiredHeightIn(min = Spacing.xl))
                }
            }
        }
    }
}

// ── the app context: the Search location gate (SHOWUP-188) ──────────────────────────────────────

/** Fit-harness anchors for the gate. */
internal const val GATE_CTA_TAG = "location-gate-cta"
internal const val GATE_CONTENT_TAG = "location-gate-content"
internal const val GATE_HEADLINE_TAG = "location-gate-headline"
internal const val GATE_NOTE_TAG = "location-gate-note"
/** The content's scroll -- a last resort past the final sacrifice level, measured for overflow. */
internal const val GATE_SCROLL_TAG = "location-gate-scroll"

/**
 * The gate's order of sacrifice, as levels: "spacer -> radar 170 -> 130 (D 140 -> 110) -> privacy-note
 * margin 18 -> 12. Never shrink the headline, never let it scroll."
 */
internal enum class GateFit(val radar: Dp, val radarOverProfile: Dp, val noteGap: Dp) {
    Full(170.dp, 140.dp, 18.dp),
    CompactRadar(130.dp, 110.dp, 18.dp),
    Tight(130.dp, 110.dp, 12.dp),
}

/**
 * The location screen in its app context. StatusBar -> SearchTopBar (no filter) -> [back pill, D]
 * -> the content column (radar, headline, body, [privacy note, A], ONE flexible spacer, the CTA) ->
 * TabBar. Backdrop 0, content 1, chrome 2, as the reference stacks them.
 *
 * THE CTA SITS DIRECTLY ABOVE THE TABBAR IN EVERY STATE: it is a band pinned to the bottom of the
 * column (`padding-bottom: 22`), and the spacer above it takes the difference between the states.
 * Past the last sacrifice level -- a 320-wide fold at accessibility type -- the content scrolls under
 * the pinned band rather than push the CTA off screen, Profile 12's recorded precedent.
 */
@Composable
private fun LocationGateLayout(
    state: LocationState,
    requesting: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    app: LocationScreenContext.App,
) {
    val blocked = state != LocationState.Ask
    val overProfile = app.onBack != null
    // D: Android back and the back gesture are the back pill. A-C are a tab root: the host's back.
    app.onBack?.let { back -> BackHandler { back() } }

    Box(Modifier.fillMaxSize().background(Cream)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            // Profile 12's three layers; dialled back on B, C and D (orbs .16 / .14, wash .28).
            WelcomeBackdrop(
                placement = OrbPlacement.Startup,
                orangeAlpha = if (blocked) 0.16f else 0.32f,
                violetAlpha = if (blocked) 0.14f else 0.28f,
                washAlpha = if (blocked) 0.28f else 0.55f,
            )
            Column(Modifier.fillMaxSize()) {
                // No filter button: with location off, the only job is turning it on. D is the bar of
                // the profile it was opened from -- Premium, no chip.
                SearchTopBar(
                    likes = app.likes, onLikes = app.onLikes, onFilters = {},
                    modifier = Modifier.testTag(SEARCH_TOP_BAR_TAG),
                    premium = overProfile, filter = false, chip = !overProfile,
                )
                app.onBack?.let { back -> BackPill(app.backLabel, back, rowBackground = Color.Transparent) }

                Column(Modifier.weight(1f).fillMaxWidth()) {
                    // The region above the CTA band, measured here -- the band's own height grows
                    // when large type wraps its label, so it is never assumed.
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val area = maxHeight
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(GATE_SCROLL_TAG)) {
                            // The content column's `padding: 4 24 0`; the spacer's floor is 16.
                            Column(Modifier.heightIn(min = area).padding(start = 24.dp, end = 24.dp, top = 4.dp)) {
                                GateContent(state, overProfile, available = area - 4.dp - 16.dp)
                                // THE ONE FLEXIBLE ELEMENT, floored at 16.
                                Spacer(Modifier.weight(1f).requiredHeightIn(min = 16.dp))
                            }
                        }
                    }
                    PrimaryButton(
                        label = if (blocked) LocationCopy.OPEN_SETTINGS else LocationCopy.ALLOW,
                        // Never disabled: while our dialog is up it simply stops answering.
                        onClick = { if (blocked) onOpenSettings() else if (!requesting) onAllow() },
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 22.dp).testTag(GATE_CTA_TAG),
                        variant = PrimaryButtonVariant.Sunset,
                        // Beside its icon, `Allow location access` takes a third line on the Fold
                        // cover at 2.0x type; two cut it off mid-word (GateFitTest). A taller
                        // button beats a clipped label -- Profile 12's own call for its longest one.
                        labelMaxLines = 3,
                        // The leading icon is decorative: the visible label is the name.
                        leading = {
                            Box(Modifier.clearAndSetSemantics {}) {
                                Icon(if (blocked) BrandIcon.Settings else BrandIcon.MapPin, 18.dp, tint = Color.White, strokeWidth = 2.2f)
                            }
                        },
                    )
                }
                TabBar(active = app.tab, onTab = app.onTab, likes = app.likes)
            }
        }
    }
}

/** The gate's content above the spacer, at the first [GateFit] level that fits [available]. */
@Composable
private fun GateContent(state: LocationState, overProfile: Boolean, available: Dp) {
    SubcomposeLayout(Modifier.fillMaxWidth().testTag(GATE_CONTENT_TAG)) { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val budget = available.roundToPx()
        var placeables = emptyList<androidx.compose.ui.layout.Placeable>()
        for (level in GateFit.entries) {
            placeables = subcompose(level) { GateColumn(state, overProfile, level) }.map { it.measure(loose) }
            if (placeables.sumOf { it.height } <= budget) break
        }
        val height = placeables.sumOf { it.height }
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEach { it.place(0, y); y += it.height }
        }
    }
}

@Composable
private fun GateColumn(state: LocationState, overProfile: Boolean, fit: GateFit) {
    val (lead, em, tail) = when (state) {
        LocationState.Ask -> Triple(LocationCopy.App.A_LEAD, LocationCopy.App.A_EM, LocationCopy.App.A_TAIL)
        LocationState.Denied -> Triple(LocationCopy.App.B_LEAD, LocationCopy.App.B_EM, LocationCopy.App.B_TAIL)
        LocationState.ServicesOff -> Triple(LocationCopy.App.C_LEAD, LocationCopy.App.C_EM, LocationCopy.App.C_TAIL)
    }
    val body = when (state) {
        LocationState.Ask -> LocationCopy.App.A_BODY
        LocationState.Denied -> LocationCopy.App.B_BODY
        LocationState.ServicesOff -> LocationCopy.App.C_BODY
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LocationRadar(height = if (overProfile) fit.radarOverProfile else fit.radar, live = state == LocationState.Ask)
        // Lora 700 / 30 / 1.12 / -0.015em, centred, balanced -- the Search group's size, not
        // Profile 12's 32. The screen's heading; a state swapped in place is announced.
        WashHeadline(
            parts = listOf(lead to false, em to true, tail to false),
            fontSize = 30.sp,
            lineHeight = (30f * 1.12f).sp,
            letterSpacing = (-0.015).em,
            balance = true,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 10.dp)
                .testTag(GATE_HEADLINE_TAG)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
        // Manrope 500 / 14.5 / 1.5, centred, at most 330 wide.
        Text(
            body,
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 330.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 14.5.sp, lineHeight = (14.5f * 1.5f).sp, textAlign = TextAlign.Center,
        )
        // A only: the pitch's privacy note, Profile 12's own. Not on B, C, D -- recovery, not pitch.
        if (state == LocationState.Ask) PrivacyNote(Modifier.padding(top = fit.noteGap).testTag(GATE_NOTE_TAG))
    }
}

/**
 * The content above the spacer, at the first [LocationFit] level that leaves room for the spacer's
 * 12 floor in [available]. B and C have no radar or rows, so for them every level is the same.
 */
@Composable
private fun LocationContent(state: LocationState, available: Dp) {
    SubcomposeLayout(Modifier.fillMaxWidth().testTag(LOCATION_CONTENT_TAG)) { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val budget = (available - Spacing.xl).roundToPx() // the spacer floor above, which moves with it
        var placeables = emptyList<androidx.compose.ui.layout.Placeable>()
        for (level in LocationFit.entries) {
            placeables = subcompose(level) { LocationColumn(state, level) }.map { it.measure(loose) }
            val height = placeables.sumOf { it.height }
            if (height <= budget || state != LocationState.Ask) break
        }
        val height = placeables.sumOf { it.height }
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEach { it.place(0, y); y += it.height }
        }
    }
}

@Composable
private fun LocationColumn(state: LocationState, fit: LocationFit) {
    Column(Modifier.fillMaxWidth()) {
        if (state == LocationState.Ask) {
            // The radar block: `padding: 16px 28px 0`, then the hero's own 8 top margin. Decorative.
            Box(Modifier.padding(top = Spacing.xxl + Spacing.md).fillMaxWidth()) {
                LocationRadar(height = fit.radar)
            }
        }

        // Headline block: 12 on A, 56 on B and C -- "so the headline still reads anchored rather
        // than floating" with no radar above it.
        val (lead, em, tail) = when (state) {
            LocationState.Ask -> Triple(LocationCopy.A_LEAD, LocationCopy.A_EM, LocationCopy.A_TAIL)
            LocationState.Denied -> Triple(LocationCopy.B_LEAD, LocationCopy.B_EM, LocationCopy.B_TAIL)
            LocationState.ServicesOff -> Triple(LocationCopy.C_LEAD, LocationCopy.C_EM, LocationCopy.C_TAIL)
        }
        WashHeadline(
            parts = listOf(lead to false, em to true, tail to false),
            fontSize = 32.sp,
            lineHeight = (32f * 1.1f).sp,
            letterSpacing = (-0.015).em,
            balance = true,
            modifier = Modifier.padding(top = if (state == LocationState.Ask) Spacing.xl else 56.dp),
        )

        if (state == LocationState.Ask) {
            // Body block 4, rows 22 below it.
            //
            // THE GAP IS BETWEEN PARAGRAPHS, NOT BETWEEN PIPS (Philipp, 8 October 2026). The pip
            // no longer sets a row's height (see BenefitRow), so the reference's gap -- which sat
            // between 40-tall rows -- is widened by the overhang a one-line row used to add. All
            // three paragraphs now sit the same distance apart: the larger of the two gaps the
            // reference produced. The bottom padding keeps row 3's overhanging pip as far from the
            // privacy note as it was.
            Column(
                Modifier.padding(top = Spacing.xs + 22.dp, bottom = PipOverhang),
                verticalArrangement = Arrangement.spacedBy(fit.rowGap + PipOverhang),
            ) {
                LOCATION_BENEFITS.forEachIndexed { index, benefit -> BenefitRow(index, benefit) }
            }
            PrivacyNote(Modifier.padding(top = 22.dp))
        } else {
            Text(
                recoveryBody(state),
                modifier = Modifier.padding(top = Spacing.xxl),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 15.sp, lineHeight = (15f * 1.55f).sp,
            )
        }
    }
}

/** B's and C's body, the settings path in bold where the platform table has one. */
private fun recoveryBody(state: LocationState) = buildAnnotatedString {
    if (state == LocationState.ServicesOff) {
        append(LocationCopy.C_BODY)
    } else {
        append(LocationCopy.B_BODY)
        append(LocationCopy.B_PATH_LEAD)
        // Read inline by a screen reader, not as a link -- it is a path to follow, not a control.
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(LocationCopy.B_PATH) }
        append(LocationCopy.B_PATH_TAIL)
    }
}

/** The lilac pip's side, and the gap between it and the title. */
private val Pip = 40.dp
private val PipGap = 14.dp

/**
 * How far a pip reaches past a ONE-line title, above and below: the pip is 40, a title line is
 * 17 x 1.25. A two-line title is taller than the pip, and then nothing overhangs.
 */
private val PipOverhang = (Pip - (17f * 1.25f).dp) / 2

/**
 * One benefit row. ONE GROUP for a screen reader -- title and sub read together -- and the pip is
 * decorative.
 *
 * THE PIP IS CENTRED ON THE TITLE, NOT ON THE ROW (Philipp, 8 October 2026). The reference sets
 * `alignItems: center` on the whole row, which on row 2 -- the only one with a sub-line -- put the
 * map pin halfway down the grey text, a line below the bold title it belongs to. Now the pip sits
 * level with the title on every row, and the sub-line runs beneath the title, aligned to its text.
 *
 * And the pip TAKES NO HEIGHT: the title sets the row. A 40-tall pip beside a 21-tall one-line
 * title used to add invisible space above and below that row, so the paragraphs sat unevenly apart
 * (15 above row 2 and 25 above row 3 in Philipp's screenshot). With the text setting the height,
 * the gap the column puts between rows is the gap the eye sees between paragraphs.
 */
@Composable
private fun BenefitRow(index: Int, benefit: LocationBenefit) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    // Measured at its full 40, reported as no height, drawn centred on the point
                    // the row aligns it to -- the middle of the title.
                    .layout { measurable, constraints ->
                        val pip = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                        layout(pip.width, 0) { pip.place(0, -pip.height / 2) }
                    }
                    // Inside the zero-height report, so the tag measures the pip as drawn.
                    .testTag(locationRowTag(index, "pip"))
                    .size(Pip)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LilacWash)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(benefit.icon, IconSizes.sm, tint = Purple, strokeWidth = 1.8f)
            }
            // Lora 700 / 17 / 1.25, -0.005em, `text-wrap: balance`.
            WashHeadline(
                parts = listOf(benefit.title to false),
                fontSize = 17.sp,
                modifier = Modifier.weight(1f).testTag(locationRowTag(index, "title")),
                lineHeight = (17f * 1.25f).sp,
                letterSpacing = (-0.005).em,
                balance = true,
            )
        }
        if (benefit.sub != null) {
            Text(
                benefit.sub,
                // Under the title's text, not under the pip.
                modifier = Modifier.padding(start = Pip + PipGap, top = 3.dp).testTag(locationRowTag(index, "sub")),
                color = SubLine, fontFamily = Manrope, fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp, lineHeight = (13.5f * 1.35f).sp,
            )
        }
    }
}

/** Row 2's sub-line ink, `rgba(0,0,0,0.62)` in the reference -- carried as warm ink at the same weight. */
private val SubLine = com.showup.designsystem.Muted

/** The privacy note: a lavender-washed card with a 16 lock. Static text. */
@Composable
private fun PrivacyNote(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PrivacyNoteWash)
            .padding(horizontal = 14.dp, vertical = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp).clearAndSetSemantics {}) {
            Icon(BrandIcon.Lock, 16.dp, tint = Purple, strokeWidth = 1.8f)
        }
        Text(
            LocationCopy.PRIVACY,
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp, lineHeight = (12.5f * 1.45f).sp,
        )
    }
}

/**
 * The radar: three rings, four nearby dots and the sunset pin, on the reference's 240 x 160 canvas.
 *
 * ILLUSTRATION GEOMETRY, NOT TOKENS -- the CLAUDE.md rule. Every coordinate is the reference SVG's.
 * STATIC: "the radar is static", and its animation is out of scope.
 *
 * At [height] 120 (the ticket's first sacrifice) the drawing is scaled to fit, so it shrinks as a
 * picture rather than being cropped. `overflow: visible` in the reference -- the outer ring runs 16
 * past the top and bottom of its box -- so nothing here clips it either.
 *
 * THE ONE RADAR (SHOWUP-188 ③): [live] is Profile 12's drawing, unchanged. `live = false` is the
 * location gate's B, C and D -- slate rings at .22 / .14 / .08, no dots, no halo, a grey pin and one
 * slash -- "recovery, not pitch". A second radar component is a bug.
 */
@Composable
internal fun LocationRadar(height: Dp, live: Boolean = true) {
    Box(
        Modifier.fillMaxWidth().height(height).testTag(LOCATION_RADAR_TAG).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val scale = height.value / 160f
        Canvas(Modifier.size(240.dp * scale, height)) {
            val u = size.width / 240f // one reference unit
            fun p(x: Float, y: Float) = Offset(x * u, y * u)

            val ring = 1.25f * u
            val dash = PathEffect.dashPathEffect(floatArrayOf(2f * u, 4f * u))
            val ink = if (live) Purple else RadarSlate
            val alphas = if (live) floatArrayOf(0.45f, 0.30f, 0.18f) else floatArrayOf(0.22f, 0.14f, 0.08f)
            drawCircle(ink.copy(alpha = alphas[0]), 24f * u, p(120f, 80f), style = Stroke(ring))
            drawCircle(ink.copy(alpha = alphas[1]), 56f * u, p(120f, 80f), style = Stroke(ring, pathEffect = dash))
            drawCircle(ink.copy(alpha = alphas[2]), 96f * u, p(120f, 80f), style = Stroke(ring, pathEffect = dash))

            if (live) {
                // Nearby dots: paper-white fill, violet ring -- one per ring, at asymmetric angles.
                listOf(p(148f, 62f), p(74f, 108f), p(198f, 118f)).forEach { c ->
                    drawCircle(Cream, 4.5f * u, c, style = Fill)
                    drawCircle(Purple, 4.5f * u, c, style = Stroke(1.5f * u))
                }
                // The fourth, smaller and lavender: "further away".
                drawCircle(Cream, 3.5f * u, p(44f, 50f), style = Fill)
                drawCircle(Lavender.copy(alpha = 0.7f), 3.5f * u, p(44f, 50f), style = Stroke(1.25f * u))

                // The soft orange halo behind the pin.
                drawCircle(Orange.copy(alpha = 0.18f), 18f * u, p(120f, 80f), style = Fill)
            }

            // The pin: the 24-grid map-pin, scaled 1.25 and placed at translate(120,80) +
            // translate(-12,-16) -- origin (108, 64). Sunset-filled top to bottom, white stroke.
            withTransform({
                translate(108f * u, 64f * u)
                scale(1.25f * u, 1.25f * u, pivot = Offset.Zero)
            }) {
                val pin = Path().apply {
                    moveTo(21f, 10f)
                    cubicTo(21f, 17f, 12f, 23f, 12f, 23f)
                    cubicTo(12f, 23f, 3f, 17f, 3f, 10f)
                    arcTo(Rect(3f, 1f, 21f, 19f), 180f, 180f, false)
                    close()
                }
                if (live) {
                    drawPath(
                        pin,
                        Brush.verticalGradient(
                            0f to SunsetStops[0].second,
                            1f to SunsetStops[1].second,
                            startY = 1f, endY = 23f,
                        ),
                    )
                    drawPath(pin, Color.White, style = Stroke(width = 1.6f))
                    drawCircle(Color.White, 3f, Offset(12f, 10f), style = Fill)
                } else {
                    drawPath(pin, RadarSlate.copy(alpha = 0.55f))
                    drawPath(pin, RadarSlate.copy(alpha = 0.65f), style = Stroke(width = 1.6f))
                    drawCircle(RadarSlateDot, 3f, Offset(12f, 10f), style = Fill)
                }
            }
            if (!live) {
                // The slash: (-24, 18) to (21, -21) from the pin's centre, 1.6, round caps.
                drawLine(
                    RadarSlate.copy(alpha = 0.55f), p(96f, 98f), p(141f, 59f),
                    strokeWidth = 1.6f * u, cap = StrokeCap.Round,
                )
            }
        }
    }
}

// ── previews: the three states at the ticket's three frames; 375 first, where A is tight ──────

@Preview(name = "Location · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PLoc_A375() { ProfileLocationScreen(LocationState.Ask) }

@Preview(name = "Location · B · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PLoc_B375() { ProfileLocationScreen(LocationState.Denied) }

@Preview(name = "Location · C · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PLoc_C375() { ProfileLocationScreen(LocationState.ServicesOff) }

@Preview(name = "Location · A · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PLoc_A390() { ProfileLocationScreen(LocationState.Ask) }

@Preview(name = "Location · B · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PLoc_B430() { ProfileLocationScreen(LocationState.Denied) }

@Preview(name = "Location · A · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PLoc_A320() { ProfileLocationScreen(LocationState.Ask) }

/**
 * The Search location gate (SHOWUP-188) -- `ScreenLocationGate`, a thin wrapper around the one
 * location screen in its app context. [onBack] is D's: the gate pushed over a profile opened from
 * the Likes tab, with the Likes tab active; null on the Search tab, where the gate is a root.
 */
@Composable
fun LocationGateScreen(
    state: LocationState,
    tab: Tab,
    likes: Int,
    onTab: (Tab) -> Unit,
    onLikes: () -> Unit,
    onAllow: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    requesting: Boolean = false,
    onBack: (() -> Unit)? = null,
) = ProfileLocationScreen(
    state = state,
    onAllow = onAllow,
    onOpenSettings = onOpenSettings,
    requesting = requesting,
    context = LocationScreenContext.App(tab = tab, likes = likes, onTab = onTab, onLikes = onLikes, onBack = onBack),
)

// ── previews: the gate's four frames; A and D at 375 x 667 first, where they are tight ──────────

@Composable private fun GatePreview(state: LocationState, fromLikes: Boolean = false) = LocationGateScreen(
    state = state, tab = if (fromLikes) Tab.Likes else Tab.Search, likes = 2, onTab = {}, onLikes = {},
    onBack = if (fromLikes) ({}) else null,
)

@Preview(name = "Gate · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PGate_A375() { GatePreview(LocationState.Ask) }

@Preview(name = "Gate · D · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PGate_D375() { GatePreview(LocationState.Denied, fromLikes = true) }

@Preview(name = "Gate · B · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PGate_B390() { GatePreview(LocationState.Denied) }

@Preview(name = "Gate · C · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PGate_C430() { GatePreview(LocationState.ServicesOff) }

@Preview(name = "Gate · A · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PGate_A320() { GatePreview(LocationState.Ask) }
