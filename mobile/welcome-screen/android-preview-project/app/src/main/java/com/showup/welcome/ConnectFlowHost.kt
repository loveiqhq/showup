/*
 * ConnectFlowHost.kt
 * ShowUp · demo driver for the Connect flow (SHOWUP-144)
 *
 * **Scaffolding, not product.** The real screen is [ConnectAccountScreen], which is pure: it takes
 * a state and renders it. This host fakes the round trip that a provider SDK and our link-identity
 * endpoint would drive, so the flow can be walked on a device before either exists.
 *
 * It deliberately cycles through a different ending on each attempt — success, cancel, network
 * error, declined, conflict — so a reviewer reaches every branch by tapping the same button five
 * times rather than needing five differently-broken accounts. When the real integration lands, this
 * file is deleted and a view model takes its place; nothing in ConnectAccountScreen changes.
 */
package com.showup.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.showup.analytics.AnalyticsTracker
import com.showup.analytics.NoOpAnalytics
import com.showup.analytics.SignUpAnalytics
import kotlinx.coroutines.delay

@Composable
fun ConnectFlowHost(
    onDone: (ConnectExit) -> Unit,
    /**
     * SHOWUP-144's twelve events are reported from here rather than from the screen, because this
     * is what owns the state transitions -- and several of the events ARE transitions rather than
     * taps: link succeeded, link failed, linking timeout, conflict raised.
     *
     * This host is scaffolding for the provider SDKs that do not exist yet. When they arrive the
     * transitions move with them, and these calls move too; the event names and properties do not.
     */
    analytics: AnalyticsTracker = NoOpAnalytics,
) {
    var state by remember { mutableStateOf(ConnectState.Idle) }
    var provider by remember { mutableStateOf(AuthMethod.Apple) }
    var kind by remember { mutableStateOf(ErrorKind.Network) }
    var attempt by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    // "repeat conflicts in one session" is a named event in SHOWUP-144, so the count has to be
    // kept: the second conflict is a different signal from the first.
    var conflicts by remember { mutableIntStateOf(0) }

    fun track(pair: Pair<String, Map<String, Any>>) = analytics.track(pair.first, pair.second)
    fun providerName() = provider.name.lowercase()

    // The fake round trip. Keyed on `running` so a tap starts exactly one of these, which is also
    // what makes the screen's own double-tap latch observable rather than theoretical.
    LaunchedEffect(running, attempt) {
        if (!running) return@LaunchedEffect
        state = ConnectState.Tapped
        delay(500)
        state = ConnectState.Handoff
        delay(1400)
        when (attempt % 5) {
            0 -> {
                state = ConnectState.Linking
                delay(1600)
                state = ConnectState.Success
                track(SignUpAnalytics.provider(SignUpAnalytics.LINK_SUCCEEDED, providerName()))
            }
            // The user closed the provider sheet before it finished. Distinct from an error: no
            // failure happened, they changed their mind.
            1 -> {
                state = ConnectState.Cancelled
                track(SignUpAnalytics.provider(SignUpAnalytics.SHEET_DISMISSED, providerName()))
            }
            2 -> {
                kind = ErrorKind.Network
                state = ConnectState.Error
                track(SignUpAnalytics.linkFailed(providerName(), "network"))
            }
            3 -> {
                kind = ErrorKind.Declined
                state = ConnectState.Error
                track(SignUpAnalytics.linkFailed(providerName(), "declined"))
            }
            else -> {
                state = ConnectState.Linking
                delay(1200)
                state = ConnectState.Conflict
                conflicts += 1
                track(SignUpAnalytics.provider(SignUpAnalytics.CONFLICT_RAISED, providerName()))
                // Reported IN ADDITION to conflict_raised, not instead of it, so the plain count
                // of conflicts stays correct.
                if (conflicts > 1) {
                    track(SignUpAnalytics.conflictRepeated(conflicts, providerName()))
                }
            }
        }
        attempt += 1
        running = false
    }

    ConnectAccountScreen(
        state = state,
        provider = provider,
        kind = kind,
        onSelect = { m ->
            provider = m
            track(SignUpAnalytics.provider(SignUpAnalytics.PROVIDER_TAPPED, m.name.lowercase()))
            running = true
        },
        // SHOWUP-146 needs to tell these three apart, so the host reports which one
        // happened rather than collapsing them into a bare "done".
        onSkip = {
            analytics.track(SignUpAnalytics.SKIP_TAPPED, emptyMap())
            onDone(ConnectExit.Skipped)
        },
        onContinue = { onDone(ConnectExit.Connected) },
        // The 8s cap firing is a real transition, not a demo shortcut.
        onLinkingTimeout = {
            // The 8-second cap in SHOWUP-144's acceptance criteria. Reported separately from
            // link_failed even though it lands on the same error state: a timeout and a refusal
            // are different problems with different fixes.
            track(SignUpAnalytics.provider(SignUpAnalytics.LINKING_TIMEOUT, providerName()))
            kind = ErrorKind.Network
            state = ConnectState.Error
        },
        onResolveConflict = {
            track(SignUpAnalytics.provider(SignUpAnalytics.CONFLICT_RESOLVE_TAPPED, providerName()))
            onDone(ConnectExit.ResolvedConflict)
        },
        onUseDifferentAccount = {
            analytics.track(SignUpAnalytics.CONFLICT_DIFFERENT_ACCOUNT_TAPPED, emptyMap())
            state = ConnectState.Idle
        },
    )
}
