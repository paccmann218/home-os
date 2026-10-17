package com.homeos.bridge.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.DisplayS
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.theme.Motion
import com.homeos.bridge.theme.avatarGradient
import com.homeos.bridge.ui.icons.HomeOsIcons

/**
 * Where the bottom dock can go.
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
 * correction is made with tone instead: shell icons never run at full strength.
 */
@Composable
private fun shellIconTint(active: Boolean) =
    if (active) HomeOs.colors.onShell.copy(alpha = 0.94f) else HomeOs.colors.onShellMuted

/**
 * A person's avatar: a deep two-stop gradient disc with a serif monogram and a hard ring.
 *
 * The gradient is picked by hashing the alias, so a person keeps the same identity on every screen
 * without the backend storing one. The ring is the same 2dp ink as a paper block's border, which is
 * what keeps the avatar in the same design family as everything around it rather than looking like
 * a component borrowed from another app.
 */
@Composable
fun PersonAvatar(
    alias: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    present: Boolean? = null,
    dim: Boolean = false,
) {
    val colors = HomeOs.colors
    val (deep, mid) = avatarGradient(alias)
    val fill = if (dim) {
        // A dataset person is not in the room and never will be; draining the colour says so
        // before the caption does.
        Brush.linearGradient(listOf(colors.inkMuted, colors.inkMuted.copy(alpha = 0.75f)))
    } else {
        Brush.linearGradient(
            colors = listOf(deep, mid),
            start = Offset.Zero,
            end = Offset(120f, 160f),
        )
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(fill)
                .border(HomeOsDims.border, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = alias.take(1).uppercase(),
                style = HomeOs.type.blockTitle.copy(fontFamily = DisplayS),
                color = colors.paper,
            )
        }
        if (present != null && !dim) {
            Box(
                Modifier
                    .size(size * 0.30f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(colors.paper)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (present) colors.greenInk else colors.inkMuted),
            )
        }
    }
}

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
    val onShellLine = colors.onShell.copy(alpha = 0.14f)
    val dot by animateColorAsState(
        targetValue = if (linked) colors.green else colors.red,
        animationSpec = Motion.effects(),
        label = "linkDot",
    )

    GlassSurface(
        level = GlassLevel.Chrome,
        shape = RoundedCornerShape(0.dp),
        modifier = modifier
            .fillMaxWidth()
            // A hairline under the bar. Without an edge, glass over a dark gradient has no visible
            // boundary and the bar appears to float with content sliding out of nowhere.
            .drawBehind {
                drawLine(
                    color = onShellLine,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = statusBars.calculateTopPadding())
                .height(ShellDims.topBar)
                .padding(start = HomeOsDims.screenMargin, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append("Home")
                    withStyle(SpanStyle(color = colors.teal)) { append("OS") }
                },
                style = type.section,
                color = colors.onShell,
            )
            Spacer(Modifier.weight(1f))

            Row(
                Modifier
                    .clip(HomeOsShapes.pill)
                    .background(colors.onShell.copy(alpha = 0.06f), HomeOsShapes.pill)
                    .border(1.dp, colors.onShellMuted.copy(alpha = 0.40f), HomeOsShapes.pill)
                    .combinedClickable(
                        onClick = onSwitchPerson,
                        onLongClick = onOpenDev,
                        onLongClickLabel = "Open dev mode",
                    )
                    .padding(start = 5.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                    .semantics {
                        contentDescription =
                            "Acting as $alias. ${if (linked) "Connected" else "Not connected"}. " +
                            "Tap to switch person, long press for dev mode."
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersonAvatar(alias = alias, size = 28.dp)
                Text(alias.ifEmpty { "no person" }, style = type.label, color = colors.onShell)
                StatusDot(on = linked, onColor = dot)
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

/**
 * The dock: a floating glass pill rather than a full-width bar.
 *
 * Inset from all three edges so the content scrolls visibly beneath it, which is the point of
 * having glass at all — a bar welded to the bottom of the screen has nothing to refract and may as
 * well be opaque. It takes the hard 2dp outline, because unlike the top bar it is an object sitting
 * on top of the page rather than chrome trying to disappear.
 */
@Composable
fun HomeOsDock(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val navBars = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = HomeOsDims.screenMargin,
                end = HomeOsDims.screenMargin,
                bottom = navBars.calculateBottomPadding() + ShellDims.dockGap,
            ),
    ) {
        GlassSurface(
            level = GlassLevel.Dock,
            shape = HomeOsShapes.pill,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ShellDims.dock)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Destination.entries.forEach { destination ->
                    DockItem(
                        destination = destination,
                        selected = destination == current,
                        onSelect = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    destination: Destination,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.effects(),
        label = "dockEmphasis",
    )

    Box(
        modifier
            .height(HomeOsDims.minTouch)
            .clip(HomeOsShapes.pill)
            .background(colors.teal.copy(alpha = 0.16f * emphasis), HomeOsShapes.pill)
            .clickable(onClick = onSelect)
            .semantics { contentDescription = destination.label },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (selected) destination.iconSelected else destination.icon,
                contentDescription = null,
                tint = if (selected) colors.teal else shellIconTint(active = false),
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                destination.label,
                style = HomeOs.type.micro,
                color = if (selected) colors.onShell else colors.onShellMuted,
            )
        }
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
