package com.homeos.bridge.ui.room

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.Device
import com.homeos.bridge.theme.HomeOs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The home, as a live 3D model.
 *
 * Built by hand on a Compose `Canvas`, with no 3D dependency, as the sibling of `GraphView` — same
 * projection shape, same painter's-algorithm depth sort, same rule that the renderer and the hit
 * test share one `project()` rather than two copies of the same constants.
 *
 * The measured budget says this is not close to tight: a sweep on this device held 60fps with zero
 * missed vsyncs at 3000 quads, and this scene draws roughly 450. The things that *are* tight are
 * allocation and invalidation, so the draw lambda allocates nothing and the frame counter is read
 * only inside it, and the clock parks entirely once the camera and every lamp have settled.
 */
@Composable
fun RoomView(
    devices: List<Device>,
    modifier: Modifier = Modifier,
    onSelect: (Anchor?) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = HomeOs.colors
    val reduced = HomeOs.reducedMotion

    var scene by remember { mutableStateOf<Scene?>(null) }
    LaunchedEffect(Unit) {
        scene = withContext(Dispatchers.Default) {
            val plan = RoomPlanReader.fromAssets(context)
            Scene(plan, buildMesh(plan))
        }
    }

    val ready = scene
    if (ready == null) {
        Box(modifier)
        return
    }

    // A finger's width, for the forgiving half of the hit test.
    val touchSlop = with(LocalDensity.current) { 26.dp.toPx() }
    val sim = remember(ready) { RoomSim(ready) }
    val scratch = remember(ready) { Scratch(ready.mesh.count) }
    var frame by remember(ready) { mutableIntStateOf(0) }
    var selected by remember(ready) { mutableStateOf(-1) }

    // Live device state only touches goals; the integrator does the rest, so a websocket event
    // makes the room brighten over about a third of a second instead of snapping.
    LaunchedEffect(devices, ready) { sim.aim(devices) }

    LaunchedEffect(ready, reduced) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                sim.advance(dt, reduced)
                frame++
            }
            // Park. This lives inside a scrolling list and a still room must cost nothing.
            if (sim.settled) {
                sim.awaitChange()
                last = 0L
            }
        }
    }

    Canvas(
        modifier
            .pointerInput(ready) {
                detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                    sim.camera.drag(pan.x, pan.y)
                    if (zoom != 1f) sim.camera.pinch(zoom)
                    sim.wake()
                }
            }
            .pointerInput(ready) {
                detectTapGestures { tap ->
                    val hit = scratch.pick(tap.x, tap.y, ready.mesh, touchSlop)
                    selected = hit
                    if (hit >= 0) {
                        val a = ready.mesh.anchors[hit]
                        sim.camera.focus(a.x, a.y, a.z, 0.66f)
                        onSelect(a)
                    } else {
                        sim.camera.reset()
                        onSelect(null)
                    }
                    sim.wake()
                }
            },
    ) {
        // Read inside the draw lambda so Compose attributes the invalidation to the draw node and
        // nothing above it recomposes. Reading it during composition is the mistake that turns a
        // 1.7ms draw into a 60Hz recomposition of the whole screen.
        @Suppress("UNUSED_EXPRESSION")
        frame

        drawScene(ready, sim, scratch, selected, colors.shell)
    }
}

private class Scene(val plan: RoomPlan, val mesh: RoomMesh)

// ------------------------------------------------------------------------------- the simulation

/**
 * Everything that changes between frames, deliberately *not* snapshot state.
 *
 * These are written sixty times a second while the camera eases and the lamps fade; routing them
 * through Compose state would recompose the subtree to move a float the draw lambda could read
 * directly.
 */
private class RoomSim(scene: Scene) {
    val camera = RoomCameraState(scene.plan.azimuth, scene.plan.elevation)
    private val anchors = scene.mesh.anchors

    val level = FloatArray(anchors.size)
    val goal = FloatArray(anchors.size)
    val tint = IntArray(anchors.size) { WARM_WHITE }

    private var awake = true

    val settled: Boolean
        get() = !awake && camera.settled && level.indices.all { abs(level[it] - goal[it]) < 0.002f }

    fun wake() {
        awake = true
    }

    /** Maps the backend's devices onto the fixtures that claim them. */
    fun aim(devices: List<Device>) {
        val byId = devices.associateBy { it.id }
        for (i in anchors.indices) {
            val a = anchors[i]
            val device = a.bind?.let { byId[it] }
            goal[i] = when {
                device == null -> 0f
                a.kind == AnchorKind.Light -> if (device.isOn) {
                    if (a.dimmable) ((device.brightness ?: 100) / 100f).coerceIn(0f, 1f) else 1f
                } else {
                    0f
                }
                a.kind == AnchorKind.Fan -> ((device.level ?: 0) / 3f).coerceIn(0f, 1f)
                a.kind == AnchorKind.Ac -> if (device.isOn) 1f else 0f
                a.kind == AnchorKind.Sensor -> 0f
                else -> 0f
            }
            a.authoredColor?.let { tint[i] = it }
        }
        awake = true
    }

    /** house-control's integrator: rises at 7, falls at 5, frame-rate independent. */
    fun advance(dt: Float, reduced: Boolean) {
        camera.ease(dt, instant = reduced)
        for (i in level.indices) {
            val g = goal[i]
            if (abs(level[i] - g) < 0.002f) {
                level[i] = g
                continue
            }
            val rate = if (g > level[i]) 7f else 5f
            val k = if (reduced) 1f else 1f - exp(-dt * rate)
            level[i] += (g - level[i]) * k
        }
        awake = false
    }

    suspend fun awaitChange() {
        // Suspends until something sets `awake` again. One frame of latency on wake is invisible
        // and it is what makes a still room cost literally nothing.
        while (!awake) kotlinx.coroutines.delay(60)
    }
}

private const val WARM_WHITE = 0xFFFFBD7A.toInt()

// ----------------------------------------------------------------------------------- the frame

/** Per-frame scratch, allocated once. Nothing in the draw lambda may allocate. */
private class Scratch(count: Int) {
    val px = FloatArray(count * 4)
    val py = FloatArray(count * 4)
    val depth = FloatArray(count)
    val visible = BooleanArray(count)
    val order = IntArray(count)
    var sorted = 0
    val projector = RoomProjector()
    val point = FloatArray(2)
    val path = Path()
    val faceDot = FloatArray(5)

    /** Screen-space positions of the fixtures, for glows and for the hit test. */
    val anchorX = FloatArray(64)
    val anchorY = FloatArray(64)
    val anchorPersp = FloatArray(64)
    var anchorCount = 0

    /**
     * What is under the finger, exactly.
     *
     * The first version measured distance to each fixture's projected centre point, which is only
     * right if every device is a dot. Tapping the television — a box half a metre wide on screen —
     * usually missed, because its anchor sits at the centre of its footprint and the thing you
     * aimed at was its screen. So this walks the drawn quads from nearest to furthest and returns
     * the first one the point actually falls inside: you select what you can see.
     *
     * The polygons are convex and already projected for this frame, so it is four cross products
     * per candidate and it stops at the first hit.
     */
    fun pick(x: Float, y: Float, mesh: RoomMesh, radiusPx: Float): Int {
        // Exact first: walk the drawn surfaces from nearest to furthest and take the first one the
        // point actually falls inside, so tapping a television selects the television.
        for (i in sorted - 1 downTo 0) {
            val q = order[i]
            if (!visible[q]) continue
            val anchor = mesh.anchorOf[q]
            if (anchor < 0) continue
            if (contains(q, x, y)) return anchor
        }
        // Then forgiving, because exactness alone makes most of the room unusable: a bedside lamp
        // is about five pixels across at this scale, and nobody can hit that. If the finger landed
        // near a fixture without landing on it, take the fixture.
        var best = -1
        var bestD = radiusPx * radiusPx
        for (i in 0 until min(anchorCount, mesh.anchors.size)) {
            val dx = anchorX[i] - x
            val dy = anchorY[i] - y
            val d = dx * dx + dy * dy
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun contains(q: Int, x: Float, y: Float): Boolean {
        val i = q * 4
        var sign = 0
        for (k in 0 until 4) {
            val ax = px[i + k]
            val ay = py[i + k]
            val n = i + (k + 1) % 4
            val cross = (px[n] - ax) * (y - ay) - (py[n] - ay) * (x - ax)
            if (cross > 0f) {
                if (sign < 0) return false
                sign = 1
            } else if (cross < 0f) {
                if (sign > 0) return false
                sign = -1
            }
        }
        return true
    }
}

private fun DrawScope.drawScene(
    scene: Scene,
    sim: RoomSim,
    s: Scratch,
    selected: Int,
    fog: Color,
) {
    val mesh = scene.mesh
    val n = mesh.count
    if (n == 0) return

    s.projector.set(sim.camera, size.width, size.height)
    val p = s.projector

    // Six normals in the whole scene, so the visibility test is a table lookup rather than a dot
    // product per quad. This is the one trick that makes flat-shaded axis-aligned geometry cheap.
    s.faceDot[FACE_PX] = p.toCameraX
    s.faceDot[FACE_NX] = -p.toCameraX
    s.faceDot[FACE_PY] = p.toCameraY
    s.faceDot[FACE_PZ] = p.toCameraZ
    s.faceDot[FACE_NZ] = -p.toCameraZ

    // Project, cull, and build the sort key in one pass.
    s.sorted = 0
    for (q in 0 until n) {
        if (s.faceDot[mesh.face[q].toInt()] <= 0f) {
            s.visible[q] = false
            continue
        }
        s.visible[q] = true
        var maxDepth = -Float.MAX_VALUE
        for (k in 0 until 4) {
            val i = q * 4 + k
            val d = p.project(mesh.vx[i], mesh.vy[i], mesh.vz[i], s.point)
            s.px[i] = s.point[0]
            s.py[i] = s.point[1]
            if (d > maxDepth) maxDepth = d
        }
        // One key for every sorted quad: the depth of its furthest corner.
        //
        // This started out mixed - max-corner for walls, centroid for everything else - on the
        // reasoning that a wall face is coplanar with a separating plane and its furthest corner
        // is the exact half-space answer, which is true. What it misses is that two quads compared
        // on *different* keys are not comparable at all, and the result was furniture and wall
        // caps swapping places along every room boundary. A consistent key that is slightly wrong
        // in rare cases beats an exact key that is only exact against half the scene.
        s.depth[q] = maxDepth
        if (mesh.kind[q] != KIND_FLOOR) {
            s.order[s.sorted++] = q
        }
    }

    // Nearly sorted frame to frame, so insertion sort is a few hundred compares; and it is stable,
    // which is what lets equal keys act as a group with a fixed internal paint order.
    insertionSortDescending(s.order, s.depth, s.sorted)

    // 1. floors, unsorted and first: nothing is ever beneath them.
    for (q in 0 until n) {
        if (!s.visible[q] || mesh.kind[q] != KIND_FLOOR) continue
        fillQuad(s, q, Color(mesh.color[q]))
    }

    // 2. the pools of light the lamps throw on the floor.
    drawPools(scene, sim, s)

    // 3. everything solid, far to near.
    for (i in 0 until s.sorted) {
        val q = s.order[i]
        if (!s.visible[q]) continue
        val lit = irradiance(scene, sim, q)
        var c = Color(mesh.color[q])
        if (lit > 0.004f) {
            val anchor = nearestLamp(scene, sim, q)
            if (anchor >= 0) c = lerpColor(c, Color(sim.tint[anchor]), lit)
        }
        // Depth fog, so far geometry recedes into the shell rather than into grey.
        val f = ((s.depth[q] + 1.2f) / 3.2f).coerceIn(0f, 1f) * 0.35f
        fillQuad(s, q, lerpColor(c, fog, f))
    }

    // 4. the fixtures themselves, additive, nearest last.
    drawGlows(scene, sim, s, selected)
}

private fun DrawScope.fillQuad(s: Scratch, q: Int, color: Color) {
    val i = q * 4
    s.path.rewind()
    s.path.moveTo(s.px[i], s.py[i])
    s.path.lineTo(s.px[i + 1], s.py[i + 1])
    s.path.lineTo(s.px[i + 2], s.py[i + 2])
    s.path.lineTo(s.px[i + 3], s.py[i + 3])
    s.path.close()
    drawPath(s.path, color)
}

/**
 * How much lamp light lands on a quad.
 *
 * Inverse-square from each lit fixture through a soft knee, so two lamps overlapping brighten a
 * surface without ever blowing it out. Cheap enough to run per frame: a few hundred quads against
 * at most a handful of lit fixtures.
 */
private fun irradiance(scene: Scene, sim: RoomSim, q: Int): Float {
    val mesh = scene.mesh
    val n = faceNormalOf(mesh.face[q].toInt())
    var e = 0f
    for (i in sim.level.indices) {
        val lv = sim.level[i]
        if (lv < 0.01f) continue
        val a = mesh.anchors[i]
        if (a.kind != AnchorKind.Light) continue
        val dx = (a.x - mesh.cx[q]) * mesh.sceneRadius
        val dy = (a.y - mesh.cy[q]) * mesh.sceneRadius
        val dz = (a.z - mesh.cz[q]) * mesh.sceneRadius
        val d2 = max(dx * dx + dy * dy + dz * dz, 0.1225f)
        val d = sqrt(d2)
        val ndotl = max(0f, (dx / d) * n[0] + (dy / d) * n[1] + (dz / d) * n[2])
        if (ndotl <= 0f) continue
        e += LAMP_INTENSITY * lv.pow(1.5f) / d2 * ndotl
    }
    return (e / (e + 2.2f)) * 0.85f
}

private fun nearestLamp(scene: Scene, sim: RoomSim, q: Int): Int {
    val mesh = scene.mesh
    var best = -1
    var bestD = Float.MAX_VALUE
    for (i in sim.level.indices) {
        if (sim.level[i] < 0.01f) continue
        if (mesh.anchors[i].kind != AnchorKind.Light) continue
        val a = mesh.anchors[i]
        val dx = a.x - mesh.cx[q]
        val dy = a.y - mesh.cy[q]
        val dz = a.z - mesh.cz[q]
        val d = dx * dx + dy * dy + dz * dz
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

private const val LAMP_INTENSITY = 9f

/**
 * The pool a lamp throws on the floor.
 *
 * A circle lying on the floor projects to an ellipse squashed by exactly `abs(sin(pitch))` — which
 * was measured rather than assumed, to within 0.4% across the whole elevation range. So this is a
 * circle inside a y-scale, which is exact and costs one draw, rather than projected geometry.
 */
private fun DrawScope.drawPools(scene: Scene, sim: RoomSim, s: Scratch) {
    val mesh = scene.mesh
    val p = s.projector
    val squash = p.floorForeshorten()

    for (i in sim.level.indices) {
        val lv = sim.level[i]
        if (lv < 0.02f) continue
        val a = mesh.anchors[i]
        if (a.kind != AnchorKind.Light) continue
        val room = mesh.rooms.firstOrNull { it.id == a.room } ?: continue

        val persp = p.project(a.x, mesh.ny(0f), a.z, s.point)
        val cx = s.point[0]
        val cy = s.point[1]
        if (persp.isNaN()) continue

        val radiusModel = (1.1f + 1.6f * sqrt(lv)) / mesh.sceneRadius
        p.project(a.x + radiusModel, mesh.ny(0f), a.z, s.point)
        val radiusPx = abs(s.point[0] - cx)
        if (radiusPx < 1f) continue

        // Clipped to its own room, so a lamp never spills light through a wall.
        s.path.rewind()
        floorCorner(p, s, mesh, room.x, room.z, true)
        floorCorner(p, s, mesh, room.x1, room.z, false)
        floorCorner(p, s, mesh, room.x1, room.z1, false)
        floorCorner(p, s, mesh, room.x, room.z1, false)
        s.path.close()

        // A real falloff, not a disc. This is the one place a gradient brush is worth building per
        // frame: `GraphView`'s rule against them is about *per node* at two hundred nodes, and
        // there are at most a handful of lit fixtures here. A flat circle reads as a sticker.
        val c = Color(sim.tint[i])
        val e = lv.pow(1.5f)
        val pool = Brush.radialGradient(
            0.00f to c.copy(alpha = 0.40f * e),
            0.42f to c.copy(alpha = 0.17f * e),
            0.74f to c.copy(alpha = 0.05f * e),
            1.00f to Color.Transparent,
            center = Offset(cx, cy),
            radius = radiusPx,
        )
        clipPath(s.path) {
            withTransform({ scale(1f, squash, pivot = Offset(cx, cy)) }) {
                drawCircle(
                    brush = pool,
                    radius = radiusPx,
                    center = Offset(cx, cy),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}

private fun floorCorner(p: RoomProjector, s: Scratch, mesh: RoomMesh, x: Float, z: Float, first: Boolean) {
    p.project(mesh.nx(x), mesh.ny(0f), mesh.nz(z), s.point)
    if (first) s.path.moveTo(s.point[0], s.point[1]) else s.path.lineTo(s.point[0], s.point[1])
}

/**
 * Fixture halos, and the ring on whatever is selected.
 *
 * Three flat circles rather than a gradient brush, following the rule `GraphView` already states:
 * a brush per fixture is a brush allocation per fixture per frame.
 */
private fun DrawScope.drawGlows(scene: Scene, sim: RoomSim, s: Scratch, selected: Int) {
    val mesh = scene.mesh
    val p = s.projector
    s.anchorCount = min(mesh.anchors.size, s.anchorX.size)

    for (i in 0 until s.anchorCount) {
        val a = mesh.anchors[i]
        val persp = p.project(a.x, a.y, a.z, s.point)
        s.anchorX[i] = s.point[0]
        s.anchorY[i] = s.point[1]
        s.anchorPersp[i] = persp

        val lv = sim.level[i]
        if (a.kind == AnchorKind.Light && lv > 0.02f) {
            val c = Color(sim.tint[i])
            val r = (14f + 10f * lv) * (RoomCameraDefaults.FOCAL / (RoomCameraDefaults.FOCAL + persp))
            val e = lv.pow(1.5f)
            drawCircle(c.copy(alpha = 0.55f * e), r, Offset(s.point[0], s.point[1]), blendMode = BlendMode.Plus)
            drawCircle(c.copy(alpha = 0.22f * e), r * 1.9f, Offset(s.point[0], s.point[1]), blendMode = BlendMode.Plus)
            drawCircle(c.copy(alpha = 0.09f * e), r * 3.4f, Offset(s.point[0], s.point[1]), blendMode = BlendMode.Plus)
        }

        if (i == selected) {
            drawCircle(
                color = Color(0xFF60D3CD),
                radius = 22f,
                center = Offset(s.point[0], s.point[1]),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
        }
    }
}

// --------------------------------------------------------------------------------------- utils

private val NORMALS = arrayOf(
    floatArrayOf(1f, 0f, 0f),
    floatArrayOf(-1f, 0f, 0f),
    floatArrayOf(0f, 1f, 0f),
    floatArrayOf(0f, 0f, 1f),
    floatArrayOf(0f, 0f, -1f),
)

private fun faceNormalOf(face: Int) = NORMALS[face]

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/** Descending, in place, stable. Both properties are load-bearing; see the call site. */
private fun insertionSortDescending(order: IntArray, key: FloatArray, count: Int) {
    for (i in 1 until count) {
        val v = order[i]
        val k = key[v]
        var j = i - 1
        while (j >= 0 && key[order[j]] < k) {
            order[j + 1] = order[j]
            j--
        }
        order[j + 1] = v
    }
}
