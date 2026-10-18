package com.homeos.bridge.ui.room

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Exterior wall thickness, in metres. */
const val EXTERIOR_THICKNESS = 0.2f

/** Interior wall thickness. Thinner, because it is a partition rather than a shell. */
const val INTERIOR_THICKNESS = 0.1f

/** How tall a wall is left when it is cut away on the camera side so you can see in. */
const val CURB_HEIGHT = 0.14f

/** Coordinates are compared on a 1 mm grid, so two rooms sharing an edge actually share a key. */
private const val GRID = 1000f
private const val EPS = 1e-4f

/**
 * One run of wall: a straight, uniform stretch with at most one opening in it.
 *
 * @param alongX true when the run extends along x at a constant z; false when it extends along z
 *   at a constant x.
 * @param inside which side of the line the room is on, +1 for the min side and -1 for the max —
 *   so the outward normal is `-inside`. **Zero for interior runs**, where rooms lie on both sides
 *   and the question has no answer; nothing may read it in that case.
 */
data class WallRun(
    val alongX: Boolean,
    val coord: Float,
    val a: Float,
    val b: Float,
    val interior: Boolean,
    val inside: Int,
    val opening: PlanOpening?,
) {
    val thickness: Float get() = if (interior) INTERIOR_THICKNESS else EXTERIOR_THICKNESS

    /** The outward normal in x, for a run extending along z. Zero for an `alongX` run. */
    val normalX: Float get() = if (alongX) 0f else -inside.toFloat()

    /** The outward normal in z, for a run extending along x. Zero for an `alongZ` run. */
    val normalZ: Float get() = if (alongX) -inside.toFloat() else 0f
}

/**
 * Turns room rectangles into walls.
 *
 * Ported from house-control's `web/src/three/house.js` (MIT, (c) 2026 0xProbability). The idea that
 * makes it work is that walls are never authored: every room contributes its four edges to a shared
 * map keyed by `axis:millimetre`, and an entry that collects **two** edges is a wall between two
 * rooms while an entry that collects **one** is an outside wall. Everything else — where to split,
 * what is a doorway, which side faces out — falls out of that one observation.
 *
 * Pure: no Compose, no Android, no camera. The camera only decides later which exterior runs get
 * cut down to a curb, and that is the mesh builder's business, not this function's.
 */
fun deriveWalls(plan: RoomPlan): List<WallRun> {
    val lines = HashMap<Long, Line>()

    fun line(alongX: Boolean, coord: Float): Line {
        val k = (if (alongX) 1L else 0L) shl 32 or (quantise(coord).toLong() and 0xFFFFFFFFL)
        return lines.getOrPut(k) { Line(alongX, coord) }
    }

    // 1. Every room's four edges. `inside` records which side the room is on, so an exterior run
    //    knows which way is out without ever being told.
    for (r in plan.rooms) {
        line(true, r.z).edges += Edge(r.x, r.x1, +1)
        line(true, r.z1).edges += Edge(r.x, r.x1, -1)
        line(false, r.x).edges += Edge(r.z, r.z1, +1)
        line(false, r.x1).edges += Edge(r.z, r.z1, -1)
    }

    // 2. Openings attach to whichever line they sit on. One that sits on no room edge is dropped
    //    rather than rendered somewhere arbitrary.
    for (o in plan.openings) {
        val alongX = !o.vertical
        val coord = if (alongX) o.fromZ else o.fromX
        val k = (if (alongX) 1L else 0L) shl 32 or (quantise(coord).toLong() and 0xFFFFFFFFL)
        val l = lines[k] ?: continue
        val from = if (alongX) o.fromX else o.fromZ
        val to = if (alongX) o.toX else o.toZ
        l.openings += Hole(min(from, to), max(from, to), o)
    }

    // 3. Split each line at every endpoint, classify each interval by how many rooms cover it,
    //    then merge neighbours that agree so a long wall is one box and not forty.
    val runs = ArrayList<WallRun>()
    for (l in lines.values) {
        val cuts = sortedCuts(l)
        var open: WallRun? = null

        for (i in 0 until cuts.size - 1) {
            val a = cuts[i]
            val b = cuts[i + 1]
            if (b - a <= EPS) continue
            val mid = (a + b) / 2f

            val covering = l.edges.filter { it.a < mid - EPS && mid + EPS < it.b }
            if (covering.isEmpty()) {
                // A gap between rooms. Nothing to build, and it breaks the current run.
                open?.let { runs += it }
                open = null
                continue
            }
            val interior = covering.size > 1
            val inside = if (interior) 0 else covering[0].inside
            val hole = l.openings.firstOrNull { it.a < mid + EPS && mid - EPS < it.b }?.opening

            val merged = open
            open = if (merged != null &&
                merged.interior == interior &&
                merged.inside == inside &&
                merged.opening === hole
            ) {
                merged.copy(b = b)
            } else {
                merged?.let { runs += it }
                WallRun(l.alongX, l.coord, a, b, interior, inside, hole)
            }
        }
        open?.let { runs += it }
    }

    return extendFreeEnds(runs)
}

/**
 * Pushes a run's end outward where nothing continues from it, so corners close instead of leaving a
 * square notch where two walls meet.
 *
 * Half the thickness is the exact amount: the perpendicular wall's centre line sits on the corner,
 * so reaching half its width covers the gap and no more. A run that butts into a neighbour on the
 * same line is left alone — it is already continuous there.
 */
private fun extendFreeEnds(runs: List<WallRun>): List<WallRun> {
    fun continues(run: WallRun, at: Float): Boolean = runs.any { other ->
        other !== run &&
            other.alongX == run.alongX &&
            quantise(other.coord) == quantise(run.coord) &&
            (quantise(other.a) == quantise(at) || quantise(other.b) == quantise(at))
    }
    return runs.map { run ->
        // Half the exterior thickness for every run, interior ones included - that is what the
        // source does, and the corner an interior wall has to close is against an exterior one.
        val grow = EXTERIOR_THICKNESS / 2f
        val a = if (continues(run, run.a)) run.a else run.a - grow
        val b = if (continues(run, run.b)) run.b else run.b + grow
        run.copy(a = a, b = b)
    }
}

/** Every point where something changes along this line, on the grid and in order. */
private fun sortedCuts(l: Line): FloatArray {
    val set = sortedSetOf<Int>()
    for (e in l.edges) {
        set += quantise(e.a)
        set += quantise(e.b)
    }
    for (o in l.openings) {
        set += quantise(o.a)
        set += quantise(o.b)
    }
    return FloatArray(set.size) { i -> set.elementAt(i) / GRID }
}

private fun quantise(v: Float): Int = (v * GRID).roundToInt()

private class Line(val alongX: Boolean, val coord: Float) {
    val edges = ArrayList<Edge>()
    val openings = ArrayList<Hole>()
}

private class Edge(val a: Float, val b: Float, val inside: Int)

private class Hole(val a: Float, val b: Float, val opening: PlanOpening)
