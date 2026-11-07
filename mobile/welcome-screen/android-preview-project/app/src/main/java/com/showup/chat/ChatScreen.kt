/*
 * ChatScreen.kt
 * ShowUp · the coordinate chat (SHOWUP-194): B, the start grid, and C to E, the thread with its dock;
 * F, the End the date sheet, over it. One screen, one screen id, from `screen-chat-reference.jsx`.
 *
 * ONE COLUMN, ONE FLEXIBLE ELEMENT. Header, then the thread (the only scroll), then the dock (the
 * field, then the chips), then the OS keyboard. The keyboard is the system's and its height is never
 * known here: the column is padded by the larger of the keyboard and the navigation bar, and the
 * thread absorbs the difference. No absolute positions.
 *
 * The screen takes [ChatUi] and returns pixels; every decision is the model's or [ChatRules]'.
 */
package com.showup.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.date.ActiveDate
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.OverflowTapTarget
import com.showup.designsystem.ShadowViolet
import com.showup.designsystem.blurBehindModalShell
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.pillShadows
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.rememberMotion
import com.showup.search.CentredStatement
import com.showup.search.SearchCopy
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline
import java.time.ZoneId

/** The field's text: 14.5 / 500 / 1.4, ink. */
private val FieldText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, lineHeight = (14.5 * 1.4).sp, color = Fg)

@Composable
fun ChatScreen(
    ui: ChatUi,
    zone: ZoneId,
    onBack: () -> Unit,
    onPick: (ChatReason, ChipSurface) -> Unit,
    onEdit: (String) -> Unit,
    onAckGuide: () -> Unit,
    onSend: () -> Unit,
    onEndCard: () -> Unit,
    onKeep: () -> Unit,
    onEnd: () -> Unit,
    onRetry: () -> Unit,
) {
    val date = ui.date
    val motion = rememberMotion()
    // `ModalShell`'s backdrop blur, arriving with the sheet.
    val blur by animateFloatAsState(
        targetValue = if (ui.endSheet) 1f else 0f,
        animationSpec = tween(if (motion.enabled) Motion.SHEET else 0),
        label = "chat-sheet-blur",
    )
    Box(Modifier.fillMaxSize().background(Cream)) {
        if (date == null) return@Box
        Column(
            Modifier
                .fillMaxSize()
                .blurBehindModalShell(blur)
                // The larger of the keyboard and the navigation bar: with the keys up the dock sits on
                // them, with them down it clears the gesture bar. Never a height in code.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
        ) {
            ChatHeader(date, ui.now, zone, onBack)
            when {
                ui.loading -> Box(Modifier.weight(1f).fillMaxWidth().background(Elevated))
                ui.failed != null -> Box(Modifier.weight(1f).fillMaxWidth().background(Elevated)) {
                    CentredStatement(ChatCopy.LOAD_FAILED, SearchCopy.TRY_AGAIN, onRetry)
                }
                ui.start -> StartGrid(Modifier.weight(1f), onPick)
                else -> Thread(ui, date, zone, Modifier.weight(1f), onAckGuide, onEndCard)
            }
            if (!ui.loading && ui.failed == null && !ui.start) Dock(ui, date, onPick, onEdit, onSend)
        }
        if (ui.endSheet) {
            EndDateSheet(date.match.name, ending = ui.ending, failed = ui.endFailed != null, onKeep = onKeep, onEnd = onEnd)
        }
    }
}

// ── B · start ───────────────────────────────────────────────────────────────

/**
 * B: one scroll, white, pad 20 16 20 -- the guide (the headline Lora 700 25 with `reason` washed, then
 * two lines 14 / 1.5 muted, at most 290, 20 below), the five tiles in two columns, and the privacy line
 * 18 below. No field and no dock: a tile opens the thread with that chip.
 */
@Composable
private fun StartGrid(modifier: Modifier, onPick: (ChatReason, ChipSurface) -> Unit) {
    val body = TextStyle(fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Muted, textAlign = TextAlign.Center)
    Column(
        modifier
            .fillMaxWidth()
            .background(Elevated)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WashHeadline(
            parts = listOf(ChatCopy.START_HEADLINE_LEAD to false, ChatCopy.START_HEADLINE_ITALIC to true),
            fontSize = 25.sp,
            lineHeight = (25 * 1.14).sp,
            letterSpacing = (-0.02).em,
            balance = true,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        val text = AnnotatedString(ChatCopy.START_BODY_1 + "\n" + ChatCopy.START_BODY_2)
        Text(
            text,
            modifier = Modifier.widthIn(max = 290.dp).prettyWrap(text, body, centered = true).cssLineBox(body.lineHeight),
            style = body,
        )
        Spacer(Modifier.height(20.dp))
        ChipGrid(SHOWN_REASONS, selected = null, compact = false, surface = ChipSurface.Entry, onPick = onPick)
        LockLine(ChatCopy.START_PRIVACY, Modifier.padding(top = 18.dp))
    }
}

// ── C to E · the thread ──────────────────────────────────────────────────────

/**
 * The thread, pad 14 14 8 (6 14 6 while the guide waits, so it has the room on a short phone), gap 12:
 * the messages oldest first, the avatar beside the last of each of the match's runs. While "I can't make
 * it today" is selected the guide (or its collapsed line) sits at the foot of the thread -- the
 * reference's `margin-top: auto`. Otherwise, once such a message is in, the End date card follows the
 * messages as the thread's last item, as the reference and the spec sheet's E place it.
 */
@Composable
private fun Thread(
    ui: ChatUi,
    date: ActiveDate,
    zone: ZoneId,
    modifier: Modifier,
    onAckGuide: () -> Unit,
    onEndCard: () -> Unit,
) {
    val scroll = rememberScrollState()
    val guide = ui.selected == ChatReason.CantMakeIt
    val waiting = guide && ui.guide == Guide.Locked
    val footKey: Any? = when {
        guide -> ui.guide
        ui.endCard -> "end-card"
        else -> null
    }
    ThreadScroll(
        state = scroll,
        contentPadding = if (waiting) PaddingValues(horizontal = 14.dp, vertical = 6.dp) else PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
        messageCount = ui.messages.size,
        footKey = footKey,
        modifier = modifier.fillMaxWidth().background(Elevated).testTag(CHAT_THREAD_TAG),
        foot = if (guide) {
            { CancelGuide(date.match.name, acked = ui.guide == Guide.Acked, onAck = onAckGuide) }
        } else {
            null
        },
    ) {
        ui.messages.forEachIndexed { i, message ->
            val lastOfRun = !message.fromYou && (i == ui.messages.lastIndex || ui.messages[i + 1].fromYou)
            ChatBubble(message, date.match.name, date.match.photoUrl, showAvatar = lastOfRun, zone = zone)
        }
        if (ui.endCard) EndDateCard(onEndCard)
    }
}

// ── the dock ─────────────────────────────────────────────────────────────────

/**
 * The field (pad 11 14 9, gap 9) and the compact chips (pad 2 14 10, gap 6, no heading) on cream 96%
 * glass under a soft rule. A failed send says so between the two and keeps the draft and the chip.
 */
@Composable
private fun Dock(
    ui: ChatUi,
    date: ActiveDate,
    onPick: (ChatReason, ChipSurface) -> Unit,
    onEdit: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().pinnedGlass().testTag(CHAT_DOCK_TAG)) {
        Composer(ui, date.match.name, onEdit, onSend)
        if (ui.sendFailed) {
            Text(
                ChatCopy.SEND_FAILED,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
                color = DangerFg,
                fontFamily = Manrope,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
        ChipGrid(
            SHOWN_REASONS,
            selected = ui.selected,
            compact = true,
            surface = ChipSurface.Dock,
            onPick = onPick,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
        )
    }
}

/**
 * The field and Send. The field: radius 20, pad 9 14, a 1.5 border in the ink while a chip is selected
 * (otherwise the border token), filled soft and disabled while the guide waits; it grows to 90 and then
 * scrolls. Send: 46 round, the sunset with the violet glow when there is text to send, the border token
 * when not -- both crossing over [Motion.TINT]. After `Got it` the field takes the focus.
 */
@Composable
private fun Composer(ui: ChatUi, name: String, onEdit: (String) -> Unit, onSend: () -> Unit) {
    val motion = rememberMotion()
    val tint = tween<Color>(if (motion.enabled) Motion.TINT else 0)
    val border by animateColorAsState(fieldBorder(ui), tint, label = "field-border")
    val fill by animateColorAsState(if (ui.locked) BorderSoft else Elevated, tint, label = "field-fill")
    val focus = remember { FocusRequester() }
    var lastGuide by remember { mutableStateOf(ui.guide) }
    LaunchedEffect(ui.guide) {
        if (lastGuide == Guide.Locked && ui.guide == Guide.Acked) focus.requestFocus()
        lastGuide = ui.guide
    }
    val placeholder = when {
        ui.locked -> ChatCopy.PLACEHOLDER_LOCKED
        ui.selected == ChatReason.CantMakeIt -> ChatCopy.PLACEHOLDER_SORRY.format(name)
        else -> ChatCopy.PLACEHOLDER
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val shape = RoundedCornerShape(20.dp)
        BasicTextField(
            value = ui.draft,
            onValueChange = onEdit,
            enabled = !ui.locked,
            textStyle = FieldText,
            cursorBrush = SolidColor(Fg),
            modifier = Modifier
                .weight(1f)
                .testTag(CHAT_FIELD_TAG)
                .focusRequester(focus)
                .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box(
                    Modifier
                        // Android's 48 floor: the reference's field is 45.6 tall, a touch area under it.
                        .heightIn(min = 48.dp)
                        .background(fill, shape)
                        .border(1.5.dp, border, shape)
                        // Pad 9 14, plus the 1.5 border CSS counts in the box, plus the 2 every browser
                        // pads a textarea with -- the reference never resets it, and the sheet shows it.
                        .padding(horizontal = 17.5.dp, vertical = 12.5.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (ui.draft.isEmpty()) {
                        Text(placeholder, style = FieldText.copy(color = FgSubtle))
                    }
                    // The reference's textarea `max-height: 90`: past it the field scrolls.
                    Box(Modifier.heightIn(max = 90.dp)) { inner() }
                }
            },
        )
        Spacer(Modifier.width(9.dp))
        SendButton(ui.canSend, onSend)
    }
}

@Composable
private fun SendButton(enabled: Boolean, onSend: () -> Unit) {
    val motion = rememberMotion()
    val on by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(if (motion.enabled) Motion.TINT else 0),
        label = "send-on",
    )
    OverflowTapTarget(
        touch = Modifier
            .testTag(CHAT_SEND_TAG)
            .clickable(enabled = enabled, role = Role.Button, onClick = onSend)
            .semantics { contentDescription = ChatCopy.SEND },
        floor = 48.dp,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clearAndSetSemantics {}
                .background(Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The sunset and its glow fade in over the border-token disc, as CSS fades `background`.
            Box(Modifier.size(46.dp).graphicsLayer { alpha = on }.pillShadows(SunsetBrush, ShadowViolet))
            // `margin-left: -1, margin-top: 1`: the paper plane sits optically centred in the disc.
            Box(Modifier.offset(x = (-1).dp, y = 1.dp)) { Icon(BrandIcon.Send, 19.dp, tint = Color.White, strokeWidth = 2.1f) }
        }
    }
}
