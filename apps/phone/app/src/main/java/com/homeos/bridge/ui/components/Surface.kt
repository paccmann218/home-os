package com.homeos.bridge.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.theme.Motion
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.theme.color

/**
 * A card: the surface everything in this app is read on.
 *
 * This replaces the paper block, and the change is the whole point of the redesign. The old block
 * was warm off-white at 17:1 against a near-black background with a 2dp near-black border — two of
 * the handful of things that reliably make an interface read as generated rather than designed: a
 * border on every card, and surfaces separated by raw value contrast instead of by luminance.
 *
 * So this one is **one step up the dark ramp** from the background it sits on, with a hairline at
 * 8% that refines the edge without claiming to be the edge. Structure comes from fill. The only
 * things bright enough to shout are the room and the graph, which are the two things worth it.
 *
 * @param accent the semantic hue, drawn as a rail inside the left edge. On dark the bright tone
 *   finally works here: 5.9-9.4:1, against the 1.59:1 the same tone measured on paper.
 * @param lead the one block a screen is about. Adds a faint wash of the accent from the top-left,
 *   which is as much emphasis as this design now spends — the hard chromatic plate it replaces was
 *   loud enough that ten of them down one scroll read as a texture rather than as ten emphases.
 */
@Composable
fun HomeOsCard(
    modifier: Modifier = Modifier,
    accent: Plate = Plate.None,
    lead: Boolean = false,
    onClick: (() -> Unit)? = null,
    shape: Shape = HomeOsShapes.block,
    padding: PaddingValues = PaddingValues(HomeOsDims.blockPadding),
    fillHeight: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HomeOs.colors
    val reduced = HomeOs.reducedMotion
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()

    LaunchedEffect(pressed) {
        if (pressed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val press by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.988f else 1f,
        animationSpec = Motion.spatialFast(),
        label = "cardPress",
    )

    val hue = accent.color(colors)
    val railed = accent != Plate.None
    val railWidth = 3.dp
    val focusRing = colors.teal
    val focusOffset = HomeOsDims.focusOffset
    val ringWidth = HomeOsDims.focusRing

    Column(
        modifier
            .graphicsLayer { scaleX = press; scaleY = press }
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .defaultMinSize(minHeight = if (onClick != null) HomeOsDims.minTouch else 0.dp)
            .drawBehind {
                if (!focused) return@drawBehind
                val grow = (focusOffset + ringWidth / 2).toPx()
                val outline = shape.createOutline(
                    size = Size(size.width + grow * 2, size.height + grow * 2),
                    layoutDirection = layoutDirection,
                    density = this,
                )
                translate(-grow, -grow) {
                    drawOutline(outline, focusRing, style = Stroke(ringWidth.toPx()))
                }
            }
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .background(colors.shellRaised, shape)
            .then(
                if (lead && railed) {
                    Modifier.drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                0.0f to hue.copy(alpha = 0.10f),
                                1.0f to Color.Transparent,
                                center = Offset(0f, 0f),
                                radius = size.maxDimension * 0.9f,
                            ),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (railed) {
                    Modifier.drawBehind {
                        drawRect(
                            Brush.verticalGradient(listOf(hue, hue.copy(alpha = 0.72f))),
                            size = Size(railWidth.toPx(), size.height),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .border(HomeOsDims.glassBorder, colors.onShell.copy(alpha = 0.08f), shape)
            .padding(padding)
            .then(if (railed) Modifier.padding(start = railWidth) else Modifier),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * A control inside a card.
 *
 * Identified by its label clearing 4.5:1 and by a fill one step further up the ramp, not by an
 * outline — the rule that replaces the ink border is that structure comes from fill and a control
 * is identified by its content. [accent] marks the one option a card recommends.
 */
@Composable
fun CardAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color? = null,
) {
    val colors = HomeOs.colors
    val tint = accent ?: colors.onShell

    Row(
        modifier
            .defaultMinSize(minHeight = 42.dp)
            .pressable(onClick = onClick, label = label)
            .clip(HomeOsShapes.pill)
            .background(
                if (accent != null) accent.copy(alpha = 0.16f) else colors.surfaceHi,
                HomeOsShapes.pill,
            )
            .border(
                HomeOsDims.glassBorder,
                if (accent != null) accent.copy(alpha = 0.42f) else colors.onShell.copy(alpha = 0.10f),
                HomeOsShapes.pill,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = HomeOs.type.label, color = tint, textAlign = TextAlign.Center)
        if (icon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * Screen margins for a card.
 *
 * Symmetric now. The paper block reserved four points on its right for a plate it might or might
 * not draw, and this had to be short by exactly that much to keep edges in line down the screen.
 * With the plate gone, the compensation goes too.
 */
fun Modifier.cardMargin(): Modifier = padding(horizontal = HomeOsDims.screenMargin)
