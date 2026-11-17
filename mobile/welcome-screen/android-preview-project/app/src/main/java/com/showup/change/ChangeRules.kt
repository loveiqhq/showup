/*
 * ChangeRules.kt
 * ShowUp · Date 05's rules, as plain functions (SHOWUP-196)
 *
 * When the change tiles and chips show, the deadline, the distance line, the labels. No view, so each
 * is tested without one (`ChangeRulesTest`); Swift has the same functions under the same names.
 */
package com.showup.change

import com.showup.AppRoute
import com.showup.chat.ChatReason
import com.showup.chat.ChipSurface
import com.showup.chat.SHOWN_REASONS
import com.showup.search.meetTime
import java.time.Instant
import java.time.ZoneId

/** How long before the BINDING meet time a suggestion can no longer be answered (3 Nov 2026). */
private const val DEADLINE_MINUTES = 30L

/** The answer deadline: the binding meet time less 0:30, counted from the binding time, never the suggested one. */
fun changeDeadline(binding: Instant): Instant = binding.minusSeconds(DEADLINE_MINUTES * 60)

/** The last slot the server sent, `{latest}`; null when it sent none. */
fun latestSlot(slots: List<Instant>): Instant? = slots.maxOrNull()

/** `HH:mm` on the phone's clock: every `{time}`, `{deadline}`, `{latest}` and slot (R19), as `meetTime` reads it. */
fun changeClock(at: Instant, zone: ZoneId): String = meetTime(at, zone)

/**
 * Whether the entry for [kind] shows, for the locked card's tiles and the chat's two chips alike (R7, one
 * check). Hidden for both people when the server says no (a committed check-in), from the cutoff on, and --
 * for the time only -- while there is no later slot. Evaluated with the screens' clock tick, so they hide in
 * place. A server before Date 05 sends no block: neither shows.
 */
fun changeKindAvailable(change: DateChange?, kind: ChangeKind, now: Instant): Boolean {
    if (change == null || !change.allowed || now >= change.cutoffAt) return false
    return kind != ChangeKind.Time || change.slots.isNotEmpty()
}

/** The locked card's tiles, in the chips' order. */
fun changeTilesFor(change: DateChange?, now: Instant): List<ChatReason> = buildList {
    if (changeKindAvailable(change, ChangeKind.Time, now)) add(ChatReason.ChangeTime)
    if (changeKindAvailable(change, ChangeKind.Place, now)) add(ChatReason.ChangePlace)
}

/** The chips the start grid and the dock show: the shipped ones, the two changes only while [changeTilesFor] has them. */
fun visibleReasons(change: DateChange?, now: Instant): List<ChatReason> {
    val changes = changeTilesFor(change, now).toSet()
    return SHOWN_REASONS.filter { it !in setOf(ChatReason.ChangeTime, ChatReason.ChangePlace) || it in changes }
}

/** The kind a change chip or tile opens; null for any other chip. */
fun changeKindOf(reason: ChatReason): ChangeKind? = when (reason) {
    ChatReason.ChangeTime -> ChangeKind.Time
    ChatReason.ChangePlace -> ChangeKind.Place
    else -> null
}

/**
 * The picker a chip or tile opens: its kind, and the §42 entry -- `coordinate_tile` for the locked card's
 * tiles, `chat_chip` for the grid and the dock. Null for a chip that opens no picker.
 */
fun changePickerRoute(reason: ChatReason, surface: ChipSurface): AppRoute.ChangePicker? {
    val kind = changeKindOf(reason) ?: return null
    val entry = if (surface == ChipSurface.CoordinateCard) ChangeEntry.CoordinateTile else ChangeEntry.ChatChip
    return AppRoute.ChangePicker(kind.wire, entry.wire)
}

/**
 * `350 m` under a kilometre, rounded to the nearest 50; `1.1 km` from it, one decimal (R3). Integer maths
 * so a half rounds up on every phone: 349 is 350 m, 999 reads `1.0 km` (50 m steps reach a full kilometre),
 * 1049 is `1.0 km`, 1050 is `1.1 km`. Never in analytics.
 */
fun formatDistance(meters: Int): String {
    val m = meters.coerceAtLeast(0)
    val stepped = (m + 25) / 50 * 50
    if (m < 1000 && stepped < 1000) return "$stepped m"
    val tenths = (m + 50) / 100
    return "${tenths / 10}.${tenths % 10} km"
}

/** `Café`, `Bar`, `Restaurant`: the card and request card read `{Type} · {address}`. */
fun placeTypeLabel(type: PlaceType): String = when (type) {
    PlaceType.Cafe -> "Café"
    PlaceType.Bar -> "Bar"
    PlaceType.Restaurant -> "Restaurant"
}

/** The row's tag, uppercased as the kit draws it. */
fun placeTypeTag(type: PlaceType): String = placeTypeLabel(type).uppercase()
