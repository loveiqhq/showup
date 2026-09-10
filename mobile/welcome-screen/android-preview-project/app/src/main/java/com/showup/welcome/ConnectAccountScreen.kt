/*
 * ConnectAccountScreen.kt
 * ShowUp · Connect an account — the whole first-time connection flow (SHOWUP-144)
 *
 * Ten states out of one component, driven by `state` + `provider` + `kind`, exactly as the ticket
 * requires: no per-provider screen, no per-state route, and the conflict as a modal over the
 * mounted screen rather than a route of its own.
 *
 *   A  Idle          three connect buttons and the skip
 *   B  Tapped        spinner on the tapped one, siblings dimmed, skip still live
 *   C  Handoff       Apple's sheet — the system owns the foreground
 *   D  Handoff       Google's sheet — likewise
 *   E  Linking       token returned, our backend is linking; capped at 8s
 *   F  Success       linked, forward into profile creation
 *   G  Cancelled     the user closed the sheet — neutral, never an error
 *   H  Error         network or declined
 *   I  Conflict      the identity belongs to an existing account
 *   J  Resolve       the owning provider's sheet, in sign-in mode — not ours
 *
 * C, D and J are drawn by the OS or the provider SDK. Nothing in this file reproduces them, and
 * nothing assumes their height: they differ per provider, per OS version and per account count.
 *
 * Built to welcome/screen-connect-reference.jsx (numbers) and SHOWUP-144 (behaviour and copy),
 * with the epic's provider-button note overruling both wherever they disagree.
 */
package com.showup.welcome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant

import com.showup.designsystem.IconSizes
import com.showup.designsystem.Motion
import com.showup.designsystem.Radius
import com.showup.designsystem.Spacing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.StatusBadge
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Subtle
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Success
import com.showup.tutorial.NextButton
import com.showup.tutorial.NextVariant
import com.showup.designsystem.rememberMotion
import kotlinx.coroutines.delay

/** The eight screen states. The two OS handoffs share one of them; the provider tells them apart. */
enum class ConnectState { Idle, Tapped, Handoff, Linking, Success, Cancelled, Error, Conflict }

/** The two failures the ticket recognises. Anything else is a bug in our handling, not a state. */
enum class ErrorKind { Network, Declined }

/**
 * "Linking must not become a dead end. It has no exit by design, so cap it — 8s — and fall out to
 * error · network rather than spinning on." The ticket also flags the number itself as a guess to
 * be instrumented, which is why it is a named constant rather than buried inside the effect.
 */
const val LINKING_TIMEOUT_MS = 8_000L

/**
 * Whether the platform dims the screen for us during an OS handoff.
 *
 * On Android every route out of this screen is drawn by something that already dims or covers:
 * Credential Manager's bottom sheet for Google, a Custom Tab or an app switch for Facebook, a web
 * auth session for Apple. Ours would be the second layer, and the ticket calls a double scrim a
 * visible bug — hence one flag rather than a scrim that is always on.
 *
 * The scrim under the **conflict modal is ours** on both platforms, because that modal is ours.
 * That is the distinction the acceptance criterion is really asking about: exactly one dim layer
 * in each of the two states, not zero, and not the same owner in both.
 */
const val PLATFORM_DIMS_HANDOFF = true

@Composable
fun ConnectAccountScreen(
    state: ConnectState = ConnectState.Idle,
    provider: AuthMethod = AuthMethod.Apple,
    kind: ErrorKind = ErrorKind.Network,
    /** From the provider. Null or blank is a real case — Apple often shares nothing. */
    firstName: String? = "Leo",
    /** A provider whose credential sub-task is not done ships hidden, not disabled. */
    configured: Set<AuthMethod> = CONNECT_METHODS.toSet(),
    /** Null when the backend cannot return one; the copy has a separate string for that. */
    conflictEmail: String? = "leo@gmail.com",
    /** The provider that OWNS the existing account — not the one the user just tapped. */
    conflictOwner: AuthMethod = AuthMethod.Google,
    onSelect: (AuthMethod) -> Unit = {},
    onSkip: () -> Unit = {},
    onContinue: () -> Unit = {},
    onLinkingTimeout: () -> Unit = {},
    onResolveConflict: (AuthMethod) -> Unit = {},
    onUseDifferentAccount: () -> Unit = {},
    onTerms: () -> Unit = {},
    onPrivacy: () -> Unit = {},
) {
    val methods = availableMethods(CONNECT_METHODS, configured)

    // Idempotency. "Double-tapping a provider creates exactly one link request." The list already
    // disables its buttons once something is in flight, but a second tap can land in the frame
    // before that state arrives, so the latch closes on the first tap and reopens only when the
    // state actually moves.
    var dispatched by remember(state) { mutableStateOf(false) }
    val selectOnce: (AuthMethod) -> Unit = { m -> if (!dispatched) { dispatched = true; onSelect(m) } }

    // E is the one state with no exit of its own, so it gets a clock.
    LaunchedEffect(state, provider) {
        if (state == ConnectState.Linking) {
            delay(LINKING_TIMEOUT_MS)
            onLinkingTimeout()
        }
    }

    // There is no back on this screen — the phone is already verified, so there is nothing behind
    // it, and the platform default (leave the app) is the honest behaviour rather than a handler
    // that silently swallows the gesture. The conflict is the one exception: it has exactly two
    // exits and back is neither of them.
    BackHandler(enabled = state == ConnectState.Conflict) { /* resolve, or use a different account */ }

    Box(Modifier.fillMaxSize()) {
        when (state) {
            ConnectState.Linking -> LinkingHero(provider)
            ConnectState.Success -> SuccessHero(provider, firstName, onContinue)
            else -> MethodListLayout(
                state = state, provider = provider, kind = kind, methods = methods,
                onSelect = selectOnce, onSkip = onSkip, onTerms = onTerms, onPrivacy = onPrivacy,
            )
        }

        // Ours only where the platform draws none. During an OS handoff on Android it does.
        if (state == ConnectState.Handoff && !PLATFORM_DIMS_HANDOFF) Scrim()

        if (state == ConnectState.Conflict) {
            Scrim()
            ConflictSheet(
                email = conflictEmail, owner = conflictOwner,
                onResolve = { onResolveConflict(conflictOwner) },
                onUseDifferent = onUseDifferentAccount,
            )
        }
    }
}

/**
 * rgba(20,12,30,0.42), and deliberately not tappable.
 *
 * No `clickable`, so taps fall through to nothing — an accidental dismiss would drop the user into
 * a list where every provider they own raises this same modal again.
 */
@Composable
private fun Scrim() {
    Box(Modifier.fillMaxSize().background(Color(0x6B140C1E)))
}

// ─────────────────────────────────────────────────────────────
// A / B / C / D / G / H / I — one column, one method list
// ─────────────────────────────────────────────────────────────
@Composable
private fun MethodListLayout(
    state: ConnectState,
    provider: AuthMethod,
    kind: ErrorKind,
    methods: List<AuthMethod>,
    onSelect: (AuthMethod) -> Unit,
    onSkip: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val inFlight = state == ConnectState.Tapped || state == ConnectState.Handoff
    val errored = state == ConnectState.Error
    val short = methodSpec(provider).short

    // Scroll only when the frame runs out, per the 10 September decision.
    //
    // This is the screen the fit sweep had the most to say about: 39 findings across five short
    // phones, every one of them the same shape. The states that add a banner -- cancelled, and
    // both errors -- push the bottom group down until the one weighted spacer has nothing left,
    // and a Column with nothing left shrinks its children in place. The legal line went to zero
    // height and "Skip and continue to profile" was measured at 15dp: the escape hatch from a
    // failed social sign-in, too small to hit, on the phones most likely to be someone's only
    // phone.
    //
    // Nothing changes where it already fits. The inner column is floored at the viewport, so on
    // all twelve devices 390dp and wider the spacer divides the leftover space exactly as before.
    WelcomeScaffold(scrollWhenTight = true) {
        val compact = LocalConfiguration.current.screenHeightDp < 700
        Wordmark()

        // "At 375 x 667 the wordmark -> headline 96 collapses first, then the headline steps
        // 40 -> 34." Those are the only two things allowed to move.
        Spacer(Modifier.height(if (compact) 44.dp else 96.dp))

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
            // The heart trails the headline INSIDE the text, on the baseline of whatever line
            // the text ends on -- see WashHeadline's `trailing`. It used to be a sibling in a Row,
            // which reserved its width against every line: the headline wrapped badly and the
            // heart was pushed off the right edge.
            WashHeadline(
                parts = listOf("Welcome to " to false, "Show Up" to true),
                fontSize = if (compact) 34.sp else 40.sp,
                trailing = BrandIcon.Heart,
            )
            Text(
                // A hard break, not a wrap: the ticket specifies two lines and names the break.
                "Connect an account for easier future sign-ins.\nOr continue and start creating your profile.",
                modifier = Modifier.widthIn(max = 310.dp),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 16.sp, lineHeight = 23.2.sp,
            )
        }

        // ONE weighted spacer, per the reference. The banner and the notice are inserted above the
        // list inside the bottom group, so they eat into this spacer and the buttons keep their
        // distance from the bottom across A, B, G and H.
        Spacer(Modifier.weight(1f))

        AuthMethodList(
            methods = methods,
            onSelect = onSelect,
            loading = if (inFlight) provider else null,
            errorFor = if (errored) provider else null,
            errorMessage = if (errored) when (kind) {
                ErrorKind.Network -> "We couldn't reach $short. Check your connection and try again."
                ErrorKind.Declined -> "$short didn't return a valid sign-in. Try again, or use a different method."
            } else null,
            // The tapped provider holds first position through in-flight, cancel and error, so
            // "try again" is always the same target under the thumb. Idle and the conflict sit
            // underneath in canonical order — the conflict must not reorder anything.
            suggested = provider.takeIf {
                state != ConnectState.Idle && state != ConnectState.Conflict
            },
            notice = if (state == ConnectState.Cancelled) {
                { CancelledNotice(provider) }
            } else null,
            onSkip = onSkip,
        )

        LegalLine(onTerms, onPrivacy)
    }
}

/** "By continuing you agree to our **Terms** and **Privacy Policy**." Both are real links. */
@Composable
private fun LegalLine(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val link = SpanStyle(color = Fg, fontWeight = FontWeight.SemiBold)
    Text(
        buildAnnotatedString {
            append("By continuing you agree to our ")
            withLink(LinkAnnotation.Clickable("terms") { onTerms() }) {
                withStyle(link) { append("Terms") }
            }
            append(" and ")
            withLink(LinkAnnotation.Clickable("privacy") { onPrivacy() }) {
                withStyle(link) { append("Privacy Policy") }
            }
            append(".")
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
        // Muted rather than the reference's fg-subtle: 46% ink measures 3.04:1 and 1.4.3 wants
        // 4.5 for body text. Same substitution as on 140/142/143 — see audit finding 7.
        color = Subtle, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 17.4.sp,
        textAlign = TextAlign.Center,
    )
}

// ─────────────────────────────────────────────────────────────
// E — linking
// ─────────────────────────────────────────────────────────────
@Composable
private fun LinkingHero(provider: AuthMethod) {
    WelcomeScaffold {
        Wordmark()
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                GradientRing()
                Box(
                    Modifier
                        .size(84.dp)
                        .shadow(6.dp, CircleShape, ambientColor = Purple, spotColor = Purple)
                        .clip(CircleShape)
                        .background(Elevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        methodSpec(provider).icon, 42.dp,
                        tint = if (provider == AuthMethod.Apple) Color.Black else Fg,
                        opticalCentre = true,
                    )
                }
            }
            Column(
                Modifier.padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WashHeadline(
                    parts = listOf("Signing you " to false, "in" to true, "…" to false),
                    fontSize = 28.sp, lineHeight = 32.2.sp, letterSpacing = (-0.015).em,
                )
                Text(
                    "Verifying your ${linkingSubject(provider)} and setting things up. This takes a second.",
                    color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 15.sp, lineHeight = 21.75.sp, textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            "Don't close the app.",
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
            color = Subtle, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 17.4.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Verifying your Apple ID" / "your Google account" — the ticket names both forms. */
private fun linkingSubject(m: AuthMethod): String = when (m) {
    AuthMethod.Apple -> "Apple ID"
    AuthMethod.Google -> "Google account"
    AuthMethod.Facebook -> "Facebook account"
    else -> "account"
}

/** 120 box, r 52, 4dp stroke, a 28% arc on the sunset ramp, one turn every 1.4s. */
@Composable
private fun GradientRing() {
    val motion = rememberMotion()
    val angle by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f, targetValue = if (motion.enabled) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE_LONG, easing = LinearEasing), RepeatMode.Restart),
        label = "ringAngle",
    )
    Canvas(Modifier.size(120.dp).rotate(angle)) {
        val w = 4.dp.toPx()
        val r = 52.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Fg.copy(alpha = 0.06f), radius = r, center = c, style = Stroke(w))
        drawArc(
            brush = Brush.linearGradient(listOf(Orange, Purple)),
            startAngle = -90f, sweepAngle = 360f * 0.28f, useCenter = false,
            style = Stroke(w, cap = StrokeCap.Round),
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2),
        )
    }
}

// ─────────────────────────────────────────────────────────────
// F — success
// ─────────────────────────────────────────────────────────────
@Composable
private fun SuccessHero(provider: AuthMethod, firstName: String?, onContinue: () -> Unit) {
    WelcomeScaffold {
        Wordmark()
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(120.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    drawCircle(
                        Brush.radialGradient(
                            0f to Success.copy(alpha = 0.22f),
                            0.7f to Success.copy(alpha = 0f),
                            center = center, radius = r,
                        ),
                        radius = r,
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(88.dp)
                        .shadow(10.dp, CircleShape, ambientColor = Success, spotColor = Success)
                        .clip(CircleShape)
                        .background(Elevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        methodSpec(provider).icon, 44.dp,
                        tint = if (provider == AuthMethod.Apple) Color.Black else Fg,
                        opticalCentre = true,
                    )
                }
                // The 3dp ring is the page background, not white — it has to disappear into the
                // canvas rather than read as a second badge outline.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        // right 4 / bottom 4 inside the 120 box, per the reference — not flush.
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(36.dp)
                        .background(Cream, CircleShape)
                        .padding(3.dp)
                        .background(Success, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BrandIcon.Check, 18.dp, tint = Color.White, strokeWidth = 2.4.dp)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusBadge("${methodSpec(provider).short} connected")
                // No name from the provider is a real case, not a defensive default: Apple's
                // private-relay users often share nothing. The italic run still has to be the
                // emphasis, so the whole sentence changes shape rather than the name being
                // replaced by an empty span — "You're in, null." is the bug this prevents.
                val name = firstName?.trim().orEmpty()
                WashHeadline(
                    parts = if (name.isEmpty()) {
                        listOf("You're " to false, "in" to true, "." to false)
                    } else {
                        listOf("You're " to false, "in" to true, ", $name." to false)
                    },
                    fontSize = 32.sp, lineHeight = 35.2.sp, letterSpacing = (-0.015).em,
                )
                Text(
                    "We'll never post or message anyone on your behalf. Let's finish your profile in 90 seconds.",
                    modifier = Modifier.widthIn(max = 280.dp),
                    color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 15.sp, lineHeight = 21.75.sp, textAlign = TextAlign.Center,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.End) {
            NextButton("Continue", onContinue, variant = NextVariant.Sunset)
        }
    }
}

/** Orange tone: bg orange 12%, orange text, a 5dp dot, Manrope 700 11 uppercase, tracking .08. */
// ─────────────────────────────────────────────────────────────
// I — the conflict modal
// ─────────────────────────────────────────────────────────────
/**
 * Bottom-anchored and content-sized. Never given a height and never centred, so a long address
 * grows it upward instead of clipping, scrolling or truncating.
 *
 * It renders over the mounted screen rather than replacing it, which is what keeps the method list
 * behind it identical before and after a dismiss: nothing re-runs, because nothing unmounted.
 */
@Composable
private fun ConflictSheet(
    email: String?,
    owner: AuthMethod,
    onResolve: () -> Unit,
    onUseDifferent: () -> Unit,
) {
    val short = methodSpec(owner).short
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(8.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            Modifier
                .shadow(30.dp, RoundedCornerShape(Radius.pill), ambientColor = Purple, spotColor = Purple)
                .clip(RoundedCornerShape(Radius.pill))
                .background(Elevated)
                .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = 26.dp, bottom = 22.dp),
        ) {
            // Orange, not danger. Nothing failed here — the account simply exists.
            Box(
                Modifier.size(IconSizes.badge).background(Orange.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BrandIcon.Shield, 26.dp, tint = Orange, strokeWidth = 1.8.dp)
            }
            Spacer(Modifier.height(Spacing.xxl))

            // One plain run, so no wash: there is no emphasis phrase in this headline, and an
            // empty <em> would paint a glow under nothing.
            WashHeadline(
                parts = listOf("You already have an account." to false),
                fontSize = 26.sp, lineHeight = 29.9.sp, letterSpacing = (-0.015).em,
            )
            Spacer(Modifier.height(Spacing.lg))

            Text(
                buildAnnotatedString {
                    // Both values are interpolated. With no address the sentence changes shape
                    // rather than rendering an empty bold span or a masked placeholder — naming
                    // the account is the modal's whole job, so a blank there is worse than a
                    // different sentence.
                    if (email.isNullOrBlank()) {
                        append("That account is already on Show Up — you signed up with $short. ")
                    } else {
                        withStyle(SpanStyle(color = Fg, fontWeight = FontWeight.Bold)) { append(email) }
                        append(" is already on Show Up — you signed up with $short. ")
                    }
                    append("Continue with $short to pick up where you left off.")
                },
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 15.sp, lineHeight = 21.75.sp,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                // The product reason, not boilerplate — it is why no second account is offered.
                // Verbatim, per the ticket.
                "One person, one account. Show-up Rates only work if you can't start over.",
                color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 13.sp, lineHeight = 18.2.sp,
            )
            Spacer(Modifier.height(22.dp))

            // Names the OWNING provider, not the one the user tapped: tapping Apple on a
            // Google-owned account offers Continue with Google. Getting this backwards sends the
            // user round a loop. It wears that provider's own button, because the ticket lists
            // this CTA alongside the three on the method list.
            PrimaryButton(
                methodSpec(owner).label, onResolve,
                variant = providerVariant(owner),
                height = 54.dp,
                leading = { Icon(methodSpec(owner).icon, 18.dp, tint = providerTint(owner)) },
            )
            Spacer(Modifier.height(Spacing.md))

            // No border, so the pair never reads as two equal choices. This is the only dismiss:
            // there is no close icon and the scrim above does not accept taps.
            PrimaryButton(
                "Use a different account", onUseDifferent,
                variant = PrimaryButtonVariant.Plain, height = 50.dp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Previews — the seven states the ticket wants evidence for, at all three sizes
// ─────────────────────────────────────────────────────────────
@Preview(name = "A idle · 390x844", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CAIdle() { ConnectAccountScreen() }

@Preview(name = "A idle · 375x667", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun CAIdleSmall() { ConnectAccountScreen() }

@Preview(name = "A idle · 430x932", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun CAIdleLarge() { ConnectAccountScreen() }

@Preview(name = "B tapped · Apple", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CATapped() { ConnectAccountScreen(state = ConnectState.Tapped) }

@Preview(name = "B tapped · Google", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CATappedGoogle() {
    ConnectAccountScreen(state = ConnectState.Tapped, provider = AuthMethod.Google)
}

@Preview(name = "E linking", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CALinking() { ConnectAccountScreen(state = ConnectState.Linking) }

@Preview(name = "F success", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CASuccess() { ConnectAccountScreen(state = ConnectState.Success) }

@Preview(name = "F success · no name", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CASuccessNoName() { ConnectAccountScreen(state = ConnectState.Success, firstName = null) }

@Preview(name = "G cancelled", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CACancelled() { ConnectAccountScreen(state = ConnectState.Cancelled) }

@Preview(name = "H error · network", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CAErrorNetwork() { ConnectAccountScreen(state = ConnectState.Error) }

@Preview(name = "H error · declined", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CAErrorDeclined() {
    ConnectAccountScreen(state = ConnectState.Error, provider = AuthMethod.Google, kind = ErrorKind.Declined)
}

@Preview(name = "I conflict", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CAConflict() { ConnectAccountScreen(state = ConnectState.Conflict) }

@Preview(name = "I conflict · 38-char address, 375x667", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun CAConflictLongEmail() {
    ConnectAccountScreen(state = ConnectState.Conflict, conflictEmail = "leonhard.schwarzkopf@studio-mantis.com")
}

@Preview(name = "I conflict · no address", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CAConflictNoEmail() {
    ConnectAccountScreen(state = ConnectState.Conflict, conflictEmail = null)
}

@Preview(name = "A idle · Facebook cut from v1", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CAWithoutFacebook() {
    ConnectAccountScreen(configured = setOf(AuthMethod.Apple, AuthMethod.Google))
}
