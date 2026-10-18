package com.homeos.bridge.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
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

// The light's own colour, not the field's. Cool at 20 C and below, warm at 29 and above, and
// close to colourless in between, so an ordinary room does not look like it is on fire.
// Pulled a long way down in chroma from the versions that sat on a cool field. Over a warm ground
// a saturated blue does not read as cool light, it reads as mud: the two hues are near opposite and
// what they average to is grey. These are tints of the same light instead of separate colours.
internal val AmbientCool = Color(0xFF9FC4E8) //  20 C and below
internal val AmbientNeutral = Color(0xFFF7E6CC) //  23 C
internal val AmbientWarm = Color(0xFFFFBE7A) //  29 C and above

/**
 * Hue follows temperature.
 *
 * Internal rather than private because the 3D room tints its air with the same function: two
 * temperature-to-colour ramps in one app is the bug the projection comment in `GraphView` is a
 * monument to, in a different costume.
 */
internal fun ambientTint(temperatureC: Float?): Color {
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
 * One tiled noise bitmap for the whole app.
 *
 * Large flat fields band badly on 8-bit panels: a wide gradient crosses a quantisation boundary and
 * you see the step. A few percent of grain dithers it away. Drawn once at the root, never per block.
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
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * The dot grid, as one repeating tile.
 *
 * A grid of dots is the one piece of texture this style allows itself, and it is what keeps a flat
 * bold field from reading as a blank wall. Built as a tiled shader rather than as a few hundred
 * `drawCircle` calls a frame, for the obvious reason.
 */
@Composable
private fun rememberDotGrid(dotColor: Color): Brush {
    val density = LocalDensity.current
    val spacing = with(density) { 22.dp.roundToPx() }.coerceAtLeast(8)
    val radius = with(density) { 1.6.dp.toPx() }.coerceAtLeast(1f)
    return remember(spacing, radius, dotColor) {
        val bitmap = Bitmap.createBitmap(spacing, spacing, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            color = dotColor.toArgb()
        }
        canvas.drawCircle(spacing / 2f, spacing / 2f, radius, paint)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * The field: a flat bold colour, a dot grid, and one light source behind it all.
 *
 * The bloom sits high and fades toward the bottom of the screen, so the field reads as lit from
 * behind rather than as a solid fill — and the dots go *under* it, so they wash out where the light
 * is strongest and sharpen as it falls away. That gradient is the only soft thing in the design,
 * which is exactly why it can afford to be there: everything on top of it is a hard-edged block.
 *
 * The light still carries the room. Its hue warms as the room warms and cools as it cools, it dims
 * when the room is dark, and it stops moving entirely when nobody is home. Two drift periods that
 * do not divide into each other (37s and 53s), so the pattern never visibly loops, and reduced
 * motion stills it.
 */
private fun Modifier.ambient(ambience: Ambience): Modifier = composed {
    val colors = HomeOs.colors
    val reduced = HomeOs.reducedMotion
    val moving = ambience.occupied && !reduced
    val grain = rememberGrain()
    val dots = rememberDotGrid(colors.ink)

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

    val lit = 1f - (ambience.dim.coerceIn(0f, 1f) * 0.45f)

    drawBehind {
        val w = size.width
        val h = size.height

        // 1. the field
        drawRect(colors.shell)

        // 2. the dot grid, at the strength of a texture and not of a pattern
        drawRect(brush = dots, alpha = 0.13f)

        // 3. the light, behind and above, falling away toward the front.
        //
        // The bloom is [shellBloom] rather than white, and that is the whole difference between a
        // background that looks lit and one that looks like a colour with a white gradient over
        // it. White desaturates whatever it crosses, so a white bloom on a warm field washes a
        // pale grey hole through the middle of every screen; a bloom in the field's own family at
        // a higher lightness is simply more of the same light.
        drawRect(
            brush = Brush.radialGradient(
                0.00f to colors.shellBloom.copy(alpha = 0.96f * lit),
                0.34f to colors.shellBloom.copy(alpha = 0.62f * lit),
                0.68f to colors.shellBloom.copy(alpha = 0.18f * lit),
                1.00f to Color.Transparent,
                center = Offset(w * (0.30f + 0.14f * phaseA), h * (-0.12f + 0.08f * phaseB)),
                radius = maxOf(w, h) * 0.86f,
            ),
        )
        // The room's own colour, carried by the light rather than by the field, so the field stays
        // the one constant and the temperature is still legible before any number is read.
        drawRect(
            brush = Brush.radialGradient(
                0.00f to tint.copy(alpha = 0.22f * lit),
                1.00f to Color.Transparent,
                center = Offset(w * (0.78f - 0.12f * phaseB), h * (0.12f + 0.06f * phaseA)),
                radius = maxOf(w, h) * 0.70f,
            ),
        )
        // 4. and the front falls away, which is what gives the field a direction. Into
        // [shellDeep] rather than into ink: a black wash over a warm colour greys it, and the
        // bottom third of every screen came out the colour of wet cardboard.
        drawRect(
            brush = Brush.verticalGradient(
                0.34f to Color.Transparent,
                1.00f to colors.shellDeep.copy(alpha = 0.92f),
            ),
        )

        drawRect(brush = grain, alpha = 0.03f, blendMode = BlendMode.Plus)
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
