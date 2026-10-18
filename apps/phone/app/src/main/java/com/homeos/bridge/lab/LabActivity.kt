package com.homeos.bridge.lab

import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.homeos.bridge.data.Device
import com.homeos.bridge.theme.HomeOsTheme
import com.homeos.bridge.ui.room.RoomView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A throwaway bench for the three questions the room renderer's design rests on.
 *
 * It is a separate activity rather than a dev-mode screen so that it never ships inside the product
 * navigation and so it can be launched straight from adb:
 *
 *     adb shell am start -n com.homeos.bridge/.lab.LabActivity
 *     adb logcat -s ROOMLAB
 *
 * Delete this file, its manifest entry and the package once the numbers are recorded in the plan.
 * The whole point is that the renderer's budget is a measurement and not an extrapolation.
 */
class LabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Lab() }
    }
}

@Composable
private fun Lab() {
    // The sweep and the probes have done their job; their results are recorded in the plan. The
    // bench now hosts the room renderer on its own, so it can be iterated without dragging the
    // whole app on and off the device for every tweak.
    HomeOsTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF070C0E))) {
            RoomView(devices = LAB_DEVICES, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Stand-ins for the five real bedroom devices, so the bench can light the room on its own. */
private val LAB_DEVICES = listOf(
    Device("light", "light", "Room light (ESP32 LED)", "bedroom", "on", null, null, 70, 0, 100, null, null, null, null),
    Device("bulb", "light", "WiZ bulb", "bedroom", "on", null, null, 55, 0, 100, null, null, null, null),
    Device("ac", "ac", "Air conditioner", "bedroom", "on", 23, null, null, 18, 30, null, null, null, null),
    Device("fan", "fan", "Fan", "bedroom", "on", null, 2, null, 0, 3, null, null, null, null),
    Device("env", "sensor", "Room sensor", "bedroom", null, null, null, null, null, null, 24.6f, 61f, 100f, null),
)

// ---------------------------------------------------------------- experiment 1: the quad sweep

private const val START_N = 100
private const val MAX_N = 3000
private const val STEP_N = 100

/** Frames held at each N before its result is logged. The first 30 are discarded as warm-up. */
private const val FRAMES_PER_STEP = 90
private const val WARMUP_FRAMES = 30

/**
 * Draws N reused-`Path` quads a frame and reports the frame time at each N.
 *
 * Quad *area* is held constant as N rises — total covered area stays about twice the viewport — so
 * what the sweep isolates is the per-draw-call cost of splitting one scene into more, smaller
 * pieces. That is exactly the tradeoff the real renderer makes when it segments long wall faces,
 * so the number this produces is the number that matters, rather than a synthetic overdraw curve.
 */
@Composable
private fun QuadSweep(modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    var n by remember { mutableIntStateOf(START_N) }
    var done by remember { mutableStateOf(false) }

    // Not snapshot state: written every frame, read only by the sweep itself.
    val timings = remember { ArrayList<Float>(FRAMES_PER_STEP) }
    val path = remember { Path() }

    // Positions on a jittered grid, rebuilt only when N changes.
    val layout = remember(n) { QuadLayout(n) }

    LaunchedEffect(Unit) {
        var last = 0L
        var held = 0
        while (!done) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val ms = (now - last) / 1_000_000f
                    held++
                    if (held > WARMUP_FRAMES) timings.add(ms)
                }
                last = now
                frame++

                if (held >= FRAMES_PER_STEP) {
                    timings.sort()
                    val median = timings[timings.size / 2]
                    val p95 = timings[(timings.size * 95) / 100]
                    val janky = timings.count { it > 16.7f }
                    Log.i(
                        "ROOMLAB",
                        "sweep n=$n median=${"%.2f".format(median)}ms " +
                            "p95=${"%.2f".format(p95)}ms janky=$janky/${timings.size}",
                    )
                    timings.clear()
                    held = 0
                    if (n >= MAX_N) {
                        Log.i("ROOMLAB", "sweep done")
                        done = true
                    } else {
                        n += STEP_N
                    }
                }
            }
        }
    }

    Canvas(modifier) {
        // Read inside the draw lambda so Compose attributes the invalidation to the draw node and
        // does not recompose anything above it. This is the rule the real renderer has to follow.
        @Suppress("UNUSED_EXPRESSION")
        frame

        val count = layout.count
        val half = sqrt(2f * size.width * size.height / count) / 2f
        for (i in 0 until count) {
            val cx = layout.x[i] * size.width
            val cy = layout.y[i] * size.height
            path.rewind()
            path.moveTo(cx - half, cy - half)
            path.lineTo(cx + half, cy - half)
            path.lineTo(cx + half, cy + half)
            path.lineTo(cx - half, cy + half)
            path.close()
            drawPath(path, layout.colors[i])
        }
    }
}

private class QuadLayout(val count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val colors = Array(count) { Color.Transparent }

    init {
        val rnd = Random(11)
        for (i in 0 until count) {
            x[i] = rnd.nextFloat()
            y[i] = rnd.nextFloat()
            // Opaque, varied: flat-shaded faces, which is what the renderer actually draws.
            colors[i] = Color(
                red = 0.35f + rnd.nextFloat() * 0.5f,
                green = 0.35f + rnd.nextFloat() * 0.45f,
                blue = 0.30f + rnd.nextFloat() * 0.5f,
                alpha = 1f,
            )
        }
    }
}

// ------------------------------------------------- experiment 2: does drawVertices render at all

/**
 * Three triangles through the *platform* canvas.
 *
 * If this renders, the entire opaque scene can become one draw call with per-vertex colours and
 * zero per-frame allocation, and the performance question stops being interesting. `drawVertices`
 * was on hwui's unsupported list under the old OpenGL renderer; everything has been Skia since
 * Android P and minSdk here is 31, so it should work — but "should" is why this exists.
 *
 * Note it goes through `nativeCanvas`, never `androidx.compose.ui.graphics.Vertices`, whose
 * constructor takes `List<Offset>` and `List<Color>` and would box thousands of objects a frame.
 */
@Composable
private fun VerticesProbe(modifier: Modifier = Modifier) {
    val paint = remember { Paint() }
    var reported by remember { mutableStateOf(false) }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val verts = floatArrayOf(
            w * 0.05f, h * 0.85f, w * 0.20f, h * 0.15f, w * 0.35f, h * 0.85f,
            w * 0.38f, h * 0.85f, w * 0.53f, h * 0.15f, w * 0.68f, h * 0.85f,
            w * 0.71f, h * 0.85f, w * 0.86f, h * 0.15f, w * 0.99f, h * 0.85f,
        )
        val colors = intArrayOf(
            0xFF60D3CD.toInt(), 0xFF60D3CD.toInt(), 0xFF0B625E.toInt(),
            0xFFB6A2F3.toInt(), 0xFFB6A2F3.toInt(), 0xFF543A8B.toInt(),
            0xFFDDBA56.toInt(), 0xFFDDBA56.toInt(), 0xFF7C550F.toInt(),
        )
        drawIntoCanvas { canvas ->
            runCatching {
                canvas.nativeCanvas.drawVertices(
                    android.graphics.Canvas.VertexMode.TRIANGLES,
                    verts.size,
                    verts,
                    0,
                    null,
                    0,
                    colors,
                    0,
                    null,
                    0,
                    0,
                    paint,
                )
            }.onFailure {
                if (!reported) Log.e("ROOMLAB", "drawVertices threw: ${it.message}")
            }
        }
        if (!reported) {
            Log.i("ROOMLAB", "drawVertices called without throwing - check the screenshot for 3 triangles")
            reported = true
        }
    }
}

// ----------------------------------------- experiment 3: is the floor foreshortening sin(pitch)?

/**
 * Checks that a circle on the floor projects to an ellipse squashed by exactly `abs(sin(pitch))`.
 *
 * If it holds, a light pool is one `drawCircle` inside a `scale(1f, foreshorten)` transform, which
 * is exact and costs nothing. If it does not, pools have to be built as projected geometry. Two
 * floor points one metre apart along model x and along model z are projected, and the ratio of
 * their screen separations is compared with the prediction.
 */
private fun foreshortenReport(): String {
    val focal = 4.70f
    val scale = 1000f
    fun project(x: Float, z: Float, yaw: Float, pitch: Float): Pair<Float, Float> {
        val x1 = x * cos(yaw) + z * sin(yaw)
        val z1 = -x * sin(yaw) + z * cos(yaw)
        val y1 = 0f * cos(pitch) - z1 * sin(pitch)
        val z2 = 0f * sin(pitch) + z1 * cos(pitch)
        val persp = focal / (focal + z2)
        return (x1 * scale * persp) to (y1 * scale * persp)
    }

    val out = StringBuilder("foreshorten check  ")
    for (deg in intArrayOf(26, 47, 70)) {
        val pitch = Math.toRadians(deg.toDouble()).toFloat()
        // Yaw 0 so model x maps to screen x and model z maps to screen y; a small step keeps the
        // measurement local, which is the regime a light pool actually lives in.
        val d = 0.02f
        val o = project(0f, 0f, 0f, pitch)
        val ax = project(d, 0f, 0f, pitch)
        val az = project(0f, d, 0f, pitch)
        val spanX = abs(ax.first - o.first)
        val spanZ = abs(az.second - o.second)
        val measured = spanZ / spanX
        val predicted = abs(sin(pitch))
        out.append("${deg}deg measured=${"%.4f".format(measured)} sin=${"%.4f".format(predicted)}  ")
        Log.i("ROOMLAB", "foreshorten ${deg}deg measured=$measured predicted=$predicted")
    }
    return out.toString()
}
