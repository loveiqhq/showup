/*
 * SignUpFlow.kt
 * ShowUp · the walkable sign-up flow — Startup → phone → code → connect (SHOWUP-140/142/143/144)
 *
 * This is the piece that turns five rendered screens into something a person can actually walk
 * through. Every screen stays pure: it takes values and emits events. All the state lives here.
 *
 * DEV SCAFFOLDING, CLEARLY MARKED. Two things in this file are stand-ins for services that do not
 * exist yet, and both are gathered into [DevAuth] so they are one edit to remove:
 *
 *   1. The verification code is fixed at [DevAuth.TEST_CODE]. Twilio is not connected, so no SMS is
 *      sent and no server checks anything. The code is shown on screen in a dev strip, because a
 *      test flow you cannot get through is not a test flow.
 *   2. The resend "sends" nothing. It restarts the cooldown, which is the only visible behaviour.
 *
 * Nothing else here is fake. The typing, the validation, the country list, the error states, the
 * cooldown timer, the routing and the back behaviour are all real and all survive Twilio landing.
 */
package com.showup.welcome

import com.showup.designsystem.Spacing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.analytics.AnalyticsTracker
import com.showup.analytics.NoOpAnalytics
import com.showup.analytics.SignUpAnalytics
import com.showup.designsystem.Manrope
import kotlinx.coroutines.delay

/** Everything that stands in for a backend. Delete this object and the compiler finds every use. */
object DevAuth {
    /**
     * The code that "works" until Twilio is wired up.
     *
     * Six digits, because SHOWUP-143 specifies six and the slots are built for six. Deliberately
     * not 123456: that is the first thing anyone tries by accident, and it would hide the mismatch
     * state — which is a state we need to be able to demonstrate.
     */
    const val TEST_CODE = "480726"

    /**
     * Seconds before a resend is offered. Real cooldown, fake send.
     *
     * 60, matching the server's `OTP_RESEND_COOLDOWN`. It was 30, which is the mismatch the
     * product side ruled against for email on 10 September 2026 -- and the phone screen had it
     * too: the link would go live at 30s and `/auth/phone/start` would answer 429 for another 30.
     * Latent only because DevAuth sends nothing yet, so it would have surfaced the day Twilio
     * landed. One number, and the server owns it.
     */
    const val RESEND_COOLDOWN = 60

    /** Set false to hide the on-screen hint without removing the fixed code. */
    const val SHOW_HINT = true
}

/** Where the user is. One flat enum — this flow has no nesting and no side routes. */
private enum class Step { Startup, WelcomeBack, Phone, Code, Connect }

/**
 * What the device remembers about the last person to sign in on it.
 *
 * Null means nobody — a fresh install, or after "Use a different account" cleared it. That is a
 * real state, not a missing value, and it decides two things: which screen launch opens, and
 * whether Welcome back can greet anyone by name.
 */
data class RememberedAccount(val name: String, val lastUsed: AuthMethod)

@Composable
fun SignUpFlow(
    /**
     * Null on a device that has never been signed in on, which is where a new install starts.
     *
     * SHOWUP-140: "Renders on first launch only. If a device already has an account, the app opens
     * Welcome back instead." This is that condition.
     */
    remembered: RememberedAccount? = null,
    /**
     * Where the flow leaves the user. SHOWUP-146: [SignUpOutcome.NewAccount] is shown the
     * tutorial, [SignUpOutcome.ReturningMember] goes straight into the app.
     */
    onFinished: (SignUpOutcome) -> Unit = {},
    onOpenLegal: (String) -> Unit = {},
    /**
     * Where events go. NoOp by default, so nothing is sent and the flow behaves identically
     * whether or not analytics is switched on -- which is also what makes it testable.
     */
    analytics: AnalyticsTracker = NoOpAnalytics,
) {
    var step by rememberSaveable { mutableStateOf(if (remembered != null) Step.WelcomeBack else Step.Startup) }
    var account by remember { mutableStateOf(remembered) }

    // SHOWUP-146. Which door the user came through decides, at the far end, whether they are
    // shown the tutorial. A launch straight onto Welcome back is a log-in by definition -- the
    // device would not remember anyone otherwise.
    var entry by rememberSaveable {
        mutableStateOf(if (remembered != null) Entry.LogIn else Entry.CreateAccount)
    }

    // rememberSaveable throughout: a rotation must not empty the field the user is typing into.
    var country by rememberSaveable(stateSaver = CountrySaver) {
        mutableStateOf(DEFAULT_COUNTRY)
    }
    /**
     * Whether the locale default below has already had its one turn.
     *
     * Saved, and that is the entire point. `CountrySaver` restored the country correctly and the
     * locale effect then overwrote it on the next composition, so a country the user had picked
     * survived a rotation but not a process death -- a saver that worked and was undone one line
     * later. The flag cannot be derived from `country` itself: the initial value is
     * [DEFAULT_COUNTRY] and "restored Germany" is indistinguishable from "has not chosen yet".
     */
    var localeDefaultApplied by rememberSaveable { mutableStateOf(false) }
    var phoneDigits by rememberSaveable { mutableStateOf("") }
    var phoneError by rememberSaveable { mutableStateOf<PhoneError?>(null) }
    var showCountrySheet by rememberSaveable { mutableStateOf(false) }

    var codeDigits by rememberSaveable { mutableStateOf("") }
    var codeMismatch by rememberSaveable { mutableStateOf(false) }
    var cooldown by rememberSaveable { mutableIntStateOf(DevAuth.RESEND_COOLDOWN) }

    // Tracking-only state. SHOWUP-143 wants the attempt number on a failed verify and whether a
    // resend followed a mismatch; neither is derivable from the screen's own state, because the
    // mismatch flag is cleared the moment the user edits a digit.
    var verifyAttempts by rememberSaveable { mutableIntStateOf(0) }
    var lastVerifyFailed by rememberSaveable { mutableStateOf(false) }

    /** Reports an event built by the SignUpAnalytics catalogue. */
    fun track(pair: Pair<String, Map<String, Any>>) = analytics.track(pair.first, pair.second)

    // Screenviews. Keyed on `step`, so each fires once when the screen becomes visible and again
    // if the user comes back to it -- which is the behaviour a funnel needs.
    LaunchedEffect(step) {
        when (step) {
            Step.Startup -> track(SignUpAnalytics.screenViewed(SignUpAnalytics.Screen.CREATE_ACCOUNT))
            // SHOWUP-142 asks for the lastUsed value on the screenview, including `unknown`.
            Step.WelcomeBack -> track(
                SignUpAnalytics.screenViewed(
                    SignUpAnalytics.Screen.WELCOME_BACK,
                    mapOf("last_used" to (account?.lastUsed?.name?.lowercase() ?: "unknown")),
                )
            )
            Step.Phone -> track(SignUpAnalytics.screenViewed(SignUpAnalytics.Screen.PHONE_NUMBER))
            Step.Code -> track(SignUpAnalytics.screenViewed(SignUpAnalytics.Screen.CODE_ENTRY))
            // SHOWUP-144: one screenview with a `state` property, not ten. See CONFLICTS A8.
            Step.Connect -> track(
                SignUpAnalytics.screenViewed(
                    SignUpAnalytics.Screen.CONNECT_SSO,
                    mapOf("state" to "idle"),
                )
            )
        }
    }

    // The country pill defaults from device locale — SHOWUP-143 asks for exactly this, and the
    // region the platform reports is the same ISO key the table is built on.
    //
    // ONCE. LaunchedEffect(Unit) runs again on a fresh composition, which is exactly what a process
    // death produces, so unguarded this ran after the state had been restored and replaced the
    // user's choice with the locale's. First launch is unchanged: the flag starts false, the
    // default is applied, and nothing about SHOWUP-143's behaviour moves.
    val configuration = LocalConfiguration.current
    LaunchedEffect(Unit) {
        if (!localeDefaultApplied) {
            @Suppress("DEPRECATION")
            val region = configuration.locales.get(0)?.country
            country = countryForRegion(region)
            localeDefaultApplied = true
        }
    }

    // One ticking clock for the resend. Restarts whenever the cooldown is reset.
    LaunchedEffect(step, cooldown) {
        if (step == Step.Code && cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    /** The number as it is shown back to the user on the code screen. */
    val fullNumber = "${country.dial} ${formatNational(phoneDigits, country)}"

    Box(Modifier.fillMaxWidth()) {
        when (step) {
            Step.Startup -> StartupScreen(
                // ON here, and OFF in the component's default, which is not a contradiction.
                //
                // This target is the preview the spec sheet is reviewed against, and the sheet
                // draws the row: turning it off here would hide it from design review and from
                // anyone walking the flow on a device. The figure itself is the sheet's own
                // placeholder — "234.000" is not a measured number and SHOWUP-140 says so.
                //
                // In the real app it stays OFF until the count is real, because a fabricated
                // statistic on the first screen a user ever sees is a claim, not a mock. That is
                // what the component's `false` default protects.
                //
                // Matches iOS, which passes `true` here for the same reason.
                showSocialProof = true,
                onCreateAccount = {
                    analytics.track(SignUpAnalytics.CREATE_ACCOUNT_TAPPED, emptyMap())
                    entry = Entry.CreateAccount
                    step = Step.Phone
                },
                onLogin = {
                    analytics.track(SignUpAnalytics.LOG_IN_TAPPED, emptyMap())
                    entry = Entry.LogIn
                    step = Step.WelcomeBack
                },
                onTerms = {
                    track(SignUpAnalytics.legalLinkTapped(
                        SignUpAnalytics.Legal.TERMS, SignUpAnalytics.Screen.CREATE_ACCOUNT))
                    onOpenLegal("Terms & Conditions")
                },
                onPrivacy = {
                    track(SignUpAnalytics.legalLinkTapped(
                        SignUpAnalytics.Legal.PRIVACY, SignUpAnalytics.Screen.CREATE_ACCOUNT))
                    onOpenLegal("Privacy Policy")
                },
                onLegalNotice = {
                    track(SignUpAnalytics.legalLinkTapped(
                        SignUpAnalytics.Legal.LEGAL_NOTICE, SignUpAnalytics.Screen.CREATE_ACCOUNT))
                    onOpenLegal("Legal Notice")
                },
            )

            Step.WelcomeBack -> WelcomeBackScreen(
                // Empty name and Unknown method when the device remembers nobody — which is
                // exactly what tapping "Log in" on Startup means: someone who has an account but
                // not on THIS device. The screen already handles it: the headline drops to a plain
                // "Welcome back" with no name, and the "last login was via…" hint disappears
                // rather than claiming a method that never happened here.
                name = account?.name.orEmpty(),
                lastUsed = account?.lastUsed ?: AuthMethod.Unknown,
                onContinue = { method ->
                    // SHOWUP-142 wants `method` and `is_last_used` on every auth-method tap,
                    // including the three providers that go nowhere yet -- the intent to use them
                    // is exactly what the funnel needs to know.
                    track(SignUpAnalytics.authMethodTapped(
                        method = method.name.lowercase(),
                        isLastUsed = account?.lastUsed == method,
                    ))
                    // Phone is the one method that goes anywhere: it is ours, and 143 is built.
                    // The three providers are live targets with nothing behind them yet — their
                    // SDK work is the sub-tasks on SHOWUP-144.
                    if (method == AuthMethod.Phone) {
                        // Reaching this screen at all means logging in, whether the user tapped
                        // "Log in" on Startup or the app opened here on a remembered device.
                        entry = Entry.LogIn
                        phoneError = null
                        step = Step.Phone
                    }
                },
                onGetHelp = {
                    analytics.track(SignUpAnalytics.GET_HELP_TAPPED, emptyMap())
                    onOpenLegal("Get help")
                },
                // Clears the remembered account and returns to Startup, per SHOWUP-142. Clearing
                // it is the point -- coming back to this screen afterwards must not still know
                // the old name.
                onUseDifferentAccount = {
                    analytics.track(SignUpAnalytics.USE_DIFFERENT_ACCOUNT_TAPPED, emptyMap())
                    account = null
                    step = Step.Startup
                },
                onLegal = {
                    track(SignUpAnalytics.legalLinkTapped(
                        SignUpAnalytics.Legal.LEGAL_NOTICE, SignUpAnalytics.Screen.WELCOME_BACK))
                    onOpenLegal("Legal Notice")
                },
                onPrivacy = {
                    track(SignUpAnalytics.legalLinkTapped(
                        SignUpAnalytics.Legal.PRIVACY, SignUpAnalytics.Screen.WELCOME_BACK))
                    onOpenLegal("Privacy Policy")
                },
            )

            Step.Phone -> {
                BackHandler { step = Step.Startup }
                PhoneNumberScreen(
                    value = phoneDigits,
                    onValueChange = {
                        phoneDigits = it
                        // Validation runs on submit, so a rejected number clears its error the
                        // moment the user starts fixing it rather than nagging while they type.
                        phoneError = null
                    },
                    country = country,
                    error = phoneError,
                    onBack = { step = Step.Startup },
                    onSubmit = {
                        analytics.track(SignUpAnalytics.PHONE_SUBMITTED, emptyMap())
                        val problem = validate(phoneDigits, country)
                        phoneError = problem
                        if (problem != null) {
                            // Our outcome, not the ticket's three-value vocabulary -- see the note
                            // on PHONE_VALIDATION_FAILED. Reporting a reason the code cannot
                            // produce would describe something that did not happen.
                            track(SignUpAnalytics.phoneValidationFailed(
                                reason = problem.name.lowercase(),
                                country = country.iso,
                            ))
                        }
                        if (problem == null) {
                            codeDigits = ""
                            codeMismatch = false
                            cooldown = DevAuth.RESEND_COOLDOWN
                            step = Step.Code
                        }
                    },
                    onOpenCountryList = { showCountrySheet = true },
                )
            }

            Step.Code -> {
                // Back and "Edit phone number" are the same journey, so they behave identically:
                // return to A with the number intact, which is what SHOWUP-143 requires.
                BackHandler { step = Step.Phone }
                VerifyCodeScreen(
                    phone = fullNumber,
                    digits = codeDigits,
                    onDigitsChange = {
                        codeDigits = it
                        codeMismatch = false
                    },
                    mismatch = codeMismatch,
                    cooldownSeconds = cooldown,
                    onBack = { step = Step.Phone },
                    onVerify = {
                        analytics.track(SignUpAnalytics.CODE_SUBMITTED, emptyMap())
                        verifyAttempts += 1
                        if (codeDigits == DevAuth.TEST_CODE) {
                            // SHOWUP-146. Connect (SHOWUP-144) belongs to account creation: it is
                            // where a brand-new account is offered a provider to link. Someone
                            // signing back in has been past it already, so they skip both it and
                            // the tutorial and land in the app.
                            if (entry == Entry.LogIn) {
                                onFinished(outcomeOf(entry, null))
                            } else {
                                step = Step.Connect
                            }
                        } else {
                            codeMismatch = true
                            lastVerifyFailed = true
                            track(SignUpAnalytics.codeVerifyFailed(verifyAttempts))
                            // A mistyped code must not cost another wait — the ticket says the
                            // mismatch releases the cooldown, so the resend is live immediately.
                            cooldown = 0
                        }
                    },
                    onResend = {
                        // SHOWUP-143 wants how long the user waited and whether this followed a
                        // mismatch. The cooldown counts DOWN from the full value, so the time
                        // actually waited is the difference -- and after a mismatch it is released
                        // to 0, which would otherwise read as a full wait.
                        track(SignUpAnalytics.resendRequested(
                            secondsWaited = DevAuth.RESEND_COOLDOWN - cooldown,
                            afterMismatch = lastVerifyFailed,
                        ))
                        lastVerifyFailed = false
                        codeDigits = ""
                        codeMismatch = false
                        cooldown = DevAuth.RESEND_COOLDOWN
                    },
                    onEditNumber = {
                        analytics.track(SignUpAnalytics.EDIT_PHONE_TAPPED, emptyMap())
                        step = Step.Phone
                    },
                )
            }

            // SHOWUP-144. Its own host drives the ten states. Every one of its exits leaves the
            // sign-up flow; SHOWUP-146 decides which of the two destinations it leaves for.
            Step.Connect -> ConnectFlowHost(
                onDone = { exit -> onFinished(outcomeOf(entry, exit)) },
                onOpenLegal = onOpenLegal,
                analytics = analytics,
            )
        }

        if (showCountrySheet) {
            CountrySheet(
                current = country,
                onPick = {
                    country = it
                    // The old number was validated against the old country's rules, so the verdict
                    // no longer means anything. Clearing it is honest; keeping it would show an
                    // error naming the wrong country.
                    phoneError = null
                    showCountrySheet = false
                },
                onDismiss = { showCountrySheet = false },
            )
        }

        // ── dev strip ────────────────────────────────────────────────────────
        // Only on the code screen, only while the code is fixed. Goes away with DevAuth.
        if (DevAuth.SHOW_HINT && step == Step.Code) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = Spacing.xs)
                    .background(Color(0xE61D1129), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = Spacing.xl, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "TEST BUILD", color = Color(0xFFFFAE8F), fontFamily = Manrope,
                    fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.7.sp,
                )
                Text(
                    "no SMS is sent · the code is ${DevAuth.TEST_CODE}",
                    color = Color.White, fontFamily = Manrope,
                    fontWeight = FontWeight.Medium, fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Countries survive a rotation as their ISO code rather than as an object.
 *
 * rememberSaveable can only store primitives, and the alternative — re-deriving the country from
 * locale after every rotation — would silently undo a choice the user made by hand.
 */
private val CountrySaver = androidx.compose.runtime.saveable.Saver<Country, String>(
    save = { it.iso },
    restore = { iso -> COUNTRIES.firstOrNull { it.iso == iso } ?: DEFAULT_COUNTRY },
)
