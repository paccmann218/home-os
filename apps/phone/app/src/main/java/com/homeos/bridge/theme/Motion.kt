package com.homeos.bridge.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Motion tokens hand-ported from androidx StandardMotionTokens.
 *
 * The Standard scheme is deliberate. Material 3 Expressive's spatial springs sit at damping 0.6,
 * which is consumer-playful and wrong for something you look at when you feel ill. Expressive is
 * also not in any stable release, so nothing here depends on it shipping.
 *
 * The governing rule is the split: springs for anything that moves or resizes, critically damped
 * for colour and opacity. A bouncing opacity is the most common amateur tell in a Compose app.
 */
object Motion {
    private const val SPATIAL_DAMPING = 0.9f

    /** Press, chip toggle: anything that must feel immediate. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SPATIAL_DAMPING, stiffness = 1400f)

    /** Block enter, layout change. */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SPATIAL_DAMPING, stiffness = 700f)

    /** Sheets and large transitions. */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> =
        spring(dampingRatio = SPATIAL_DAMPING, stiffness = 300f)

    /** Colour and alpha. Critically damped, so it never overshoots. */
    fun <T> effects(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1400f)

    /** Colour and alpha, slow: the ambient background drifting between room states. */
    fun <T> effectsSlow(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 400f)
}
