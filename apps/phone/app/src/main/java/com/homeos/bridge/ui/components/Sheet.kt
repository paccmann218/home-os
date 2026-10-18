package com.homeos.bridge.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.ui.icons.HomeOsIcons
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * A bottom sheet, built in-composition rather than with `ModalBottomSheet`.
 *
 * The reason is concrete: a `ModalBottomSheet` is hosted in its own platform window, and Haze
 * cannot sample a backdrop across windows, so a glass sheet there would have nothing to blur. Doing
 * it inside the composition keeps the sheet genuinely translucent over the content behind it, and
 * gives the spring curve to this app rather than to Material's defaults.
 *
 * The scrim blurs as well as darkens. A flat black wash is the cheapest possible modal and it reads
 * as a screenshot with a filter on it; softening the page is what actually says *that is still
 * there, it is just not what you are looking at*. Both surfaces sample the same source node, so the
 * blur does not compound — the sheet is simply thicker glass than the scrim, which is why it is
 * legible while the page behind it is not.
 *
 * Place it in [Shell]'s `overlay` slot so it sits above the chrome.
 */
@Composable
fun HomeOsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HomeOs.colors
    val reduced = HomeOs.reducedMotion
    val hazeState = LocalHazeState.current
    val glassOn = HomeOs.glass && hazeState != null
    val navBars = WindowInsets.navigationBars.asPaddingValues()
    val statusBars = WindowInsets.statusBars.asPaddingValues()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // A scrim must not be clickable-with-ripple; it is a dismiss target, not a control.
    val scrimInteraction = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (glassOn) {
                        Modifier.hazeEffect(
                            state = hazeState!!,
                            style = HazeStyle(
                                backgroundColor = colors.shell,
                                // Lighter than it was, and blurred harder. The sheet in front of
                                // it is 60% opaque now rather than 86%, so the page behind is
                                // something you are meant to make out through the glass; at the
                                // old density it was a dark brown wall and the transparency had
                                // nothing to show.
                                tint = HazeTint(colors.shellSunk.copy(alpha = 0.46f)),
                                blurRadius = 26.dp,
                                noiseFactor = 0.06f,
                                fallbackTint = HazeTint(colors.shellSunk.copy(alpha = 0.76f)),
                            ),
                        )
                    } else {
                        Modifier.background(colors.shellSunk.copy(alpha = 0.80f))
                    },
                )
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = if (reduced) fadeIn(tween(200)) else slideInVertically(tween(340)) { it } + fadeIn(tween(200)),
        exit = if (reduced) fadeOut(tween(160)) else slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            GlassSurface(
                level = GlassLevel.Sheet,
                shape = HomeOsShapes.sheet,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight - statusBars.calculateTopPadding() - 24.dp),
            ) {
                // Everything from here down is glass: the blocks, their controls, the close
                // key. One provider, so no screen has to know which material it is drawing on,
                // and nothing can be missed and left as the single cream rectangle in the stack.
                CompositionLocalProvider(LocalOnGlass provides true) {
                Column(Modifier.fillMaxWidth()) {
                    // The grabber is the thing that says *this came up from the bottom and can go
                    // back down*. It is decorative as a control and essential as a signpost, which
                    // is why the close button next to it is the real affordance.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(HomeOsShapes.pill)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            colors.ink.copy(alpha = 0.10f),
                                            colors.ink.copy(alpha = 0.26f),
                                            colors.ink.copy(alpha = 0.10f),
                                        ),
                                    ),
                                ),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = HomeOsDims.screenMargin, end = 12.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title.orEmpty(),
                            style = HomeOs.type.section,
                            color = colors.onShell,
                            modifier = Modifier.weight(1f),
                        )
                        GlassIconButton(
                            icon = HomeOsIcons.close,
                            contentDescription = "Close",
                            onClick = onDismiss,
                            diameter = 38.dp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    GlassDivider(Modifier.padding(horizontal = HomeOsDims.screenMargin))
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = HomeOsDims.screenMargin,
                                // The full margin on both sides: a pane reserves no room for a
                                // hard offset, so the short end margin a paper block needs would
                                // just read as the sheet being out of true.
                                end = HomeOsDims.screenMargin,
                                top = HomeOsDims.gutter,
                                bottom = navBars.calculateBottomPadding() + 24.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter),
                        content = content,
                    )
                }
                }
            }
        }
    }
}

/** A collapsed disclosure, for the layers of an explanation that are not needed up front. */
@Composable
fun Disclosure(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = HomeOsDims.minTouch)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = type.blockTitle, color = colors.onShell)
                subtitle?.let { Text(it, style = type.caption, color = colors.onShellMuted) }
            }
            Icon(
                if (expanded) HomeOsIcons.expandMore else HomeOsIcons.chevronRight,
                contentDescription = null,
                tint = colors.onShellMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter)) {
                content()
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
