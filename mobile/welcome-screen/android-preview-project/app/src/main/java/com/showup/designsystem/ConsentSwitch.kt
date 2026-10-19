/*
 * ConsentSwitch.kt
 * ShowUp · the iOS-style consent switch (SHOWUP-163)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE THUMB ANIMATES, AND THAT IS THE WHOLE REASON THIS FILE EXISTS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The kit's `ConsentToggle` moves its thumb with `justify-content: flex-start | flex-end` and then
 * declares `transition: transform 220ms` on it. Those two never meet: flex realigns the child
 * instantly and there is no transform to interpolate, so the transition is dead code and the thumb
 * jumps. The reference file's own header names it as a KNOWN KIT SHORTCUT and says not to copy it:
 *
 *     "ConsentToggle moves its thumb with justify-content, so the transform transition never runs.
 *      Build a real switch whose thumb animates."
 *
 * So the thumb here is placed by an ANIMATED OFFSET, and the track colour crossfades on the same
 * curve. 220ms on [ShowUpEasing], which is `cubic-bezier(.22,1,.36,1)` -- the kit's own easing,
 * already in `Motion`.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * 51 x 31 DRAWN, 44 x 44 TOUCHED
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The second shortcut the reference names: "ConsentToggle is 51x31. Its hit target must be >= 44x44
 * (pad it)." 51 x 31 is the iOS system switch's size and is what makes it read as a real permission
 * control rather than a brand widget, so the drawing keeps it and the touch area is padded out
 * around it. This project has shipped a 23dp target inside a 56dp row before; the padding is not
 * decoration.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY IT IS HERE AND NOT IN THE SCREEN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `PillButton` sat in `WelcomeShell.kt`, so the tutorial grew its own copy and the two drifted for
 * three weeks. A switch is the most obviously reusable control on this screen -- Location (12) and
 * the settings surfaces will all want one -- so it starts shared rather than being extracted after
 * the second copy exists.
 */
package com.showup.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The drawn track, from the reference: `width: 51, height: 31, borderRadius: 9999`. */
private val TrackWidth = 51.dp
private val TrackHeight = 31.dp

/** `width: 27, height: 27` inside `padding: 2`. */
private val ThumbSize = 27.dp
private val TrackPadding = 2.dp

/** `rgba(29,17,41,0.14)` -- the off track. A tint of the foreground, not a grey. */
private val TrackOff = Color(0x24_1D_11_29)

/**
 * An iOS-style switch whose thumb actually moves.
 *
 * @param label the visible label this switch belongs to. It becomes the accessible name, because
 *   a switch announced as "switch, on" with no subject is a switch nobody can use with a screen
 *   reader off-screen. The row's own text is the name; this does not invent a second one.
 * @param onChange receives the value the user asked for, not the value it now holds. Switching OFF
 *   on Stay reachable opens a confirmation and the state does not change until it is confirmed, so
 *   the caller -- not this control -- decides what happens next.
 */
@Composable
fun ConsentSwitch(
    on: Boolean,
    label: String,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A person who has turned animation off in Settings gets the end state immediately. Not a
    // shorter animation -- none, which is what that setting means.
    val motion = if (rememberMotion().enabled) Motion.CONFIRM else 0
    // THE ANIMATED VALUE IS A POSITION, not an alignment. 51 - 27 - 2 - 2 = 20.
    val travel = TrackWidth - ThumbSize - TrackPadding - TrackPadding
    val thumbX by animateDpAsState(
        targetValue = if (on) travel else 0.dp,
        animationSpec = tween(durationMillis = motion, easing = ShowUpEasing),
        label = "consent-switch-thumb",
    )
    val track by animateColorAsState(
        targetValue = if (on) Success else TrackOff,
        animationSpec = tween(durationMillis = motion, easing = ShowUpEasing),
        label = "consent-switch-track",
    )

    Box(
        modifier
            // THE TAP TARGET, not the drawing. The track is 31 tall and the floor is 44, so the
            // switch grows around it rather than the drawing growing. This project has shipped a
            // 23dp target inside a 56dp row -- a control whose hit area is smaller than it looks
            // is a bug the eye cannot see.
            // 48, NOT THE 44 DEFAULT. `ComponentSizes.minTapTarget` is 44 on both platforms
            // and `TapTarget.kt` says why that is unresolved: the token predates this control and
            // changing it would move hit areas on screens nobody is re-testing. A NEW control has
            // no such excuse -- Material's floor is 48, `CLAUDE.md` states the rule as "44pt /
            // 48dp", and the 4dp is padding around a 31dp track, so it costs nothing anybody can
            // see. The ticket asks for ">= 44" and this clears it on both platforms rather than
            // meeting it on one.
            .minTapTarget(48.dp)
            .clip(CircleShape)
            .toggleable(
                value = on,
                role = Role.Switch,
                // The value the user ASKED for. Switching off here opens a confirmation and the
                // state does not move until it is confirmed, so the caller decides what happens.
                onValueChange = onChange,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(TrackWidth, TrackHeight)
                .clip(CircleShape)
                .background(track)
                .padding(TrackPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = thumbX)
                    // `0 2px 5px rgba(20,12,28,0.28)`. Drawn as an elevation because the thumb is
                    // a small circle on a flat track and the direction of the light does not read
                    // at 27dp -- the one place in this project where elevation is honest.
                    .shadow(2.dp, CircleShape)
                    .size(ThumbSize)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
