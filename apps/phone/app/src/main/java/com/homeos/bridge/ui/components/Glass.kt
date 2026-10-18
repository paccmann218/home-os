package com.homeos.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsColors
import com.homeos.bridge.theme.HomeOsDims
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * How thick the glass is, and how light falls on it.
 *
 * Four things vary per level, and each of them is load-bearing.
 *
 * **Blur** scales with surface area, because larger glass simulates thicker material; one blur
 * value everywhere is the clearest tell of cheap glass. A blur of zero means *tint only*: the
 * ambient background is a smooth gradient by construction, and blurring a smooth field changes
 * nothing a viewer can see, so the small surfaces skip the sampling pass entirely and the frame
 * budget goes to the three surfaces that have real detail behind them.
 *
 * **Surface alpha** is a floor, not a preference. A translucent surface has no fixed backdrop, so
 * its contrast cannot be certified by inspection; the only real fix is an opacity high enough that
 * the worst backdrop still clears the ratio. `ContrastTest` composites every level over pure white
 * and pure black, including the sheen below, and requires 4.5:1 in both. Apple shipped iOS 26 and
 * then raised opacity after legibility complaints — the floor is the lesson.
 *
 * **Sheen and rim** are the difference between glass and a grey rectangle. A flat uniform border is
 * the single most reliable giveaway of imitation glass: real glass is lit from somewhere, so the
 * top edge catches a specular line, the bottom edge falls into shade, and the outer rim is bright
 * at the top-left and gone by the bottom-right. Border *width* stays 1dp at every level and only
 * the alpha moves, following Windows 11, which holds stroke-width 1 across all seven of its
 * elevation levels.
 *
 * **Lift** is how far the surface floats off the shell. Nothing here takes the hard 2dp outline
 * that paper blocks take: an outline is what makes a surface feel printed onto the page, and these
 * are meant to feel suspended above it.
 */
enum class GlassLevel(
    val blur: Dp,
    val surfaceAlpha: Float,
    /** How strongly the top of the surface catches light. */
    val sheen: Float,
    /** The lit top-left of the outer rim. Zero means no rim at all. */
    val rimTop: Float,
    /** Where the rim has fallen away to by the bottom-right. */
    val rimBottom: Float,
    /** Ambient shadow under the surface, so it reads as floating rather than painted on. */
    val lift: Dp,
) {
    /** A chip, a tile, a small control. Tint only — see the note on blur above. */
    Control(blur = 0.dp, surfaceAlpha = 0.74f, sheen = 0.055f, rimTop = 0.24f, rimBottom = 0.06f, lift = 0.dp),

    /** The top bar. It should recede, so it carries no rim and dissolves downward. */
    Chrome(blur = 24.dp, surfaceAlpha = 0.72f, sheen = 0.05f, rimTop = 0f, rimBottom = 0f, lift = 0.dp),

    /** The floating dock: the most object-like surface in the app, so it is lit the hardest. */
    Dock(blur = 26.dp, surfaceAlpha = 0.80f, sheen = 0.05f, rimTop = 0.34f, rimBottom = 0.08f, lift = 20.dp),

    /** A sheet or dialog. Interrupts, so it floats highest. */
    Sheet(blur = 32.dp, surfaceAlpha = 0.86f, sheen = 0.045f, rimTop = 0.28f, rimBottom = 0.07f, lift = 26.dp),
}

/**
 * The falloff that makes a surface dissolve instead of ending at a line.
 *
 * Used by the top bar. An edge-to-edge bar has no corners to light, so the only thing that can read
 * as its boundary is a rule across the screen — and a rule across the screen is exactly what makes
 * chrome look bolted on. Fading the material out over its last third removes the boundary entirely
 * and lets content slide under it the way it slides under a page gutter.
 */
val GlassFadeDown: Brush = Brush.verticalGradient(
    0.00f to Color.Black,
    0.58f to Color.Black,
    1.00f to Color.Transparent,
)

/**
 * A glass surface: translucent, lit, rimmed, and never holding content that has to be read
 * precisely. Glass is for chrome and controls only — numbers, charts and body text all live on
 * opaque paper.
 *
 * Falls back to an opaque surface when the user has asked the system for more contrast, or when the
 * device cannot blur. That is deliberate: when someone says they need contrast, the honest answer
 * is to stop being translucent rather than to tune an alpha down. The sheen goes with it, because a
 * simulated light source is decoration, and decoration is the first thing that should drop.
 */
@Composable
fun GlassSurface(
    level: GlassLevel,
    shape: Shape,
    modifier: Modifier = Modifier,
    /** Fades the whole surface along its length. See [GlassFadeDown]. */
    fade: Brush? = null,
    /** Painted over the glass and under the content: a selection tint, a warning wash. */
    tint: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = HomeOs.colors
    val hazeState = LocalHazeState.current
    val blurred = HomeOs.glass && hazeState != null && level.blur > 0.dp
    val lit = HomeOs.glass

    val surface = when {
        blurred -> Modifier.hazeEffect(
            state = hazeState!!,
            style = HazeStyle(
                backgroundColor = colors.shell,
                tint = HazeTint(colors.shellRaised.copy(alpha = level.surfaceAlpha)),
                blurRadius = level.blur,
                noiseFactor = 0.12f,
                // If the platform refuses to blur, a solid scrim is the only honest fallback; a
                // thin tint over unblurred content would be unreadable.
                fallbackTint = HazeTint(colors.shellRaised),
            ),
        ) { if (fade != null) mask = fade }

        lit && fade != null -> Modifier.background(
            // No haze pass on this level, so the falloff has to be baked into the fill.
            brush = Brush.verticalGradient(
                0.00f to colors.shellRaised.copy(alpha = level.surfaceAlpha),
                0.58f to colors.shellRaised.copy(alpha = level.surfaceAlpha),
                1.00f to Color.Transparent,
            ),
            shape = shape,
        )

        lit -> Modifier.background(colors.shellRaised.copy(alpha = level.surfaceAlpha), shape)

        else -> Modifier.background(colors.shellRaised, shape)
    }

    Box(
        modifier
            .then(if (level.lift > 0.dp) Modifier.lift(level.lift, shape) else Modifier)
            .clip(shape)
            .then(surface)
            .then(if (lit) Modifier.glassLight(shape, level, colors, faded = fade != null) else Modifier)
            .then(if (tint != null) Modifier.background(tint, shape) else Modifier)
            .then(
                when {
                    !lit -> Modifier.border(
                        HomeOsDims.glassBorder,
                        colors.onShell.copy(alpha = 0.30f),
                        shape,
                    )
                    level.rimTop > 0f -> Modifier.border(
                        HomeOsDims.glassBorder,
                        rim(colors, level),
                        shape,
                    )
                    else -> Modifier
                },
            ),
        content = content,
    )
}

/**
 * The outer rim: bright at the top-left, gone by the bottom-right.
 *
 * Direction is the whole point. A rim of one constant alpha is a flat outline however low you set
 * it; a rim that falls away implies a light source above and to the left, which is the same light
 * the sheen and the bevel are lit by. Three surfaces agreeing about where the light comes from is
 * what the eye reads as material.
 */
private fun rim(colors: HomeOsColors, level: GlassLevel): Brush = Brush.linearGradient(
    0.00f to colors.onShell.copy(alpha = level.rimTop),
    0.40f to colors.onShell.copy(alpha = level.rimTop * 0.55f),
    1.00f to colors.onShell.copy(alpha = level.rimBottom),
    start = Offset.Zero,
    end = Offset(220f, 260f),
)

/**
 * Light on the glass itself: a sheen down the face, and a bevel around the inside edge.
 *
 * The bevel is one stroke with a four-stop gradient — specular white at the very top, nothing
 * through the middle, shadow at the very bottom. It is stroked on the shape outline *inside* the
 * clip, so the outer half of the stroke is clipped away and what survives is a crisp hairline
 * hugging the inside of the curve. That is the detail that reads as thickness, and without it a
 * translucent fill with an outline reads as a sticker however good the blur behind it is.
 */
private fun Modifier.glassLight(
    shape: Shape,
    level: GlassLevel,
    colors: HomeOsColors,
    faded: Boolean,
) = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(HomeOsDims.glassEdge.toPx())

    val sheen = Brush.verticalGradient(
        0.00f to colors.onShell.copy(alpha = level.sheen),
        0.42f to Color.Transparent,
        1.00f to if (faded) Color.Transparent else colors.shellSunk.copy(alpha = 0.10f),
    )
    val bevel = Brush.verticalGradient(
        0.00f to colors.onShell.copy(alpha = if (faded) 0.16f else 0.40f),
        0.22f to colors.onShell.copy(alpha = 0.04f),
        0.74f to Color.Transparent,
        1.00f to if (faded) Color.Transparent else colors.shellSunk.copy(alpha = 0.42f),
    )

    onDrawBehind {
        drawRect(sheen)
        drawOutline(outline, brush = bevel, style = stroke)
    }
}

/**
 * A small glass surface, drawn in a single pass: tint, sheen, bevel, rim.
 *
 * This is what every control in the app is made of — chips, tiles, the composer, the add slot. It
 * exists separately from [GlassSurface] because controls are small and numerous, and a control does
 * not need a backdrop sample: it sits on the ambient gradient, which is smooth, so there is nothing
 * for a blur to reveal. What it does need is to be *lit the same way* as the chrome above it, which
 * is the whole reason this is one function rather than a handful of borders at call sites.
 *
 * [selection] is a 0..1 animated value rather than a boolean so the hue arrives by crossfade. The
 * selected state is carried by four channels at once — fill, specular strength, rim colour and rim
 * alpha — because any one of them alone is a weak signal on a translucent surface, and because a
 * hard high-contrast outline, which would be a strong signal, is exactly the device this design is
 * trying to keep for paper.
 */
@Composable
fun Modifier.frostedSurface(
    shape: Shape,
    selection: Float = 0f,
    accent: Color = HomeOs.colors.teal,
): Modifier {
    val colors = HomeOs.colors
    if (!HomeOs.glass) {
        return this
            .clip(shape)
            .background(if (selection > 0.5f) accent.copy(alpha = 0.22f) else colors.shellRaised, shape)
            .border(HomeOsDims.glassBorder, colors.onShell.copy(alpha = 0.40f), shape)
    }

    val level = GlassLevel.Control
    return this
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val edge = Stroke(HomeOsDims.glassEdge.toPx())
            val hair = Stroke(HomeOsDims.glassBorder.toPx())

            val base = colors.shellRaised.copy(alpha = level.surfaceAlpha)
            val wash = Brush.verticalGradient(
                0.00f to colors.onShell.copy(alpha = level.sheen + 0.075f * selection),
                0.45f to accent.copy(alpha = 0.11f * selection),
                1.00f to colors.shellSunk.copy(alpha = 0.10f * (1f - selection)),
            )
            val bevel = Brush.verticalGradient(
                0.00f to colors.onShell.copy(alpha = 0.34f + 0.26f * selection),
                0.26f to colors.onShell.copy(alpha = 0.04f),
                0.74f to Color.Transparent,
                1.00f to colors.shellSunk.copy(alpha = 0.34f),
            )
            val rimColor = androidx.compose.ui.graphics.lerp(colors.onShell, accent, selection)
            val rimBrush = Brush.linearGradient(
                0.00f to rimColor.copy(alpha = level.rimTop + 0.28f * selection),
                0.45f to rimColor.copy(alpha = level.rimTop * 0.5f + 0.18f * selection),
                1.00f to rimColor.copy(alpha = level.rimBottom + 0.16f * selection),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )

            onDrawBehind {
                drawOutline(outline, SolidColor(base))
                drawRect(wash)
                drawOutline(outline, brush = bevel, style = edge)
                drawOutline(outline, brush = rimBrush, style = hair)
            }
        }
}

/**
 * The ambient shadow that puts air under a floating surface.
 *
 * Pure black rather than a tinted shadow: on a shell this dark a coloured shadow is invisible, and
 * what is wanted is not a colour but the absence of one directly beneath the object.
 */
private fun Modifier.lift(elevation: Dp, shape: Shape) = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black,
    spotColor = Color.Black,
)
