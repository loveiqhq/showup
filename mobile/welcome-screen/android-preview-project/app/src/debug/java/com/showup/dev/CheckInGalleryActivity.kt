/*
 * CheckInGalleryActivity.kt
 * ShowUp · every Check-In frame the tickets name, one per launch, on a real device (debug only)
 *
 * The evidence renders (`EvidenceScreenshots`) are Robolectric: right layout, but software drawing
 * -- no RenderEffect blur, no elevation, hinted text advances. This draws the same fixtures on a
 * real phone, with the platform's own insets, so a frame can be captured exactly as a user sees it:
 *
 *   adb shell am start -n com.showup/com.showup.dev.CheckInGalleryActivity --es state 179B
 *   adb exec-out screencap -p > 179B.png
 *
 * One state per launch, chosen by `state` -- a picture, not a flow; the flow itself is walked in
 * MainActivity with `dev_checkin` (DevCheckInStore.kt). In the debug source set and the debug
 * manifest only, so a release build has neither the class nor the activity.
 */
package com.showup.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import com.showup.checkin.CheckInAvailabilityScreen
import com.showup.checkin.CheckInCopy
import com.showup.checkin.CheckInFilters
import com.showup.checkin.CheckInFiltersScreen
import com.showup.checkin.CheckInGateScreen
import com.showup.checkin.CheckInIntroScreen
import com.showup.checkin.CheckInMotivationScreen
import com.showup.checkin.CheckInStep
import com.showup.checkin.CheckInUpliftScreen
import com.showup.checkin.CheckInVibeScreen
import com.showup.checkin.SlotTier
import com.showup.checkin.checkInDays
import com.showup.checkin.gridOf

class CheckInGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val state = intent.getStringExtra("state") ?: "178A"
        // Back, the ✕ and a dialog's dismiss all close the frame: each screen registers its own back
        // handler, and left as no-ops here they swallowed back, so the frame could not be left.
        setContent { CheckInGalleryFrame(state, leave = ::finish) }
    }
}

/** The kit's clock, 4:20pm, so Today starts at 5pm as on every sheet. */
private val KIT_DAYS = checkInDays(java.time.LocalDateTime.of(2026, 10, 13, 16, 20))
private val FIVE = setOf("coffee_chat", "sunset", "deep_talk", "playful", "no_rush")
private const val TYPING = "I have 2 tickets for the Berghain garden tonight and nobody to go with"
private const val CONTACT = "Text me on 0176 2345 6789 and we'll grab a drink tonight"
private const val WRITTEN = "I'll take you on a rafting tour on Wannsee — picnic included"

/** The frame for one [DEV_STATES] id. Internal so DevStatesTest can prove every menu entry draws its own. */
@Composable
internal fun CheckInGalleryFrame(state: String, leave: () -> Unit = {}) {
    val today = gridOf(CheckInStep.Today, KIT_DAYS)
    val tomorrow = gridOf(CheckInStep.Tomorrow, KIT_DAYS)
    when (state) {
        "178A" -> CheckInIntroScreen(onClose = leave)
        "178B" -> CheckInGateScreen()
        "179A" -> CheckInAvailabilityScreen(today, mapOf(18 to SlotTier.Preferred, 19 to SlotTier.Preferred, 20 to SlotTier.Available), false, onBack = leave, onClose = leave, onCancelNoToday = leave, onDismissNoToday = leave)
        "179B" -> CheckInAvailabilityScreen(today, emptyMap(), false, dialogOpen = true, onBack = leave, onClose = leave, onCancelNoToday = leave, onDismissNoToday = leave)
        "179C" -> CheckInAvailabilityScreen(tomorrow, emptyMap(), false, toast = CheckInCopy.TOMORROW_TOAST, toastTick = 1, onBack = leave, onClose = leave, onCancelNoToday = leave, onDismissNoToday = leave)
        "179D" -> CheckInAvailabilityScreen(tomorrow, mapOf(12 to SlotTier.Preferred, 13 to SlotTier.Preferred, 19 to SlotTier.Available), false, onBack = leave, onClose = leave, onCancelNoToday = leave, onDismissNoToday = leave)
        "179HIW" -> CheckInAvailabilityScreen(today, mapOf(18 to SlotTier.Preferred), true, onBack = leave, onClose = leave, onCancelNoToday = leave, onDismissNoToday = leave)
        "180A" -> CheckInMotivationScreen(selected = null, onBack = leave, onClose = leave)
        "180B" -> CheckInMotivationScreen(selected = null, toast = CheckInCopy.MOTIVATION_TOAST, toastTick = 1, onBack = leave, onClose = leave)
        "180C" -> CheckInMotivationScreen(selected = "long_term", onBack = leave, onClose = leave)
        "181A" -> CheckInVibeScreen(picked = emptySet(), onBack = leave, onClose = leave)
        "181B" -> CheckInVibeScreen(picked = emptySet(), toast = CheckInCopy.VIBE_TOAST_EMPTY, toastTick = 1, onBack = leave, onClose = leave)
        "181C" -> CheckInVibeScreen(picked = setOf("coffee_chat", "playful", "no_rush"), onBack = leave, onClose = leave)
        "181D" -> CheckInVibeScreen(picked = FIVE, toast = CheckInCopy.VIBE_TOAST_MAX, toastTick = 1, onBack = leave, onClose = leave)
        "181D-end" -> {
            val scroll = remember { ScrollState(0) }
            LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue) }
            CheckInVibeScreen(picked = FIVE, toast = CheckInCopy.VIBE_TOAST_MAX, toastTick = 1, groupsScroll = scroll, onBack = leave, onClose = leave)
        }
        "182A" -> CheckInFiltersScreen(CheckInFilters(genders = setOf("woman")), onBack = leave, onClose = leave)
        "182B" -> CheckInFiltersScreen(CheckInFilters(genders = emptySet()), toast = CheckInCopy.FILTERS_TOAST, toastTick = 1, onBack = leave, onClose = leave)
        "182C" -> CheckInFiltersScreen(CheckInFilters(setOf("woman", "non_binary"), minAge = 25, maxAge = 35, distanceKm = 15), onBack = leave, onClose = leave)
        "183A" -> CheckInUpliftScreen(text = "", onBack = leave, onClose = leave)
        "183B" -> CheckInUpliftScreen(text = TYPING, onBack = leave, onClose = leave)
        "183C" -> CheckInUpliftScreen(text = CONTACT, blocked = true, toast = CheckInCopy.CONTACT_ERROR, toastTick = 1, onBack = leave, onClose = leave)
        "183D" -> CheckInUpliftScreen(text = WRITTEN, saving = true, onBack = leave, onClose = leave)
        "183E" -> CheckInUpliftScreen(text = WRITTEN, saveFailed = true, onBack = leave, onClose = leave)
        // Loud, never a quiet fallback: a menu entry with no frame must be seen, not shown as the intro.
        else -> Text("Unknown state $state")
    }
}
