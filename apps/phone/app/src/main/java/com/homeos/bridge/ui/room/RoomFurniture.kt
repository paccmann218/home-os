package com.homeos.bridge.ui.room

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * An axis-aligned box in world space, which is the only solid this renderer knows how to draw.
 *
 * [detail] ranks a box by how much it contributes: 0 is the silhouette and must always be drawn,
 * 1 is a part you would notice missing, 2 is decoration. It is the budget knob — if the frame ever
 * needs saving, the build drops everything above a threshold and the furniture gets simpler rather
 * than the room getting emptier.
 */
data class Box(
    val x0: Float, val y0: Float, val z0: Float,
    val x1: Float, val y1: Float, val z1: Float,
    val color: Int,
    val detail: Int = 0,
)

/**
 * The furniture catalogue.
 *
 * Kept in Kotlin rather than in the plan asset for the same reason `APPLIANCE_CATALOGUE` is: this
 * is a catalogue of *shapes the renderer knows how to build*, not a description of one home. The
 * plan says "a sofa here, 2.5 m wide"; what a sofa is made of belongs with the code that draws it.
 *
 * Ported from house-control's `furniture.js` (MIT, (c) 2026 0xProbability), read from source rather
 * than guessed. Its builders stand each piece on y = 0, centre it on its footprint and face it
 * toward +z, and the plan's `rot` turns it from there — the same convention is kept here.
 *
 * Two deliberate departures. Cylinders become boxes, because the renderer draws axis-aligned boxes
 * and nothing else, and at this scale a ten-sided cylinder and a box are the same handful of
 * pixels. And legs under about 2 cm are dropped entirely: six quads each to say nothing.
 */
enum class Piece {
    Rug, Bed, Nightstand, Dresser, Plant,
    Sofa, Armchair, CoffeeTable, Tv, Sideboard,
    DiningSet, Island, Kitchen, Fridge,
    Vanity, Bathtub, Toilet;

    companion object {
        fun of(name: String): Piece? = when (name) {
            "rug" -> Rug
            "bed" -> Bed
            "nightstand" -> Nightstand
            "dresser" -> Dresser
            "plant" -> Plant
            "sofa" -> Sofa
            "armchair" -> Armchair
            "coffeeTable" -> CoffeeTable
            "tv" -> Tv
            "sideboard" -> Sideboard
            "diningSet" -> DiningSet
            "island" -> Island
            "kitchen" -> Kitchen
            "fridge" -> Fridge
            "vanity" -> Vanity
            "bathtub" -> Bathtub
            "toilet" -> Toilet
            else -> null
        }
    }
}

private const val WOOD = 0xFFA98459.toInt()
private const val DARK_WOOD = 0xFF5A4334.toInt()
private const val WHITE = 0xFFECEAE5.toInt()
private const val BLACK = 0xFF1B1C21.toInt()
private const val METAL = 0xFF9A9FA8.toInt()
private const val LINEN = 0xFFF1EFEA.toInt()

/** Scales a colour toward black, holding alpha. house-control's `darker()`. */
internal fun darker(color: Int, k: Float): Int {
    val a = color.toLong() and 0xFF000000L
    val r = ((color shr 16 and 0xFF) * k).roundToInt().coerceIn(0, 255)
    val g = ((color shr 8 and 0xFF) * k).roundToInt().coerceIn(0, 255)
    val b = ((color and 0xFF) * k).roundToInt().coerceIn(0, 255)
    return (a or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()).toInt()
}

/**
 * A part, in the piece's own space: centred on x and z, standing on [y0] above the piece's base.
 * Rotation and placement happen once, in [boxesOf].
 */
private class Part(
    val cx: Float, val y0: Float, val cz: Float,
    val w: Float, val h: Float, val d: Float,
    val color: Int,
    val detail: Int = 0,
)

/** Expands one placement into world-space boxes. Unknown pieces never reach here. */
fun boxesOf(f: PlanFurniture): List<Box> {
    val parts = when (f.piece) {
        Piece.Rug -> rug(f)
        Piece.Bed -> bed(f)
        Piece.Nightstand -> nightstand()
        Piece.Dresser -> dresser(f)
        Piece.Plant -> plant(f)
        Piece.Sofa -> sofa(f)
        Piece.Armchair -> seating(0.92f, 0.88f, f.color ?: 0xFFC28A5A.toInt(), 1)
        Piece.CoffeeTable -> coffeeTable(f)
        Piece.Tv -> tv(f)
        Piece.Sideboard -> sideboard(f)
        Piece.DiningSet -> diningSet(f)
        Piece.Island -> island(f)
        Piece.Kitchen -> kitchen(f)
        Piece.Fridge -> fridge()
        Piece.Vanity -> vanity(f)
        Piece.Bathtub -> bathtub()
        Piece.Toilet -> toilet()
    }

    val rad = Math.toRadians(f.rot.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    // Right angles are the expected case, so dimensions swap rather than needing oriented boxes.
    // An armchair at 12 degrees stays axis-aligned and simply sits where it was told to.
    val swap = abs(s) > 0.5f

    return parts.map { p ->
        val px = p.cx * c - p.cz * s
        val pz = p.cx * s + p.cz * c
        val w = if (swap) p.d else p.w
        val d = if (swap) p.w else p.d
        Box(
            x0 = f.x + px - w / 2f, y0 = p.y0, z0 = f.z + pz - d / 2f,
            x1 = f.x + px + w / 2f, y1 = p.y0 + p.h, z1 = f.z + pz + d / 2f,
            color = p.color,
            detail = p.detail,
        )
    }
}

// ------------------------------------------------------------------------------------ bedroom

private fun rug(f: PlanFurniture): List<Part> {
    val w = f.width ?: 2.0f
    val d = f.depth ?: 1.4f
    val base = f.color ?: 0xFF3B4A66.toInt()
    return listOf(
        Part(0f, 0.002f, 0f, w, 0.016f, d, base),
        Part(0f, 0.012f, 0f, w - 0.24f, 0.008f, d - 0.24f, darker(base, 1.18f), detail = 1),
    )
}

private fun bed(f: PlanFurniture): List<Part> {
    val w = f.width ?: 1.6f
    val depth = 2.1f
    val duvet = f.color ?: 0xFF7F8FB5.toInt()
    return listOf(
        Part(0f, 0.04f, 0f, w + 0.1f, 0.26f, depth + 0.04f, DARK_WOOD),
        Part(0f, 0f, -depth / 2f, w + 0.16f, 1.02f, 0.10f, darker(duvet, 0.6f)),
        Part(0f, 0.28f, 0.02f, w, 0.20f, depth - 0.06f, LINEN),
        Part(0f, 0.40f, depth * 0.17f, w + 0.05f, 0.13f, depth * 0.63f, duvet),
        Part(0f, 0.50f, depth * 0.36f, w + 0.06f, 0.05f, 0.30f, darker(duvet, 0.8f), detail = 1),
        Part(-w / 4f, 0.46f, -depth / 2f + 0.36f, w / 2f - 0.14f, 0.13f, 0.42f, LINEN, detail = 1),
        Part(w / 4f, 0.46f, -depth / 2f + 0.36f, w / 2f - 0.14f, 0.13f, 0.42f, LINEN, detail = 1),
    )
}

private fun nightstand(): List<Part> = listOf(
    Part(0f, 0.06f, 0f, 0.46f, 0.44f, 0.40f, WOOD),
    Part(0f, 0.28f, 0.20f, 0.40f, 0.006f, 0.01f, DARK_WOOD, detail = 2),
)

private fun dresser(f: PlanFurniture): List<Part> {
    val w = f.width ?: 1.3f
    return listOf(
        Part(0f, 0.10f, 0f, w, 0.74f, 0.45f, WHITE),
        Part(w / 2f - 0.30f, 0.84f, 0f, 0.18f, 0.18f, 0.18f, 0xFFC9A15E.toInt(), detail = 1),
        Part(0f, 0.347f, 0.226f, w - 0.06f, 0.006f, 0.01f, 0xFFB9B6AE.toInt(), detail = 2),
        Part(0f, 0.594f, 0.226f, w - 0.06f, 0.006f, 0.01f, 0xFFB9B6AE.toInt(), detail = 2),
    )
}

/**
 * A plant, as boxes.
 *
 * The source builds its foliage from a dozen icosahedra, which this renderer has no way to draw.
 * Three staggered blocks read as a plant at this size, and the jitter is left out on purpose — a
 * plant that reshuffles itself on every rebuild is a bug you notice without being able to name.
 */
private fun plant(f: PlanFurniture): List<Part> {
    val h = f.height ?: 1.2f
    val trunk = h * 0.55f
    return listOf(
        Part(0f, 0f, 0f, 0.32f, 0.30f, 0.32f, 0xFFD7D0C4.toInt()),
        Part(0f, 0.30f, 0f, 0.04f, max(0.05f, trunk - 0.30f), 0.04f, 0xFF6B4A33.toInt(), detail = 1),
        Part(0f, trunk - 0.06f, 0f, 0.40f, 0.26f, 0.38f, 0xFF3F7D52.toInt()),
        Part(-0.08f, trunk + 0.14f, 0.05f, 0.34f, 0.24f, 0.32f, 0xFF4F9462.toInt(), detail = 1),
        Part(0.07f, trunk + 0.30f, -0.04f, 0.26f, 0.20f, 0.26f, 0xFF35694A.toInt(), detail = 1),
    )
}

// ------------------------------------------------------------------------------------- living

/** The shared frame behind both the sofa and the armchair, exactly as the source factors it. */
private fun seating(width: Float, depth: Float, color: Int, cushions: Int): List<Part> {
    val dark = darker(color, 0.82f)
    val arm = 0.2f
    val inner = width - arm * 2f
    val parts = ArrayList<Part>(8 + cushions * 2)
    parts += Part(0f, 0.10f, 0f, width, 0.20f, depth, dark)
    parts += Part(0f, 0.30f, -depth / 2f + 0.12f, inner, 0.52f, 0.24f, dark)
    parts += Part(-(width / 2f - arm / 2f), 0.30f, 0f, arm, 0.36f, depth, dark)
    parts += Part(width / 2f - arm / 2f, 0.30f, 0f, arm, 0.36f, depth, dark)
    val cw = inner / cushions
    for (i in 0 until cushions) {
        val x = -inner / 2f + cw * (i + 0.5f)
        parts += Part(x, 0.30f, 0.10f, cw - 0.02f, 0.17f, depth - 0.30f, color)
        parts += Part(x, 0.46f, -depth / 2f + 0.30f, cw - 0.04f, 0.36f, 0.16f, color, detail = 1)
    }
    return parts
}

private fun sofa(f: PlanFurniture): List<Part> {
    val w = f.width ?: 2.2f
    val color = f.color ?: 0xFF8B93A7.toInt()
    return seating(w, 0.95f, color, if (w > 2f) 3 else 2) + listOf(
        Part(-w / 2f + 0.42f, 0.46f, -0.10f, 0.36f, 0.36f, 0.12f, 0xFFD9B98A.toInt(), detail = 1),
    )
}

private fun coffeeTable(f: PlanFurniture): List<Part> {
    val w = f.width ?: 1.1f
    return listOf(
        Part(0f, 0.36f, 0f, w, 0.04f, 0.58f, WOOD),
        Part(0f, 0.14f, 0f, w - 0.16f, 0.02f, 0.44f, BLACK, detail = 1),
        Part(-w / 2f + 0.08f, 0f, -0.22f, 0.04f, 0.36f, 0.04f, BLACK, detail = 1),
        Part(w / 2f - 0.08f, 0f, -0.22f, 0.04f, 0.36f, 0.04f, BLACK, detail = 1),
        Part(-w / 2f + 0.08f, 0f, 0.22f, 0.04f, 0.36f, 0.04f, BLACK, detail = 1),
        Part(w / 2f - 0.08f, 0f, 0.22f, 0.04f, 0.36f, 0.04f, BLACK, detail = 1),
        Part(0.22f, 0.40f, 0.05f, 0.24f, 0.035f, 0.17f, 0xFFC9553F.toInt(), detail = 2),
        Part(-0.30f, 0.40f, -0.08f, 0.10f, 0.09f, 0.10f, WHITE, detail = 2),
    )
}

private fun tv(f: PlanFurniture): List<Part> {
    val w = f.width ?: 1.7f
    val sw = w * 0.84f
    val sh = sw * 0.5625f
    return listOf(
        Part(0f, 0.10f, 0f, w, 0.36f, 0.40f, DARK_WOOD),
        Part(0f, 0.72f, -0.15f, sw + 0.03f, sh + 0.03f, 0.035f, BLACK),
        // The screen is emissive in the source. Here it is simply a lit colour: a dark panel with
        // the glow already in it, which is what an unlit renderer can honestly show.
        Part(0f, 0.745f, -0.129f, sw - 0.02f, sh - 0.02f, 0.01f, 0xFF27407A.toInt()),
        Part(0f, 0.46f, 0.05f, 0.50f, 0.07f, 0.09f, 0xFF2A2C33.toInt(), detail = 1),
    )
}

private fun sideboard(f: PlanFurniture): List<Part> {
    val w = f.width ?: 1.6f
    return listOf(
        Part(0f, 0.16f, 0f, w, 0.60f, 0.42f, WOOD),
        Part(-w / 2f + 0.30f, 0.76f, 0f, 0.14f, 0.26f, 0.14f, 0xFF3D6B8C.toInt(), detail = 1),
        Part(w / 2f - 0.35f, 0.76f, 0f, 0.30f, 0.05f, 0.20f, 0xFFD8C9A8.toInt(), detail = 2),
    )
}

// ------------------------------------------------------------------------------------- dining

private fun diningSet(f: PlanFurniture): List<Part> {
    val w = f.width ?: 1.8f
    val chairs = f.count ?: 6
    val depth = 0.95f
    val parts = ArrayList<Part>(4 + chairs * 2)
    parts += Part(0f, 0.71f, 0f, w, 0.05f, depth, 0xFFB48D63.toInt())
    parts += Part(0f, 0.76f, 0f, 0.32f, 0.07f, 0.32f, WHITE, detail = 2)
    parts += Part(0f, 0f, 0f, 0.14f, 0.71f, 0.14f, BLACK, detail = 1)

    val perSide = (chairs + 1) / 2
    for (i in 0 until chairs) {
        val front = i < perSide
        val k = if (front) i else i - perSide
        val n = if (front) perSide else chairs - perSide
        if (n == 0) continue
        val x = -w / 2f + (w / n) * (k + 0.5f)
        val z = (if (front) 1f else -1f) * (depth / 2f + 0.12f)
        parts += Part(x, 0.42f, z, 0.44f, 0.05f, 0.44f, 0xFF2B2E37.toInt(), detail = 1)
        parts += Part(x, 0.47f, z + (if (front) 0.20f else -0.20f), 0.42f, 0.40f, 0.04f, 0xFF2B2E37.toInt(), detail = 1)
    }
    return parts
}

private fun island(f: PlanFurniture): List<Part> {
    val w = f.width ?: 2.0f
    val stools = f.count ?: 3
    val parts = ArrayList<Part>(2 + stools * 2)
    parts += Part(0f, 0f, -0.04f, w - 0.12f, 0.88f, 0.62f, 0xFF39424F.toInt())
    parts += Part(0f, 0.88f, 0f, w, 0.05f, 0.86f, 0xFFE9E7E2.toInt())
    for (i in 0 until stools) {
        val x = -w / 2f + (w / stools) * (i + 0.5f)
        parts += Part(x, 0f, 0.62f, 0.04f, 0.64f, 0.04f, METAL, detail = 1)
        parts += Part(x, 0.64f, 0.62f, 0.34f, 0.05f, 0.34f, 0xFFC28A5A.toInt(), detail = 1)
    }
    return parts
}

// ------------------------------------------------------------------------------------ kitchen

private fun kitchen(f: PlanFurniture): List<Part> {
    val w = f.width ?: 3.0f
    val upper = f.upper
    val parts = ArrayList<Part>(10)
    parts += Part(0f, 0f, 0f, w, 0.86f, 0.60f, 0xFFE4E1DA.toInt())
    parts += Part(0f, 0.86f, 0.005f, w + 0.02f, 0.04f, 0.63f, 0xFF33363D.toInt())
    parts += Part(-w / 4f, 0.90f, 0f, 0.58f, 0.012f, 0.50f, 0xFF0C0D10.toInt(), detail = 1)
    parts += Part(w / 4f, 0.90f, 0.02f, 0.50f, 0.012f, 0.38f, METAL, detail = 1)
    parts += Part(w / 4f, 0.90f, -0.22f, 0.03f, 0.24f, 0.03f, METAL, detail = 2)
    if (upper) {
        parts += Part(0f, 1.48f, -0.125f, w, 0.72f, 0.35f, 0xFFE4E1DA.toInt())
        parts += Part(-w / 4f, 1.42f, -0.09f, 0.70f, 0.10f, 0.42f, 0xFF2F3239.toInt(), detail = 1)
    }
    return parts
}

private fun fridge(): List<Part> = listOf(
    Part(0f, 0f, 0f, 0.70f, 1.92f, 0.68f, 0xFFC5C9D0.toInt()),
    Part(0f, 1.25f, 0.34f, 0.66f, 0.008f, 0.005f, 0xFF7C8088.toInt(), detail = 2),
    Part(-0.26f, 1.34f, 0.35f, 0.02f, 0.50f, 0.03f, 0xFF70747C.toInt(), detail = 1),
    Part(-0.26f, 0.76f, 0.35f, 0.02f, 0.40f, 0.03f, 0xFF70747C.toInt(), detail = 1),
)

// ----------------------------------------------------------------------------------- bathroom

private fun vanity(f: PlanFurniture): List<Part> {
    val w = f.width ?: 1.0f
    return listOf(
        Part(0f, 0.20f, 0f, w, 0.62f, 0.48f, WOOD),
        Part(0f, 0.82f, 0f, w + 0.03f, 0.04f, 0.50f, WHITE),
        Part(0f, 0.86f, 0.02f, 0.38f, 0.10f, 0.38f, WHITE, detail = 1),
        Part(0f, 0.86f, -0.18f, 0.03f, 0.20f, 0.03f, METAL, detail = 2),
        Part(0f, 1.08f, -0.225f, w * 0.82f, 0.80f, 0.025f, 0xFFB9C7DC.toInt()),
    )
}

private fun bathtub(): List<Part> = listOf(
    Part(0f, 0f, 0f, 1.70f, 0.56f, 0.78f, WHITE),
    Part(0f, 0.545f, 0f, 1.50f, 0.02f, 0.58f, 0xFF9FD3EC.toInt()),
    Part(-0.74f, 0.56f, 0f, 0.03f, 0.20f, 0.03f, METAL, detail = 2),
)

private fun toilet(): List<Part> = listOf(
    Part(0f, 0f, -0.24f, 0.38f, 0.78f, 0.17f, WHITE),
    Part(0f, 0f, 0.10f, 0.38f, 0.40f, 0.475f, WHITE),
    Part(0f, 0.40f, 0.10f, 0.40f, 0.03f, 0.50f, WHITE, detail = 1),
)
