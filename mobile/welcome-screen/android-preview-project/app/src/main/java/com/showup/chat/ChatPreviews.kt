/*
 * ChatPreviews.kt
 * ShowUp · every frame of the coordinate chat (SHOWUP-194) at the narrowest phone (320 x 686) and the
 * widest (430 x 932), from [ChatSamples]. The fit sweep measures all the sizes between.
 */
package com.showup.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.time.ZoneId

private val Berlin: ZoneId = ZoneId.of("Europe/Berlin")

@Composable
private fun Chat(ui: ChatUi) = ChatScreen(ui, Berlin, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})

@Preview(name = "Date 02 · A · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewTutorial320() = ChatTutorialScreen("Leonie", onOpen = {}, onBack = {})

@Preview(name = "Date 02 · A · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewTutorial430() = ChatTutorialScreen("Leonie", onOpen = {}, onBack = {})

@Preview(name = "Date 02 · B · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewStart320() = Chat(ChatSamples.B)

@Preview(name = "Date 02 · B · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewStart430() = Chat(ChatSamples.B)

@Preview(name = "Date 02 · C · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewThread320() = Chat(ChatSamples.C)

@Preview(name = "Date 02 · C · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewThread430() = Chat(ChatSamples.C)

@Preview(name = "Date 02 · D · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGuide320() = Chat(ChatSamples.D)

@Preview(name = "Date 02 · D · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewGuide430() = Chat(ChatSamples.D)

@Preview(name = "Date 02 · D after Got it · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGuideAcked320() = Chat(ChatSamples.D.copy(guide = Guide.Acked))

@Preview(name = "Date 02 · E · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewEndCard320() = Chat(ChatSamples.E)

@Preview(name = "Date 02 · E · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewEndCard430() = Chat(ChatSamples.E)

@Preview(name = "Date 02 · F · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewEndSheet320() = Chat(ChatSamples.F)

@Preview(name = "Date 02 · F · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewEndSheet430() = Chat(ChatSamples.F)

@Preview(name = "Date 02 · G · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewYouEnded320() = DateEndedScreen(ChatSamples.G, photoUrl = null, onFindNext = {})

@Preview(name = "Date 02 · G · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewYouEnded430() = DateEndedScreen(ChatSamples.G, photoUrl = null, onFindNext = {})

@Preview(name = "Date 02 · H · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewMatchEnded320() = DateEndedScreen(ChatSamples.H, photoUrl = null, onFindNext = {})

@Preview(name = "Date 02 · H · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewMatchEnded430() = DateEndedScreen(ChatSamples.H, photoUrl = null, onFindNext = {})

@Preview(name = "Date 02 · failed first read · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewFailed320() = Chat(ChatSamples.B.copy(failed = com.showup.api.CallFailure.Network))

@Preview(name = "Date 02 · failed send · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewSendFailed320() = Chat(ChatSamples.C.copy(sendFailed = true))
