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

    /** Seconds before a resend is offered. Real cooldown, fake send. */
    const val RESEND_COOLDOWN = 30

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
    var phoneDigits by rememberSaveable { mutableStateOf("") }
    var phoneError by rememberSaveable { mutableStateOf<PhoneError?>(null) }
    var showCountrySheet by rememberSaveable { mutableStateOf(false) }

    var codeDigits by rememberSaveable { mutableStateOf("") }
    var codeMismatch by rememberSaveable { mutableStateOf(false) }
    var cooldown by rememberSaveable { mutableIntStateOf(DevAuth.RESEND_COOLDOWN) }

    // The country pill defaults from device locale — SHOWUP-143 asks for exactly this, and the
    // region the platform reports is the same ISO key the table is built on.
    val configuration = LocalConfiguration.current
    LaunchedEffect(Unit) {
        @Suppress("DEPRECATION")
        val region = configuration.locales.get(0)?.country
        country = countryForRegion(region)
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
                // The dates figure is hidden until the number is worth showing — the minimum is
                // still to be decided, so the toggle is off rather than the figure invented.
                showSocialProof = false,
                onCreateAccount = { entry = Entry.CreateAccount; step = Step.Phone },
                onLogin = { entry = Entry.LogIn; step = Step.WelcomeBack },
                onTerms = { onOpenLegal("Terms & Conditions") },
                onPrivacy = { onOpenLegal("Privacy Policy") },
                onLegalNotice = { onOpenLegal("Legal Notice") },
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
                onGetHelp = { onOpenLegal("Get help") },
                // Clears the remembered account and returns to Startup, per SHOWUP-142. Clearing
                // it is the point -- coming back to this screen afterwards must not still know
                // the old name.
                onUseDifferentAccount = { account = null; step = Step.Startup },
                onLegal = { onOpenLegal("Legal Notice") },
                onPrivacy = { onOpenLegal("Privacy Policy") },
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
                        val problem = validate(phoneDigits, country)
                        phoneError = problem
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
                            // A mistyped code must not cost another wait — the ticket says the
                            // mismatch releases the cooldown, so the resend is live immediately.
                            cooldown = 0
                        }
                    },
                    onResend = {
                        codeDigits = ""
                        codeMismatch = false
                        cooldown = DevAuth.RESEND_COOLDOWN
                    },
                    onEditNumber = { step = Step.Phone },
                )
            }

            // SHOWUP-144. Its own host drives the ten states. Every one of its exits leaves the
            // sign-up flow; SHOWUP-146 decides which of the two destinations it leaves for.
            Step.Connect -> ConnectFlowHost(
                onDone = { exit -> onFinished(outcomeOf(entry, exit)) },
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
                    .padding(top = 4.dp)
                    .background(Color(0xE61D1129), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
