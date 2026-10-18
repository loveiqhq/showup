/*
 * CheckInTabPlaceholders.kt
 * ShowUp · where the Check-In hands over to screens that are not built yet
 *
 * **Scaffolding, not product** -- the same standing as `HomePlaceholderScreen`. Two destinations the
 * Check-In tickets name but do not build:
 *
 *   · SEARCH, DATE SEARCH UNLOCKED. Story 06: "Find my date, saved -> Search, date search unlocked.
 *     No toast and no confirmation screen." Search is its own ticket ("Out of scope: Search itself,
 *     beyond landing there"). Without a destination the commit would land nowhere, so this screen
 *     says where the user is and that the real one is not built.
 *   · SETTINGS. Story 01: on the gate, "Settings navigates normally". There is no Settings screen in
 *     this app yet; a tab that did nothing would be a dead control, so it lands here, with the tab
 *     bar, and Search and Instant behave as the gate's own tabs do.
 *
 * Both say they are placeholders on screen, so neither can be mistaken for the product. Deleted
 * when Search and Settings land; MainActivity then routes to those instead.
 */
package com.showup.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.Spacing
import com.showup.designsystem.Subtle
import com.showup.designsystem.Tab
import com.showup.designsystem.TabBar

/** Where a successful commit lands until Search is built. */
@Composable
fun SearchUnlockedPlaceholderScreen() {
    PlaceholderStatement(
        title = "Date search unlocked",
        body = "Your Check-in is saved. Search opens here once it is built.",
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

/**
 * Settings from the gate's tab bar. [onTab] gets Search and Instant -- the gate's two other tabs --
 * and Android back returns to Search, the tab the gate is.
 */
@Composable
fun SettingsPlaceholderScreen(onTab: (Tab) -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        PlaceholderStatement(
            title = "Settings",
            body = "Profile and Show-up Rate live here once Settings is built.",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        TabBar(active = Tab.Settings, onTab = { if (it != Tab.Settings) onTab(it) })
    }
}

/** The shared statement: a title, a line, and the words that say it is a placeholder. */
@Composable
private fun PlaceholderStatement(title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                title,
                Modifier.semantics { heading() },
                fontFamily = Lora, fontSize = 30.sp, color = Fg,
                textAlign = TextAlign.Center, lineHeight = 34.sp,
            )
            Text(
                body,
                Modifier.widthIn(max = 280.dp),
                fontFamily = Manrope, fontSize = 15.sp, color = Neutral,
                textAlign = TextAlign.Center, lineHeight = 22.sp,
            )
            Text(
                "Placeholder screen. It goes away with the real one.",
                Modifier
                    .widthIn(max = 280.dp)
                    .padding(top = Spacing.sm),
                fontFamily = Manrope, fontSize = 12.sp, color = Subtle,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Search unlocked (placeholder) · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewSearchUnlocked() = SearchUnlockedPlaceholderScreen()

@Preview(name = "Settings (placeholder) · 440", widthDp = 440, heightDp = 956)
@Composable private fun PreviewSettings() = SettingsPlaceholderScreen()
