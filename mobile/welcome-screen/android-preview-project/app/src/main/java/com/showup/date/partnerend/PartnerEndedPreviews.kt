/*
 * PartnerEndedPreviews.kt
 * ShowUp · Date 08's three payloads at the narrowest phone (320 x 686) and the widest (430 x 932), from
 * [PartnerEndedSamples]. The fit sweep measures all the sizes between.
 */
package com.showup.date.partnerend

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.showup.date.PartnerScreen

@Preview(name = "Date 08 · A no-show · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewA320() = PartnerEndedSamples.frame(PartnerScreen.YouNoShowed)

@Preview(name = "Date 08 · A no-show · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewA430() = PartnerEndedSamples.frame(PartnerScreen.YouNoShowed)

@Preview(name = "Date 08 · B uncomfortable · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewB320() = PartnerEndedSamples.frame(PartnerScreen.DateEndedDiscomfort)

@Preview(name = "Date 08 · B uncomfortable · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewB430() = PartnerEndedSamples.frame(PartnerScreen.DateEndedDiscomfort)

@Preview(name = "Date 08 · C met · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewC320() = PartnerEndedSamples.frame(PartnerScreen.DateClosedPositive)

@Preview(name = "Date 08 · C met · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewC430() = PartnerEndedSamples.frame(PartnerScreen.DateClosedPositive)
