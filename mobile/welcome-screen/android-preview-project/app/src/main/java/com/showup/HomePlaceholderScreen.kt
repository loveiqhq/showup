/*
 * HomePlaceholderScreen.kt
 * ShowUp · where both endings of the sign-up flow arrive (SHOWUP-146)
 *
 * **Scaffolding, not product.** The real app does not exist in this preview project, so without
 * something here the two endings SHOWUP-146 distinguishes would be indistinguishable: a returning
 * member who correctly skips the tutorial would land on a blank screen, which looks like a bug
 * rather than like the rule working.
 *
 * So this screen states which rule sent the user here. That makes the ticket demonstrable -- walk
 * the flow twice, screenshot both, and the acceptance evidence is the two screenshots.
 *
 * Deleted when the real home screen lands; MainActivity then routes to that instead.
 */
package com.showup

import com.showup.designsystem.Spacing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.Subtle
import com.showup.welcome.SignUpOutcome

@Composable
fun HomePlaceholderScreen(outcome: SignUpOutcome, onStartOver: () -> Unit) {
    // The whole point of the screen: name the rule that applied, in the ticket's own terms.
    val rule = when (outcome) {
        SignUpOutcome.NewAccount ->
            "New account. The tutorial was shown first, then the app."
        SignUpOutcome.ReturningMember ->
            "Returning member. The tutorial was skipped, as SHOWUP-146 requires."
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "The app starts here",
                fontFamily = Lora, fontSize = 30.sp, color = Fg,
                textAlign = TextAlign.Center, lineHeight = 34.sp,
            )
            Text(
                rule,
                fontFamily = Manrope, fontSize = 15.sp, color = Neutral,
                textAlign = TextAlign.Center, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            Text(
                "Placeholder screen. It goes away with the real home screen.",
                fontFamily = Manrope, fontSize = 12.sp, color = Subtle,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp).padding(top = Spacing.sm),
            )
            // 44dp minimum, found by ScreenFitTest: 12dp of padding around a 14sp label came to
            // 43dp, one short of the smallest comfortable tap target on any phone.
            Box(
                Modifier
                    .padding(top = Spacing.lg)
                    .defaultMinSize(minHeight = 48.dp)
                    .border(1.dp, Border, RoundedCornerShape(50))
                    .clickable(onClick = onStartOver)
                    .padding(horizontal = 22.dp, vertical = Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text("Start over", fontFamily = Manrope, fontSize = 14.sp, color = Fg)
            }
        }
    }
}

@Preview(name = "home - new account", showBackground = true)
@Composable
private fun HomeNew() = HomePlaceholderScreen(SignUpOutcome.NewAccount, {})

@Preview(name = "home - returning member", showBackground = true)
@Composable
private fun HomeReturning() = HomePlaceholderScreen(SignUpOutcome.ReturningMember, {})
