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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.homeos.bridge.theme.ink

/**
 * A block: the only surface in this app that holds content.
 *
 * It is made of one of two materials, and which one is decided by [LocalOnGlass] rather than by
 * the call site, so no screen has to know.
 *
 * **On the field it is paper.** Warm off-white, an ink border, a hard offset behind it. The border
 * is load-bearing there: it supplies the 3:1 non-text contrast that identifies a surface against a
 * saturated field, which a translucent one could never guarantee.
 *
 * **In a modal it is a pane.** Translucent, lit from the top-left, with a specular edge and a soft
 * shadow — and no border and no hard offset at all. That is not a reskin, it is the correct answer
 * to a different question. A hard offset is a shape casting a shadow onto the thing behind it, and
 * inside a sheet the thing behind it is more glass; you get a black rectangle hanging in mid-air.
 * A pane on glass is lit by the same light as the sheet holding it, which is what makes a stack of
 * them read as one object with depth rather than as cards on a tray.
 *
 * How a block carries its hue is the same in both materials: [lead] blocks stand further off their
 * background, and everything else takes the hue as a rail inside the left edge.
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

    // Every block gets the hard offset now, not just the one that leads.
    //
    // Earlier versions of this design spent the offset as emphasis, on one block per screen, and
    // made it chromatic because a black shadow measured 1.38:1 on a near-black shell and could not
    // be seen. On a bright field a black shadow measures 10.59:1, and the canonical style puts one
    // under *everything* - that uniformity is the texture, rather than a thing to be rationed.
    // The one case translucency cannot serve: when someone has asked the system for more
    // contrast, the honest answer is to stop being see-through rather than to tune an alpha.
    val onGlass = LocalOnGlass.current && HomeOs.glass
    val offset = when {
        onGlass -> 0.dp
        lead -> HomeOsDims.plateOffsetLead
        else -> HomeOsDims.plateOffset
    }
    val railed = plate != Plate.None
    // A pane has nowhere to travel to, so it answers a press by brightening instead. The light on
    // it is the whole of its modelling, so the light is what reacts.
    val press by animateFloatAsState(
        targetValue = if (pressed && !reduced) 1f else 0f,
        animationSpec = Motion.effects(),
        label = "blockPress",
    )

    // Reduced motion keeps the press legible without moving anything: the block stays put and the
    // plate cross-fades out instead.
    val travel by animateDpAsState(
        targetValue = if (pressed && !reduced) offset else 0.dp,
        animationSpec = Motion.spatialFast(),
        label = "blockTravel",
    )
    val plateAlpha by animateFloatAsState(
        targetValue = if (reduced && pressed) 0f else 1f,
        animationSpec = Motion.effects(),
        label = "plateAlpha",
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

    Box(
        modifier
            // Reserve the plate's room on every block, plated or not, so paper edges line up down
            // the screen whether or not a given block happens to lead.
            .padding(end = offset, bottom = offset),
    ) {
        if (!onGlass) {
            Box(
                Modifier
                    .matchParentSize()
                    .plateOffset(offset)
                    .alpha(plateAlpha)
                    .background(colors.plateShadow, shape),
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
                // Deliberately no elevation shadow on a pane, which is the one thing that looked
                // obvious and does not work. Android draws an elevation shadow as the blurred
                // silhouette of the outline, and excludes the inside of it only for an opaque
                // caster; a pane is translucent, so the shadow shows *through* it as a dark
                // vignette inside the edges — which reads as a second, smaller rectangle nested
                // in the first. Depth here comes from the light instead: the graded fill, the
                // diagonal shine, the inner shade and the specular rim. The sheet is the object
                // with elevation; the panes are inside it.
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
                .then(
                    if (onGlass) {
                        Modifier.pane(
                            shape = shape,
                            colors = colors,
                            lead = lead,
                            press = press,
                            accent = if (railed) plate.color(colors) else null,
                        )
                    } else {
                        // Flat. A gradient on a paper block is the one thing that style will not
                        // have: the fill is a single value and the only modelling is the offset.
                        Modifier.background(colors.paper, shape)
                    },
                )
                .then(
                    if (railed && !onGlass) {
                        Modifier.drawBehind {
                            drawRect(railBrush, size = Size(railWidth.toPx(), size.height))
                        }
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (onGlass) Modifier else Modifier.border(HomeOsDims.border, colors.border, shape),
                )
                .padding(padding)
                .then(if (railed && !onGlass) Modifier.padding(start = railWidth) else Modifier),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * The glass pane: fill, shine, inner shade and a specular edge, in one cached draw.
 *
 * Four layers, and each is answering something specific.
 *
 *  - **The fill is a gradient, not a value.** Glass is brighter where it faces the light. A flat
 *    translucent rectangle is a tinted window; a graded one is a pane.
 *  - **The shine is diagonal.** A vertical highlight reads as a gradient someone applied; a
 *    diagonal one reads as a light source in the room, and it is the same top-left light the
 *    sheet, the dock and every control are lit by.
 *  - **The inner shade** at the foot is what gives the pane thickness. Without it the bottom edge
 *    is just where the fill stops.
 *  - **The edge is one stroke with four stops** — specular white at the top, nothing through the
 *    middle, ink at the bottom. It is stroked at double width inside the clip, so the outer half
 *    is thrown away and what survives is a crisp hairline hugging the inside of the curve. That
 *    detail is the whole difference between glass and a translucent sticker.
 *
 * [press] brightens all of it, because on a pane the light is the only thing there is to move.
 */
private fun Modifier.pane(
    shape: Shape,
    colors: com.homeos.bridge.theme.HomeOsColors,
    lead: Boolean,
    press: Float,
    accent: Color?,
) = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val edge = Stroke(HomeOsDims.glassEdge.toPx() * 2f)

    // A leading pane is simply nearer the light.
    val top = (if (lead) 0.64f else 0.54f) + 0.08f * press
    val bottom = (if (lead) 0.44f else 0.36f) + 0.08f * press

    val fill = Brush.verticalGradient(
        0f to colors.paper.copy(alpha = top),
        1f to colors.paper.copy(alpha = bottom),
    )
    val shine = Brush.linearGradient(
        0.00f to colors.specular.copy(alpha = 0.40f + 0.14f * press),
        0.26f to colors.specular.copy(alpha = 0.11f),
        0.58f to Color.Transparent,
        start = Offset.Zero,
        end = Offset(size.width * 0.85f, size.height * 1.7f),
    )
    val shade = Brush.verticalGradient(
        0.58f to Color.Transparent,
        1.00f to colors.ink.copy(alpha = 0.07f),
    )
    val rim = Brush.verticalGradient(
        0.00f to colors.specular.copy(alpha = 0.95f),
        0.26f to colors.specular.copy(alpha = 0.26f),
        0.74f to Color.Transparent,
        1.00f to colors.ink.copy(alpha = 0.28f),
    )

    // ------------------------------------------------------------------- the pigment, if any
    //
    // What this replaces is a 4dp bar of flat colour down the left edge. It was doing an honest
    // job — the hue is semantic and the block has to carry it — and it looked like a tab stuck on
    // the side of a card, which on glass is worse than on paper because everything else has
    // stopped having edges.
    //
    // So the hue enters through the border and runs into the pane, the way a wet edge runs into
    // wet paper. Four passes, and the third is the one that makes it read as paint rather than as
    // a gradient:
    //
    //  1. the bleed, falling away over about 46dp from the edge
    //  2. two blooms whose centres sit *outside* the pane, so the wash never ends on a straight
    //     line — which is the giveaway of a gradient and the thing a real wash never does
    //  3. a ring where the pigment dried darker, because a watercolour wash is always strongest
    //     at its boundary and palest in the middle; without it this is just a fade
    //  4. the border itself, carrying the hue at full strength and losing it across the face
    //
    // It is kept to the outer band on purpose, and not only for looks. Text starts one block
    // padding in (16dp), where the wash has fallen to about 0.15 of a *light* tone — the bright
    // member of the pair, not the ink one, precisely so that laying it under text costs almost
    // nothing. `ContrastTest` pins the ceiling.
    val reach = (46.dp.toPx()).coerceAtMost(size.width * 0.62f)
    val wash = if (accent == null) null else {
        val k = if (lead) 1.12f else 1f
        listOf(
            Brush.horizontalGradient(
                0.00f to accent.copy(alpha = 0.46f * k),
                0.22f to accent.copy(alpha = 0.22f * k),
                0.46f to accent.copy(alpha = 0.085f * k),
                0.74f to accent.copy(alpha = 0.025f * k),
                1.00f to Color.Transparent,
                startX = 0f,
                endX = reach,
            ),
            Brush.radialGradient(
                0.00f to accent.copy(alpha = 0.14f * k),
                0.55f to accent.copy(alpha = 0.05f * k),
                1.00f to Color.Transparent,
                center = Offset(-reach * 0.25f, size.height * 0.24f),
                radius = reach * 1.9f,
            ),
            Brush.radialGradient(
                0.00f to accent.copy(alpha = 0.13f * k),
                0.60f to accent.copy(alpha = 0.035f * k),
                1.00f to Color.Transparent,
                center = Offset(reach * 0.10f, size.height * 1.02f),
                radius = reach * 1.55f,
            ),
            Brush.radialGradient(
                0.58f to Color.Transparent,
                0.76f to accent.copy(alpha = 0.085f * k),
                0.90f to Color.Transparent,
                center = Offset(-reach * 0.18f, size.height * 0.46f),
                radius = reach * 2.1f,
            ),
        )
    }
    val accentRim = accent?.let {
        Brush.horizontalGradient(
            0.00f to it.copy(alpha = 0.85f),
            0.16f to it.copy(alpha = 0.34f),
            0.46f to Color.Transparent,
            startX = 0f,
            endX = size.width,
        )
    }

    onDrawBehind {
        drawOutline(outline, fill)
        wash?.forEach { drawRect(it) }
        drawRect(shine)
        drawRect(shade)
        drawOutline(outline, rim, style = edge)
        accentRim?.let { drawOutline(outline, it, style = edge) }
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
    val onGlass = LocalOnGlass.current
    // On glass the fill has to stay translucent or the control becomes the one opaque thing on a
    // transparent pane, which is exactly the sticker problem one level further down.
    val fill = when {
        onGlass && accent != null -> accent.copy(alpha = 0.30f)
        onGlass -> colors.paper.copy(alpha = 0.42f)
        accent != null -> accent.copy(alpha = 0.20f).compositeOver(colors.paper)
        else -> colors.paperSunk
    }

    Row(
        modifier
            .defaultMinSize(minHeight = 42.dp)
            .pressable(onClick = onClick, label = label)
            .clip(HomeOsShapes.pill)
            .background(fill, HomeOsShapes.pill)
            .then(
                if (onGlass) {
                    Modifier.border(
                        HomeOsDims.glassEdge,
                        Brush.verticalGradient(
                            0.00f to colors.specular.copy(alpha = 0.85f),
                            0.70f to Color.Transparent,
                            1.00f to colors.ink.copy(alpha = 0.22f),
                        ),
                        HomeOsShapes.pill,
                    )
                } else {
                    Modifier.border(1.5.dp, colors.ink, HomeOsShapes.pill)
                },
            )
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

/** Screen margins for a block. See [blockEndMargin] for why the two sides differ on paper. */
@Composable
fun Modifier.blockMargin(): Modifier = padding(
    start = HomeOsDims.screenMargin,
    end = blockEndMargin(),
)

/**
 * The end margin a list of blocks wants.
 *
 * A paper block reserves the plate offset on its right for a shadow it may or may not draw, so the
 * margin is short by exactly that much or every block would sit inset from the heading above it.
 * A pane reserves nothing, so it wants the full margin — and getting this wrong is the kind of
 * mistake that makes a screen look very subtly out of true for a reason nobody can name.
 */
@Composable
fun blockEndMargin(): Dp =
    if (LocalOnGlass.current && HomeOs.glass) {
        HomeOsDims.screenMargin
    } else {
        HomeOsDims.screenMargin - HomeOsDims.plateOffset
    }
