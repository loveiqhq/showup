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

import com.showup.designsystem.Spacing

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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withLink
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

// MethodSpec, the canonical order and the provider treatments now live in
// AuthMethodList.kt, shared with the Connect screen. SHOWUP-145 requires exactly that:
// "The method list uses the same component and the same ordered data source as welcome 04."

@Composable
fun WelcomeBackScreen(
    /**
     * The remembered member's first name, or empty when the device remembers nobody.
     *
     * Empty is the DEFAULT on purpose. It used to be "Leo", which is fine in a preview and wrong
     * everywhere else: a caller that forgot to pass anything greeted a stranger by name. The
     * defaults now describe a device that has never been signed in on, so forgetting to pass
     * something produces the honest screen rather than a fabricated one.
     */
    name: String = "",
    /** Device state. [AuthMethod.Unknown] means nobody has signed in here, so no hint is shown. */
    lastUsed: AuthMethod = AuthMethod.Unknown,
    /** Which providers have finished credential setup. The rest are hidden, not greyed out. */
    configured: Set<AuthMethod> = LOGIN_METHODS.toSet(),
    onContinue: (AuthMethod) -> Unit = {},
    onGetHelp: () -> Unit = {},
    onUseDifferentAccount: () -> Unit = {},
    onLegal: () -> Unit = {},
    onPrivacy: () -> Unit = {},
) {
    // A provider whose credential setup is not finished is hidden, not disabled (SHOWUP-145).
    val methods = availableMethods(LOGIN_METHODS, configured)
    val known = lastUsed != AuthMethod.Unknown && lastUsed in methods
    val primary = if (known) lastUsed else methods.firstOrNull() ?: AuthMethod.Phone

    // Scroll only when the frame runs out, per the 10 September decision.
    //
    // On every phone where the content fits -- all twelve at 390dp and wider, and most of the
    // 360s -- this changes nothing at all: the inner column is floored at the viewport height, so
    // the two weighted spacers still divide the leftover space and the layout is what it was.
    //
    // On the short ones it is the difference between a screen and a broken one. At 320x686 with a
    // 24-character name, "Continue with Facebook" was measured at 7.5dp tall: a real sign-in
    // button squeezed to a sliver, because a Column with nothing left to give shrinks its children
    // in place rather than pushing them off the edge. Nothing looked wrong in a preview.
    WelcomeScaffold(scrollWhenTight = true) {
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
        Column {
            if (known) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
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
                        methodSpec(primary).hint,
                        color = Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                    )
                }
            }

            // The same component and the same ordered data source as Connect — SHOWUP-145.
            // No skip here: Welcome back has no way past sign-in.
            AuthMethodList(
                methods = methods,
                onSelect = onContinue,
                suggested = primary,
            )
        }

        Spacer(Modifier.weight(1f))

        // Both phrases are real links with their own hit areas (SHOWUP-142).
        //
        // One paragraph with LinkAnnotation rather than a Row of Texts: a Row cannot wrap
        // mid-sentence, so on a narrow frame the line would clip instead of flowing.
        val helpLink = SpanStyle(color = Fg, fontWeight = FontWeight.SemiBold)
        Text(
            buildAnnotatedString {
                append("Trouble signing in? ")
                withLink(LinkAnnotation.Clickable("help") { onGetHelp() }) {
                    withStyle(helpLink) { append("Get help") }
                }
                append(" or ")
                withLink(LinkAnnotation.Clickable("switch") { onUseDifferentAccount() }) {
                    withStyle(helpLink) { append("Use a different account") }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
            // Muted, not Subtle — see audit finding 7.
            color = Subtle, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 17.4.sp,
            textAlign = TextAlign.Center,
        )

        // No Terms & Conditions on this screen — consent was given at sign-up.
        val legalLink = SpanStyle(
            color = Muted, fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        )
        Text(
            buildAnnotatedString {
                withLink(LinkAnnotation.Clickable("legal") { onLegal() }) {
                    withStyle(legalLink) { append("Legal Notice") }
                }
                append(" · ")
                withLink(LinkAnnotation.Clickable("privacy") { onPrivacy() }) {
                    withStyle(legalLink) { append("Privacy Policy") }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
            color = Subtle, fontFamily = Manrope, fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The state a device is in when someone taps "Log in" on Startup having never signed in here.
 *
 * No name in the headline and no "last login was via…" hint, because neither is true. This is the
 * default the screen ships with, so a caller that passes nothing gets this rather than a greeting
 * addressed to a stranger.
 */
@Preview(name = "no account on this device", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewNoAccount() { WelcomeBackScreen() }

@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun WBPreviewSmall() { WelcomeBackScreen(name = "Leo", lastUsed = AuthMethod.Phone) }

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewReference() { WelcomeBackScreen(name = "Leo", lastUsed = AuthMethod.Phone) }

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun WBPreviewLarge() { WelcomeBackScreen(name = "Leo", lastUsed = AuthMethod.Phone) }

@Preview(name = "lastUsed = Apple", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewApple() { WelcomeBackScreen(name = "Leo", lastUsed = AuthMethod.Apple) }

@Preview(name = "lastUsed = Google", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewGoogle() { WelcomeBackScreen(name = "Leo", lastUsed = AuthMethod.Google) }

@Preview(name = "lastUsed = Facebook", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewFacebook() { WelcomeBackScreen(name = "Leo", lastUsed = AuthMethod.Facebook) }

@Preview(name = "lastUsed = unknown - no hint row", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewUnknown() { WelcomeBackScreen(name = "Leo", lastUsed = AuthMethod.Unknown) }

@Preview(name = "no name - no italic span", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewNoName() { WelcomeBackScreen(name = "", lastUsed = AuthMethod.Phone) }

@Preview(name = "24-character name", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WBPreviewLongName() { WelcomeBackScreen(name = "Maximiliana Konstantina", lastUsed = AuthMethod.Phone) }

@Preview(name = "390 x 844 - with system bars", showSystemUi = true, device = "spec:width=390dp,height=844dp")
@Composable
private fun WBPreviewSystemUi() { WelcomeBackScreen(name = "Leo", lastUsed = AuthMethod.Phone) }
