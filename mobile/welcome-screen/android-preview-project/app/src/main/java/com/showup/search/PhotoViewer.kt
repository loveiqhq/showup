/*
 * PhotoViewer.kt
 * ShowUp · the fullscreen photo viewer (Search 01: "A photo tap opens the fullscreen viewer")
 *
 * "Reuse the platform's own media viewer if the build has one" -- it has none, so this is the
 * smallest one that does the job: it opens at the photo tapped, swipes through every photo in
 * profile order, and closes with its X or Android back, back to the same scroll position (the
 * stack underneath never moved). Ink, not black: the design has no pure black.
 */
package com.showup.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** `rgba(255,255,255,.16)` -- the close disc on the dark viewer. */
private val CloseDisc = Color.White.copy(alpha = 0.16f)

@Composable
fun PhotoViewer(
    name: String,
    photos: List<String>,
    startAt: Int,
    onPage: (Int) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val pager = rememberPagerState(initialPage = startAt.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) { photos.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { onPage(it) } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Fg)
            .semantics {
                dialog()
                paneTitle = "$name's photos"
            },
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val url = photos[page]
                if (url.isNotEmpty()) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Photo ${page + 1} of ${photos.size}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .cssLinearGradient(155f, PHOTO_PLACEHOLDERS[page % PHOTO_PLACEHOLDERS.size], listOf(0f, 0.5f, 1f))
                            .semantics { contentDescription = "Photo ${page + 1} of ${photos.size}" },
                    )
                }
            }
        }
        Text(
            "${pager.currentPage + 1} / ${photos.size}",
            style = TextStyle(color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFeatureSettings = "tnum"),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 18.dp),
        )
        // Draws 36 and answers at 44, like every close control in the app.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 8.dp, end = 12.dp)
                .size(ComponentSizes.minTapTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Close") { onClose() }
                .semantics { contentDescription = "Close photos" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.requiredSize(36.dp).background(CloseDisc, CircleShape), contentAlignment = Alignment.Center) {
                Icon(BrandIcon.Close, 18.dp, tint = Color.White, strokeWidth = 2.2f)
            }
        }
    }
}
