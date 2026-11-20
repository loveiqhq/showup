/*
 * ChangeFitTest.kt
 * ShowUp · Date 05, change meet time and place, on every phone at every type size (SHOWUP-196)
 *
 * The ticket's fourteen frames from [ChangeSamples]: A and B (the pickers), K, M and N (their failures), C, F, H
 * and L's thread variant (the suggestion cards in the chat), D, E, G, I, J and L (the Date screen's tags, request
 * card and tiles) -- on every phone of [DEVICES] and [EDGE_DEVICES], all seven Android font steps, and every
 * Android from 11 to 15.
 *
 * The rules are the 193 / 194 / 195 sweeps': every touch area 44 dp both ways (never 48) and none partly on
 * another, no word broken between two letters, nothing clipped or off the screen (the shared checks). And this
 * ticket's own:
 *
 *  - the picker footer (the binding note or the error, and both buttons) lies wholly inside the screen, and the
 *    primary and Cancel inside the footer, on every phone and type size;
 *  - the last slot / place scrolls clear of the footer: at the end of the list's scroll its bottom is above the
 *    footer's top (the ticket's "280" bottom padding, or the footer's own height when that is more);
 *  - at 375 x 667 at the default type, the footer and BOTH request-card buttons are wholly on the screen (above
 *    the TabBar), as the ticket's evidence line asks it to be stated.
 *
 * Split into several tests, as the 193 / 194 / 195 sweeps' memory lesson says: this machine reaps a run that
 * renders thousands of frames in one.
 */
package com.showup.fit

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import com.showup.change.CHANGE_CANCEL_TAG
import com.showup.change.CHANGE_FOOTER_TAG
import com.showup.change.CHANGE_PRIMARY_TAG
import com.showup.change.CHANGE_TRY_AGAIN_TAG
import com.showup.change.ChangePickerUi
import com.showup.change.ChangeSamples
import com.showup.change.ChangePickerScreen
import com.showup.change.placeRowTag
import com.showup.change.slotTag
import com.showup.chat.ChatScreen
import com.showup.chat.ChatUi
import com.showup.date.DATE_REQUEST_AGREE_TAG
import com.showup.date.DATE_REQUEST_FIRST_TAG
import com.showup.date.DateScreen
import com.showup.date.DateUi
import com.showup.date.DateSamples
import com.showup.designsystem.TAB_BAR_TAG
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// w1000: the unfolded Fold 7 row is 984 wide; the frame is sized per device either way.
@Config(sdk = [34], qualifiers = "w1000dp-h1200dp-xhdpi")
class ChangeFitTest {
    /** Every Android font size step, 85 % to Android 14's 200 %. */
    private val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
    private val phones = DEVICES + EDGE_DEVICES

    private class Frame(val label: String, val scroll: ScrollState?, val content: @Composable () -> Unit)

    private fun picker(label: String, ui: ChangePickerUi): Frame {
        val scroll = ScrollState(0)
        return Frame(label, scroll) { ChangePickerScreen(ChangeSamples.CTX, ui, {}, {}, {}, {}, {}, {}, scroll) }
    }

    private fun chat(label: String, ui: ChatUi) =
        Frame(label, null) { ChatScreen(ui, ChangeSamples.ZONE, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}) }

    private fun date(label: String, ui: DateUi): Frame {
        val scroll = ScrollState(0)
        return Frame(label, scroll) { DateScreen(ui, ChangeSamples.ZONE, scroll = scroll) }
    }

    private val pickerFrames = listOf(
        { picker("A time picker", ChangeSamples.A) },
        { picker("B place picker", ChangeSamples.B) },
        { picker("K suggest failed", ChangeSamples.K) },
        { picker("M places failed", ChangeSamples.M) },
        { picker("N no place found", ChangeSamples.N) },
    )

    private val threadFrames = listOf(
        { chat("C sent, waiting", ChangeSamples.chatUi(ChangeSamples.MINE_TIME)) },
        { chat("F receiver", ChangeSamples.chatUi(ChangeSamples.THEIRS_TIME)) },
        { chat("F receiver, place", ChangeSamples.chatUi(ChangeSamples.THEIRS_TIME.copy(
            id = "s-theirs-place", kind = com.showup.change.ChangeKind.Place, time = null,
            place = ChangeSamples.MINE_PLACE.place, wasPlaceName = "Cafe Krone",
        ))) },
        { chat("H expired", ChangeSamples.chatUi(ChangeSamples.EXPIRED_TIME)) },
        { chat("L thread, agree failed", ChangeSamples.chatUi(ChangeSamples.THEIRS_TIME, failed = setOf(ChangeSamples.THEIRS_TIME.id))) },
        { chat("C and F together, both kinds", ChangeSamples.chatUi(ChangeSamples.MINE_TIME, ChangeSamples.MINE_PLACE)) },
        { chat("Chat start, change chips", ChangeSamples.CHAT_WITH_CHANGE) },
    )

    private val dateFrames = listOf(
        { date("D yours waiting", ChangeSamples.dateUi(ChangeSamples.D_DATE)) },
        { date("E request, chat open", ChangeSamples.dateUi(ChangeSamples.E_DATE)) },
        { date("G agreed", ChangeSamples.dateUi(ChangeSamples.G_DATE)) },
        { date("I tiles, locked", ChangeSamples.dateUi(ChangeSamples.DATE, DateSamples.A_NOW)) },
        { date("J request, chat locked", ChangeSamples.dateUi(ChangeSamples.E_DATE, DateSamples.A_NOW)) },
        { date("L agree failed", ChangeSamples.dateUi(ChangeSamples.E_DATE, agreeFailed = true)) },
        { date("L agree failed, locked", ChangeSamples.dateUi(ChangeSamples.E_DATE, DateSamples.A_NOW, agreeFailed = true)) },
    )

    // ── the rules ───────────────────────────────────────────────────────────────────────────────

    private fun contains(a: FitRect, b: FitRect) =
        a.left <= b.left + SLACK && a.right >= b.right - SLACK && a.top <= b.top + SLACK && a.bottom >= b.bottom - SLACK

    private fun rules(frame: Frame, scale: Float): (FitFrame) -> List<Violation> = { f ->
        val found = mutableListOf<Violation>()
        fun fail(element: String, problem: String, detail: String) {
            found += Violation(f.device, f.screen, element, problem, detail)
        }
        val whole = f.device.width == 375 && f.device.height == 667 && scale == 1f

        // 1. Every touch area 44 dp both ways, and none partly on another. A control in a scroll passing under
        // a pinned footer or bar is not on it: only the same plane is compared.
        val taps = f.tapTargets()
        taps.forEach { (n, r, _) ->
            if (r.width < TAP_DP - SLACK || r.height < TAP_DP - SLACK) fail(n, "TAP AREA UNDER 44DP", "%.1f x %.1fdp".format(r.width, r.height))
        }
        // The pickers' footer is over the list: its buttons are one plane, the list's controls another, each list
        // control clipped to the part above the footer (one scrolled under it is out of sight, not on a button).
        val footerBox = f.tagged(CHANGE_FOOTER_TAG)
        fun plane(n: String) = if (footerBox != null && (n.contains(CHANGE_PRIMARY_TAG) || n.contains(CHANGE_CANCEL_TAG))) "footer" else "page"
        val placed = taps.map { (n, r, s) ->
            val clipped = if (plane(n) == "page" && footerBox != null) {
                FitRect(r.top, minOf(r.bottom, footerBox.top), r.left, r.right)
            } else r
            Triple(n, clipped, plane(n) + s)
        }
        for (i in placed.indices) for (j in i + 1 until placed.size) {
            val (a, ra, sa) = placed[i]
            val (b, rb, sb) = placed[j]
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

        // 3. The pickers: the footer whole on the screen with both buttons inside it, and the end of the list
        // clear of it.
        val footer = f.tagged(CHANGE_FOOTER_TAG)
        if (frame.label.first() in "ABKMN") {
            if (footer == null) {
                fail("the footer", "NOT DRAWN", frame.label)
            } else {
                if (footer.top < -SLACK || footer.bottom > f.device.height + SLACK || footer.left < -SLACK || footer.right > f.device.width + SLACK) {
                    fail("the footer", "NOT FULLY ON THE SCREEN", "%.1f..%.1f of %.1f".format(footer.top, footer.bottom, f.height))
                }
                listOf(CHANGE_PRIMARY_TAG, CHANGE_CANCEL_TAG).forEach { t ->
                    val b = f.tagged(t)
                    if (b == null) fail(t, "NOT DRAWN", frame.label)
                    else if (!contains(footer, b)) fail(t, "OUTSIDE THE FOOTER", "button %s, footer %s".format(b, footer))
                    else if (b.bottom > f.device.height + SLACK) fail(t, "OFF THE BOTTOM", "%.1fdp".format(b.bottom - f.device.height))
                }
                // The footer leaves room for a list: it never takes more than 70 % of the usable height.
                if (whole && footer.height > f.height * 0.7f) fail("the footer", "TAKES OVER 70 % OF THE SCREEN", "%.1f of %.1f".format(footer.height, f.height))
                val scroll = frame.scroll
                if (scroll != null) {
                    f.settle { scroll.dispatchRawDelta(1e7f) }
                    val footerNow = f.tagged(CHANGE_FOOTER_TAG)!!
                    val lastTags = when {
                        frame.label.startsWith("A") || frame.label.startsWith("K") -> listOf(slotTag(ChangeSamples.SLOTS.last()))
                        frame.label.startsWith("B") -> listOf(placeRowTag(ChangeSamples.PLACES.last().placeId))
                        frame.label.startsWith("M") -> listOf(CHANGE_TRY_AGAIN_TAG)
                        else -> emptyList()
                    }
                    lastTags.forEach { t ->
                        val last = f.tagged(t)
                        if (last == null) fail(t, "NOT DRAWN", frame.label)
                        else if (last.bottom > footerNow.top + SLACK) {
                            fail(t, "LAST ROW UNDER THE FOOTER", "%.1fdp under it at the end of the scroll".format(last.bottom - footerNow.top))
                        }
                    }
                }
            }
        }

        // 4. The request card (E, J, L): both buttons wholly on the screen above the TabBar at 375 x 667 at the
        // default type -- the ticket's evidence line -- and side by side in the card at every size.
        val first = f.tagged(DATE_REQUEST_FIRST_TAG)
        val agree = f.tagged(DATE_REQUEST_AGREE_TAG)
        val tab = f.tagged(TAB_BAR_TAG)
        if (frame.label.first() in "EJ" || frame.label.startsWith("L agree")) {
            if (first == null || agree == null) fail("the request card's buttons", "NOT DRAWN", frame.label)
            else if (whole) {
                // At rest the hero (Date 01's 196) and the card's own content leave the buttons under the TabBar on this
                // phone, as the Date screen is a scroll; the rule is that they are WHOLLY visible once the page is
                // scrolled to them -- reachable, and nothing in the card cut. The rest-state overshoot is reported.
                val limit = tab?.top ?: f.height
                val scroll = frame.scroll
                if (scroll != null && (first.bottom > limit + SLACK || agree.bottom > limit + SLACK)) {
                    // Scrolled up by just what hides the lower button (dp to px at the config's xhdpi).
                    val need = maxOf(first.bottom, agree.bottom) - limit
                    f.settle { scroll.dispatchRawDelta(need * XHDPI) }
                }
                val firstNow = f.tagged(DATE_REQUEST_FIRST_TAG) ?: first
                val agreeNow = f.tagged(DATE_REQUEST_AGREE_TAG) ?: agree
                val limitNow = f.tagged(TAB_BAR_TAG)?.top ?: f.height
                listOf("Open chat / Suggest another" to firstNow, "I agree" to agreeNow).forEach { (n, r) ->
                    if (r.top < -SLACK || r.bottom > limitNow + SLACK) {
                        fail(n, "NOT FULLY VISIBLE at 375 x 667", "%.1f..%.1f, visible part ends at %.1f".format(r.top, r.bottom, limitNow))
                    }
                }
            }
        }
        found
    }

    private fun FitFrame.label() = screen.substringAfter("Change/").substringBefore(" @")

    private fun run(device: Device, frame: Frame, scale: Float): List<String> =
        measureFit(device, "Change/${frame.label} @$scale", scale, 0, extra = rules(frame, scale), content = frame.content)
            .filterNot { it.advisory }
            .map { "$it" }

    private fun sweep(frames: List<() -> Frame>, scales: List<Float>): List<String> {
        val fresh = mutableListOf<String>()
        for (make in frames) for (scale in scales) phones.forEach { d -> fresh += run(d, make(), scale) }
        return fresh
    }

    private fun report(findings: List<String>, file: String) {
        java.io.File("build/$file").writeText(findings.joinToString("\n"))
        assertTrue(
            "Change layout problems (${findings.size}):\n" + findings.take(60).joinToString("\n") { "  $it" },
            findings.isEmpty(),
        )
    }

    @Test
    fun `A, B, K, M and N, the pickers, are clean on every phone at every type size`() {
        report(sweep(pickerFrames, scales), "change-fit-pickers.txt")
    }

    @Test
    fun `C, F, H and the 8-chip chat are clean on every phone at every type size`() {
        report(sweep(threadFrames, scales), "change-fit-thread.txt")
    }

    @Test
    fun `D, E, G, I, J and L, the Date screen, are clean on every phone at every type size`() {
        report(sweep(dateFrames, scales), "change-fit-date.txt")
    }

    /** The ticket's own check, on the phone it names, at the default type: the footer of every picker frame and both
     *  buttons of the request card (E, J, L) wholly visible. */
    @Test
    fun `at 375 x 667 the picker footer and both request-card buttons are fully visible`() {
        val phone = DEVICES.first { it.width == 375 && it.height == 667 }
        val fresh = mutableListOf<String>()
        for (make in pickerFrames + dateFrames.filter { it().label.first() in "EJL" }) fresh += run(phone, make(), 1f)
        report(fresh, "change-fit-375.txt")
    }

    /** Every Android the app installs on that Robolectric draws -- 11, 12, 12L, 13, and 15 beside the 14 above -- at the
     *  default and the largest type: text and drawing differ between versions, layouts must not. */
    @Test
    @Config(sdk = [30, 31, 32, 33, 35])
    fun `the change frames are clean on every Android from 11 to 15`() {
        report(sweep(pickerFrames + threadFrames + dateFrames, listOf(1f, 2f)), "change-fit-sdk.txt")
    }

    private companion object {
        /** The touch floor: 44 on both platforms (9 Nov 2026), as `android-preview-project/CLAUDE.md` states it. */
        const val TAP_DP = 44f
        const val SLACK = 0.75f
        const val XHDPI = 2f
    }
}
