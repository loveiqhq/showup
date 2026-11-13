/*
 * ArrivalHost.kt
 * ShowUp · the arrival gate's sheets over a screen, the screen behind them blurred (SHOWUP-195)
 *
 * The one place the Date screen and the chat share, so both blur exactly as `ChatScreen`'s End date sheet
 * does: `ModalShell`'s backdrop, arriving with the sheet.
 */
package com.showup.arrival

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.showup.designsystem.Motion
import com.showup.designsystem.blurBehindModalShell
import com.showup.designsystem.rememberMotion

@Composable
fun ArrivalOver(
    ui: GateUi,
    name: String,
    onYes: () -> Unit,
    onNotYet: () -> Unit,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    content: @Composable () -> Unit,
) {
    val motion = rememberMotion()
    val blur by animateFloatAsState(
        targetValue = if (ui.phase is GatePhase.Idle) 0f else 1f,
        animationSpec = tween(if (motion.enabled) Motion.SHEET else 0),
        label = "arrival-sheet-blur",
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().blurBehindModalShell(blur)) { content() }
        ArrivalSheetHost(ui, name, onYes, onNotYet, onClose, onUndo)
    }
}
