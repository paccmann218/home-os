package com.homeos.bridge.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.Display
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
 * A person's avatar: a deep two-stop gradient disc with a serif monogram.
 *
 * The gradient is picked by hashing the alias, so a person keeps the same identity on every screen
 * without the backend storing one.
 *
 * The ring adapts to what the avatar is standing on, and that is not fussiness. On paper the ring
 * is the same 2dp ink as a block border, which is what keeps the avatar in the same family as
 * everything around it. On glass that same ring is a near-black line on a dark translucent surface
 * — invisible as structure and faintly grubby as a mark — so there it becomes a light hairline,
 * lit from the top like every other edge on that layer.
 */
@Composable
fun PersonAvatar(
    alias: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    present: Boolean? = null,
    dim: Boolean = false,
    onGlass: Boolean = false,
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
                .then(
                    if (onGlass) {
                        Modifier.drawBehind {
                            drawCircle(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        colors.onShell.copy(alpha = 0.46f),
                                        colors.onShell.copy(alpha = 0.12f),
                                    ),
                                ),
                                radius = this.size.minDimension / 2f - HomeOsDims.glassBorder.toPx() / 2f,
                                style = Stroke(HomeOsDims.glassBorder.toPx()),
                            )
                        }
                    } else {
                        Modifier.border(HomeOsDims.border, colors.border, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = alias.take(1).uppercase(),
                style = HomeOs.type.blockTitle.copy(fontFamily = Display),
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
                    .background(if (present) colors.green else colors.onShellMuted),
            )
        }
    }
}

/**
 * The top bar: who you are, and whether the home can hear you.
 *
 * It has no bottom edge. An edge-to-edge bar cannot be lit — it has no corners — so the only thing
 * available to mark its boundary is a rule across the screen, and a rule across the screen is the
 * thing that makes chrome look bolted on. Instead the material fades out over its last third, so
 * content passes under it and disappears rather than being cut off.
 *
 * Tap the identity chip to switch person; this is one phone shared by a household, not a
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
    val borderColor = colors.border
    val borderPx = with(LocalDensity.current) { HomeOsDims.border.toPx() }
    val dot by animateColorAsState(
        targetValue = if (linked) colors.green else colors.red,
        animationSpec = Motion.effects(),
        label = "linkDot",
    )

    GlassSurface(
        level = GlassLevel.Chrome,
        shape = RectangleShape,
        modifier = modifier
            .fillMaxWidth()
            // A hard rule under the bar. The dissolve that used to be here was the right answer on
            // a dark field, where any line reads as chrome bolted on; here every edge in the design
            // is a hard one and a fading bar is the thing that would look out of place.
            .drawBehind {
                drawRect(
                    color = borderColor,
                    topLeft = Offset(0f, size.height - borderPx),
                    size = androidx.compose.ui.geometry.Size(size.width, borderPx),
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
                    withStyle(SpanStyle(color = colors.lens)) { append("OS") }
                },
                style = type.section,
                color = colors.onShell,
            )
            Spacer(Modifier.weight(1f))

            Row(
                Modifier
                    .combinedClickable(
                        onClick = onSwitchPerson,
                        onLongClick = onOpenDev,
                        onLongClickLabel = "Open dev mode",
                    )
                    .frostedSurface(HomeOsShapes.pill)
                    .padding(start = 5.dp, end = 11.dp, top = 5.dp, bottom = 5.dp)
                    .semantics {
                        contentDescription =
                            "Acting as $alias. ${if (linked) "Connected" else "Not connected"}. " +
                            "Tap to switch person, long press for dev mode."
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PersonAvatar(alias = alias, size = 28.dp, onGlass = true)
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
 * The dock: a floating glass pill, inset from all three edges so content scrolls visibly beneath
 * it — which is the point of having glass at all. A bar welded to the bottom of the screen has
 * nothing to refract and may as well be opaque.
 *
 * Selection is one lens that slides, not an outline that appears on whichever item is current. The
 * lens is the only moving part; the icons just fill.
 */
@Composable
fun HomeOsDock(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val navBars = WindowInsets.navigationBars.asPaddingValues()
    val position by animateFloatAsState(
        targetValue = Destination.entries.indexOf(current).toFloat(),
        animationSpec = Motion.spatialDefault(),
        label = "dockLens",
    )

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
                    .slidingLens(
                        position = position,
                        count = Destination.entries.size,
                        // Brighter glass rather than a colour: see the `glass` note on the lens.
                        accent = colors.paper.copy(alpha = 0.58f),
                        light = colors.specular,
                        shade = colors.ink,
                        inset = 7.dp,
                        glass = true,
                    ),
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
    // The accent marks the destination from inside the pane now, rather than being the pane.
    //
    // Unselected is full ink rather than the muted tone, because the dock is the most transparent
    // surface in the app and the muted tone measured 2.6:1 over the worst backdrop it can float
    // across. Nothing is lost: what separates the current tab is the pane and the filled glyph,
    // not a difference in text weight.
    val iconTint by animateColorAsState(
        targetValue = if (selected) colors.lens else colors.ink,
        animationSpec = Motion.effects(),
        label = "dockIcon",
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) colors.lens else colors.ink,
        animationSpec = Motion.effects(),
        label = "dockLabel",
    )

    Box(
        modifier
            .height(HomeOsDims.minTouch)
            .pressable(onClick = onSelect, label = destination.label),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (selected) destination.iconSelected else destination.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(destination.label, style = HomeOs.type.micro, color = labelTint)
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
            .frostedSurface(HomeOsShapes.block, selection = 1f, accent = colors.red)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            HomeOsIcons.cloudOff,
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text("Graph memory off", style = HomeOs.type.label, color = colors.ink)
            Text(
                "Fixed rules only: no personalisation, no conflict resolution, no explanations.",
                style = HomeOs.type.caption,
                color = colors.inkMuted,
            )
        }
    }
}
