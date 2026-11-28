/*
 * PartnerEndedRoute.kt
 * ShowUp · Date 08 as a destination: one view per showing, and no way out but `Proceed to review` (SHOWUP-199, R6, R7)
 */
package com.showup.date.partnerend

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.showup.AppRoute
import com.showup.date.PartnerScreen

/** One value per process: what a saved flag is compared with to tell a rotation from a restore after process death. */
internal var partnerEndedProcessId: String = java.util.UUID.randomUUID().toString()

/**
 * The screen with its destination's duties. Back is consumed, predictive back included: the date is over and the
 * person owes a review, so there is nothing to go back to and nowhere else to go. There is no tab bar either.
 *
 * [onShown] runs once per showing. A rotation does not count as a second view, while a new showing (another route
 * entry) or the same screen after the process was killed and restored does ("each time the screen is shown, including
 * when B reopens the app"). The flag saved across a configuration change is the id of the process that reported, so a
 * restore in a new process finds a stranger's id and reports again.
 */
@Composable
fun PartnerEndedRoute(
    screen: PartnerScreen,
    name: String,
    photoUrl: String?,
    onShown: () -> Unit,
    onProceed: () -> Unit,
) {
    var reportedBy by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (reportedBy != partnerEndedProcessId) {
            reportedBy = partnerEndedProcessId
            onShown()
        }
    }
    BackHandler(enabled = true) {}
    PartnerEndedScreen(screen, name, photoUrl, onProceed = onProceed)
}

/**
 * Over whatever is up, with only the flow under it: the Date screen's chat and a Date 05 picker leave the stack, so
 * back can never reopen them. Their sheets (Date 06, 07) are the host's to close.
 */
fun NavController.showPartnerEnded(route: AppRoute.PartnerEnded) {
    navigate(route) {
        popUpTo(AppRoute.Flow) { inclusive = false }
        launchSingleTop = true
    }
}
