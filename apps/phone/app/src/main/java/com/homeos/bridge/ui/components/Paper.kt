package com.homeos.bridge.ui.components

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.theme.Motion
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.theme.color
import com.homeos.bridge.theme.ink

/**
 * A paper block: the only surface in this app that holds content.
 *
 * Warm off-white, a 2dp near-black border, and a hard chromatic plate offset 4dp behind it. The
 * border and the plate are both load-bearing. The border supplies the 3:1 non-text contrast that
 * identifies a surface, which a translucent one can never guarantee; the plate replaces the
 * canonical black hard shadow, which measures 1.38:1 on this app's dark shell and would be
 * invisible.
 *
 * Pressing is the whole interaction language: the block travels exactly its plate's offset and
 * lands flush on it. It does not dim, scale or ripple — there is no [androidx.compose.material3]
 * indication here on purpose, because the travel *is* the feedback.
 *
 * @param reverse the inverted variant: rests flat and *lifts*, gaining a plate. Used sparingly —
 *   once per screen at most, for the single action that screen is about.
 */
@Composable
fun PaperBlock(
    modifier: Modifier = Modifier,
    plate: Plate = Plate.None,
    onClick: (() -> Unit)? = null,
    shape: Shape = HomeOsShapes.block,
    padding: PaddingValues = PaddingValues(HomeOsDims.blockPadding),
    reverse: Boolean = false,
    /** Overrides the paper fill. Used for a selected state, as a light tint of the plate's hue. */
    fill: Color? = null,
    /**
     * Stretches the block to its parent's height. Needed for blocks sitting side by side: without
     * it they size to their own content, and two plates at different heights read as a mistake.
     */
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

    val offset = HomeOsDims.plateOffset
    val restTravel = if (reverse) offset else 0.dp
    val activeTravel = if (reverse) 0.dp else offset

    // Reduced motion keeps the press legible without moving anything: the block stays put and the
    // plate cross-fades out instead.
    val travel by animateDpAsState(
        targetValue = when {
            reduced -> restTravel
            pressed -> activeTravel
            else -> restTravel
        },
        animationSpec = Motion.spatialFast(),
        label = "blockTravel",
    )
    val plateAlpha by animateFloatAsState(
        targetValue = when {
            reverse && !pressed -> 0f
            reduced && pressed -> 0f
            else -> 1f
        },
        animationSpec = Motion.effects(),
        label = "plateAlpha",
    )

    val focusRing = colors.teal
    val focusOffset = HomeOsDims.focusOffset
    val ringWidth = HomeOsDims.focusRing

    Box(
        modifier
            // Reserve the plate's room so the offset copy is never clipped by a scrolling parent.
            .padding(end = offset, bottom = offset),
    ) {
        if (plate != Plate.None) {
            Box(
                Modifier
                    .matchParentSize()
                    .plateOffset(offset)
                    .alpha(plateAlpha)
                    .background(plate.color(colors), shape),
            )
        }
        Column(
            Modifier
                .plateOffset(travel)
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
                .border(HomeOsDims.border, colors.border, shape)
                .background(fill ?: colors.paper, shape)
                .padding(padding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * Offsets placement without reporting a different size, so the plate copy and the block stay the
 * same measured size as each other. `Modifier.offset` would do this too, but going through [layout]
 * keeps the offset in the layout pass rather than deferring it to draw, which matters when the
 * block is inside a scrolling list.
 */
private fun Modifier.plateOffset(amount: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(amount.roundToPx(), amount.roundToPx())
    }
}

/**
 * A chip: the Check in screen's unit of interaction, and the smallest thing that still gets a
 * plate. 48dp minimum height, because the reference neobrutalism library's 40px control would fail
 * Android's touch-target floor.
 *
 * The plate is always there, because the plate is what makes a block look pressable — a chip
 * without one is just a bordered rectangle, and Check in is the screen where the press matters
 * most. Selection is carried by a tint of the plate's own hue plus a filled icon, so it reads
 * without relying on the shadow being absent.
 */
@Composable
fun ChipBlock(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    plate: Plate = Plate.Body,
    selected: Boolean = false,
) {
    val colors = HomeOs.colors
    PaperBlock(
        modifier = modifier,
        plate = plate,
        onClick = onClick,
        shape = HomeOsShapes.chip,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        // A 14% wash of the plate hue over paper. Ink still measures well above 4.5:1 on it.
        fill = if (selected) plate.ink(colors).copy(alpha = 0.14f).compositeOver(colors.paper) else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) plate.ink(colors) else colors.inkMuted,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = HomeOs.type.label,
                color = colors.ink,
            )
        }
    }
}
