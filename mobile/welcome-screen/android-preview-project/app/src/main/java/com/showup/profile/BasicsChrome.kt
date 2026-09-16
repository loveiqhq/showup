/*
 * BasicsChrome.kt
 * ShowUp · the shell every screen in "The basics" shares
 *
 * WHY THIS FILE EXISTS AT ALL
 *
 * The profile epic lists four things as built-once infrastructure, and says a second copy of any of
 * them is a bug: the header shell with the leading slot as a VARIANT, the step progress bar, the
 * single-question scaffold with its one flex spacer, and the reserved status region. Three screens
 * share them and the differences between those screens are parameters, not forks.
 *
 * WHY IT IS HERE AND NOT IN designsystem/
 *
 * [AppHeader] needs the back chevron, and `Icon` / `BrandIcon` still live in
 * `welcome/WelcomeShell.kt` -- a screen file. Putting the header in `designsystem` would make the
 * design system import from a screen, which is the dependency this project has been burned by
 * twice. The header is the profile GROUP's shell, which is what the epic asks for; promoting it to
 * a general primitive is blocked on relocating Icon, and that is its own task.
 */
package com.showup.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Spacing
import com.showup.designsystem.ComponentSizes
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** What the header's leading 36 slot holds. */
enum class HeaderLeading {
    /**
     * Nothing -- but the slot still occupies its 36.
     *
     * Step 1. Profile creation is mandatory once entered, so there is nothing behind it to return
     * to. The slot is kept rather than removed because removing it un-centres the title, and the
     * title is the SAME STRING on all three steps: it must not shift as the user advances.
     */
    None,

    /** A back chevron. Steps 2 and 3. */
    Back,
}

/**
 * The section header: a centred title with a 36 slot on each side.
 *
 * THE TITLE IS CENTRED BY GEOMETRY, NOT BY GUESSWORK
 *
 * Both slots are a fixed 36 whatever they hold, and the title takes what is between them with
 * `weight(1f)` and centre alignment. Centring the title in the REMAINING space instead would put it
 * 36 off-centre whenever only one slot is filled -- and would move it by 36 between step 1 and
 * step 2, which is the one thing a shared title must never do.
 *
 * The back control's touch area is [ComponentSizes.minTapTarget], not the 36 it draws.
 */
@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: HeaderLeading = HeaderLeading.Back,
    onBack: () -> Unit = {},
    /** Spoken by TalkBack. The chevron is a Canvas, so without this the control has no name. */
    backLabel: String = "Back",
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            if (leading == HeaderLeading.Back) {
                // requiredSize, not size: the drawn slot is 36 and the TOUCH area is 44.
                //
                // `.size(36.dp)` pins the maximum as well as the minimum, so a `defaultMinSize(44)`
                // under it is silently inert -- the fit harness caught exactly that here, reporting
                // 36dp at all 17 widths. `requiredSize` ignores the parent's 36 and overflows it
                // symmetrically, so the hit area grows while the layout slot, and therefore the
                // centred title, does not move.
                //
                // The ticket says a "36 x 36 target". 36 fails the 44 floor every tappable thing in
                // this app is held to, and the welcome flow's back control already resolved the
                // same conflict the same way. Nothing visible changes: the control has no fill,
                // only the 24 chevron is drawn. Raised with design.
                Box(
                    Modifier
                        .requiredSize(ComponentSizes.minTapTarget)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = backLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BrandIcon.ChevronLeft, 24.dp, tint = Fg, strokeWidth = 2.dp)
                }
            }
        }

        Text(
            title,
            modifier = Modifier.weight(1f),
            color = Fg,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )

        // Empty on every screen in this flow, and still occupying its 36 for the same reason the
        // leading slot does.
        Box(Modifier.width(36.dp))
    }
}

/**
 * The single-question screen scaffold: header, a top-anchored content column, ONE flexible spacer,
 * and a bottom-anchored CTA row.
 *
 * THE ONE SPACER IS THE WHOLE DESIGN
 *
 * Everything in [content] is top-anchored and everything in [cta] is bottom-anchored, with exactly
 * one `weight(1f)` between them. The keyboard's height is not ours -- it varies by OS, language and
 * prediction settings -- so the spacer is what absorbs every device and keyboard difference. At
 * 390 x 844 it resolves to about 102; at 375 x 667 it collapses; at 430 x 932 it takes the surplus.
 * A second flexible element would split that budget and the CTA would stop sitting where it does.
 *
 * NO AMBIENT BACKDROP. Flat [Cream], unlike the welcome flow's orange and violet orbs. The keyboard
 * owns the bottom half of every screen in this group and a backdrop behind it reads as noise.
 *
 * `imePadding` is what keeps the CTA above the keyboard without anyone hard-coding a keyboard
 * height; `safeDrawing` insets take the status bar and the gesture bar. Between them there is no
 * absolute Y anywhere in this flow.
 */
@Composable
fun BasicsScaffold(
    title: String,
    leading: HeaderLeading,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    cta: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        AppHeader(title = title, leading = leading, onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                // Content pad-top 4, gutter 24, floor 0 -- the CTA row carries its own bottom
                // margin because it differs per screen (18 on name, 10 on email).
                .padding(start = 24.dp, end = 24.dp, top = 4.dp),
        ) {
            content()

            // The ONE spacer. Not two, and nothing else in this column is flexible.
            Box(Modifier.weight(1f))

            cta()
        }
    }
}
