/*
 * ArrivalPreviews.kt
 * ShowUp · every frame of the arrival check-in (SHOWUP-195) at the narrowest phone (320 x 686) and the
 * widest (430 x 932), from [ArrivalSamples]. The fit sweep (ArrivalFitTest) measures all the sizes
 * between; Robolectric cannot draw the sheets' backdrop blur, a preview on API 31+ can.
 */
package com.showup.arrival

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.showup.date.DateSamples
import com.showup.profile.LocationState

@Preview(name = "Date 03 · A · Check-in bar · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewBar320() = ArrivalSamples.dateScreen()

@Preview(name = "Date 03 · A · Check-in bar · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewBar430() = ArrivalSamples.dateScreen()

@Preview(name = "Date 03 · B · Have you arrived? · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGuard320() = ArrivalSamples.over(ArrivalSamples.B) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · B · Have you arrived? · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewGuard430() = ArrivalSamples.over(ArrivalSamples.B) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · B · reading the location · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGuardBusy320() = ArrivalSamples.over(ArrivalSamples.B_BUSY) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · B · with the report note · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGuardNote320() = ArrivalSamples.over(ArrivalSamples.B_NOTE) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · C · no location fix · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewNoFix320() = ArrivalSamples.over(ArrivalSamples.C) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · C · no location fix · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewNoFix430() = ArrivalSamples.over(ArrivalSamples.C) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · C2 · check-in not saved · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewNotSaved320() = ArrivalSamples.over(ArrivalSamples.C2) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · C2 · check-in not saved · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewNotSaved430() = ArrivalSamples.over(ArrivalSamples.C2) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · D · checked in · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewDone320() = ArrivalSamples.over(ArrivalSamples.D) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · D · checked in · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewDone430() = ArrivalSamples.over(ArrivalSamples.D) { ArrivalSamples.dateScreen() }

@Preview(name = "Date 03 · E · after Close · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewAfterClose320() = ArrivalSamples.dateScreen(ArrivalSamples.CHECKED_IN)

@Preview(name = "Date 03 · E · after Close · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewAfterClose430() = ArrivalSamples.dateScreen(ArrivalSamples.CHECKED_IN)

@Preview(name = "Date 03 · F · from the chat chip · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewChatChip320() = ArrivalSamples.over(ArrivalSamples.F) { ArrivalSamples.chat() }

@Preview(name = "Date 03 · F · from the chat chip · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewChatChip430() = ArrivalSamples.over(ArrivalSamples.F) { ArrivalSamples.chat() }

@Preview(name = "Date 03 · G · location off for Show Up · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGateDenied320() = ArrivalSamples.gate(LocationState.Denied)

@Preview(name = "Date 03 · G · location off for Show Up · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewGateDenied430() = ArrivalSamples.gate(LocationState.Denied)

@Preview(name = "Date 03 · G · never asked · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGateAsk320() = ArrivalSamples.gate(LocationState.Ask)

@Preview(name = "Date 03 · G · location off on the phone · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGateServicesOff320() = ArrivalSamples.gate(LocationState.ServicesOff)

@Preview(name = "Date 03 · G · 10 min after the meet time · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGateLate320() =
    ArrivalSamples.gate(LocationState.Denied, now = DateSamples.DATE.meetAt.plusSeconds(600))

@Preview(name = "Date 03 · G · date tomorrow (no countdown) · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGateTomorrow320() =
    ArrivalSamples.gate(LocationState.Denied, date = DateSamples.DATE.copy(meetDay = "tomorrow"))

@Preview(name = "Date 03 · H · what the match sees · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewArrival320() = ArrivalSamples.dateScreen(ArrivalSamples.ARRIVED)

@Preview(name = "Date 03 · H · what the match sees · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewArrival430() = ArrivalSamples.dateScreen(ArrivalSamples.ARRIVED)

@Preview(name = "Date 03 · H · before the chat unlocks · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewArrivalLocked320() = ArrivalSamples.dateScreen(ArrivalSamples.ARRIVED, now = DateSamples.A_NOW)
