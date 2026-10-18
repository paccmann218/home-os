package com.homeos.bridge.ui.room

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The camera, in one place.
 *
 * `GraphView` carries a comment that is a monument to what happens when a projection is written
 * down twice: its renderer projected at 0.46 of the short side and its hit test at 0.38, so every
 * tap resolved against a picture 17% smaller than the one on screen — close enough near the middle
 * to look like it worked. So here there is one [RoomProjector] and both callers use it.
 *
 * [FOCAL] is not a taste value. house-control's rig is a 24° vertical field of view, and for a
 * scene normalised to unit radius that is exactly `1 / tan(12°)` of camera distance.
 */
object RoomCameraDefaults {
    const val FOCAL = 4.70f
    const val PROJECTION = 0.47f

    /**
     * The look-at height, in metres, before normalisation. Deliberately low: the near walls are cut
     * to 14 cm curbs and the interior walls are only 1.25 m, so the visible mass of this scene sits
     * near the floor and aiming at mid-wall would frame mostly empty air.
     */
    const val CENTRE_Y = 0.60f

    /** Orbit limits, matching house-control: ±40° of the authored azimuth, 26°–70° of elevation. */
    const val AZIMUTH_RANGE = 0.698f
    const val ELEVATION_MIN = 0.454f
    const val ELEVATION_MAX = 1.222f

    /** Dolly, not scale: 0.55 is close in, 1.2 is backed off. */
    const val ZOOM_MIN = 0.55f
    const val ZOOM_MAX = 1.20f

    /** Drag sensitivity. Far gentler than `GraphView`'s, because the range here is only ±40°. */
    const val DRAG_AZIMUTH = 420f
    const val DRAG_ELEVATION = 520f
}

/**
 * Where the camera is and where it is going.
 *
 * Plain `var`s rather than snapshot state on purpose: these change every frame while the camera is
 * easing, and routing that through Compose state would recompose the whole subtree sixty times a
 * second to move a number the draw lambda could have read directly.
 */
class RoomCameraState(baseAzimuth: Float, baseElevation: Float) {
    val baseAzimuth: Float = Math.toRadians(baseAzimuth.toDouble()).toFloat()

    var azimuth: Float = this.baseAzimuth
    var elevation: Float = Math.toRadians(baseElevation.toDouble()).toFloat()
    var zoom: Float = 1f
    var targetX: Float = 0f
    var targetY: Float = 0f
    var targetZ: Float = 0f

    var goalAzimuth: Float = azimuth
    var goalElevation: Float = elevation
    var goalZoom: Float = 1f
    var goalTargetX: Float = 0f
    var goalTargetY: Float = 0f
    var goalTargetZ: Float = 0f

    fun drag(dx: Float, dy: Float) {
        goalAzimuth = (goalAzimuth - dx / RoomCameraDefaults.DRAG_AZIMUTH).coerceIn(
            baseAzimuth - RoomCameraDefaults.AZIMUTH_RANGE,
            baseAzimuth + RoomCameraDefaults.AZIMUTH_RANGE,
        )
        goalElevation = (goalElevation + dy / RoomCameraDefaults.DRAG_ELEVATION).coerceIn(
            RoomCameraDefaults.ELEVATION_MIN,
            RoomCameraDefaults.ELEVATION_MAX,
        )
    }

    fun pinch(factor: Float) {
        goalZoom = (goalZoom / factor).coerceIn(
            RoomCameraDefaults.ZOOM_MIN,
            RoomCameraDefaults.ZOOM_MAX,
        )
    }

    fun focus(x: Float, y: Float, z: Float, zoom: Float) {
        goalTargetX = x
        goalTargetY = y
        goalTargetZ = z
        goalZoom = zoom.coerceIn(RoomCameraDefaults.ZOOM_MIN, RoomCameraDefaults.ZOOM_MAX)
    }

    fun reset() {
        goalAzimuth = baseAzimuth
        goalTargetX = 0f
        goalTargetY = 0f
        goalTargetZ = 0f
        goalZoom = 1f
    }

    /** True once everything is close enough to its goal that another frame would show nothing. */
    val settled: Boolean
        get() = kotlin.math.abs(azimuth - goalAzimuth) < 1e-4f &&
            kotlin.math.abs(elevation - goalElevation) < 1e-4f &&
            kotlin.math.abs(zoom - goalZoom) < 1e-4f &&
            kotlin.math.abs(targetX - goalTargetX) < 1e-4f &&
            kotlin.math.abs(targetY - goalTargetY) < 1e-4f &&
            kotlin.math.abs(targetZ - goalTargetZ) < 1e-4f

    /** Frame-rate independent easing, house-control's `k = 1 - exp(-dt * 5.5)`. */
    fun ease(dt: Float, instant: Boolean) {
        val k = if (instant) 1f else 1f - exp(-dt * 5.5f)
        azimuth += (goalAzimuth - azimuth) * k
        elevation += (goalElevation - elevation) * k
        zoom += (goalZoom - zoom) * k
        targetX += (goalTargetX - targetX) * k
        targetY += (goalTargetY - targetY) * k
        targetZ += (goalTargetZ - targetZ) * k
        if (settled) snap()
    }

    private fun snap() {
        azimuth = goalAzimuth
        elevation = goalElevation
        zoom = goalZoom
        targetX = goalTargetX
        targetY = goalTargetY
        targetZ = goalTargetZ
    }
}

/**
 * Projects scene-normalised model points to screen pixels.
 *
 * One instance is reused for the whole frame and rebuilt by [set]; nothing here allocates, because
 * it runs a few thousand times per frame.
 */
class RoomProjector {
    private var cosY = 1f
    private var sinY = 0f
    private var cosP = 1f
    private var sinP = 0f
    private var scale = 1f
    private var focal = RoomCameraDefaults.FOCAL
    private var originX = 0f
    private var originY = 0f
    private var tx = 0f
    private var ty = 0f
    private var tz = 0f

    /** The unit vector pointing from the scene toward the camera, in model space. */
    var toCameraX = 0f
        private set
    var toCameraY = 0f
        private set
    var toCameraZ = 0f
        private set

    fun set(camera: RoomCameraState, width: Float, height: Float) {
        cosY = cos(camera.azimuth)
        sinY = sin(camera.azimuth)
        cosP = cos(camera.elevation)
        sinP = sin(camera.elevation)
        // A dolly: distance rises with zoom and the on-screen scale falls, which is what gives the
        // near/far size ratio its change. Scaling alone would just make a flat picture bigger.
        focal = RoomCameraDefaults.FOCAL * camera.zoom
        scale = min(width, height) * RoomCameraDefaults.PROJECTION / camera.zoom
        originX = width / 2f
        originY = height / 2f
        tx = camera.targetX
        ty = camera.targetY
        tz = camera.targetZ
        // Derived from the projection above rather than written independently, which is how the
        // sign on x came to be wrong: the yaw puts the camera at +sinY, so a vector that said
        // -sinY disagreed with the very transform it was supposed to describe, and backface
        // culling then kept exactly the faces it should have dropped.
        toCameraX = cosP * sinY
        toCameraY = sinP
        toCameraZ = -cosP * cosY
    }

    /**
     * Writes screen x into `out[0]` and screen y into `out[1]`, and returns view-space depth —
     * larger is further away, which is the key the painter's sort runs on.
     *
     * Note `ty - y`: model y points up and screen y points down, and the flip has to happen
     * *before* the pitch rotation, not after it. Negating the result instead satisfies one of the
     * two things this has to get right and breaks the other — taller geometry rises correctly, but
     * further geometry also sinks down the screen, so the far wall ends up painted across the
     * floor in front of it. Doing it here keeps both: up is up, and away is up.
     */
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Float {
        val dx = x - tx
        val dy = ty - y
        val dz = z - tz
        val x1 = dx * cosY + dz * sinY
        val z1 = -dx * sinY + dz * cosY
        val y1 = dy * cosP - z1 * sinP
        val z2 = dy * sinP + z1 * cosP
        val persp = focal / (focal + z2)
        out[0] = originX + x1 * scale * persp
        out[1] = originY + y1 * scale * persp
        return z2
    }

    /** How much a circle lying on the floor is squashed on screen. Measured to be exactly this. */
    fun floorForeshorten(): Float = kotlin.math.abs(sinP)
}
