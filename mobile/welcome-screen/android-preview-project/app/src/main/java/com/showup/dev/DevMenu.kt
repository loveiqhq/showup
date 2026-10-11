package com.showup.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.showup.analytics.EventLog
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Purple
import com.showup.designsystem.Radius
import com.showup.designsystem.SelectedRowWash
import com.showup.designsystem.Spacing
import com.showup.designsystem.Subtle
import com.showup.observability.ProhibitedFields
import com.showup.profile.LocationArrival
import com.showup.welcome.FlowScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The developer menu (8 October 2026): jump to any screen, and watch every tracking event as it
 * fires. For Esma and Philipp, so a new screen can be reached without walking the whole app.
 *
 * DEBUG BUILDS ONLY. The host draws [DevTab] and [DevMenu] behind `BuildConfig.DEBUG`, a
 * compile-time constant, so R8 removes them -- and this whole file with them -- from every release
 * build. A store build cannot contain it.
 *
 * Plain on purpose: it is a tool, not a screen, and is drawn from the design-system tokens rather
 * than from any design. It is not in ScreenFitTest and needs no design review.
 */

/**
 * The small "DEV" tab on the right edge that opens the menu. Hidden for the rest of the launch by
 * the menu's own switch, so a screenshot for design review can be taken without it.
 *
 * NARROWER THAN THE GUTTER, so it can never cover a control. Its first version was a horizontal
 * "DEV" pill, and on the gender step it sat on top of the last option's radio. Every screen keeps
 * [Spacing.screenGutter] clear at its edges; this stays inside that strip, with the letters stacked.
 */
@Composable
fun BoxScope.DevTab(onOpen: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .width(Spacing.screenGutter - Spacing.sm)
            .height(ComponentSizes.controlHeight)
            .background(Fg.copy(alpha = 0.82f), RoundedCornerShape(topStart = Radius.errorBox, bottomStart = Radius.errorBox))
            .clickable(role = Role.Button, onClickLabel = "Open the developer menu", onClick = onOpen)
            .semantics { contentDescription = "Developer menu" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "D\nE\nV", color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 9.sp, lineHeight = 10.sp, textAlign = TextAlign.Center,
        )
    }
}

/** Everything the menu shows, decided by the host. */
data class DevMenuFacts(
    val current: FlowScreen,
    /** Null while the token store is still being read. */
    val signedIn: Boolean?,
    val notificationsAsk: Boolean,
    val oldAndroid: Boolean,
    val location: LocationArrival,
)

private enum class DevTabPage { Screens, Events }

@Composable
fun DevMenu(
    facts: DevMenuFacts,
    events: List<EventLog.Entry>,
    onJump: (FlowScreen) -> Unit,
    onClearEvents: () -> Unit,
    onHideTab: () -> Unit,
    onClose: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(DevTabPage.Screens) }
    // A text field on the screen underneath keeps its keyboard up over the menu otherwise.
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus(force = true) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            // Owns every pointer over it, so nothing under the menu can be pressed through it:
            // hit testing stops at the topmost sibling that has pointer input.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.screenGutter),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Developer menu", color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            DevTextButton("Close", onClose)
        }
        Text(
            "Only in builds run from Android Studio. Store builds do not contain it.",
            color = Muted, fontFamily = Manrope, fontSize = 12.sp,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DevPageTab("Screens", page == DevTabPage.Screens) { page = DevTabPage.Screens }
            DevPageTab("Events (${events.size})", page == DevTabPage.Events) { page = DevTabPage.Events }
        }
        when (page) {
            DevTabPage.Screens -> ScreensPage(facts, onJump, onHideTab)
            DevTabPage.Events -> EventsPage(events, onClearEvents)
        }
    }
}

@Composable
private fun ScreensPage(facts: DevMenuFacts, onJump: (FlowScreen) -> Unit, onHideTab: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val status = when (facts.signedIn) {
                null -> "Checking sign-in..."
                true -> "Signed in. Every screen saves to your real account."
                false -> "Not signed in. Log in on Startup to reach the profile screens."
            }
            Text(status, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = ComponentSizes.controlHeightMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hide the DEV tab until the app is next opened", color = Muted, fontFamily = Manrope, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = false, onCheckedChange = { if (it) onHideTab() })
            }
        }
        DevSection.entries.forEach { section ->
            // Prefixed: a section and a screen share names ("SignUp", "Home"), and a lazy list
            // refuses a key it has already seen -- the menu crashed on its first opening without it.
            item(key = "section:${section.name}") {
                Text(
                    section.title.uppercase(), color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm).semantics { heading() },
                )
            }
            items(FlowScreen.entries.filter { it.devSection() == section }, key = { "screen:${it.name}" }) { screen ->
                val jump = if (facts.signedIn == null && screen.needsAccount) {
                    null
                } else {
                    devJump(screen, facts.signedIn == true, facts.notificationsAsk, facts.oldAndroid, facts.location)
                }
                ScreenRow(screen, here = screen == facts.current, jump = jump, onJump = onJump)
            }
        }
        item { Spacer(Modifier.heightIn(min = Spacing.xxl)) }
    }
}

@Composable
private fun ScreenRow(screen: FlowScreen, here: Boolean, jump: DevJump?, onJump: (FlowScreen) -> Unit) {
    val note = when {
        here -> "You are here"
        jump == null -> "Checking sign-in..."
        jump is DevJump.NeedsLogIn -> "Log in first"
        jump is DevJump.Skipped -> jump.why
        else -> null
    }
    val enabled = !here && jump != null && jump !is DevJump.NeedsLogIn
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ComponentSizes.controlHeightMedium)
            .background(if (here) SelectedRowWash else Color.Transparent, RoundedCornerShape(Radius.errorBox))
            .clickable(enabled = enabled, role = Role.Button) { onJump(screen) }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            screen.devLabel(), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            color = if (enabled || here) Fg else Subtle,
        )
        if (note != null) Text(note, color = if (here) Purple else Muted, fontFamily = Manrope, fontSize = 12.sp)
    }
}

@Composable
private fun EventsPage(events: List<EventLog.Entry>, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Newest first. Nothing is sent anywhere: no analytics tool is connected yet.",
            color = Muted, fontFamily = Manrope, fontSize = 12.sp, modifier = Modifier.weight(1f),
        )
        DevTextButton("Clear", onClear)
    }
    if (events.isEmpty()) {
        Text(
            "No events yet. Tap through the app and every event appears here as it fires.",
            color = Fg, fontFamily = Manrope, fontSize = 14.sp, modifier = Modifier.padding(top = Spacing.xl),
        )
        return
    }
    val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        items(events, key = { it.id }) { entry ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Elevated, RoundedCornerShape(Radius.errorBox))
                    .padding(Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.event, color = Fg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(time.format(Date(entry.atMillis)), color = Subtle, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                entry.properties.toSortedMap().forEach { (key, value) ->
                    Text(
                        "$key: ${shownValue(key, value)}", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.heightIn(min = Spacing.xxl)) }
    }
}

/**
 * A property's value as the log shows it. A key on the prohibited list -- email, phone, a code, a
 * token -- is withheld even here: the events are designed to carry none of these, and if one ever
 * did, this screen is not where it should first be seen in plain text.
 *
 * Except [OWN_KEYS]: names the list holds for crash reports that mean something harmless in this
 * app's own events. Withholding them made a correct event look broken -- the first look at the log
 * showed the tutorial's `card` as "[withheld]".
 */
internal fun shownValue(key: String, value: Any): String =
    if (key !in OWN_KEYS && ProhibitedFields.isProhibited(key)) "[withheld]" else value.toString()

/** `card` is the tutorial card number (TutorialAnalytics), never a payment card: there are none. */
private val OWN_KEYS = setOf("card")

@Composable
private fun DevPageTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = ComponentSizes.minTapTarget)
            .background(if (selected) Fg else Elevated, RoundedCornerShape(Radius.pill))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun DevTextButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = ComponentSizes.minTapTarget)
            .widthIn(min = ComponentSizes.minTapTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
