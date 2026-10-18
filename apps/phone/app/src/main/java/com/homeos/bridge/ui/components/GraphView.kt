package com.homeos.bridge.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeos.bridge.data.GraphData
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The graph, laid out in three dimensions and drawn with perspective.
 *
 * This is the one screen that shows what HomeOS actually is. A list of episodes is a list; the
 * thing that makes the project a graph product is that memory has *shape* — conditions implying
 * conditions, episodes hanging off bands, playbooks superseding playbooks. So it gets drawn as it
 * is rather than described.
 *
 * Why 3D rather than a flat force layout: a real person's graph has a dense core and sparse limbs,
 * and in two dimensions the core collapses into an unreadable hairball. Spreading it over a third
 * axis and letting it turn separates the core visually, and depth gives a free, honest visual
 * hierarchy — near is bright and large, far is dim and small.
 */

/**
 * A dedicated hue for devices, kept local rather than added to the palette.
 *
 * The graph needs one more family than the UI does, and widening `HomeOsColors` for a colour that
 * appears in exactly one component would make every screen's token list longer for no reason.
 */
private val GraphDevice = Color(0xFF7C9CE8)

/**
 * Node colour by label, grouped into the six families the graphs actually contain.
 *
 * The vocabulary was read off the running graphs rather than guessed: a body graph holds Condition,
 * Intervention, Var, DeviceRef, Playbook, Person, Signal, Episode, Band, Insight, Turn and a few
 * more; the home graph holds Constraint, Task, Device, Agent, Arbitration, Intent, Room and Home.
 * Anything unrecognised stays muted rather than borrowing a family's colour and lying about it.
 */
private fun labelColor(label: String, colors: HomeOsColors): Color = when (label) {
    // Who
    "Person", "Agent" -> colors.paper
    // What happened
    "Episode", "Event", "Session", "Turn", "Decision" -> colors.teal
    // How you were
    "Condition" -> colors.amber
    // The context it was in
    "Band", "Var", "Signal", "Constraint", "ConstraintRef", "ConstraintExport", "Arbitration" ->
        colors.violet
    // What the home does about it
    "Playbook", "Step", "Action", "Intervention", "Lever", "Task", "Intent" -> colors.green
    // What it worked out
    "Insight" -> colors.red
    // The room
    "Device", "DeviceRef", "Room", "Home" -> GraphDevice
    else -> colors.onShellMuted
}

/** The families, for the legend. Kept beside the mapping so the two cannot drift apart. */
val GRAPH_FAMILIES: List<Pair<String, (HomeOsColors) -> Color>> = listOf(
    "you" to { c: HomeOsColors -> c.paper },
    "episodes" to { c: HomeOsColors -> c.teal },
    "conditions" to { c: HomeOsColors -> c.amber },
    "context" to { c: HomeOsColors -> c.violet },
    "what it does" to { c: HomeOsColors -> c.green },
    "insights" to { c: HomeOsColors -> c.red },
    "devices" to { _: HomeOsColors -> GraphDevice },
)

private class Layout(val n: Int) {
    val x = FloatArray(n)
    val y = FloatArray(n)
    val z = FloatArray(n)
    val degree = IntArray(n)
}

/**
 * The camera, in one place.
 *
 * These were two numbers in two functions and they did not agree: the renderer projected at 0.46
 * of the short side and the hit test at 0.38, so every tap resolved against a picture 17 percent
 * smaller than the one on screen. Near the middle it was close enough to look like it worked, and
 * wrong by a node or two anywhere else. A projection has exactly one scale, so it is written once.
 *
 * The value allows for the layout squashing outliers out to 1.25 of the unit ball: 1.25 x 0.41 of
 * the short side is just inside half the panel height, so a far component lands at the frame edge
 * instead of being cut in half by it.
 */
private const val PROJECTION = 0.41f
private const val FOCAL = 2.6f

/**
 * Fruchterman-Reingold in three dimensions, run once off the main thread.
 *
 * Repulsion is the O(n^2) term and the reason `limit` matters: at 220 nodes that is ~24k pairs per
 * iteration, which finishes in well under a second. A Barnes-Hut tree would scale further and is
 * not worth the complexity for a graph that is bounded by a screen.
 */
private fun layout(data: GraphData, iterations: Int = 260): Layout {
    val n = data.nodes.size
    val l = Layout(n)
    if (n == 0) return l

    val index = HashMap<String, Int>(n * 2)
    data.nodes.forEachIndexed { i, node -> index[node.id] = i }
    val eu = IntArray(data.edges.size)
    val ev = IntArray(data.edges.size)
    data.edges.forEachIndexed { i, e ->
        val a = index[e.source] ?: 0
        val b = index[e.target] ?: 0
        eu[i] = a
        ev[i] = b
        l.degree[a]++
        l.degree[b]++
    }

    // Start on a sphere rather than at random: a random cloud takes far longer to untangle, and a
    // shell gives the simulation a sane scale from the first iteration.
    val rnd = Random(11)
    for (i in 0 until n) {
        val theta = rnd.nextFloat() * 2f * Math.PI.toFloat()
        val phi = kotlin.math.acos(2f * rnd.nextFloat() - 1f)
        val r = 0.6f + rnd.nextFloat() * 0.4f
        l.x[i] = r * sin(phi) * cos(theta)
        l.y[i] = r * sin(phi) * sin(theta)
        l.z[i] = r * cos(phi)
    }

    val k = cbrt(1.0 / n).toFloat()
    val dx = FloatArray(n)
    val dy = FloatArray(n)
    val dz = FloatArray(n)

    for (iter in 0 until iterations) {
        java.util.Arrays.fill(dx, 0f)
        java.util.Arrays.fill(dy, 0f)
        java.util.Arrays.fill(dz, 0f)

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var ax = l.x[i] - l.x[j]
                var ay = l.y[i] - l.y[j]
                var az = l.z[i] - l.z[j]
                var d2 = ax * ax + ay * ay + az * az
                if (d2 < 1e-6f) {
                    ax = (rnd.nextFloat() - 0.5f) * 1e-3f
                    ay = (rnd.nextFloat() - 0.5f) * 1e-3f
                    az = (rnd.nextFloat() - 0.5f) * 1e-3f
                    d2 = ax * ax + ay * ay + az * az + 1e-6f
                }
                val d = sqrt(d2)
                val f = (k * k) / d
                val ux = ax / d * f
                val uy = ay / d * f
                val uz = az / d * f
                dx[i] += ux; dy[i] += uy; dz[i] += uz
                dx[j] -= ux; dy[j] -= uy; dz[j] -= uz
            }
        }

        for (e in eu.indices) {
            val i = eu[e]
            val j = ev[e]
            val ax = l.x[i] - l.x[j]
            val ay = l.y[i] - l.y[j]
            val az = l.z[i] - l.z[j]
            val d = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(1e-4f)
            val f = (d * d) / k
            val ux = ax / d * f
            val uy = ay / d * f
            val uz = az / d * f
            dx[i] -= ux; dy[i] -= uy; dz[i] -= uz
            dx[j] += ux; dy[j] += uy; dz[j] += uz
        }

        val temp = 0.1f * (1f - iter.toFloat() / iterations)
        for (i in 0 until n) {
            val d = sqrt(dx[i] * dx[i] + dy[i] * dy[i] + dz[i] * dz[i]).coerceAtLeast(1e-6f)
            val step = minOf(d, temp) / d
            l.x[i] += dx[i] * step
            l.y[i] += dy[i] * step
            l.z[i] += dz[i] * step
            // Gentle pull to the origin keeps sparse limbs from drifting off screen.
            l.x[i] *= 0.995f
            l.y[i] *= 0.995f
            l.z[i] *= 0.995f
        }
    }

    // Normalise into a unit ball so the camera never has to be retuned per graph.
    //
    // The radius to divide by is a high *percentile*, not the maximum, and that distinction is the
    // whole difference between a picture and a smudge. A real person graph has disconnected
    // components - a stray Turn, an orphan Intervention - and Fruchterman-Reingold has nothing
    // attracting a separate component back to the mass, so repulsion carries it out until the
    // origin pull balances. Dividing by the maximum then hands the entire scale to that one
    // outlier: it lands on the rim and everything that matters is squeezed into a clump in the
    // middle, which is exactly what p01 looked like.
    //
    // So the bulk sets the scale, and anything past the ball is squashed asymptotically towards
    // 1.25 instead of being clipped. Squashing rather than clamping keeps a far component
    // readable as *far* rather than stacking every outlier on one shell.
    // Recentre first. The origin pull keeps the mass *near* zero, not *on* it, and after the
    // squash below a drift of a tenth of a radius is a graph visibly hanging off one corner of its
    // viewport. Centroid rather than bounding box, so a far component shifts the frame a little
    // instead of dragging it all the way out.
    var mx = 0f
    var my = 0f
    var mz = 0f
    for (i in 0 until n) {
        mx += l.x[i]
        my += l.y[i]
        mz += l.z[i]
    }
    mx /= n
    my /= n
    mz /= n
    for (i in 0 until n) {
        l.x[i] -= mx
        l.y[i] -= my
        l.z[i] -= mz
    }

    val radii = FloatArray(n) { i ->
        sqrt(l.x[i] * l.x[i] + l.y[i] * l.y[i] + l.z[i] * l.z[i])
    }
    val pivot = radii.sorted()[((n - 1) * 0.88f).toInt()].coerceAtLeast(1e-5f)
    for (i in 0 until n) {
        l.x[i] /= pivot
        l.y[i] /= pivot
        l.z[i] /= pivot
        val r = sqrt(l.x[i] * l.x[i] + l.y[i] * l.y[i] + l.z[i] * l.z[i])
        if (r > 1f) {
            val squashed = 1f + (1f - kotlin.math.exp(-(r - 1f))) * 0.25f
            val f = squashed / r
            l.x[i] *= f
            l.y[i] *= f
            l.z[i] *= f
        }
    }
    return l
}

/**
 * Draws [data] as an orbiting 3D graph.
 *
 * Drag to turn it; it drifts on its own otherwise. Tapping a node pins its caption.
 */
@Composable
fun GraphView(
    data: GraphData,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val colors = HomeOs.colors
    val reduced = HomeOs.reducedMotion
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    var solved by remember(data) { mutableStateOf<Layout?>(null) }
    LaunchedEffect(data) {
        solved = withContext(Dispatchers.Default) { layout(data) }
    }

    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(-0.25f) }
    var selected by remember(data) { mutableStateOf<Int?>(null) }

    // Slow drift, so a graph nobody is touching still reads as a three-dimensional object.
    val spin = rememberInfiniteTransition(label = "graphSpin")
    val drift by if (reduced) {
        remember { mutableFloatStateOf(0f) }
    } else {
        spin.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(64_000, easing = LinearEasing), RepeatMode.Restart),
            label = "drift",
        )
    }

    val nodeColors = remember(data, colors) {
        data.nodes.map { labelColor(it.label, colors) }
    }
    // Edge endpoints resolved to indices once. Looking them up by id inside the draw loop would be
    // O(edges x nodes) every frame -- about 290k string compares per frame on p01's graph.
    val edgeIndex = remember(data) {
        val index = HashMap<String, Int>(data.nodes.size * 2)
        data.nodes.forEachIndexed { i, node -> index[node.id] = i }
        val from = IntArray(data.edges.size)
        val to = IntArray(data.edges.size)
        data.edges.forEachIndexed { i, e ->
            from[i] = index[e.source] ?: 0
            to[i] = index[e.target] ?: 0
        }
        from to to
    }
    val captionStyle = remember(colors) {
        TextStyle(fontSize = 9.sp, color = colors.onShell)
    }

    Box(
        modifier
            .pointerInput(data) {
                detectDragGestures { _, drag ->
                    yaw += drag.x / 220f
                    pitch = (pitch - drag.y / 260f).coerceIn(-1.3f, 1.3f)
                }
            }
            .pointerInput(data, solved) {
                detectTapGestures { tap ->
                    val l = solved ?: return@detectTapGestures
                    // Must include the drift, or taps miss by however far the graph has turned.
                    selected = nearestNode(
                        l, data, tap,
                        size.width.toFloat(), size.height.toFloat(),
                        yaw + drift, pitch,
                    )
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val l = solved ?: return@Canvas
            val n = data.nodes.size
            if (n == 0) return@Canvas

            val totalYaw = yaw + drift
            val cy = cos(totalYaw)
            val sy = sin(totalYaw)
            val cp = cos(pitch)
            val sp = sin(pitch)

            val cx = size.width / 2f
            val cyc = size.height / 2f
            val scale = minOf(size.width, size.height) * PROJECTION
            val focal = FOCAL

            // A vignette turns the panel from a black hole into a lit viewport, and it darkens the
            // corners where far nodes land, which reinforces the depth cue rather than fighting it.
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0.00f to colors.teal.copy(alpha = 0.10f),
                    0.45f to colors.violet.copy(alpha = 0.045f),
                    1.00f to Color.Transparent,
                    center = Offset(cx, cyc * 0.92f),
                    radius = maxOf(size.width, size.height) * 0.70f,
                ),
            )

            // Project once per frame, then reuse for edges, nodes and labels.
            val px = FloatArray(n)
            val py = FloatArray(n)
            val pz = FloatArray(n)
            val pr = FloatArray(n)
            for (i in 0 until n) {
                val x0 = l.x[i]
                val y0 = l.y[i]
                val z0 = l.z[i]
                val x1 = x0 * cy + z0 * sy
                val z1 = -x0 * sy + z0 * cy
                val y1 = y0 * cp - z1 * sp
                val z2 = y0 * sp + z1 * cp
                val persp = focal / (focal + z2)
                px[i] = cx + x1 * scale * persp
                py[i] = cyc + y1 * scale * persp
                pz[i] = z2
                pr[i] = persp
            }

            // Depth order: far first, so near nodes genuinely occlude.
            val order = (0 until n).sortedByDescending { pz[it] }

            val (edgeFrom, edgeTo) = edgeIndex

            // How much ink one edge is worth depends on how many there are. The same alpha that
            // draws a 100-edge home graph as a legible lattice draws a 900-edge body graph as a
            // wash, and the structure disappears under its own density.
            val edgeInk = (260f / edgeFrom.size.coerceAtLeast(1)).coerceIn(0.40f, 1f)
            val nodeScale = (90f / n).coerceIn(0.62f, 1f)

            for (e in edgeFrom.indices) {
                val i = edgeFrom[e]
                val j = edgeTo[e]
                val depth = ((pr[i] + pr[j]) / 2f).coerceIn(0.4f, 1.6f)
                val alpha = ((depth - 0.4f) / 1.2f).coerceIn(0.06f, 0.55f)
                drawLine(
                    color = lerp(nodeColors[i], nodeColors[j], 0.5f)
                        .copy(alpha = alpha * 0.85f * edgeInk),
                    start = Offset(px[i], py[i]),
                    end = Offset(px[j], py[j]),
                    strokeWidth = (0.85f * depth).dp.toPx(),
                )
            }

            order.forEach { i ->
                val depth = pr[i].coerceIn(0.4f, 1.6f)
                val base = (3.2f + (l.degree[i].coerceAtMost(12)) * 0.42f) * nodeScale
                val radius = (base * depth).dp.toPx()
                val color = nodeColors[i]
                val near = ((depth - 0.4f) / 1.2f).coerceIn(0f, 1f)
                val isSelected = selected == i

                // Glow, body, rim. Three flat draws rather than a gradient brush per node, which
                // at 220 nodes would mean 220 brush allocations every frame.
                drawCircle(color.copy(alpha = 0.10f + 0.12f * near), radius * 2.3f, Offset(px[i], py[i]))
                drawCircle(color.copy(alpha = 0.35f + 0.6f * near), radius, Offset(px[i], py[i]))
                drawCircle(
                    color = colors.shellSunk.copy(alpha = 0.55f * near),
                    radius = radius * 0.42f,
                    center = Offset(px[i], py[i]),
                )
                if (isSelected) {
                    drawCircle(
                        color = colors.onShell,
                        radius = radius * 1.9f,
                        center = Offset(px[i], py[i]),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f.dp.toPx()),
                    )
                }
            }

            if (showLabels) {
                // Only the nearest, best-connected nodes get a caption, and only where the caption
                // does not collide with one already placed. Labelling everything turns the picture
                // into a word cloud and destroys the structure that is the entire point of drawing
                // it; overlapping labels do the same thing more slowly.
                val placed = ArrayList<FloatArray>(10)
                fun free(x: Float, y: Float, w: Float, h: Float): Boolean {
                    placed.forEach { r ->
                        if (x < r[2] && r[0] < x + w && y < r[3] && r[1] < y + h) return false
                    }
                    return true
                }

                // One caption per distinct word. Most of p01 is sleep episodes, so without this
                // the nearest fourteen nodes label themselves "sleep" eleven times, which tells a
                // reader nothing they could not already see from the colour and costs the room
                // where a Band or an Insight could have named itself.
                val spoken = HashSet<String>()

                order.asReversed()
                    .filter { pr[it] > 0.98f && l.degree[it] >= 2 }
                    .take(40)
                    .forEach { i ->
                        if (placed.size >= 12) return@forEach
                        if (!spoken.add(data.nodes[i].caption)) return@forEach
                        val measured = measurer.measure(data.nodes[i].caption, captionStyle)
                        val w = measured.size.width.toFloat()
                        val h = measured.size.height.toFloat()
                        val radius = (3.2f + l.degree[i].coerceAtMost(12) * 0.42f) * nodeScale * pr[i]
                        val ox = px[i] + radius.dp.toPx() + 6f
                        val oy = py[i] - h / 2f
                        if (ox + w > size.width - 6f || oy < 4f || oy + h > size.height - 28f) return@forEach
                        if (!free(ox - 3f, oy - 2f, w + 6f, h + 4f)) return@forEach
                        placed.add(floatArrayOf(ox - 3f, oy - 2f, ox + w + 3f, oy + h + 2f))
                        drawText(
                            textLayoutResult = measured,
                            color = colors.onShell.copy(alpha = 0.90f),
                            topLeft = Offset(ox, oy),
                        )
                    }

                selected?.let { i ->
                    val node = data.nodes[i]
                    val measured = measurer.measure("${node.label} · ${node.caption}", captionStyle)
                    val w = measured.size.width.toFloat()
                    val ox = (px[i] + 14f).coerceAtMost(size.width - w - 8f)
                    val oy = (py[i] + 12f).coerceAtMost(size.height - 34f)
                    drawRoundRect(
                        color = colors.shellSunk.copy(alpha = 0.88f),
                        topLeft = Offset(ox - 6f, oy - 4f),
                        size = androidx.compose.ui.geometry.Size(w + 12f, measured.size.height + 8f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                    )
                    drawText(
                        textLayoutResult = measured,
                        color = colors.onShell,
                        topLeft = Offset(ox, oy),
                    )
                }
            }
        }
    }
}

/** Screen-space hit test against the projected positions. */
private fun nearestNode(
    l: Layout,
    data: GraphData,
    tap: Offset,
    width: Float,
    height: Float,
    yaw: Float,
    pitch: Float,
): Int? {
    val n = data.nodes.size
    if (n == 0) return null
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    val cx = width / 2f
    val cyc = height / 2f
    val scale = minOf(width, height) * PROJECTION
    val focal = FOCAL
    var best = -1
    var bestD = Float.MAX_VALUE
    for (i in 0 until n) {
        val x1 = l.x[i] * cy + l.z[i] * sy
        val z1 = -l.x[i] * sy + l.z[i] * cy
        val y1 = l.y[i] * cp - z1 * sp
        val z2 = l.y[i] * sp + z1 * cp
        val persp = focal / (focal + z2)
        val sx = cx + x1 * scale * persp
        val sYy = cyc + y1 * scale * persp
        val d = abs(sx - tap.x) + abs(sYy - tap.y)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return if (bestD < 48f) best else null
}

