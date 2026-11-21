/*
 * ChangeSamples.kt
 * ShowUp · SHOWUP-196's frames A to N drawn still, with nothing wired -- the two pickers, the thread's suggestion
 * cards, the Date screen's waiting tags, request card and tiles, and the failures. One copy for the previews
 * ([ChangePreviews]), the debug States page and the fit sweep, as [com.showup.arrival.ArrivalSamples] is for 195.
 *
 * The values are the kit's (`screen-change-reference.jsx`): Leonie, 19:00 at Cafe Krone, Oranienstrasse 42,
 * Kreuzberg; the slots 19:15 to 21:30; the five fixture places. Times are the Date screen's sample day (3 Nov 2026,
 * 19:00 Berlin = 18:00Z), the answer-by is 18:30 (binding time - 0:30) and the clock is the sheet's 17:42 (chat open)
 * or 15:55 (chat locked).
 */
package com.showup.change

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.showup.arrival.ArrivalSamples
import com.showup.chat.ChatMessage
import com.showup.chat.ChatReason
import com.showup.chat.ChatSamples
import com.showup.chat.ChatScreen
import com.showup.chat.ChatUi
import com.showup.date.ActiveDate
import com.showup.date.DateSamples
import com.showup.date.DateScreen
import com.showup.date.DateUi
import java.time.Instant
import java.time.ZoneId

object ChangeSamples {
    val ZONE: ZoneId = DateSamples.ZONE
    private val MEET: Instant = Instant.parse("2026-11-03T18:00:00Z")
    private val DEADLINE: Instant = MEET.minusSeconds(1800)

    private fun berlin(hhmm: String): Instant = Instant.parse("2026-11-03T$hhmm:00Z").minusSeconds(3600)

    /** The kit's slot list, 19:15 to 21:30 in 15-minute steps. */
    val SLOTS: List<Instant> = (0..9).map { MEET.plusSeconds(900L * (it + 1)) }

    /** The kit's five places, with their distances from Cafe Krone. */
    val PLACES = listOf(
        PlaceOption("p-lenz", "Bar Lenz", PlaceType.Bar, "Lausitzer Platz 9, Kreuzberg", 350),
        PlaceOption("p-nove", "Osteria Nove", PlaceType.Restaurant, "Wiener Str. 14, Kreuzberg", 600),
        PlaceOption("p-fraeu", "Café Fräulein", PlaceType.Cafe, "Manteuffelstr. 30, Kreuzberg", 800),
        PlaceOption("p-kanal", "Kanal Bar", PlaceType.Bar, "Paul-Lincke-Ufer 21, Kreuzberg", 1100),
        PlaceOption("p-linde", "Lindenblatt", PlaceType.Restaurant, "Skalitzer Str. 82, Kreuzberg", 1400),
    )

    val CTX = ChangeSheetContext(name = "Leonie", bindingTime = MEET, bindingPlace = "Cafe Krone", slots = SLOTS, zone = ZONE)

    private fun place(o: PlaceOption) = ChangePlace(o.placeId, o.name, o.type, o.address)

    /** My 19:30 suggestion, sent 17:41. */
    val MINE_TIME = ChangeSuggestion(
        id = "s-mine-time", kind = ChangeKind.Time, byYou = true, time = berlin("19:30"), place = null,
        wasTime = MEET, wasPlaceName = null, deadline = DEADLINE, createdAt = berlin("17:41"),
    )

    /** My Bar Lenz suggestion, sent 17:41. */
    val MINE_PLACE = ChangeSuggestion(
        id = "s-mine-place", kind = ChangeKind.Place, byYou = true, time = null, place = place(PLACES[0]),
        wasTime = MEET, wasPlaceName = "Cafe Krone", deadline = DEADLINE, createdAt = berlin("17:41"),
    )

    /** Leonie's 19:30 suggestion, sent 17:38. */
    val THEIRS_TIME = MINE_TIME.copy(id = "s-theirs-time", byYou = false, createdAt = berlin("17:38"))

    /** H: mine, not agreed by 18:30 -- the server's snapshot of the binding values then. */
    val EXPIRED_TIME = MINE_TIME.copy(
        id = "s-expired", status = ChangeStatus.Expired, expiredAt = DEADLINE,
        expiredBindingTime = MEET, expiredBindingPlaceName = "Cafe Krone",
    )

    private val CHANGE = DateChange(cutoffAt = DEADLINE, allowed = true, slots = SLOTS, timeWas = null, placeWas = null, pending = emptyList())

    /** The date with the Date 05 block a server of this version sends: tiles and chips allowed, nothing open. */
    val DATE: ActiveDate = DateSamples.DATE.copy(change = CHANGE)

    private fun withPending(vararg open: ChangeSuggestion) = DATE.copy(change = CHANGE.copy(pending = open.toList()))

    /** Leonie's earlier message, which the thread frames seed (the kit's `CHANGE_SEED`). */
    val SEED = ChatMessage("seed", ChatReason.OnMyWay, "Looking forward to tonight!", fromYou = false, sentAt = berlin("17:20"), read = null)

    private val CHAT_DATE = DATE.copy(coordinateOpen = true, chatTutorialSeen = true)

    /** A thread over [DATE] holding [suggestions]. */
    fun chatUi(vararg suggestions: ChangeSuggestion, failed: Set<String> = emptySet()): ChatUi = ChatSamples.B.copy(
        date = CHAT_DATE, messages = listOf(SEED), suggestions = suggestions.toList(), agreeFailed = failed,
    )

    /**
     * The chat's start grid with a change block: all eight chips, two of them the change chips. A payload with no
     * `change` block (an older server) fits six; the fit sweep needs this one to see the eight-chip grid and dock.
     */
    val CHAT_WITH_CHANGE: ChatUi = ChatSamples.B.copy(date = CHAT_DATE)

    /** The Date screen at [now] (locked before 16:30Z, open after). */
    fun dateUi(date: ActiveDate, now: Instant = DateSamples.B_NOW, agreeFailed: Boolean = false) =
        DateUi(date = date, now = now, loading = false, agreeFailed = agreeFailed)

    /** A: the time picker with 19:30 picked. */
    val A = ChangePickerUi(ChangeKind.Time, ChangeEntry.CoordinateTile, slot = SLOTS[1])

    /** B: the place picker with Bar Lenz picked. */
    val B = ChangePickerUi(
        ChangeKind.Place, ChangeEntry.ChatChip, place = PLACES[0],
        list = PlacesView.Loaded(PLACES, google = true, forQuery = ""),
    )

    /** K: A with the send error. */
    val K = A.copy(sendFailed = true)

    /** M: the Places request failed. */
    val M = ChangePickerUi(ChangeKind.Place, ChangeEntry.ChatChip, list = PlacesView.Failed)

    /** N: a search with no result. */
    val N = ChangePickerUi(
        ChangeKind.Place, ChangeEntry.ChatChip, query = "sushi",
        list = PlacesView.Loaded(emptyList(), google = true, forQuery = "sushi"),
    )

    /** D: one time and one place of mine, both open. */
    val D_DATE = withPending(MINE_TIME, MINE_PLACE)

    /** E, J, L: Leonie's time request, with her unread message under it. */
    val E_DATE = withPending(THEIRS_TIME).copy(changeRequest = THEIRS_TIME, unread = DateSamples.UNREAD)

    /** G: the time agreed, 19:30 now binding, `Changed · was 19:00`. */
    val G_DATE = DATE.copy(meetAt = SLOTS[1], change = CHANGE.copy(timeWas = MEET))

    @Composable
    private fun picker(ui: ChangePickerUi) = ChangePickerScreen(CTX, ui, {}, {}, {}, {}, {}, {})

    @Composable
    private fun chat(ui: ChatUi) = ChatScreen(ui, ZONE, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})

    /** The Date screen with Date 03's Check-in bar in its footer, as every Date-screen frame of the sheet draws it. */
    @Composable
    private fun date(ui: DateUi) = DateScreen(ui, ZONE, checkInBar = ArrivalSamples.bar)

    /** Frame [id] ("A" to "N", and "L-thread"), drawn as the ticket's kit props draw it. */
    @Composable
    fun frame(id: String) {
        when (id) {
            "A" -> picker(A)
            "B" -> picker(B)
            "C" -> chat(chatUi(MINE_TIME))
            "D" -> date(dateUi(D_DATE))
            "E" -> date(dateUi(E_DATE))
            "F" -> chat(chatUi(THEIRS_TIME))
            "G" -> date(dateUi(G_DATE))
            "H" -> chat(chatUi(EXPIRED_TIME))
            "I" -> date(dateUi(DATE, DateSamples.A_NOW))
            "J" -> date(dateUi(E_DATE, DateSamples.A_NOW))
            "K" -> picker(K)
            "L" -> date(dateUi(E_DATE, agreeFailed = true))
            "L-thread" -> chat(chatUi(THEIRS_TIME, failed = setOf(THEIRS_TIME.id)))
            "M" -> picker(M)
            "N" -> picker(N)
            else -> Text("Unknown change frame $id")
        }
    }

    /** The ticket's frame letters, in order. */
    val LETTERS = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N")
}
