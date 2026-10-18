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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
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
 * Warm off-white, a 2dp near-black border, and a faint sheen down the face. The border is
 * load-bearing — it supplies the 3:1 non-text contrast that identifies a surface, which a
 * translucent one can never guarantee — and so is the fact that the fill is not flat: a single
 * value across a 300dp card is what makes a card look like a rectangle of paint rather than a
 * sheet with light falling across it.
 *
 * How a block carries its hue depends on whether it leads:
 *
 *  - **[lead]** blocks take the hard chromatic plate offset 4dp behind them, and press by
 *    travelling exactly that far to land flush on it. This is the signature, and it is reserved for
 *    the one block a screen is actually about, plus anything that interrupts. Ten plates down a
 *    scroll is not ten emphases — it is a texture, and a loud one.
 *  - **everything else** takes the same hue as a rail inside the left edge, and a contact shadow
 *    instead of a plate. The semantics survive intact; the noise does not.
 */
@Composable
fun PaperBlock(
    modifier: Modifier = Modifier,
    plate: Plate = Plate.None,
    lead: Boolean = false,
    onClick: (() -> Unit)? = null,
    shape: Shape = HomeOsShapes.block,
    padding: PaddingValues = PaddingValues(HomeOsDims.blockPadding),
    /**
     * Stretches the block to its parent's height. Needed for blocks sitting side by side: without
     * it they size to their own content, and two rails of different lengths read as a mistake.
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
    val plated = lead && plate != Plate.None
    val railed = !plated && plate != Plate.None

    // Reduced motion keeps the press legible without moving anything: the block stays put and the
    // plate cross-fades out instead.
    val travel by animateDpAsState(
        targetValue = if (plated && pressed && !reduced) offset else 0.dp,
        animationSpec = Motion.spatialFast(),
        label = "blockTravel",
    )
    val plateAlpha by animateFloatAsState(
        targetValue = if (reduced && pressed) 0f else 1f,
        animationSpec = Motion.effects(),
        label = "plateAlpha",
    )
    // Rail blocks compress very slightly instead. Large surfaces need far less of this than chips
    // do: at 0.96 a full-width card visibly detaches from the screen margin.
    val press by animateFloatAsState(
        targetValue = if (!plated && pressed && !reduced) 0.988f else 1f,
        animationSpec = Motion.spatialFast(),
        label = "blockPress",
    )

    val focusRing = colors.tealInk
    val focusOffset = HomeOsDims.focusOffset
    val ringWidth = HomeOsDims.focusRing
    val railWidth = HomeOsDims.rail
    // The rail sits on paper, so it uses the hue at its *paper* tone. This is the two-tone rule
    // doing real work: the plate tone is the bright member of the pair and measures 1.59:1 against
    // paper, so a rail painted in it would be a bar nobody could see. The ink tone measures
    // 5.9-7.9:1 across the five hues.
    val railBrush = Brush.verticalGradient(
        listOf(plate.ink(colors), plate.ink(colors).darken(0.84f)),
    )
    // The sheet, lit. Two stops only, and the lower one is the palette's own sunk tone rather than
    // an arbitrary grey, so a block and an inset well inside it agree about the light.
    val paperBrush = Brush.verticalGradient(
        listOf(colors.paper, lerp(colors.paper, colors.paperSunk, 0.55f)),
    )

    Box(
        modifier
            // Reserve the plate's room on every block, plated or not, so paper edges line up down
            // the screen whether or not a given block happens to lead.
            .padding(end = offset, bottom = offset),
    ) {
        if (plated) {
            Box(
                Modifier
                    .matchParentSize()
                    .plateOffset(offset)
                    .alpha(plateAlpha)
                    .background(
                        Brush.verticalGradient(
                            listOf(plate.color(colors), plate.color(colors).darken(0.80f)),
                        ),
                        shape,
                    ),
            )
        }
        Column(
            Modifier
                .plateOffset(travel)
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
                // A tight contact shadow where there is no plate, so an unplated block still sits
                // on the shell rather than being pasted flat onto it.
                .then(
                    if (plated) {
                        Modifier
                    } else {
                        Modifier.shadow(
                            elevation = 9.dp,
                            shape = shape,
                            clip = false,
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        )
                    },
                )
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
                .background(paperBrush, shape)
                .then(
                    if (railed) {
                        Modifier.drawBehind {
                            drawRect(railBrush, size = Size(railWidth.toPx(), size.height))
                        }
                    } else {
                        Modifier
                    },
                )
                .border(HomeOsDims.border, colors.border, shape)
                .padding(padding)
                .then(if (railed) Modifier.padding(start = railWidth) else Modifier),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * A control that lives *on* paper.
 *
 * Glass controls need the ambient background behind them to be glass at all, so inside a paper
 * block they would go muddy and meaningless. The paper equivalent is an ink-outlined pill: the same
 * near-black line as the block border at a lighter weight, so it reads as the same hand without
 * competing with the block that contains it. [accent] fills it, for the one option a block
 * recommends.
 */
@Composable
fun PaperAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color? = null,
) {
    val colors = HomeOs.colors
    val fill = accent?.copy(alpha = 0.20f)?.compositeOver(colors.paper) ?: colors.paperSunk

    Row(
        modifier
            .defaultMinSize(minHeight = 42.dp)
            .pressable(onClick = onClick, label = label)
            .clip(HomeOsShapes.pill)
            .background(fill, HomeOsShapes.pill)
            .border(1.5.dp, colors.ink, HomeOsShapes.pill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = HomeOs.type.label,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        if (icon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Multiplies a colour towards black, holding its alpha. Used for the lit edge of a plate. */
private fun Color.darken(factor: Float) = Color(
    red = red * factor,
    green = green * factor,
    blue = blue * factor,
    alpha = alpha,
)

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
 * Screen margins for a paper block.
 *
 * The end margin is short by exactly the plate offset, because every block reserves that much on
 * its right for a plate it may or may not draw. Without this, paper edges would sit 4dp inside the
 * margin that section headings and edge-to-edge strips use, and the screen would look very subtly
 * out of true for a reason nobody could name.
 */
fun Modifier.blockMargin(): Modifier = padding(
    start = HomeOsDims.screenMargin,
    end = HomeOsDims.screenMargin - HomeOsDims.plateOffset,
)
