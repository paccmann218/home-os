package com.homeos.bridge.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.theme.Motion
import com.homeos.bridge.ui.icons.HomeOsIcons

/**
 * Where the bottom nav can go.
 *
 * The `FILL` state is carried by a different icon rather than by colour alone, which is what
 * WCAG 1.4.1 asks for and also just reads better at 24dp.
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    Home("home", "Home", HomeOsIcons.home, HomeOsIcons.homeFilled),
    CheckIn("checkin", "Check in", HomeOsIcons.mood, HomeOsIcons.moodFilled),
    Ask("ask", "Ask", HomeOsIcons.forum, HomeOsIcons.forumFilled),
    Memory("memory", "Memory", HomeOsIcons.hub, HomeOsIcons.hubFilled),
}

/**
 * Icons drawn on the dark shell.
 *
 * Material Symbols has a `GRAD` axis precisely for this, and Google's guidance is grade -25 on dark
 * backgrounds to stop a light stroke blooming against a dark field. This app bakes one icon set
 * (weight 400, grade 0) into source rather than shipping the variable font, so the same perceptual
 * correction is made with tone instead: shell icons never run at full strength. On paper, which is
 * dark-on-light, no correction is wanted at all.
 */
@Composable
private fun shellIconTint(active: Boolean) =
    if (active) HomeOs.colors.onShell.copy(alpha = 0.94f) else HomeOs.colors.onShellMuted

/**
 * The top bar: who you are, and whether the home can hear you.
 *
 * Tap the identity chip to switch person — this is one phone shared by a household, not a
 * single-user device. Long-press it for dev mode, which keeps the console one gesture away without
 * giving it a place in the product's navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeOsTopBar(
    alias: String,
    linked: Boolean,
    onSwitchPerson: () -> Unit,
    onOpenDev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val statusBars = WindowInsets.statusBars.asPaddingValues()
    val dot by animateColorAsState(
        targetValue = if (linked) colors.green else colors.red,
        animationSpec = Motion.effects(),
        label = "linkDot",
    )

    GlassSurface(
        level = GlassLevel.Chrome,
        shape = RoundedCornerShape(0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = statusBars.calculateTopPadding())
                .height(ShellDims.topBar)
                .padding(horizontal = HomeOsDims.screenMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append("Home")
                    withStyle(SpanStyle(color = colors.teal)) { append("OS") }
                },
                style = type.blockTitle,
                color = colors.onShell,
            )
            Spacer(Modifier.weight(1f))

            Row(
                Modifier
                    .clip(HomeOsShapes.pill)
                    .border(1.dp, colors.onShellMuted.copy(alpha = 0.35f), HomeOsShapes.pill)
                    .combinedClickable(
                        onClick = onSwitchPerson,
                        onLongClick = onOpenDev,
                        onLongClickLabel = "Open dev mode",
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .semantics {
                        contentDescription =
                            "Acting as $alias. ${if (linked) "Connected" else "Not connected"}. " +
                            "Tap to switch person, long press for dev mode."
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDot(on = linked, onColor = dot)
                Text(alias.ifEmpty { "no person" }, style = type.label, color = colors.onShell)
                Icon(
                    HomeOsIcons.expandMore,
                    contentDescription = null,
                    tint = colors.onShellMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** The bottom nav. Four destinations, glass, pinned. */
@Composable
fun HomeOsBottomNav(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val navBars = WindowInsets.navigationBars.asPaddingValues()

    GlassSurface(
        level = GlassLevel.Chrome,
        shape = RoundedCornerShape(0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // Padding outside the height, so the bar is `bottomNav` tall *plus* the system
                // inset. The other order makes the gesture bar eat the labels, and leaves Shell
                // reserving more space than the chrome actually occupies.
                .padding(bottom = navBars.calculateBottomPadding())
                .height(ShellDims.bottomNav),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                val selected = destination == current
                Column(
                    Modifier
                        .weight(1f)
                        .height(HomeOsDims.minTouch)
                        .clip(HomeOsShapes.chip)
                        .clickable(onClick = { onSelect(destination) })
                        .semantics { contentDescription = destination.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (selected) destination.iconSelected else destination.icon,
                        contentDescription = null,
                        tint = if (selected) colors.teal else shellIconTint(active = false),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        destination.label,
                        style = HomeOs.type.micro,
                        color = if (selected) colors.onShell else colors.onShellMuted,
                    )
                }
            }
        }
    }
}

/** A person's initial in a bordered square: the household strip's unit. */
@Composable
fun PersonBadge(
    alias: String,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
) {
    val colors = HomeOs.colors
    Box(
        modifier
            .size(28.dp)
            .background(if (dim) colors.paperSunk else colors.paper, HomeOsShapes.well)
            .border(HomeOsDims.border, colors.border, HomeOsShapes.well),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            alias.take(1).uppercase(),
            style = HomeOs.type.label,
            color = if (dim) colors.inkMuted else colors.ink,
        )
    }
}

/** A banner for the ablation mode the project demos: FalkorDB off, fixed rules only. */
@Composable
fun AblationBanner(modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.red.copy(alpha = 0.16f), HomeOsShapes.chip)
            .border(1.dp, colors.red.copy(alpha = 0.5f), HomeOsShapes.chip)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            HomeOsIcons.cloudOff,
            contentDescription = null,
            tint = colors.red,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text("Graph memory off", style = HomeOs.type.label, color = colors.onShell)
            Text(
                "Fixed rules only: no personalisation, no conflict resolution, no explanations.",
                style = HomeOs.type.caption,
                color = colors.onShellMuted,
            )
        }
    }
}
