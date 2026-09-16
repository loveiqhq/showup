package com.showup.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether this device wants motion at all.
 *
 * Android has no single "reduce motion" flag. The honest signal is the system animator duration
 * scale, which the OS sets to 0 when someone turns animations off -- either in Accessibility >
 * Remove animations, or in Developer options. Respecting it keeps the flow usable for people who
 * get motion sick, and has the useful side effect of holding the screens still under UI tests.
 *
 * WHY THIS IS NOT CALLED `Motion`
 *
 * It was, in com.showup.tutorial, and it collided with [Motion] -- the durations. Two types named
 * Motion in one app, one meaning "how long" and one meaning "whether", is a trap that only stays
 * harmless while no file imports both: the moment one does, the shorter reference silently resolves
 * to whichever was imported and the mistake compiles. `MotionPreference` says which one it is.
 *
 * It moved out of the tutorial package for the same reason [ShowUpEasing] did: PrimaryButton needs
 * it, and the design system must not depend on a screen.
 */
@Immutable
data class MotionPreference(val enabled: Boolean)

@Composable
fun rememberMotion(): MotionPreference {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        MotionPreference(enabled = scale > 0f)
    }
}
