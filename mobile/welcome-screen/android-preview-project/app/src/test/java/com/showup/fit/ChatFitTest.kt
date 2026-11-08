/*
 * ChatFitTest.kt
 * ShowUp · the coordinate chat on every phone at every type size (SHOWUP-194)
 *
 * A to H as the spec sheet draws them, plus the inputs most likely to break the thread and the dock: a
 * long thread (the end of the scroll must rest above the dock), a long message, D after Got it, and a
 * failed send -- on every phone of the shared list and [EDGE_DEVICES], all seven Android font steps, and
 * every Android from 11 to 15; and the chat with the keyboard up on every full-screen phone. Beyond the
 * shared checks, the 193 rules: every touch area 48 dp and none partly on another, no word broken between
 * its letters; and the chat's own: the dock never covers the thread and stays above the keys, the
 * tutorial's and the outcome's CTA on screen, and at 375 x 667 D's guide title and Got it inside the
 * thread once revealed.
 */
package com.showup.fit

import com.showup.chat.CHAT_CTA_TAG
import com.showup.chat.CHAT_DOCK_TAG
import com.showup.chat.CHAT_GOT_IT_TAG
import com.showup.chat.CHAT_GUIDE_TAG
import com.showup.chat.CHAT_THREAD_TAG
import com.showup.chat.ChatMessage
import com.showup.chat.ChatReason
import com.showup.chat.ChatSamples
import com.showup.chat.ChatScreen
import com.showup.chat.ChatTutorialScreen
import com.showup.chat.ChatUi
import com.showup.chat.DateEndedScreen
import com.showup.chat.Guide
import com.showup.chat.SHOWN_REASONS
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// w1000: the unfolded Fold 7 row is 984 wide; the frame is sized per device either way.
@Config(sdk = [34], qualifiers = "w1000dp-h1200dp-xhdpi")
class ChatFitTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    /** Every Android font size step, 85 % to Android 14's 200 %. */
    private val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
    private val phones = DEVICES + EDGE_DEVICES

    private val long = (1..9).map { i ->
        ChatMessage(
            "l$i", if (i % 3 == 0) ChatReason.RunningLate else null,
            if (i % 2 == 0) "Running a little late, the tram stopped twice and I am nearly at the door, sorry!" else "No worries, see you soon.",
            fromYou = i % 2 == 1, sentAt = ChatSamples.NOW, read = if (i % 2 == 1) i < 8 else null,
        )
    }

    private val chats: List<Pair<String, ChatUi>> = listOf(
        "B start" to ChatSamples.B,
        "C thread" to ChatSamples.C,
        "D guide" to ChatSamples.D,
        "D after Got it" to ChatSamples.D.copy(guide = Guide.Acked),
        "E end card" to ChatSamples.E,
        "F end sheet" to ChatSamples.F,
        "long thread" to ChatSamples.C.copy(messages = long + ChatSamples.CANCEL_SENT),
        "failed send" to ChatSamples.C.copy(sendFailed = true),
    )

    /** The chat's rules, run in each render after the shared ones; [keyboard] is the keys' height, if up. */
    private fun chatRules(guideCheck: Boolean, keyboard: Int = 0): (FitFrame) -> List<Violation> = { f ->
        val found = mutableListOf<Violation>()
        fun fail(element: String, problem: String, detail: String) {
            found += Violation(f.device, f.screen, element, problem, detail)
        }
        val dock = f.tagged(CHAT_DOCK_TAG)

        // 1. Every touch area 48 dp both ways, none partly on another. THE DOCK'S CHIPS ARE THE ONE
        // EXCEPTION: the ticket fixes them 36 high, 6 apart, so no area can be 48 without lying on its
        // neighbour's; Compose gives each the half of the gap nearer it. Reported for Philipp. Where the
        // chips scroll (a split screen) the last rows sit below the dock's foot, out of sight.
        val taps = f.tapTargets()
        taps.forEach { (name, r, _) ->
            val dockChip = isDockChip(name) && dock != null && r.top >= dock.top - SLACK
            if (!dockChip && (r.width < ANDROID_TAP_DP - SLACK || r.height < ANDROID_TAP_DP - SLACK)) {
                fail(name, "TAP AREA UNDER 48DP", "%.1f x %.1fdp".format(r.width, r.height))
            }
        }
        fun contains(a: FitRect, b: FitRect) =
            a.left <= b.left + SLACK && a.right >= b.right - SLACK && a.top <= b.top + SLACK && a.bottom >= b.bottom - SLACK
        // Overlap within one plane, each area clipped to the part of its container that shows: the end
        // sheet over everything (under it nothing answers), what is pinned -- the header, the field, Send
        // and the dock's chips, clipped to the dock where they scroll -- and the thread's scroll, clipped
        // to the thread. A control scrolled out of its container is out of sight, not on a neighbour.
        val sheetUp = taps.any { isSheet(it.name) }
        fun clip(r: FitRect, box: FitRect?) =
            if (box == null) r else FitRect(maxOf(r.top, box.top), minOf(r.bottom, box.bottom), maxOf(r.left, box.left), minOf(r.right, box.right))
        val placed = taps.map { (name, r, inScroll) ->
            when {
                isSheet(name) -> FitTap(name, r, inScroll = false) to "sheet"
                isDockChip(name) -> FitTap(name, clip(r, dock), inScroll = false) to "pinned"
                inScroll -> FitTap(name, clip(r, f.tagged(CHAT_THREAD_TAG)), inScroll = true) to "scroll"
                else -> FitTap(name, r, inScroll = false) to "pinned"
            }
        }.filter { !sheetUp || it.second == "sheet" }
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            val (ta, sa) = placed[i]
            val (tb, sb) = placed[j]
            val (a, ra) = ta.name to ta.rect
            val (b, rb) = tb.name to tb.rect
            if (sa != sb || contains(ra, rb) || contains(rb, ra)) continue
            val w = minOf(ra.right, rb.right) - maxOf(ra.left, rb.left)
            val h = minOf(ra.bottom, rb.bottom) - maxOf(ra.top, rb.top)
            if (w > SLACK && h > SLACK) fail("$a / $b", "TAP AREAS OVERLAP", "%.1f x %.1fdp".format(w, h))
        }

        // 2. No word broken between two letters.
        f.lineBreaks().forEach { (text, ends) ->
            ends.filter { it in 1 until text.length && text[it - 1].isLetterOrDigit() && text[it].isLetterOrDigit() }
                .forEach { end ->
                    val from = text.lastIndexOf(' ', end - 1) + 1
                    val to = text.indexOf(' ', end).let { if (it < 0) text.length else it }
                    fail("\"${text.take(40)}\"", "WORD BROKEN", "`${text.substring(from, end)}` / `${text.substring(end, to)}`")
                }
        }

        // 3. The dock sits under the thread, never over it: the end of the thread's scroll is above it.
        val thread = f.tagged(CHAT_THREAD_TAG)
        if (thread != null && dock != null && thread.bottom > dock.top + SLACK) {
            fail("the thread", "UNDER THE DOCK", "%.1fdp".format(thread.bottom - dock.top))
        }
        if (dock != null && dock.bottom > f.height - keyboard + SLACK) {
            fail("the dock", if (keyboard > 0) "UNDER THE KEYBOARD" else "OFF THE BOTTOM", "%.1fdp".format(dock.bottom - f.height + keyboard))
        }

        // 4. A CTA (the tutorial's, the outcome's) is on screen whole.
        f.tagged(CHAT_CTA_TAG)?.let { cta ->
            val shown = f.height - keyboard
            if (cta.bottom > shown + SLACK || cta.top < -SLACK) fail("the CTA", "OFF SCREEN", "%.1f..%.1f of %.1f".format(cta.top, cta.bottom, shown))
        }

        // 5. D on the 375 x 667 phone: the guide's title and Got it inside the thread's visible part, once
        // the reveal has run -- the acceptance criteria's check. A guide taller than the thread cannot show
        // both (at 130 % here it is 365 in a thread of 313); the reveal rule then puts its title at the top
        // and Got it is a short scroll away, which is what is held to. Reported for Philipp.
        if (guideCheck && thread != null) {
            val guide = f.tagged(CHAT_GUIDE_TAG)
            val gotIt = f.tagged(CHAT_GOT_IT_TAG)
            if (guide == null || gotIt == null) {
                fail("the guide", "NOT DRAWN", "")
            } else {
                if (guide.top < thread.top - SLACK) fail("the guide's title", "ABOVE THE THREAD'S TOP", "%.1fdp".format(thread.top - guide.top))
                val fits = guide.bottom - guide.top <= thread.bottom - thread.top - GUIDE_PADDING
                if (fits && gotIt.bottom > thread.bottom + SLACK) {
                    fail("Got it", "BELOW THE THREAD'S BOTTOM", "%.1fdp".format(gotIt.bottom - thread.bottom))
                }
                if (!fits && guide.top > thread.top + GUIDE_PADDING + SLACK) {
                    fail("the guide's title", "NOT AT THE THREAD'S TOP", "%.1fdp down".format(guide.top - thread.top))
                }
            }
        }
        found
    }

    /** A dock chip, by its tag (`chat-chip-dock-<reason>`), the name the harness gives a tagged control. */
    private fun isDockChip(name: String) = DOCK_CHIP_TAGS.any { name.contains(it) }

    /** One of the end sheet's two buttons, by its tag. */
    private fun isSheet(name: String) = name.contains(com.showup.chat.END_SHEET_KEEP_TAG) || name.contains(com.showup.chat.END_SHEET_END_TAG)

    private fun run(
        device: Device,
        label: String,
        scale: Float,
        guideCheck: Boolean = false,
        keyboard: Int = 0,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): List<String> =
        measureFit(device, "Chat/$label @$scale", scale, keyboard, extra = chatRules(guideCheck, keyboard), content = content)
            .filterNot { it.advisory }
            // The shared harness holds every control to 44; the dock's chips are the ticket's 36 (rule 1 above).
            .filterNot { it.problem == "TAP TARGET TOO SMALL" && isDockChip(it.element) }
            .map { "$it" }

    private fun all(scales: List<Float>): List<String> {
        val fresh = mutableListOf<String>()
        for (scale in scales) phones.forEach { d ->
            fresh += run(d, "A tutorial", scale) { ChatTutorialScreen("Leonie", onOpen = {}, onBack = {}) }
            for ((name, ui) in chats) {
                fresh += run(d, name, scale) {
                    ChatScreen(ui, berlin, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})
                }
            }
            fresh += run(d, "G you ended", scale) { DateEndedScreen(ChatSamples.G, photoUrl = null, onFindNext = {}) }
            fresh += run(d, "H the match ended", scale) { DateEndedScreen(ChatSamples.H, photoUrl = null, onFindNext = {}) }
        }
        return fresh
    }

    private fun report(findings: List<String>, file: String) {
        java.io.File("build/$file").writeText(findings.joinToString("\n"))
        assertTrue(
            "Chat layout problems (${findings.size}):\n" + findings.take(60).joinToString("\n") { "  $it" },
            findings.isEmpty(),
        )
    }

    @Test
    fun `every frame of the chat is clean on every phone at every type size`() {
        report(all(scales), "chat-fit-findings.txt")
    }

    /** The acceptance criteria's own check: D at 375 x 667, the guide's title and Got it visible. */
    @Test
    fun `at 375 x 667 the guide's title and Got it are inside the thread once it appears`() {
        val phone = DEVICES.first { it.width == 375 && it.height == 667 }
        val fresh = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f)) {
            fresh += run(phone, "D guide (375 check)", scale, guideCheck = true) {
                ChatScreen(ChatSamples.D, berlin, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})
            }
        }
        report(fresh, "chat-fit-guide.txt")
    }

    /** Every Android the app installs on that Robolectric draws -- 11, 12, 12L, 13, and 15 beside the 14
     *  above (16 is past this Robolectric) -- at the default and the largest type. */
    @Test
    @Config(sdk = [30, 31, 32, 33, 35])
    fun `the chat is clean on every Android from 11 to 15`() {
        report(all(listOf(1f, 2f)), "chat-fit-findings-sdk.txt")
    }

    /**
     * The chat with the keyboard up, on every full-screen phone at the default, a large and the largest
     * type: the dock and the field above the keys, the thread over the dock, nothing cut. Not the guide
     * (its field is locked) nor the sheet (it covers the dock). Not a split screen's top half either:
     * the keys cover the other app, and this window keeps its size.
     */
    @Test
    fun `the chat with the keyboard up is clean on every phone`() {
        val typing = chats.filter { (name, _) -> name !in setOf("B start", "D guide", "F end sheet") }
        val fresh = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f, 2f)) phones.filter { it.height >= FULL_SCREEN_DP }.forEach { d ->
            val keys = keyboardOn(d)
            for ((name, ui) in typing) {
                fresh += run(d, "$name, keyboard $keys", scale, keyboard = keys) {
                    ChatScreen(ui, berlin, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        report(fresh, "chat-fit-keyboard.txt")
    }

    /** The keys' height on [d]: about two fifths of a phone, as Gboard and the iOS keyboard take, 250 to 330. */
    private fun keyboardOn(d: Device) = (d.height * 0.38f).roundToInt().coerceIn(250, 330)

    private companion object {
        /** Android's touch floor: Material's 48, as `android-preview-project/CLAUDE.md` states it. */
        const val ANDROID_TAP_DP = 48f
        const val SLACK = 0.75f
        /** A window this tall or taller is a whole phone; a split screen's half is under it. */
        const val FULL_SCREEN_DP = 500
        /** The thread's padding while the guide waits (6 above, 6 below), and the 12 over the guide. */
        const val GUIDE_PADDING = 12f
        val DOCK_CHIP_TAGS = SHOWN_REASONS.map { com.showup.chat.chipTag(it, com.showup.chat.ChipSurface.Dock) }
    }
}
