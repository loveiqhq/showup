package com.showup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.showup.tutorial.MeetInRealLifeScreen
import com.showup.tutorial.WelcomeScreen

/**
 * Host for the tutorial screens. Edge-to-edge so each screen's own safe-area handling is what
 * positions the content — which is the thing worth checking on a device.
 *
 * The navigation here is a placeholder: real routing arrives with the rest of the flow. It exists
 * so "Show me how" actually goes somewhere in the emulator.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var screen by remember { mutableIntStateOf(1) }
            when (screen) {
                1 -> WelcomeScreen(onContinue = { screen = 2 })
                else -> MeetInRealLifeScreen(onNext = { /* tutorial screen 3 — not built yet */ })
            }
        }
    }
}
