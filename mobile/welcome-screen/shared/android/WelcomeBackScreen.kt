/*
 * WelcomeBackScreen.kt
 * ShowUp · Welcome back — re-login (SHOWUP-142)
 *
 * Shown on launch whenever an account already exists on the device; a device with no account opens
 * Startup instead. The two are one visual space — same backdrop values, same wordmark at 26, same
 * position — which is why both take them from WelcomeShell rather than owning a copy.
 *
 * Built to welcome/screen-login-reference.jsx (numbers) and SHOWUP-142 (behaviour and copy).
 */
package com.showup.welcome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.Subtle

/**
 * Which method this device signed in with last. Device state, never a user setting.
 *
 * [Unknown] is a real case, not a defensive default: an unrecognised or unsupported value falls back
 * to phone as the primary button and hides the hint row entirely, because guessing a method and
 * telling someone they last used it would be a lie.
 */
enum class AuthMethod { Phone, Apple, Google, Facebook, Unknown }

/** Canonical order — phone, Apple, Google, Facebook. Never reordered beyond lifting the primary. */
private val CANONICAL = listOf(AuthMethod.Phone, AuthMethod.Apple, AuthMethod.Google, AuthMethod.Facebook)

private data class MethodSpec(val label: String, val icon: BrandIcon, val hint: String)

private val METHODS = mapOf(
    AuthMethod.Phone to MethodSpec("Continue with phone number", BrandIcon.Phone, "Last login was via phone"),
    AuthMethod.Apple to MethodSpec("Continue with Apple", BrandIcon.Apple, "Last login was via Apple"),
    AuthMethod.Google to MethodSpec("Continue with Google", BrandIcon.Google, "Last login was via Google"),
    AuthMethod.Facebook to MethodSpec("Continue with Facebook", BrandIcon.Facebook, "Last login was via Facebook"),
)

@Composable
fun WelcomeBackScreen(
    name: String = "Leo",
    lastUsed: AuthMethod = AuthMethod.Phone,
    onContinue: (AuthMethod) -> Unit = {},
    onGetHelp: () -> Unit = {},
    onUseDifferentAccount: () -> Unit = {},
    onLegal: () -> Unit = {},
    onPrivacy: () -> Unit = {},
) {
    val known = lastUsed != AuthMethod.Unknown
    val primary = if (known) lastUsed else AuthMethod.Phone
    val rest = CANONICAL.filter { it != primary }

    WelcomeScaffold {
        val compact = LocalConfiguration.current.screenHeightDp < 700
        Wordmark()

        // 120 here, against Startup's 132 — same role: the element that yields on short frames.
        Spacer(Modifier.height(if (compact) 60.dp else 120.dp))

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // With no name the headline is "Welcome back" and the italic span is omitted entirely —
            // an empty <em> would still paint a wash under nothing.
            if (name.isBlank()) {
                WashHeadline(parts = listOf("Welcome back" to false), fontSize = 44.sp)
            } else {
                WashHeadline(parts = listOf("Welcome back " to false, name to true), fontSize = 44.sp)
            }
            Text(
                "Sign back in to check your availability and see who’s free today.",
                modifier = Modifier.widthIn(max = 280.dp),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 17.sp, lineHeight = 24.65.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        // The stack floats between two equal spacers — centred in the lower band, not pinned.
        Column(
            Modifier.padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (known) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 6px dot with a 3px ring — drawn rather than a bordered Box so the ring sits
                    // outside the dot without changing its size.
                    Spacer(
                        Modifier.size(12.dp).drawBehind {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(Orange.copy(alpha = 0.18f), radius = 6.dp.toPx(), center = c)
                            drawCircle(Orange, radius = 3.dp.toPx(), center = c)
                        }
                    )
                    Text(
                        METHODS.getValue(primary).hint,
                        color = Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    )
                }
            }

            // Exactly four buttons, always. The last-used one is the only sunset button on screen.
            PillButton(
                METHODS.getValue(primary).label, { onContinue(primary) },
                variant = PillVariant.Sunset,
                leading = { Icon(METHODS.getValue(primary).icon, 18.dp, tint = Color.White) },
            )
            rest.forEach { m ->
                PillButton(
                    METHODS.getValue(m).label, { onContinue(m) },
                    variant = PillVariant.Ghost,
                    leading = { Icon(METHODS.getValue(m).icon, 18.dp, tint = Fg) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Both phrases are real links with their own hit areas (SHOWUP-142).
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "Trouble signing in? ", color = Subtle, fontFamily = Manrope,
                fontSize = 12.sp, lineHeight = 17.4.sp,
            )
            Text(
                "Get help", modifier = Modifier.clickable(role = Role.Button, onClick = onGetHelp),
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, lineHeight = 17.4.sp,
            )
            Text(" or ", color = Subtle, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 17.4.sp)
            Text(
                "Use a different account",
                modifier = Modifier.clickable(role = Role.Button, onClick = onUseDifferentAccount),
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, lineHeight = 17.4.sp,
            )
        }

        // No Terms & Conditions on this screen — consent was given at sign-up.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "Legal Notice", modifier = Modifier.clickable(role = Role.Button, onClick = onLegal),
                color = Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp, textDecoration = TextDecoration.Underline,
            )
            Text(" · ", color = Subtle, fontFamily = Manrope, fontSize = 11.5.sp)
            Text(
                "Privacy Policy", modifier = Modifier.clickable(role = Role.Button, onClick = onPrivacy),
                color = Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp, textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun WBPreviewSmall() { WelcomeBackScreen() }

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewReference() { WelcomeBackScreen() }

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun WBPreviewLarge() { WelcomeBackScreen() }

@Preview(name = "lastUsed = Apple", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewApple() { WelcomeBackScreen(lastUsed = AuthMethod.Apple) }

@Preview(name = "lastUsed = Google", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewGoogle() { WelcomeBackScreen(lastUsed = AuthMethod.Google) }

@Preview(name = "lastUsed = Facebook", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewFacebook() { WelcomeBackScreen(lastUsed = AuthMethod.Facebook) }

@Preview(name = "lastUsed = unknown - no hint row", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewUnknown() { WelcomeBackScreen(lastUsed = AuthMethod.Unknown) }

@Preview(name = "no name - no italic span", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewNoName() { WelcomeBackScreen(name = "") }

@Preview(name = "24-character name", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewLongName() { WelcomeBackScreen(name = "Maximiliana Konstantina") }

@Preview(name = "390 x 844 - with system bars", showSystemUi = true, device = "spec:width=390dp,height=844dp")
@Composable
private fun WBPreviewSystemUi() { WelcomeBackScreen() }
