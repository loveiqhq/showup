/*
 * SignUpFlow.kt
 * ShowUp · the walkable sign-up flow — Startup → phone → code → connect (SHOWUP-140/142/143/144)
 *
 * This is the piece that turns five rendered screens into something a person can actually walk
 * through. Every screen stays pure: it takes values and emits events. All the state lives here.
 *
 * NOTHING HERE IS A STAND-IN ANY MORE. This file used to open with a scaffolding notice: the
 * code was a constant, the resend restarted a local timer, and a `DevAuth` object held both so
 * they were one edit to remove. That edit has happened.
 *
 *   - The code is real. `/auth/phone/start` asks the backend to send one and
 *     `/auth/phone/verify` confirms it, returning a JWT pair written to the encrypted store.
 *   - The resend really sends, and the countdown counts to the server's `resendAvailableAt`
 *     rather than down from a number this file chose.
 *
 * No SMS provider is involved and none is needed. `LogSmsSender` is the only sender the backend
 * has; it writes the code to the server log, and `AUTH_EXPOSE_OTP` -- on unless NODE_ENV is
 * production -- also returns it on the challenge, which is what the debug-only strip displays.
 * A complete signup is walkable for nothing, and switching a paid provider on later changes one
 * binding in `auth.module.ts` and nothing in this file.
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.showup.BuildConfig
import com.showup.api.MAX_VERIFY_ATTEMPTS
import com.showup.api.RESEND_COOLDOWN_SECONDS
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
/**
 * Numbers the client mirrors from the backend, and one it uses only for arithmetic.
 *
 * WHAT THIS USED TO BE
 *
 * A fake. It held a fixed code the app compared against, a cooldown the app counted down on its
 * own, and a banner announcing both. All three are gone: the flow now calls
 * `/auth/phone/start` and `/auth/phone/verify`, and every number it shows comes from the
 * response. What is left is not a stand-in for a backend — it is the backend's own values,
 * written down where the client needs them.
 *
 * The name is kept because renaming an object referenced from three files and a test is churn
 * that would bury this explanation in a diff. It is on the list.
 */
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
    /**
     * The asynchronous half of this flow: sending a code, confirming it, and the countdown.
     *
     * Nullable ONLY so the previews and ScreenFitTest can render every screen without a backend.
     * A null model cannot sign anyone in — it renders the same screens with a default state and
     * inert actions, which is what a preview should be. The app always passes one.
     */
    auth: PhoneAuthViewModel? = null,
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

    // The countdown, the attempt count and the mismatch flag now belong to the model, because
    // every one of them is decided by a server response rather than by this composable. What is
    // left here is what the user typed.
    val authState by (auth?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(PhoneAuthState()) })
    val cooldown = authState.cooldownSeconds
    val codeMismatch = authState.lastSubmitRefused
    val verifyAttempts = authState.attempts

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

    // No ticking clock here any more. The model recomputes the countdown from the server's
    // resendAvailableAt once a second, so a device that slept through half the window wakes up
    // with the right number rather than one that was decremented while it was asleep.

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
                            // Advance only once the server has accepted the request. Moving
                            // first would put the user on a code screen waiting for an SMS that
                            // was never dispatched.
                            auth?.start(country.e164(phoneDigits)) { step = Step.Code }
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
                        auth?.clearRefusal()
                    },
                    mismatch = codeMismatch,
                    lockedOut = verifyAttempts >= MAX_VERIFY_ATTEMPTS,
                    cooldownSeconds = cooldown,
                    onBack = { step = Step.Phone },
                    onVerify = {
                        // Locked out: the server would refuse this, so the client does not ask.
                        // The CTA is already disabled in that state; this is the second guard,
                        // for a submit arriving from the keyboard's action key.
                        if (verifyAttempts >= MAX_VERIFY_ATTEMPTS) return@VerifyCodeScreen
                        analytics.track(SignUpAnalytics.CODE_SUBMITTED, emptyMap())
                        auth?.verify(country.e164(phoneDigits), codeDigits) { profileComplete ->
                            // SHOWUP-146, decided by the PROFILE rather than by what the user
                            // said they were doing. Connect and the tutorial belong to building
                            // an account; someone whose profile is already complete has been
                            // past both, whichever button they tapped to get here.
                            //
                            // Completeness rather than an "is this account new" flag because it
                            // survives an interrupted signup: a user who quit halfway through
                            // profile creation is not new, but must not be sent to Home.
                            if (profileComplete) {
                                onFinished(outcomeOf(Entry.LogIn, null))
                            } else {
                                step = Step.Connect
                            }
                        }
                    },
                    onResend = {
                        // SHOWUP-143 wants how long the user waited and whether this followed a
                        // mismatch. The cooldown counts DOWN from the full value, so the time
                        // actually waited is the difference -- and after a mismatch it is released
                        // to 0, which would otherwise read as a full wait.
                        track(SignUpAnalytics.resendRequested(
                            // Seconds actually waited, from the server's own window rather than
                            // from a local constant that no longer exists.
                            secondsWaited = authState.resendAvailableAt
                                ?.let { java.time.Duration.between(java.time.OffsetDateTime.now(), it).seconds }
                                ?.let { remaining -> (RESEND_COOLDOWN_SECONDS - remaining).coerceAtLeast(0L).toInt() }
                                ?: 0,
                            // READ FROM THE MODEL, not from a flag this composable maintains.
                            // It was such a flag, and nothing ever set it to true, so the
                            // property shipped as a constant false that no test could see. The
                            // count cannot drift the same way: the server increments it and
                            // `start` resets it, so "attempts against this challenge" is true by
                            // construction.
                            afterMismatch = verifyAttempts > 0,
                        ))
                        codeDigits = ""
                        // A new code is a new challenge; the model resets the attempt count and
                        // adopts the server's fresh resendAvailableAt.
                        auth?.start(country.e164(phoneDigits))
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
        //
        // Shows the code the SERVER generated, not a constant the app invented. It is present
        // because `LogSmsSender` is the only sender the backend has and `AUTH_EXPOSE_OTP`
        // returns the code outside production — so a signup is testable with no paid provider
        // and without reading server logs.
        //
        // THREE conditions, and each removes a different way this could leak. BuildConfig.DEBUG
        // keeps it out of any release binary; the null check keeps it absent when a server
        // chooses not to expose it; and the step check keeps it off every other screen. A
        // release build with a misconfigured server still shows nothing.
        val devCode = authState.devCode
        if (BuildConfig.DEBUG && devCode != null && step == Step.Code) {
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
                    "logged, not sent · the code is $devCode",
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
