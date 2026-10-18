package com.homeos.bridge.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.theme.Motion

/**
 * A control that presses: a hair of scale, a haptic, and no ripple ever.
 *
 * The app has two press languages and they are assigned by material, not by taste. Paper blocks
 * *travel* onto their plate, because paper is a physical sheet and the offset behind it is where it
 * can go. Glass controls *compress*, because glass has no shadow to fall into — and a translucent
 * surface that slides sideways on touch looks like a bug rather than a button.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    label: String? = null,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val reduced = HomeOs.reducedMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    LaunchedEffect(pressed) {
        if (pressed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.965f else 1f,
        animationSpec = Motion.spatialFast(),
        label = "pressScale",
    )

    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

/**
 * The selection lens: one capsule of brighter glass that slides between equal-width options.
 *
 * This replaces a per-option outline, and the reason is worth stating plainly. An outline drawn
 * around whichever option is current makes *selection* look like a property of that option — it
 * appears and disappears, and on a row of four it reads as one item having been stickered. A single
 * lens that moves says something truer: there is one current destination, and it went from there to
 * here. It is also the only treatment that survives this app having hard 2dp borders elsewhere,
 * because nothing competes with the paper blocks for the same device.
 *
 * The spring is deliberately the non-bouncy one. NN/G's review of iOS 26 singles out tabs that
 * "shimmer, bubble, and wiggle when switched" as motion without purpose, and they are right: the
 * lens should arrive, not celebrate.
 */
fun Modifier.slidingLens(
    position: Float,
    count: Int,
    accent: Color,
    light: Color,
    shade: Color,
    inset: Dp = HomeOsDims.lensInset,
    /**
     * Whether the lens is a block of colour or a brighter pane of the same glass.
     *
     * On the dock it is glass. A solid capsule on a translucent bar is the one shape that cannot
     * be made to look like it belongs there — it is opaque where everything around it is not, so
     * it reads as a sticker on a window however well it is coloured. A pane that is simply
     * *brighter glass*, with its own specular edge, is what a selected tab looks like in every
     * interface that does this convincingly, and it leaves the accent free to do the marking from
     * inside, on the icon and the label, where it is legible at 5.4:1.
     */
    glass: Boolean = false,
): Modifier = drawBehind {
    if (count <= 0) return@drawBehind
    val slot = size.width / count
    val insetX = 3.dp.toPx()
    val insetY = inset.toPx()
    val w = slot - insetX * 2
    val h = size.height - insetY * 2
    if (w <= 0f || h <= 0f) return@drawBehind

    val rect = Size(w, h)
    val radius = CornerRadius(h / 2f)
    // Translated so the gradients resolve against the capsule rather than against the whole track:
    // a vertical brush in Compose is positioned in the draw scope, not in the rect it fills, so
    // drawing the capsule in place would light it from the top of the row instead of its own top.
    translate(left = slot * position + insetX, top = insetY) {
        if (glass) {
            // Brighter glass, lit from above, with a specular edge that catches at the top and
            // falls to a hairline at the bottom. Three strokes and no outline.
            drawRoundRect(brush = SolidColor(accent), size = rect, cornerRadius = radius)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.00f to light.copy(alpha = 0.55f),
                    0.60f to Color.Transparent,
                ),
                size = rect,
                cornerRadius = radius,
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.00f to light.copy(alpha = 0.95f),
                    0.30f to light.copy(alpha = 0.22f),
                    0.78f to Color.Transparent,
                    1.00f to shade.copy(alpha = 0.22f),
                ),
                size = rect,
                cornerRadius = radius,
                style = Stroke(HomeOsDims.glassEdge.toPx()),
            )
        } else {
            // A solid accent capsule with an ink outline, not a tinted pane. On a cream surface a
            // 14% wash is invisible; a capsule on an opaque track has to be a filled shape or the
            // selected option is carried by the label weight alone.
            drawRoundRect(brush = SolidColor(accent), size = rect, cornerRadius = radius)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.00f to light.copy(alpha = 0.40f),
                    0.55f to Color.Transparent,
                ),
                size = rect,
                cornerRadius = radius,
            )
            drawRoundRect(
                brush = SolidColor(shade),
                size = rect,
                cornerRadius = radius,
                style = Stroke(HomeOsDims.border.toPx()),
            )
        }
    }
}

/** One option in a [GlassSegmented]. */
@Immutable
data class Segment(val label: String, val sub: String? = null, val mono: Boolean = false)

/**
 * A segmented control: a frosted track with the lens sliding across it.
 *
 * Used wherever two or three views of the same thing are offered. The track is glass and the lens
 * is glass, so the only hard-edged thing nearby is whatever paper the control is sitting next to —
 * which is the hierarchy this design wants: content is printed, controls are suspended.
 */
@Composable
fun GlassSegmented(
    segments: List<Segment>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = HomeOs.colors.lens,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val position by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = Motion.spatialDefault(),
        label = "segment",
    )

    Row(
        modifier
            .fillMaxWidth()
            .frostedSurface(HomeOsShapes.pill)
            .slidingLens(
                position = position,
                count = segments.size,
                accent = accent,
                light = colors.specular,
                shade = colors.ink,
                inset = 5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            val on = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = HomeOsDims.minTouch)
                    .pressable(onClick = { onSelect(index) }, label = segment.label)
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    segment.label,
                    style = if (segment.mono) type.monoMicro else type.label,
                    color = if (on) colors.onLens else colors.inkMuted,
                    textAlign = TextAlign.Center,
                )
                segment.sub?.let {
                    Text(
                        it,
                        style = type.micro,
                        color = if (on) colors.onLens else colors.inkMuted.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A chip: the Check in screen's unit of interaction, and the most-repeated control in the app.
 *
 * Glass rather than paper, and that is the correction this rewrite makes. Eleven paper chips, each
 * with a 2dp border and its own hard offset, is eleven of the loudest device the design owns on one
 * screen — which is the same as none of them being loud. As glass they recede until chosen, and
 * being chosen is carried by the frost warming to the group's hue, the icon filling, and the rim
 * taking the accent. Nothing here is colour alone.
 */
@Composable
fun GlassChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = HomeOs.colors.teal,
) {
    val colors = HomeOs.colors
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.effects(),
        label = "chipSelection",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) accent else colors.onShellMuted,
        animationSpec = Motion.effects(),
        label = "chipIcon",
    )

    Row(
        modifier
            .heightIn(min = HomeOsDims.minTouch)
            .pressable(onClick = onClick)
            .frostedSurface(HomeOsShapes.pill, selection = selection, accent = accent)
            .padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.ink else colors.inkMuted,
            modifier = Modifier.size(19.dp),
        )
        Text(text = label, style = HomeOs.type.label, color = colors.ink)
    }
}

/**
 * A round glass control. Close buttons, the composer's send key, anything that is one glyph.
 *
 * [filled] makes it the one emphatic control on its surface: the accent becomes the fill and the
 * glyph goes dark, which is the only place in the app where a shell-tone hue carries something on
 * top of it — and it is safe because the hue at plate tone is the *light* member of its pair.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    accent: Color = HomeOs.colors.teal,
    diameter: Dp = 44.dp,
) {
    val colors = HomeOs.colors
    val active = filled && enabled
    val fill by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = Motion.effects(),
        label = "iconButtonFill",
    )

    Box(
        modifier
            .size(diameter)
            .pressable(onClick = onClick, enabled = enabled, label = contentDescription)
            .frostedSurface(HomeOsShapes.pill, selection = fill, accent = accent)
            .then(
                if (active) {
                    Modifier.drawBehind {
                        // The emphatic variant is a solid key, lit from the top like everything
                        // else on this layer rather than flat-filled.
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(accent, accent.copy(alpha = 0.82f)),
                            ),
                            cornerRadius = CornerRadius(this.size.height / 2f),
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                0.00f to Color.White.copy(alpha = 0.42f),
                                0.42f to Color.Transparent,
                            ),
                            cornerRadius = CornerRadius(this.size.height / 2f),
                            style = Stroke(HomeOsDims.glassEdge.toPx()),
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> colors.inkMuted.copy(alpha = 0.55f)
                else -> colors.ink
            },
            modifier = Modifier.size(diameter * 0.43f),
        )
    }
}

/**
 * A wide glass action. Used where a screen offers one or two things to do and neither of them is
 * reading something.
 */
@Composable
fun GlassAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = HomeOs.colors.teal,
    emphatic: Boolean = false,
) {
    val colors = HomeOs.colors
    Row(
        modifier
            .defaultMinSize(minHeight = HomeOsDims.minTouch)
            .pressable(onClick = onClick, label = label)
            .frostedSurface(HomeOsShapes.pill, selection = if (emphatic) 1f else 0f, accent = accent)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(18.dp),
            )
            Box(Modifier.size(width = 8.dp, height = 1.dp))
        }
        Text(
            label,
            style = HomeOs.type.label,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Three dots that breathe while the agent is working.
 *
 * A spinner would be wrong for this wait. A spinner means indeterminate machinery, and what is
 * happening here is somebody thinking for two to four seconds over a graph. The dots pulse on
 * staggered phases of one slow cycle, which is calm at a glance and still unmistakably alive, and
 * under reduced motion they simply hold at rest rather than stopping dead at full brightness.
 */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier, accent: Color = HomeOs.colors.teal) {
    val reduced = HomeOs.reducedMotion
    val cycle = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier
            .frostedSurface(HomeOsShapes.pill)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phase by if (reduced) {
                remember { mutableStateOf(0.6f) }
            } else {
                cycle.animateFloat(
                    initialValue = 0.28f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(620, delayMillis = index * 170),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$index",
                )
            }
            Box(
                Modifier
                    .size(6.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = accent.copy(alpha = phase),
                            cornerRadius = CornerRadius(size.minDimension / 2f),
                        )
                    },
            )
        }
    }
}

/** A hairline divider for inside glass surfaces, where a 2dp ink rule would be far too loud. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawRect(
                    Brush.horizontalGradient(
                        0.00f to Color.Transparent,
                        0.18f to colors.onShell.copy(alpha = 0.14f),
                        0.82f to colors.onShell.copy(alpha = 0.14f),
                        1.00f to Color.Transparent,
                    ),
                )
            },
    )
}

