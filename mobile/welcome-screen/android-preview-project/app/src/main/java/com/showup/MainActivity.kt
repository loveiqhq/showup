package com.showup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.showup.tutorial.WelcomeScreen

/**
 * Host for the welcome card. Edge-to-edge so the screen's own safe-area handling is what
 * positions the content — which is the thing worth checking.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WelcomeScreen(onContinue = { /* tutorial card 2 — not built yet */ })
        }
    }
}
