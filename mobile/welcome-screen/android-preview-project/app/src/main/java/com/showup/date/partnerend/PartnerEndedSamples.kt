/*
 * PartnerEndedSamples.kt
 * ShowUp · Date 08's three payloads drawn still with nothing wired, for the previews and, later, the States page
 * (SHOWUP-199). The name is the kit's: Leonie.
 */
package com.showup.date.partnerend

import androidx.compose.runtime.Composable
import com.showup.date.PartnerScreen

object PartnerEndedSamples {
    @Composable
    fun frame(screen: PartnerScreen) = PartnerEndedScreen(screen, "Leonie", null) {}
}
