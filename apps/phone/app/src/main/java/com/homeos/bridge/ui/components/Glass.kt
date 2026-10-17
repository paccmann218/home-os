package com.homeos.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * How thick the glass is.
 *
 * Blur scales with surface area, because larger glass simulates thicker material. One blur value
 * for everything is the clearest tell of cheap glass.
 *
 * The alphas are floors, not preferences. A translucent surface has no fixed backdrop, so its
 * contrast cannot be certified by inspection; the only real fix is an opacity high enough that the
 * worst possible backdrop still clears the ratio. `ContrastTest` composites each of these over pure
 * white and pure black and requires 4.5:1 to hold in both. Apple shipped iOS 26 and then *raised*
 * opacity after legibility complaints — the floor is the lesson.
 *
 * Border width stays 1dp at every level and only the alpha rises, following Windows 11, which holds
 * stroke-width 1 across all seven of its elevation levels.
 */
enum class GlassLevel(
    val blur: Dp,
    val surfaceAlpha: Float,
    val borderAlpha: Float,
    /**
     * Whether this surface keeps a solid 2dp outline instead of the soft gradient rim.
     *
     * This is the one technique that makes glass and neobrutalism read as one design rather than
     * two: a glass panel that keeps the hard, high-contrast border still belongs to the same family
     * as the bordered paper blocks underneath it. Soft rims are right for chrome that should
     * disappear; hard borders are right for anything that interrupts you.
     */
    val hardBorder: Boolean,
) {
    /** A chip or a small control. */
    Control(blur = 10.dp, surfaceAlpha = 0.78f, borderAlpha = 0.06f, hardBorder = false),

    /** Top bar: should recede. */
    Chrome(blur = 24.dp, surfaceAlpha = 0.72f, borderAlpha = 0.08f, hardBorder = false),

    /** The floating bottom dock: an object, so it gets an object's outline. */
    Dock(blur = 24.dp, surfaceAlpha = 0.80f, borderAlpha = 0.14f, hardBorder = true),

    /** Bottom sheet and dialog: interrupts, so it gets a hard outline. */
    Sheet(blur = 32.dp, surfaceAlpha = 0.86f, borderAlpha = 0.16f, hardBorder = true),
}

/**
 * A glass surface: translucent, blurred, rimmed, and never holding content that has to be read
 * precisely. Glass is for chrome and atmosphere only — numbers, charts and body text all live on
 * opaque paper.
 *
 * Falls back to an opaque surface when the user has asked the system for more contrast, or when the
 * device cannot blur. That is deliberate: when someone says they need contrast, the honest answer
 * is to stop being translucent rather than to tune an alpha down.
 */
@Composable
fun GlassSurface(
    level: GlassLevel,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = HomeOs.colors
    val hazeState = LocalHazeState.current
    val glassOn = HomeOs.glass && hazeState != null

    // Soft rim: 1dp, bright at the top-left and falling to transparent, which is what actually
    // reads as a curved glass edge. Hard border: a solid 2dp outline that matches the paper blocks,
    // still with a brighter top-left so the surface keeps its sense of thickness.
    val rim = if (level.hardBorder) {
        Brush.linearGradient(
            0.0f to colors.onShell.copy(alpha = 0.92f),
            0.5f to colors.onShell.copy(alpha = 0.70f),
            1.0f to colors.onShell.copy(alpha = 0.52f),
        )
    } else {
        Brush.linearGradient(
            0.0f to colors.onShell.copy(alpha = level.borderAlpha * 2.2f),
            0.45f to colors.onShell.copy(alpha = level.borderAlpha),
            1.0f to Color.Transparent,
        )
    }
    val borderWidth = if (level.hardBorder) HomeOsDims.border else HomeOsDims.glassBorder

    val surface = if (glassOn) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = colors.shell,
                tint = HazeTint(colors.shellRaised.copy(alpha = level.surfaceAlpha)),
                blurRadius = level.blur,
                noiseFactor = 0.12f,
                // If the platform refuses to blur, a solid scrim is the only honest fallback;
                // a thin tint over unblurred content would be unreadable.
                fallbackTint = HazeTint(colors.shellRaised),
            ),
        )
    } else {
        Modifier.background(colors.shellRaised, shape)
    }

    Box(
        modifier
            .clip(shape)
            .then(surface)
            .border(borderWidth, rim, shape),
        content = content,
    )
}
