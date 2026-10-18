package com.homeos.bridge.ui.room

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Face indices. There is no -y: the camera is clamped above the horizon, so the underside of a box
// can never be seen and building it would be two more quads to cull every frame, forever.
const val FACE_PX = 0
const val FACE_NX = 1
const val FACE_PY = 2
const val FACE_PZ = 3
const val FACE_NZ = 4

// Draw kinds. The sort key differs between WALL and BODY, and FLOOR leaves the sort entirely.
const val KIND_FLOOR: Byte = 0
const val KIND_WALL: Byte = 1
const val KIND_BODY: Byte = 2
const val KIND_EMISSIVE: Byte = 3

/** What a fixture is, for the glow pass, the hit test and the control sheet. */
enum class AnchorKind { Light, Ac, Fan, Sensor, Appliance }

/**
 * A device in the model: where it is, what it is, and whether HomeOS actually drives it.
 *
 * Positions are scene-normalised, like everything else, so the hit test and the glow can project
 * them with the same [RoomProjector] the geometry uses.
 */
data class Anchor(
    val id: String,
    val name: String,
    val room: String,
    val kind: AnchorKind,
    val x: Float,
    val y: Float,
    val z: Float,
    /** The backend device id, or null when this fixture is scenery. */
    val bind: String?,
    val dimmable: Boolean,
    val authoredColor: Int?,
)

/**
 * The scene, as flat arrays.
 *
 * Parallel primitive arrays rather than a list of objects, because the per-frame loop walks all of
 * this and an object graph would mean several hundred pointer chases and a lot of cache misses to
 * do arithmetic that is otherwise trivial. Built once, off the main thread.
 */
class RoomMesh(
    val count: Int,
    val vx: FloatArray,
    val vy: FloatArray,
    val vz: FloatArray,
    val face: ByteArray,
    val color: IntArray,
    val kind: ByteArray,
    val cx: FloatArray,
    val cy: FloatArray,
    val cz: FloatArray,
    /** Index into [anchors], or -1. Lets a quad light up with its fixture. */
    val anchorOf: IntArray,
    val anchors: List<Anchor>,
    val rooms: List<PlanRoom>,
    /** World metres per normalised unit; the inverse of the build's scale factor. */
    val sceneRadius: Float,
    val centreX: Float,
    val centreZ: Float,
) {
    /** Normalises a world point into mesh space. */
    fun nx(x: Float) = (x - centreX) / sceneRadius
    fun ny(y: Float) = (y - RoomCameraDefaults.CENTRE_Y) / sceneRadius
    fun nz(z: Float) = (z - centreZ) / sceneRadius
}

// --------------------------------------------------------------------------- the lighting model

/**
 * One fixed key light and a hemisphere, exactly as house-control's rig.
 *
 * The key is fixed in **world** space, not relative to the camera. A camera-relative key makes
 * whichever face you are looking at always the lit one, which reads flat and video-gamey; a fixed
 * one means turning the room changes which wall is bright, which is most of what sells it as a
 * solid object.
 */
private val KEY_DIR = normalise(-0.60f, 0.72f, -0.34f)
private const val HEMI = 0.42f
private const val KEY_STRENGTH = 0.68f
private const val FILL = 0.13f

// Cooler than the material and only just: house-control runs its blue sky through ACES tone
// mapping and a bloom pass, which pulls the cast back. There is neither here, so the same
// #8ea6ff turned every warm paper wall a flat lavender. Desaturated, the ambient reads as
// night air and leaves the warm lamps somewhere to push against.
private const val SKY = 0xA6B2C8
private const val GROUND = 0x15171C
private const val MOON = 0xE4E0D6

private fun normalise(x: Float, y: Float, z: Float): FloatArray {
    val l = sqrt(x * x + y * y + z * z)
    return floatArrayOf(x / l, y / l, z / l)
}

private fun srgbToLinear(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
private fun linearToSrgb(c: Float) = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f

private fun faceNormal(face: Int): FloatArray = when (face) {
    FACE_PX -> floatArrayOf(1f, 0f, 0f)
    FACE_NX -> floatArrayOf(-1f, 0f, 0f)
    FACE_PY -> floatArrayOf(0f, 1f, 0f)
    FACE_PZ -> floatArrayOf(0f, 0f, 1f)
    else -> floatArrayOf(0f, 0f, -1f)
}

/**
 * The per-face light multiplier, in linear space, as `[r, g, b]`.
 *
 * Computed once for the five faces and then applied to every material at build time, which is the
 * ~84-entry lookup table the design calls for, materialised. Doing this per quad per frame would
 * mean thousands of `pow()` calls a frame to produce numbers that never change.
 */
private val FACE_LIGHT: Array<FloatArray> = Array(5) { face ->
    val n = faceNormal(face)
    val hemi = 0.5f + 0.5f * n[1]
    val ndotl = max(0f, n[0] * KEY_DIR[0] + n[1] * KEY_DIR[1] + n[2] * KEY_DIR[2])
    FloatArray(3) { ch ->
        val sky = srgbToLinear(((SKY shr (16 - ch * 8)) and 0xFF) / 255f)
        val ground = srgbToLinear(((GROUND shr (16 - ch * 8)) and 0xFF) / 255f)
        val moon = srgbToLinear(((MOON shr (16 - ch * 8)) and 0xFF) / 255f)
        (ground + (sky - ground) * hemi) * HEMI + moon * KEY_STRENGTH * ndotl + FILL
    }
}

/** Applies [FACE_LIGHT] to a material colour, in linear space, and comes back to sRGB. */
private fun shade(color: Int, face: Int, dim: Float): Int {
    val lit = FACE_LIGHT[face]
    var out = 0xFF000000.toInt()
    for (ch in 0 until 3) {
        val m = srgbToLinear(((color shr (16 - ch * 8)) and 0xFF) / 255f)
        val v = (linearToSrgb((m * lit[ch]).coerceIn(0f, 1f)) * dim).coerceIn(0f, 1f)
        out = out or ((v * 255f).roundToInt() shl (16 - ch * 8))
    }
    return out
}

// ------------------------------------------------------------------------------- the build pass

private class QuadDef(
    val x: FloatArray,
    val y: FloatArray,
    val z: FloatArray,
    val face: Int,
    val color: Int,
    val kind: Byte,
    val anchor: Int,
)

/**
 * Turns a plan into drawable geometry.
 *
 * Runs once, off the main thread. Nothing in here depends on where the camera currently is — the
 * only camera input is the **base** azimuth, which decides once and for all which exterior walls
 * are cut down so you can see inside.
 *
 * @param detailMax drops furniture parts above this rank. The budget knob: the measured ceiling is
 *   far above what this scene needs, so it stays at 2, but turning it down is the fastest way to
 *   trade detail for frames if that ever changes.
 */
fun buildMesh(plan: RoomPlan, detailMax: Int = 2): RoomMesh {
    val quads = ArrayList<QuadDef>(900)
    val anchors = ArrayList<Anchor>()

    // --- scene bounds, so the camera constants never have to be retuned for a different plan
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    for (r in plan.rooms) {
        minX = min(minX, r.x); maxX = max(maxX, r.x1)
        minZ = min(minZ, r.z); maxZ = max(maxZ, r.z1)
    }
    val centreX = (minX + maxX) / 2f
    val centreZ = (minZ + maxZ) / 2f
    // The plinth is 0.35m proud of the plan on every side, so the scene is wider than the rooms
    // are and the radius has to say so - otherwise the corners of the base hang off the viewport.
    val radius = sqrt(
        ((maxX - minX) / 2f + PLINTH_MARGIN).pow(2) + ((maxZ - minZ) / 2f + PLINTH_MARGIN).pow(2),
    ) + 0.15f

    fun nx(x: Float) = (x - centreX) / radius
    fun ny(y: Float) = (y - RoomCameraDefaults.CENTRE_Y) / radius
    fun nz(z: Float) = (z - centreZ) / radius

    fun quad(face: Int, color: Int, kind: Byte, anchor: Int, xs: FloatArray, ys: FloatArray, zs: FloatArray) {
        quads += QuadDef(xs, ys, zs, face, shade(color, face, 1f), kind, anchor)
    }

    /**
     * Five faces of an axis-aligned box, in world metres.
     *
     * @param topColor overrides the material on the `+y` face alone. Walls use it: the top of a
     *   wall is a cut through it, and a cut is not the same material as the surface.
     */
    fun box(b: Box, kind: Byte, anchor: Int = -1, dim: Float = 1f, topColor: Int = b.color) {
        val x0 = nx(b.x0); val x1 = nx(b.x1)
        val y0 = ny(b.y0); val y1 = ny(b.y1)
        val z0 = nz(b.z0); val z1 = nz(b.z1)
        fun add(face: Int, xs: FloatArray, ys: FloatArray, zs: FloatArray) {
            val c = if (face == FACE_PY) topColor else b.color
            quads += QuadDef(xs, ys, zs, face, shade(c, face, dim), kind, anchor)
        }
        add(FACE_PY, floatArrayOf(x0, x1, x1, x0), floatArrayOf(y1, y1, y1, y1), floatArrayOf(z0, z0, z1, z1))
        add(FACE_PX, floatArrayOf(x1, x1, x1, x1), floatArrayOf(y0, y0, y1, y1), floatArrayOf(z0, z1, z1, z0))
        add(FACE_NX, floatArrayOf(x0, x0, x0, x0), floatArrayOf(y0, y0, y1, y1), floatArrayOf(z1, z0, z0, z1))
        add(FACE_PZ, floatArrayOf(x0, x1, x1, x0), floatArrayOf(y0, y0, y1, y1), floatArrayOf(z1, z1, z1, z1))
        add(FACE_NZ, floatArrayOf(x1, x0, x0, x1), floatArrayOf(y0, y0, y1, y1), floatArrayOf(z0, z0, z0, z0))
    }

    // --- the plinth the model stands on
    //
    // Ported straight from house-control, and the single thing whose absence made this read as a
    // set of floating planes rather than an object: a slab 0.7m larger than the plan in both
    // directions, sitting just under the floor. A model needs something to be a model *of*.
    box(
        Box(
            minX - PLINTH_MARGIN, -0.305f, minZ - PLINTH_MARGIN,
            maxX + PLINTH_MARGIN, -0.005f, maxZ + PLINTH_MARGIN,
            PLINTH,
        ),
        // FLOOR, not BODY. Its top face sits 5mm under the room floors, so as a sorted solid it
        // paints over every one of them - the whole flat went black the first time. In the floor
        // layer it is drawn first, in build order, and the rooms land on top of it where they
        // belong.
        KIND_FLOOR,
    )

    // --- floors
    val rnd = Random(11)
    for (r in plan.rooms) {
        val base = floorColour(r.floor)
        val dim = if (r.connected) 1f else UNCONNECTED_DIM
        if (r.floor == FloorKind.Oak || r.floor == FloorKind.Walnut) {
            // Planks: thin quads, so the same covered area as one quad but with real grain
            // variation. The jitter is seeded, because a floor that reshuffles itself on every
            // rebuild is a bug you notice without being able to name.
            var z = r.z
            while (z < r.z1 - 1e-4f) {
                val z1 = min(z + PLANK, r.z1)
                val c = jitter(base, rnd, 0.09f)
                quad(
                    FACE_PY, mix(c, dim), KIND_FLOOR, -1,
                    floatArrayOf(nx(r.x), nx(r.x1), nx(r.x1), nx(r.x)),
                    floatArrayOf(ny(0f), ny(0f), ny(0f), ny(0f)),
                    floatArrayOf(nz(z), nz(z), nz(z1), nz(z1)),
                )
                z = z1
            }
        } else {
            // Tile and stone: one quad plus a handful of jittered patches. Taken literally as 4x4
            // and 3x3 tiles this room alone would be 224 quads, and they are the largest-area
            // quads on screen, so it is the one place where honest geometry is the wrong call.
            quad(
                FACE_PY, mix(base, dim), KIND_FLOOR, -1,
                floatArrayOf(nx(r.x), nx(r.x1), nx(r.x1), nx(r.x)),
                floatArrayOf(ny(0f), ny(0f), ny(0f), ny(0f)),
                floatArrayOf(nz(r.z), nz(r.z), nz(r.z1), nz(r.z1)),
            )
            repeat(7) {
                val tw = r.w / 4f
                val td = r.d / 3f
                val px = r.x + rnd.nextFloat() * (r.w - tw)
                val pz = r.z + rnd.nextFloat() * (r.d - td)
                quad(
                    FACE_PY, mix(jitter(base, rnd, 0.05f), dim), KIND_FLOOR, -1,
                    floatArrayOf(nx(px), nx(px + tw), nx(px + tw), nx(px)),
                    floatArrayOf(ny(0.001f), ny(0.001f), ny(0.001f), ny(0.001f)),
                    floatArrayOf(nz(pz), nz(pz), nz(pz + td), nz(pz + td)),
                )
            }
        }
    }

    // --- walls
    val az = Math.toRadians(plan.azimuth.toDouble()).toFloat()
    // The horizontal camera direction only. house-control uses the full 3D dot, which makes the
    // cut-away threshold depend on elevation: at 70° one of this plan's walls sits 14° of azimuth
    // from flipping back to full height during a vertical drag. Horizontal, and frozen at the base
    // azimuth, removes a pop and a rebuild hitch that are otherwise reachable by ordinary dragging.
    val camX = sin(az)
    val camZ = -cos(az)
    for (run in deriveWalls(plan)) {
        if (run.opening?.kind == OpeningKind.Door || run.opening?.kind == OpeningKind.Open) continue
        val curbed = !run.interior && (run.normalX * camX + run.normalZ * camZ) > 0.15f
        val height = when {
            curbed -> CURB_HEIGHT
            run.interior -> plan.interiorWallHeight
            else -> plan.wallHeight
        }
        val t = run.thickness / 2f
        val b = if (run.alongX) {
            Box(run.a, 0f, run.coord - t, run.b, height, run.coord + t, WALL_BODY)
        } else {
            Box(run.coord - t, 0f, run.a, run.coord + t, height, run.b, WALL_BODY)
        }
        // The top face takes the app's hardest line. `ink` is called "the hardest line on screen"
        // where it is defined, and a wall cut through the top is that line in three dimensions.
        box(b, KIND_WALL, topColor = WALL_CAP)
    }

    fun anchorIndex(a: Anchor): Int {
        anchors += a
        return anchors.size - 1
    }

    // --- furniture, in every room
    //
    // Unconnected rooms are furnished too, and only drained of colour. An empty shell next to a
    // furnished bedroom reads as unfinished; a furnished room with the life dimmed out of it reads
    // as what it is, which is a room HomeOS cannot see into.
    //
    // Appliance-shaped furniture gets an anchor even though no backend device claims it, so that
    // tapping the television offers to connect it rather than doing nothing. A model of a home you
    // cannot point at is a picture; one where every appliance is a thing you can bind is the
    // product.
    for (f in plan.furniture) {
        val room = plan.room(f.room) ?: continue
        val dim = if (room.connected) 1f else UNCONNECTED_DIM
        val anchor = applianceNameOf(f.piece)?.let { name ->
            anchorIndex(
                Anchor(
                    id = "${f.room}.${f.piece.name.lowercase()}",
                    name = name,
                    room = f.room,
                    kind = AnchorKind.Appliance,
                    x = nx(f.x), y = ny(0.9f), z = nz(f.z),
                    bind = null, dimmable = false, authoredColor = null,
                ),
            )
        } ?: -1
        for (b in boxesOf(f)) {
            if (b.detail > detailMax) continue
            box(b, KIND_BODY, anchor = anchor, dim = dim)
        }
    }

    // --- fixtures

    for (l in plan.lights) {
        val room = plan.room(l.room) ?: continue
        val dim = if (room.connected) 1f else UNCONNECTED_DIM
        val y = l.y ?: defaultLightHeight(l.kind, plan.wallHeight)
        val i = anchorIndex(
            Anchor(l.id, l.name, l.room, AnchorKind.Light, nx(l.x), ny(y), nz(l.z), l.bind, l.dimmable, l.color),
        )
        for ((b, emissive) in lightBoxes(l, y)) {
            box(b, if (emissive) KIND_EMISSIVE else KIND_BODY, i, dim)
        }
    }

    for (a in plan.acs) {
        val room = plan.room(a.room) ?: continue
        val dim = if (room.connected) 1f else UNCONNECTED_DIM
        val i = anchorIndex(
            Anchor(a.id, a.name, a.room, AnchorKind.Ac, nx(a.x), ny(a.y), nz(a.z), a.bind, false, null),
        )
        box(acBox(a, room), KIND_BODY, i, dim)
    }

    for (f in plan.fans) {
        val room = plan.room(f.room) ?: continue
        val dim = if (room.connected) 1f else UNCONNECTED_DIM
        val i = anchorIndex(
            Anchor(f.id, f.name, f.room, AnchorKind.Fan, nx(f.x), ny(f.y), nz(f.z), f.bind, false, null),
        )
        for (b in fanBoxes(f)) box(b, KIND_BODY, i, dim)
    }

    for (s in plan.sensors) {
        val room = plan.room(s.room) ?: continue
        val dim = if (room.connected) 1f else UNCONNECTED_DIM
        val i = anchorIndex(
            Anchor(s.id, s.name, s.room, AnchorKind.Sensor, nx(s.x), ny(s.y), nz(s.z), s.bind, false, null),
        )
        box(sensorBox(s, room), KIND_BODY, i, dim)
    }

    // --- flatten
    val n = quads.size
    val mesh = RoomMesh(
        count = n,
        vx = FloatArray(n * 4), vy = FloatArray(n * 4), vz = FloatArray(n * 4),
        face = ByteArray(n), color = IntArray(n), kind = ByteArray(n),
        cx = FloatArray(n), cy = FloatArray(n), cz = FloatArray(n),
        anchorOf = IntArray(n), anchors = anchors, rooms = plan.rooms,
        sceneRadius = radius, centreX = centreX, centreZ = centreZ,
    )
    for (q in quads.indices) {
        val d = quads[q]
        var sx = 0f; var sy = 0f; var sz = 0f
        for (k in 0 until 4) {
            mesh.vx[q * 4 + k] = d.x[k]; sx += d.x[k]
            mesh.vy[q * 4 + k] = d.y[k]; sy += d.y[k]
            mesh.vz[q * 4 + k] = d.z[k]; sz += d.z[k]
        }
        mesh.face[q] = d.face.toByte()
        mesh.color[q] = d.color
        mesh.kind[q] = d.kind
        mesh.anchorOf[q] = d.anchor
        mesh.cx[q] = sx / 4f; mesh.cy[q] = sy / 4f; mesh.cz[q] = sz / 4f
    }
    return mesh
}

// ----------------------------------------------------------------------------------- materials

private const val PLANK = 0.2f
private const val UNCONNECTED_DIM = 0.68f

/**
 * `paper` and `ink`, from the app's own palette: the walls of the room are literally the app's
 * paper and the cut through the top of them is literally its ink. That is the detail that makes
 * the renderer belong to this app rather than look pasted in from another one.
 *
 * Duplicated as literals because they are baked into a build-time shading LUT, which runs off the
 * main thread before any composition is in scope. **They have to move when `theme/Color.kt` moves**
 * — they were left behind once already, when the palette turned warm and the model kept a pair of
 * cool greys nobody could place.
 */
private const val WALL_BODY = 0xFFFFFBF5.toInt()
private const val WALL_CAP = 0xFF1E1813.toInt()

private const val FIXTURE_BODY = 0xFF8D8F96.toInt()

/** The slab the model stands on. */
private const val PLINTH = 0xFF141927.toInt()

/** How far the plinth stands proud of the plan on each side. */
private const val PLINTH_MARGIN = 0.35f

/**
 * Which furniture is appliance-shaped enough to offer connecting.
 *
 * A sofa is not a device and never will be, so it stays scenery. A television, a fridge and a
 * bathtub are things a home automates, and the catalogue in `Appliances.kt` already knows how.
 */
private fun applianceNameOf(piece: Piece): String? = when (piece) {
    Piece.Tv -> "Television"
    Piece.Fridge -> "Fridge"
    Piece.Kitchen -> "Kitchen"
    else -> null
}

private fun floorColour(kind: FloorKind) = when (kind) {
    FloorKind.Oak -> 0xFFC7A47C.toInt()
    FloorKind.Walnut -> 0xFF94705A.toInt()
    FloorKind.Tile -> 0xFFD5DADE.toInt()
    FloorKind.Stone -> 0xFFA2A7AE.toInt()
}

private fun jitter(color: Int, rnd: Random, amount: Float): Int =
    darker(color, 1f + (rnd.nextFloat() - 0.5f) * amount * 2f)

private fun mix(color: Int, dim: Float) = if (dim == 1f) color else darker(color, dim)

private fun defaultLightHeight(kind: LightKind, wallHeight: Float) = when (kind) {
    LightKind.Ceiling, LightKind.Spots -> wallHeight - 0.14f
    LightKind.Pendant -> wallHeight - 0.85f
    LightKind.Floor -> 1.46f
    LightKind.Table -> 0.50f
    LightKind.Strip -> 1.20f
}

/** Fixture geometry: a body, and the part that glows. `true` marks the emissive box. */
private fun lightBoxes(l: PlanLight, y: Float): List<Pair<Box, Boolean>> = when (l.kind) {
    LightKind.Ceiling -> listOf(
        Box(l.x - 0.17f, y, l.z - 0.17f, l.x + 0.17f, y + 0.05f, l.z + 0.17f, FIXTURE_BODY) to true,
    )
    LightKind.Pendant -> listOf(
        Box(l.x - 0.012f, y + 0.22f, l.z - 0.012f, l.x + 0.012f, y + 0.9f, l.z + 0.012f, 0xFF2A2C33.toInt()) to false,
        Box(l.x - 0.13f, y, l.z - 0.13f, l.x + 0.13f, y + 0.22f, l.z + 0.13f, FIXTURE_BODY) to true,
    )
    LightKind.Spots -> (0..2).map { i ->
        val dx = (i - 1) * 0.42f
        Box(l.x + dx - 0.06f, y, l.z - 0.06f, l.x + dx + 0.06f, y + 0.07f, l.z + 0.06f, FIXTURE_BODY) to true
    }
    LightKind.Floor -> listOf(
        Box(l.x - 0.15f, 0f, l.z - 0.15f, l.x + 0.15f, 0.03f, l.z + 0.15f, 0xFF2A2C33.toInt()) to false,
        Box(l.x - 0.014f, 0.03f, l.z - 0.014f, l.x + 0.014f, y - 0.06f, l.z + 0.014f, 0xFF2A2C33.toInt()) to false,
        Box(l.x - 0.16f, y - 0.06f, l.z - 0.16f, l.x + 0.16f, y + 0.22f, l.z + 0.16f, FIXTURE_BODY) to true,
    )
    LightKind.Table -> listOf(
        Box(l.x - 0.07f, y, l.z - 0.07f, l.x + 0.07f, y + 0.12f, l.z + 0.07f, 0xFF2A2C33.toInt()) to false,
        Box(l.x - 0.12f, y + 0.12f, l.z - 0.12f, l.x + 0.12f, y + 0.30f, l.z + 0.12f, FIXTURE_BODY) to true,
    )
    LightKind.Strip -> {
        val half = l.length / 2f
        val along = abs(cos(Math.toRadians(l.rot.toDouble()))) > 0.5
        if (along) {
            listOf(Box(l.x - half, y, l.z - 0.03f, l.x + half, y + 0.04f, l.z + 0.03f, FIXTURE_BODY) to true)
        } else {
            listOf(Box(l.x - 0.03f, y, l.z - half, l.x + 0.03f, y + 0.04f, l.z + half, FIXTURE_BODY) to true)
        }
    }
}

private fun acBox(a: PlanAc, room: PlanRoom): Box {
    val w = 0.84f
    val t = 0.20f
    return when (a.wall) {
        Wall.North -> Box(a.x - w / 2f, a.y - 0.28f, room.z, a.x + w / 2f, a.y, room.z + t, 0xFFE9E7E2.toInt())
        Wall.South -> Box(a.x - w / 2f, a.y - 0.28f, room.z1 - t, a.x + w / 2f, a.y, room.z1, 0xFFE9E7E2.toInt())
        Wall.West -> Box(room.x, a.y - 0.28f, a.z - w / 2f, room.x + t, a.y, a.z + w / 2f, 0xFFE9E7E2.toInt())
        Wall.East -> Box(room.x1 - t, a.y - 0.28f, a.z - w / 2f, room.x1, a.y, a.z + w / 2f, 0xFFE9E7E2.toInt())
    }
}

private fun fanBoxes(f: PlanFan): List<Box> = listOf(
    Box(f.x - 0.09f, f.y - 0.10f, f.z - 0.09f, f.x + 0.09f, f.y, f.z + 0.09f, 0xFF2A2C33.toInt()),
    Box(f.x - 0.62f, f.y - 0.07f, f.z - 0.07f, f.x + 0.62f, f.y - 0.05f, f.z + 0.07f, 0xFFB9B6AE.toInt()),
    Box(f.x - 0.07f, f.y - 0.07f, f.z - 0.62f, f.x + 0.07f, f.y - 0.05f, f.z + 0.62f, 0xFFB9B6AE.toInt()),
)

private fun sensorBox(s: PlanSensor, room: PlanRoom): Box = when (s.wall) {
    Wall.North -> Box(s.x - 0.05f, s.y, room.z, s.x + 0.05f, s.y + 0.07f, room.z + 0.04f, 0xFF2A2C33.toInt())
    Wall.South -> Box(s.x - 0.05f, s.y, room.z1 - 0.04f, s.x + 0.05f, s.y + 0.07f, room.z1, 0xFF2A2C33.toInt())
    Wall.West -> Box(room.x, s.y, s.z - 0.05f, room.x + 0.04f, s.y + 0.07f, s.z + 0.05f, 0xFF2A2C33.toInt())
    Wall.East -> Box(room.x1 - 0.04f, s.y, s.z - 0.05f, room.x1, s.y + 0.07f, s.z + 0.05f, 0xFF2A2C33.toInt())
}
