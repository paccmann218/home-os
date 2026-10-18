package com.homeos.bridge.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import kotlinx.coroutines.delay

/** Something the home said or asked, waiting to be shown. */
@Immutable
data class BannerMessage(
    val kicker: String,
    val text: String,
    /** Distinguishes two identical messages, so the second one re-triggers the timer. */
    val id: Long,
)

private const val DWELL_MS = 6_500L

/**
 * The in-app announcement.
 *
 * When the app is open, posting a system notification is the wrong surface twice over: it arrives
 * over the very screen that already shows the thing, and it hands the moment to the platform in a
 * tray full of other apps. So while HomeOS is in the foreground the home speaks here instead, on
 * the app's own glass, and the notification is kept for when nobody is looking.
 *
 * It carries the same rail as the notification card and the same hues, so the two are recognisably
 * one voice arriving by two routes.
 */
@Composable
fun HomeOsBanner(
    message: BannerMessage?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val statusBars = WindowInsets.statusBars.asPaddingValues()

    LaunchedEffect(message?.id) {
        if (message != null) {
            delay(DWELL_MS)
            onDismiss()
        }
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = message != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(tween(360)) { -it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(240)) { -it } + fadeOut(tween(180)),
        ) {
            GlassSurface(
                level = GlassLevel.Dock,
                shape = HomeOsShapes.block,
                modifier = Modifier
                    .padding(
                        start = HomeOsDims.screenMargin,
                        end = HomeOsDims.screenMargin,
                        top = statusBars.calculateTopPadding() + ShellDims.topBar - 6.dp,
                    )
                    .fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(
                            onClick = {
                                onOpen()
                                onDismiss()
                            },
                        )
                        .padding(start = 13.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(38.dp)
                            .clip(HomeOsShapes.pill)
                            .background(
                                Brush.verticalGradient(listOf(colors.teal, colors.violet)),
                            ),
                    )
                    Spacer(Modifier.width(13.dp))
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            message?.kicker.orEmpty(),
                            style = type.micro,
                            color = colors.teal,
                        )
                        Text(
                            message?.text.orEmpty(),
                            style = type.body,
                            color = colors.onShell,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
