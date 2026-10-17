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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.ui.icons.HomeOsIcons

/**
 * A bottom sheet, built in-composition rather than with `ModalBottomSheet`.
 *
 * The reason is concrete: a `ModalBottomSheet` is hosted in its own platform window, and Haze
 * cannot sample a backdrop across windows, so a glass sheet there would have nothing to blur. Doing
 * it inside the composition keeps the sheet genuinely translucent over the content behind it, and
 * gives the spring curve to this app rather than to Material's defaults.
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
    val navBars = WindowInsets.navigationBars.asPaddingValues()
    val statusBars = WindowInsets.statusBars.asPaddingValues()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // A scrim must not be clickable-with-ripple; it is a dismiss target, not a control.
    val scrimInteraction = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.shellSunk.copy(alpha = 0.72f))
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
        enter = if (reduced) fadeIn(tween(200)) else slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
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
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = HomeOsDims.screenMargin, end = 8.dp, top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The grab handle is decorative; the close button is the real affordance,
                        // because a drag target with no visible control fails discoverability.
                        Text(
                            title.orEmpty(),
                            style = HomeOs.type.section,
                            color = colors.onShell,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                HomeOsIcons.close,
                                contentDescription = "Close",
                                tint = colors.onShell,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = HomeOsDims.screenMargin,
                                end = HomeOsDims.screenMargin,
                                top = 4.dp,
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
