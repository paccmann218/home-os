package com.homeos.bridge.ui.room

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The floor plan: what the home is shaped like.
 *
 * The format is adapted from house-control (MIT, (c) 2026 0xProbability) and keeps its conventions,
 * because they are good ones and because staying close to the source makes the port checkable.
 * Metres throughout; **x runs east, z runs south, y runs up**; a room is an axis-aligned rectangle
 * `[x, z, width, depth]`, and the walls between rooms are *derived* from where those rectangles
 * touch rather than authored. An L-shaped room is two rectangles.
 *
 * Everything here is plain data and pure Kotlin — no Compose, no Android beyond reading the asset —
 * so the wall derivation that consumes it can be tested on the JVM.
 */
data class RoomPlan(
    val name: String,
    val wallHeight: Float,
    val interiorWallHeight: Float,
    val azimuth: Float,
    val elevation: Float,
    val rooms: List<PlanRoom>,
    val openings: List<PlanOpening>,
    val lights: List<PlanLight>,
    val acs: List<PlanAc>,
    val fans: List<PlanFan>,
    val sensors: List<PlanSensor>,
    val furniture: List<PlanFurniture>,
) {
    fun room(id: String): PlanRoom? = rooms.firstOrNull { it.id == id }

    /** Every fixture that names a backend device, in one list, for the scene binder. */
    val bindings: List<String>
        get() = (lights.mapNotNull { it.bind } + acs.mapNotNull { it.bind } +
            fans.mapNotNull { it.bind } + sensors.mapNotNull { it.bind }).distinct()
}

/**
 * A room.
 *
 * [connected] is the honest flag: true only where HomeOS actually drives something. The renderer
 * drains the colour out of everything else and forces its fixtures off, because a lamp that looks
 * identical to a live one and never responds is a lie told in three dimensions.
 */
data class PlanRoom(
    val id: String,
    val name: String,
    val x: Float,
    val z: Float,
    val w: Float,
    val d: Float,
    val floor: FloorKind,
    val connected: Boolean,
) {
    val x1: Float get() = x + w
    val z1: Float get() = z + d
    val centreX: Float get() = x + w / 2f
    val centreZ: Float get() = z + d / 2f
}

enum class FloorKind { Oak, Walnut, Tile, Stone }

enum class OpeningKind { Door, Window, Open }

/**
 * A hole in a wall. Must be axis-aligned, so either [fromX] == [toX] or [fromZ] == [toZ]; one that
 * does not lie on a room edge is dropped by the derivation rather than rendered in the wrong place.
 */
data class PlanOpening(
    val kind: OpeningKind,
    val fromX: Float,
    val fromZ: Float,
    val toX: Float,
    val toZ: Float,
    val sill: Float,
    val head: Float,
) {
    val vertical: Boolean get() = fromX == toX
}

enum class LightKind { Ceiling, Pendant, Spots, Floor, Table, Strip }

data class PlanLight(
    val id: String,
    val name: String,
    val room: String,
    val kind: LightKind,
    val x: Float,
    val z: Float,
    /** Null means "the default height for this fixture kind". */
    val y: Float?,
    val rot: Float,
    val length: Float,
    val dimmable: Boolean,
    /** An authored colour, which wins over colour temperature when present. */
    val color: Int?,
    /** The backend device id this fixture stands for, or null when it is scenery. */
    val bind: String?,
)

enum class Wall { North, South, East, West }

data class PlanAc(
    val id: String,
    val name: String,
    val room: String,
    val wall: Wall,
    val x: Float,
    val z: Float,
    val y: Float,
    val min: Int,
    val max: Int,
    val bind: String?,
)

data class PlanFan(
    val id: String,
    val name: String,
    val room: String,
    val x: Float,
    val z: Float,
    val y: Float,
    val bind: String?,
)

data class PlanSensor(
    val id: String,
    val name: String,
    val room: String,
    val wall: Wall,
    val x: Float,
    val z: Float,
    val y: Float,
    val bind: String?,
)

data class PlanFurniture(
    val piece: Piece,
    val room: String,
    val x: Float,
    val z: Float,
    /** Degrees about y. Only multiples of 90 are expected, since everything is axis-aligned. */
    val rot: Float,
    val width: Float?,
    val depth: Float?,
    val height: Float?,
    val color: Int?,
    /** Chairs on a dining set, stools at an island. */
    val count: Int?,
    /** Whether a kitchen run carries wall cabinets above the worktop. */
    val upper: Boolean,
)

// ----------------------------------------------------------------------------------- the parser

/**
 * Hand-parsed with `org.json`, for the same reason `data/Models.kt` gives: a reflective serialiser
 * would be a dependency and a build step to read one 3 KB asset whose shape this file already
 * states twice. Unknown fixture kinds and unknown furniture pieces are skipped rather than thrown,
 * so the asset can name something the renderer has not learned to draw yet without taking the
 * screen down with it.
 */
object RoomPlanReader {

    fun fromAssets(context: Context, path: String = "room/floorplan.json"): RoomPlan =
        parse(context.assets.open(path).bufferedReader().use { it.readText() })

    fun parse(text: String): RoomPlan {
        val json = JSONObject(text)
        val camera = json.optJSONObject("camera")
        return RoomPlan(
            name = json.optString("name", "Home"),
            wallHeight = json.optDouble("wallHeight", 2.6).toFloat(),
            interiorWallHeight = json.optDouble("interiorWallHeight", 1.25).toFloat(),
            azimuth = camera?.optDouble("azimuth", 34.0)?.toFloat() ?: 34f,
            elevation = camera?.optDouble("elevation", 47.0)?.toFloat() ?: 47f,
            rooms = json.optJSONArray("rooms").mapObjects { it.toRoom() },
            openings = json.optJSONArray("openings").mapObjects { it.toOpening() },
            lights = json.optJSONArray("lights").mapObjects { it.toLight() },
            acs = json.optJSONArray("acs").mapObjects { it.toAc() },
            fans = json.optJSONArray("fans").mapObjects { it.toFan() },
            sensors = json.optJSONArray("sensors").mapObjects { it.toSensor() },
            furniture = json.optJSONArray("furniture").mapObjects { it.toFurniture() },
        )
    }

    private fun JSONObject.toRoom(): PlanRoom? {
        val rect = optJSONArray("rect") ?: return null
        if (rect.length() < 4) return null
        return PlanRoom(
            id = optString("id"),
            name = optString("name"),
            x = rect.f(0), z = rect.f(1), w = rect.f(2), d = rect.f(3),
            floor = when (optString("floor")) {
                "oak" -> FloorKind.Oak
                "walnut" -> FloorKind.Walnut
                "tile" -> FloorKind.Tile
                "stone" -> FloorKind.Stone
                else -> FloorKind.Oak
            },
            connected = optBoolean("connected", false),
        )
    }

    private fun JSONObject.toOpening(): PlanOpening? {
        val from = optJSONArray("from") ?: return null
        val to = optJSONArray("to") ?: return null
        val kind = when (optString("kind")) {
            "door" -> OpeningKind.Door
            "window" -> OpeningKind.Window
            "open" -> OpeningKind.Open
            else -> return null
        }
        return PlanOpening(
            kind = kind,
            fromX = from.f(0), fromZ = from.f(1),
            toX = to.f(0), toZ = to.f(1),
            sill = optDouble("sill", 0.85).toFloat(),
            head = optDouble("head", 2.1).toFloat(),
        )
    }

    private fun JSONObject.toLight(): PlanLight? {
        val pos = optJSONArray("pos") ?: return null
        val kind = when (optString("type")) {
            "ceiling" -> LightKind.Ceiling
            "pendant" -> LightKind.Pendant
            "spots" -> LightKind.Spots
            "floor" -> LightKind.Floor
            "table" -> LightKind.Table
            "strip" -> LightKind.Strip
            else -> return null
        }
        val caps = optJSONArray("caps")
        val dimmable = (0 until (caps?.length() ?: 0)).any { caps?.optString(it) == "brightness" }
        return PlanLight(
            id = optString("id"),
            name = optString("name"),
            room = optString("room"),
            kind = kind,
            x = pos.f(0), z = pos.f(1),
            y = if (has("y")) optDouble("y").toFloat() else null,
            rot = optDouble("rot", 0.0).toFloat(),
            length = optDouble("length", 1.0).toFloat(),
            dimmable = dimmable,
            color = hexOrNull("color"),
            bind = stringOrNull("bind"),
        )
    }

    private fun JSONObject.toAc(): PlanAc? {
        val pos = optJSONArray("pos") ?: return null
        return PlanAc(
            id = optString("id"),
            name = optString("name"),
            room = optString("room"),
            wall = wallOf(optString("wall")),
            x = pos.f(0), z = pos.f(1),
            y = optDouble("y", 2.2).toFloat(),
            min = optInt("min", 16),
            max = optInt("max", 30),
            bind = stringOrNull("bind"),
        )
    }

    private fun JSONObject.toFan(): PlanFan? {
        val pos = optJSONArray("pos") ?: return null
        return PlanFan(
            id = optString("id"),
            name = optString("name"),
            room = optString("room"),
            x = pos.f(0), z = pos.f(1),
            y = optDouble("y", 2.34).toFloat(),
            bind = stringOrNull("bind"),
        )
    }

    private fun JSONObject.toSensor(): PlanSensor? {
        val pos = optJSONArray("pos") ?: return null
        return PlanSensor(
            id = optString("id"),
            name = optString("name"),
            room = optString("room"),
            wall = wallOf(optString("wall")),
            x = pos.f(0), z = pos.f(1),
            y = optDouble("y", 1.5).toFloat(),
            bind = stringOrNull("bind"),
        )
    }

    private fun JSONObject.toFurniture(): PlanFurniture? {
        val pos = optJSONArray("pos") ?: return null
        val piece = Piece.of(optString("piece")) ?: return null
        val size = optJSONArray("size")
        return PlanFurniture(
            piece = piece,
            room = optString("room"),
            x = pos.f(0), z = pos.f(1),
            rot = optDouble("rot", 0.0).toFloat(),
            width = if (size != null) size.f(0) else if (has("width")) optDouble("width").toFloat() else null,
            depth = if (size != null) size.f(1) else null,
            height = if (has("height")) optDouble("height").toFloat() else null,
            color = hexOrNull("color"),
            count = when {
                has("chairs") -> optInt("chairs")
                has("stools") -> optInt("stools")
                else -> null
            },
            upper = optBoolean("upper", true),
        )
    }

    private fun wallOf(s: String) = when (s) {
        "north" -> Wall.North
        "south" -> Wall.South
        "east" -> Wall.East
        else -> Wall.West
    }

    private fun JSONArray.f(i: Int) = optDouble(i, 0.0).toFloat()

    private fun JSONObject.stringOrNull(key: String) =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    /** `#rrggbb` to an opaque ARGB int. */
    private fun JSONObject.hexOrNull(key: String): Int? {
        val raw = stringOrNull(key)?.removePrefix("#") ?: return null
        return raw.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
    }

    private inline fun <T : Any> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(transform) }
    }
}
