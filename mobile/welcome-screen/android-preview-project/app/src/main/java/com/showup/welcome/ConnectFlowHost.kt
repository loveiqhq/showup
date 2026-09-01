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
import kotlinx.coroutines.delay

@Composable
fun ConnectFlowHost(onDone: (ConnectExit) -> Unit) {
    var state by remember { mutableStateOf(ConnectState.Idle) }
    var provider by remember { mutableStateOf(AuthMethod.Apple) }
    var kind by remember { mutableStateOf(ErrorKind.Network) }
    var attempt by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }

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
            }
            1 -> state = ConnectState.Cancelled
            2 -> { kind = ErrorKind.Network; state = ConnectState.Error }
            3 -> { kind = ErrorKind.Declined; state = ConnectState.Error }
            else -> {
                state = ConnectState.Linking
                delay(1200)
                state = ConnectState.Conflict
            }
        }
        attempt += 1
        running = false
    }

    ConnectAccountScreen(
        state = state,
        provider = provider,
        kind = kind,
        onSelect = { m -> provider = m; running = true },
        // SHOWUP-146 needs to tell these three apart, so the host reports which one
        // happened rather than collapsing them into a bare "done".
        onSkip = { onDone(ConnectExit.Skipped) },
        onContinue = { onDone(ConnectExit.Connected) },
        // The 8s cap firing is a real transition, not a demo shortcut.
        onLinkingTimeout = { kind = ErrorKind.Network; state = ConnectState.Error },
        onResolveConflict = { onDone(ConnectExit.ResolvedConflict) },
        onUseDifferentAccount = { state = ConnectState.Idle },
    )
}
