/*
 * ChangeSheets.kt
 * ShowUp · the time and place pickers, Date 05 A, B, K, M and N (SHOWUP-196), from `screen-change-reference.jsx`
 *
 * One shell, [ChangeFullSheet]: a full screen with one scrolling list and a footer pinned to the bottom of
 * the column -- the binding note (or K's error) over the two buttons. The footer is always above the fold;
 * the list's bottom padding lets the last row scroll clear of it.
 *
 * The screens take values and lambdas: [ChangePickerUi] in, taps out. They never receive a navigator, so
 * every frame renders in every state at every size with no device.
 */
package com.showup.change

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.date.pressable
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.CssShadow
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Purple
import com.showup.designsystem.Raised
import com.showup.designsystem.SheetErrorBox
import com.showup.designsystem.SheetPrimaryButton
import com.showup.designsystem.SheetSecondaryButton
import com.showup.designsystem.Spacing
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.roundedShadows
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.Instant
import java.time.ZoneId

const val CHANGE_PRIMARY_TAG = "change-primary"
const val CHANGE_CANCEL_TAG = "change-cancel"
const val CHANGE_SEARCH_TAG = "change-search"
const val CHANGE_LOADING_TAG = "change-loading"
const val CHANGE_TRY_AGAIN_TAG = "change-try-again"
const val CHANGE_FOOTER_TAG = "change-footer"

fun placeRowTag(placeId: String) = "change-place-$placeId"
fun slotTag(slot: Instant) = "change-slot-${slot.epochSecond}"

/**
 * The values both pickers read from the date, already the date's own: the match's first name, the BINDING
 * time and place (a pending suggestion moves nothing), the server's later slots and the phone's zone.
 */
data class ChangeSheetContext(
    val name: String,
    val bindingTime: Instant,
    val bindingPlace: String,
    val slots: List<Instant>,
    val zone: ZoneId,
) {
    val time: String get() = changeClock(bindingTime, zone)
    val deadline: String get() = changeClock(changeDeadline(bindingTime), zone)
}

// ── the shell ───────────────────────────────────────────────────────────────────────────────────

/** The list's bottom padding in the reference; the footer's measured height replaces it where that is more. */
private val ListBottomReserve = 280.dp

/** `0 -10px 28px rgba(29,17,41,.08)`, cast up. */
private val FooterLift = listOf(CssShadow(Fg.copy(alpha = 0.08f), offsetY = (-10).dp, blur = 28.dp))

/**
 * The full-screen shell: the list scrolls (pad 0 20 280), the footer sits at the bottom of the column (pad
 * 14 20 30, gap 10, the soft rule above, [FooterLift]). The footer's fill is the reference's 94% cream, drawn
 * as the opaque cream it composites to over the cream page and WITHOUT backdrop blur: the 193 rule for pinned
 * bars -- a translucent bar over white rows would show their text through it. It is aligned to the bottom of
 * the box, never placed by a Y.
 *
 * The top is the status-bar inset plus [Spacing.md]: the reference's 62 is a mock status bar (54) and a gap,
 * and the ticket says to anchor to the safe-area insets.
 */
@Composable
fun ChangeFullSheet(footer: @Composable () -> Unit, content: @Composable () -> Unit) {
    var footerPx by remember { mutableIntStateOf(0) }
    val footerDp = with(LocalDensity.current) { footerPx.toDp() }
    Box(Modifier.fillMaxSize().background(Cream)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = Spacing.md)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // The footer is over the list: its last row must be able to scroll clear of the footer, at
                    // 280 as drawn and at the footer's own height plus the row gap when that is taller (the
                    // largest type, a gesture bar).
                    .padding(start = 20.dp, end = 20.dp, bottom = maxOf(ListBottomReserve, footerDp + 16.dp)),
            ) { content() }
        }
        Footer(Modifier.align(Alignment.BottomCenter).onSizeChanged { footerPx = it.height }, footer)
    }
}

@Composable
private fun BoxScope.Footer(modifier: Modifier, content: @Composable () -> Unit) {
    val bottomInset = with(LocalDensity.current) {
        WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom).getBottom(this).toDp()
    }
    Column(
        modifier
            .fillMaxWidth()
            .testTag(CHANGE_FOOTER_TAG)
            .roundedShadows(SolidColor(Cream), 0.dp, FooterLift)
            .background(Cream)
            .drawBehind { drawRect(BorderSoft, size = Size(size.width, 1.dp.toPx())) }
            // Pad 14 20 30 plus the 1 px rule CSS counts in the box. The 30 is the mock home indicator: where the
            // real bottom inset (the gesture bar, or the keyboard) is larger it replaces the 30 rather than adding
            // to it, so a button-navigation phone keeps the drawn 30.
            .padding(start = 20.dp, end = 20.dp, top = 15.dp, bottom = maxOf(30.dp, bottomInset)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}

// ── the head ────────────────────────────────────────────────────────────────────────────────────

private val HeadTitle = TextStyle(
    fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = (24 * 1.15).sp,
    letterSpacing = (-0.01).em, color = Fg, textAlign = TextAlign.Center,
)
private val SubText = TextStyle(fontFamily = Manrope, fontSize = 13.5.sp, lineHeight = (13.5 * 1.45).sp, color = Muted)

/** The violet-tinted info box: three short lines, the value of the first in the ink, the third 6 below. */
@Composable
private fun ChangeSheetHead(title: String, current: AnnotatedString, line2: String, line3: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Margin 4 0 6.
        Text(
            title,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp).semantics { heading() }.cssLineBox(HeadTitle.lineHeight),
            style = HeadTitle,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(Purple.copy(alpha = 0.07f), shape)
                .border(1.dp, Purple.copy(alpha = 0.14f), shape)
                // Pad 12 14 plus the 1 px border, which CSS counts in the box.
                .padding(horizontal = 15.dp, vertical = 13.dp),
        ) {
            SubLine(current)
            SubLine(AnnotatedString(line2))
            Spacer(Modifier.height(6.dp))
            SubLine(AnnotatedString(line3))
        }
    }
}

@Composable
private fun SubLine(text: AnnotatedString) {
    Text(text, modifier = Modifier.fillMaxWidth().prettyWrap(text, SubText).cssLineBox(SubText.lineHeight), style = SubText)
}

private fun currentValue(label: String, value: String) = buildAnnotatedString {
    append(label)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Fg)) { append(value) }
}

// ── the binding note and the error ──────────────────────────────────────────────────────────────

private val NoteInk = Color(0xFF78350F)
private val NoteIcon = Color(0xFFB45309)

/**
 * The binding rule (A, B, C, D): the amber box 12 14 (compact 9 11), radius 14, `alert-circle` 17 (15), the
 * first sentence bold. [youDecide]: the match suggested it, so the binding holds until YOU agree. Strings in,
 * so the card, the request card and the pickers each pass the values they hold.
 *
 * The amber is the kit's warning tone for this one note (`rgba(245,158,11,.09)`, `#78350f`), which no colour
 * token holds -- it is Date 05's alone, so it stays here.
 */
@Composable
fun BindingNote(
    name: String,
    time: String,
    place: String,
    deadline: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    youDecide: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val size = if (compact) 12.sp else 13.sp
    val style = TextStyle(fontFamily = Manrope, fontSize = size, lineHeight = size * 1.45f, color = NoteInk)
    val bold = if (youDecide) ChangeCopy.BINDING_BOLD_YOU.format(time, place, deadline) else ChangeCopy.BINDING_BOLD.format(time, place, name, deadline)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        append(ChangeCopy.BINDING_REST)
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFF59E0B).copy(alpha = 0.09f), shape)
            .border(1.dp, Color(0xFFD97706).copy(alpha = 0.28f), shape)
            // Pad 12 14 (compact 9 11) plus the 1 px border.
            .padding(horizontal = if (compact) 12.dp else 15.dp, vertical = if (compact) 10.dp else 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { Icon(BrandIcon.AlertCircle, if (compact) 15.dp else 17.dp, tint = NoteIcon, strokeWidth = 2.2f) }
        Text(text, modifier = Modifier.weight(1f).prettyWrap(text, style).cssLineBox(style.lineHeight), style = style)
    }
}

/** K's box, the Date 02 / 194 `SheetErrorBox` look with the ticket's string: it replaces the binding note. */
@Composable
fun ChangeError(message: String, modifier: Modifier = Modifier) {
    SheetErrorBox(message, modifier)
}

// ── A · the time picker ─────────────────────────────────────────────────────────────────────────

/**
 * A, and K when [ChangePickerUi.sendFailed]: the head, then a 4-column grid of the server's slots (gap 6,
 * h 44, radius 12). The primary reads `Pick a time` (disabled) until one is selected, then `Suggest {slot}`.
 */
@Composable
fun TimeChangeSheet(
    ctx: ChangeSheetContext,
    ui: ChangePickerUi,
    onSelect: (Instant) -> Unit,
    onSuggest: () -> Unit,
    onCancel: () -> Unit,
) {
    val latest = latestSlot(ctx.slots)?.let { changeClock(it, ctx.zone) }.orEmpty()
    ChangeFullSheet(
        footer = {
            ChangeFooter(
                ctx, ui,
                primary = ui.slot?.let { ChangeCopy.SUGGEST.format(changeClock(it, ctx.zone)) } ?: ChangeCopy.PICK_TIME,
                onSuggest = onSuggest, onCancel = onCancel,
            )
        },
    ) {
        ChangeSheetHead(
            title = ChangeCopy.TIME_TITLE,
            current = currentValue(ChangeCopy.TIME_CURRENT, ctx.time),
            line2 = ChangeCopy.TIME_CHOOSE.format(ctx.name, latest),
            line3 = ChangeCopy.AGREE_BY.format(ctx.name, ctx.deadline, ctx.time),
        )
        SlotGrid(ctx, ui.slot, enabled = !ui.sending, onSelect)
    }
}

/** The pinned footer both pickers share: K's error or the binding note, then the primary and Cancel. */
@Composable
private fun ChangeFooter(ctx: ChangeSheetContext, ui: ChangePickerUi, primary: String, onSuggest: () -> Unit, onCancel: () -> Unit) {
    if (ui.sendFailed) {
        ChangeError(ChangeCopy.SEND_FAILED)
    } else {
        BindingNote(ctx.name, ctx.time, ctx.bindingPlace, ctx.deadline)
    }
    SheetPrimaryButton(
        label = primary,
        onClick = onSuggest,
        modifier = Modifier.testTag(CHANGE_PRIMARY_TAG),
        enabled = ui.hasSelection,
        waiting = ui.sending,
    )
    SheetSecondaryButton(ChangeCopy.CANCEL, onCancel, Modifier.testTag(CHANGE_CANCEL_TAG), inert = ui.sending)
}

private const val SLOT_COLUMNS = 4

private val SlotText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFeatureSettings = "tnum")

@Composable
private fun SlotGrid(ctx: ChangeSheetContext, selected: Instant?, enabled: Boolean, onSelect: (Instant) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ctx.slots.chunked(SLOT_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { slot ->
                    val on = slot == selected
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag(slotTag(slot))
                            .semantics { this.selected = on }
                            .then(if (enabled) Modifier.pressable(Role.RadioButton) { onSelect(slot) } else Modifier)
                            .background(if (on) Purple else Elevated, shape)
                            .then(if (on) Modifier else Modifier.border(1.dp, Border, shape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(changeClock(slot, ctx.zone), style = SlotText, color = if (on) Color.White else Fg)
                    }
                }
                // A short last row keeps its columns: the grid is 4 equal tracks, not a stretched row.
                repeat(SLOT_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── B · the place picker ────────────────────────────────────────────────────────────────────────

/**
 * B, with K (the error over the binding note), M (the list's failure) and N (a query with no result). The
 * search field is usable in every one of them; the primary reads `Pick a place` (disabled) until a row is
 * selected, then `Suggest {venue}`.
 */
@Composable
fun PlaceChangeSheet(
    ctx: ChangeSheetContext,
    ui: ChangePickerUi,
    onQuery: (String) -> Unit,
    onSelect: (PlaceOption) -> Unit,
    onRetry: () -> Unit,
    onSuggest: () -> Unit,
    onCancel: () -> Unit,
) {
    ChangeFullSheet(
        footer = {
            ChangeFooter(
                ctx, ui,
                primary = ui.place?.let { ChangeCopy.SUGGEST.format(it.name) } ?: ChangeCopy.PICK_PLACE,
                onSuggest = onSuggest, onCancel = onCancel,
            )
        },
    ) {
        ChangeSheetHead(
            title = ChangeCopy.PLACE_TITLE,
            current = currentValue(ChangeCopy.PLACE_CURRENT, ctx.bindingPlace),
            line2 = ChangeCopy.PLACE_CHOOSE,
            line3 = ChangeCopy.AGREE_BY.format(ctx.name, ctx.deadline, ctx.bindingPlace),
        )
        SearchField(ui.query, onQuery)
        when (val list = ui.list) {
            // Empty, and spoken: no new visible string or skeleton (R17).
            PlacesView.Loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag(CHANGE_LOADING_TAG)
                    .semantics { contentDescription = ChangeCopy.LOADING_PLACES },
            )
            PlacesView.Failed -> PlacesFailed(onRetry)
            is PlacesView.Loaded -> if (list.places.isEmpty()) {
                if (list.forQuery.isNotEmpty()) NoPlaces(list.forQuery)
            } else {
                PlaceList(list, ui.place?.placeId, enabled = !ui.sending, onSelect)
            }
        }
    }
}

private val SearchFieldText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Fg)

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .height(48.dp)
            .background(Elevated, shape)
            .border(1.5.dp, Border, shape)
            // Pad 0 14 plus the 1.5 border.
            .padding(horizontal = 15.5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BrandIcon.Search, 18.dp, tint = FgSubtle, strokeWidth = 2.2f)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(ChangeCopy.SEARCH_PLACEHOLDER, style = SearchFieldText.copy(color = FgSubtle))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().testTag(CHANGE_SEARCH_TAG),
                singleLine = true,
                textStyle = SearchFieldText,
                cursorBrush = SolidColor(Purple),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
            )
        }
    }
}

private val ListLabel = TextStyle(
    fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, letterSpacing = 0.08.sp, color = FgSubtle,
)
private val RowName = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Fg)
private val RowTag = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 0.05.sp, color = Purple)
private val RowAddress = TextStyle(fontFamily = Manrope, fontSize = 12.5.sp, color = Muted)

/**
 * Google's attribution for Places results shown without a Google map (R2): the text form, Manrope 400 12 in
 * `#5E5E5E`, one line, not localised, under the list. That grey is Google's mandated attribution colour --
 * a third-party brand value the house rule allows as a literal -- see Google Maps Platform's Places policy,
 * "Attribution" for results displayed without a map. It is shown only when the server says Google answered.
 */
private val GoogleAttributionInk = Color(0xFF5E5E5E)
private val GoogleAttribution = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = GoogleAttributionInk)

@Composable
private fun PlaceList(list: PlacesView.Loaded, selectedId: String?, enabled: Boolean, onSelect: (PlaceOption) -> Unit) {
    // Uppercase is the kit's `text-transform`: drawn so, but read (and tested) as the ticket's own string.
    val label = if (list.forQuery.isNotEmpty()) ChangeCopy.RESULTS else ChangeCopy.NEARBY
    Text(
        label.uppercase(),
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp).clearAndSetSemantics { text = AnnotatedString(label) },
        style = ListLabel,
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.places.forEach { PlaceRow(it, on = it.placeId == selectedId, enabled = enabled, onClick = { onSelect(it) }) }
    }
    if (list.google) {
        Text(
            ChangeCopy.GOOGLE_MAPS,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            style = GoogleAttribution,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PlaceRow(place: PlaceOption, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    // The border counts in the box: the selected 1.5 is wider than the resting 1, as in CSS.
    val bw: Dp = if (on) 1.5.dp else 1.dp
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(placeRowTag(place.placeId))
            .semantics { selected = on }
            .then(if (enabled) Modifier.pressable(Role.RadioButton, onClick = onClick) else Modifier)
            .background(if (on) Purple.copy(alpha = 0.07f) else Elevated, shape)
            .border(bw, if (on) Purple else BorderSoft, shape)
            .padding(horizontal = 12.dp + bw, vertical = 11.dp + bw),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Raised, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(BrandIcon.MapPin, 18.dp, tint = Purple, strokeWidth = 2.2f)
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(place.name, style = RowName, modifier = Modifier.weight(1f, fill = false))
                Text(placeTypeTag(place.type), style = RowTag)
            }
            Text(
                "${place.address} · ${formatDistance(place.distanceMeters)}",
                modifier = Modifier.padding(top = 2.dp),
                style = RowAddress,
            )
        }
        Box(
            Modifier
                .size(22.dp)
                .background(if (on) Purple else Elevated, CircleShape)
                .then(if (on) Modifier else Modifier.border(1.5.dp, Border, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(BrandIcon.Check, 13.dp, tint = Color.White, strokeWidth = 3f)
        }
    }
}

private val FailedText = TextStyle(
    fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = (15 * 1.45).sp, color = Muted, textAlign = TextAlign.Center,
)

/** M: pad 28 12, gap 12, the message (at most 290) and a `Try again` pill; the search field above stays usable. */
@Composable
private fun PlacesFailed(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val text = AnnotatedString(ChangeCopy.PLACES_FAILED)
        Text(
            text,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive }
                .prettyWrap(text, FailedText, centered = true)
                .cssLineBox(FailedText.lineHeight),
            style = FailedText,
        )
        val pill = RoundedCornerShape(50)
        Box(
            Modifier
                .testTag(CHANGE_TRY_AGAIN_TAG)
                .heightIn(min = 44.dp)
                .pressable(Role.Button, onClick = onRetry)
                .background(Elevated, pill)
                .border(1.dp, Border, pill)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(ChangeCopy.TRY_AGAIN, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
        }
    }
}

/** N: pad 28 12, gap 4, no red and no retry: a query with no result is an answer, not a failure. */
@Composable
private fun NoPlaces(query: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val head = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Fg, textAlign = TextAlign.Center)
        val line = AnnotatedString(ChangeCopy.NO_PLACES.format(query))
        Text(line, modifier = Modifier.prettyWrap(line, head, centered = true), style = head)
        Text(ChangeCopy.TRY_ANOTHER, style = TextStyle(fontFamily = Manrope, fontSize = 13.5.sp, color = Muted, textAlign = TextAlign.Center))
    }
}

// ── the screen the route shows ───────────────────────────────────────────────────────────────────

/** The picker for [ChangePickerUi.kind]: what the route draws. */
@Composable
fun ChangePickerScreen(
    ctx: ChangeSheetContext,
    ui: ChangePickerUi,
    onSelectSlot: (Instant) -> Unit,
    onSelectPlace: (PlaceOption) -> Unit,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    onSuggest: () -> Unit,
    onCancel: () -> Unit,
) {
    when (ui.kind) {
        ChangeKind.Time -> TimeChangeSheet(ctx, ui, onSelectSlot, onSuggest, onCancel)
        ChangeKind.Place -> PlaceChangeSheet(ctx, ui, onQuery, onSelectPlace, onRetry, onSuggest, onCancel)
    }
}
