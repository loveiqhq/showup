/*
 * PartnerEndedSamples.kt
 * ShowUp · Date 08's four payloads drawn still with nothing wired, for the previews and, later, the States page
 * (SHOWUP-199). The name is the kit's: Leonie.
 */
package com.showup.date.partnerend

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.showup.date.PartnerScreen

object PartnerEndedSamples {
    /** The kit's `meetTime`, as a 24 h clock draws it. */
    private const val SAMPLE_TIME = "19:00"


    @Composable
    fun frame(screen: PartnerScreen, scroll: ScrollState? = null) =
        PartnerEndedScreen(screen, "Leonie", null, SAMPLE_TIME, scroll ?: rememberScrollState()) {}
}
