package com.homeos.bridge.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.Motion
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.random.Random

/**
 * The room, as far as the background is concerned.
 *
 * These are real readings, not decoration. The background is the one place in the app where the
 * room's condition is legible before you read a single number.
 */
@Immutable
data class Ambience(
    /** Room temperature from the ESP32. Null while it is unknown, which reads as neutral. */
    val temperatureC: Float? = null,
    /** Nobody home stills the drift completely. An empty room should look still. */
    val occupied: Boolean = true,
    /** 0 = bright room, 1 = dark room. Scales the whole gradient down. */
    val dim: Float = 0f,
)

/** Set by [Shell] so the glass chrome knows what to sample. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

private val AmbientCool = Color(0xFF0E293D) //  20 C and below
private val AmbientNeutral = Color(0xFF0C2628) //  23 C
private val AmbientWarm = Color(0xFF3F1F10) //  29 C and above
private val AmbientDeep = Color(0xFF09161C) // the far corner, always

/**
 * Hue follows temperature. Each blob sits only 1.2-1.3:1 above the shell, so this registers as
 * atmosphere rather than as a coloured background competing with the paper blocks on top of it.
 */
private fun ambientTint(temperatureC: Float?): Color {
    val t = temperatureC ?: 23f
    return when {
        t <= 20f -> AmbientCool
        t >= 29f -> AmbientWarm
        t < 23f -> lerp(AmbientCool, AmbientNeutral, (t - 20f) / 3f)
        else -> lerp(AmbientNeutral, AmbientWarm, (t - 23f) / 6f)
    }
}

private const val NOISE_TILE = 128

/**
 * One tiled noise bitmap for the whole app, composited over the gradient.
 *
 * Large dark gradients band badly on 8-bit panels: you get visible steps where the ramp crosses a
 * quantisation boundary. A few percent of grain dithers those steps away. It is drawn once at the
 * root, never per block.
 */
@Composable
private fun rememberGrain(): Brush = remember {
    val rnd = Random(seed = 7)
    val pixels = IntArray(NOISE_TILE * NOISE_TILE) {
        val v = rnd.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(NOISE_TILE, NOISE_TILE, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, NOISE_TILE, 0, 0, NOISE_TILE, NOISE_TILE)
    ShaderBrush(
        ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated),
    )
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.001f || radius <= 0f) return
    drawRect(
        brush = Brush.radialGradient(
            0.0f to color.copy(alpha = alpha),
            0.6f to color.copy(alpha = alpha * 0.45f),
            1.0f to color.copy(alpha = 0f),
            center = center,
            radius = radius,
        ),
    )
}

/**
 * The ambient layer: a slow mesh gradient over the shell, plus grain.
 *
 * Two drift periods that do not divide into each other (37s and 53s), so the pattern never
 * visibly loops. The drift is the first thing reduced motion removes, and an empty room stops it
 * too — the gradient still shows the temperature, it just stops moving.
 */
private fun Modifier.ambient(ambience: Ambience): Modifier = composed {
    val colors = HomeOs.colors
    val reduced = HomeOs.reducedMotion
    val moving = ambience.occupied && !reduced
    val grain = rememberGrain()

    val tint by animateColorAsState(
        targetValue = ambientTint(ambience.temperatureC),
        animationSpec = Motion.effectsSlow(),
        label = "ambientTint",
    )

    val drift = rememberInfiniteTransition(label = "ambientDrift")
    val phaseA by if (moving) {
        drift.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(37_000), RepeatMode.Reverse),
            label = "phaseA",
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }
    val phaseB by if (moving) {
        drift.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(53_000), RepeatMode.Reverse),
            label = "phaseB",
        )
    } else {
        remember { mutableFloatStateOf(0.6f) }
    }

    val scale = 1f - (ambience.dim.coerceIn(0f, 1f) * 0.55f)

    drawBehind {
        drawRect(colors.shell)

        val w = size.width
        val h = size.height
        // The warm/cool blob leads, high and left, drifting on the slower period.
        blob(
            color = tint,
            center = Offset(w * (0.18f + 0.22f * phaseA), h * (0.10f + 0.10f * phaseB)),
            radius = maxOf(w, h) * 0.85f,
            alpha = 0.95f * scale,
        )
        // A second, cooler mass low and right keeps the field from reading as one flat wash.
        blob(
            color = AmbientDeep,
            center = Offset(w * (0.92f - 0.18f * phaseB), h * (0.78f + 0.14f * phaseA)),
            radius = maxOf(w, h) * 0.75f,
            alpha = 0.9f * scale,
        )
        // A faint highlight opposite the lead blob, so the gradient has a direction.
        blob(
            color = tint,
            center = Offset(w * (0.75f + 0.1f * phaseA), h * (0.34f - 0.12f * phaseB)),
            radius = maxOf(w, h) * 0.45f,
            alpha = 0.35f * scale,
        )

        drawRect(brush = grain, alpha = 0.042f, blendMode = BlendMode.Plus)
    }
}

object ShellDims {
    val topBar: Dp = 60.dp

    /** The floating dock's own height. */
    val dock: Dp = 62.dp

    /** How far the dock floats above the system inset, and how far content clears it. */
    val dockGap: Dp = 12.dp
}

/**
 * The app frame: ambient background, scrolling content, and glass chrome above it.
 *
 * The layering is what makes the hybrid work. [content] and the background sit inside one
 * `hazeSource` node so the chrome can sample both; the chrome is a sibling drawn above, so it never
 * blurs itself. Content never sits on glass and glass never sits under a number.
 */
@Composable
fun Shell(
    ambience: Ambience,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    /** Sheets and dialogs. Drawn above the chrome, and still able to blur the content below. */
    overlay: @Composable () -> Unit = {},
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val glassOn = HomeOs.glass
    val hazeState = rememberHazeState(blurEnabled = glassOn && HazeDefaults.blurEnabled())
    val statusBars = WindowInsets.statusBars.asPaddingValues()
    val navBars = WindowInsets.navigationBars.asPaddingValues()

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .ambient(ambience)
                .hazeSource(hazeState),
        ) {
            content(
                PaddingValues(
                    top = statusBars.calculateTopPadding() + ShellDims.topBar,
                    // Clear the dock plus the gap it floats on, twice: once below it and once
                    // above, so the last block never sits tight against the glass.
                    bottom = navBars.calculateBottomPadding() + ShellDims.dock + ShellDims.dockGap * 2,
                ),
            )
        }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(Modifier.align(Alignment.TopCenter)) { topBar() }
            Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
            overlay()
        }
    }
}
