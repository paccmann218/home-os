package com.homeos.bridge.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed models for the HomeOS API, hand-parsed from org.json.
 *
 * Hand-parsing rather than kotlinx.serialization is deliberate: org.json is already a dependency,
 * the backend is loosely typed (several fields are null until a device reports in), and an explicit
 * parser makes every one of those nulls visible at the boundary instead of crashing a screen later.
 *
 * Every shape here was read off the running backend, not inferred from the route handlers.
 */

/** Some stored episode summaries are double-encoded UTF-8, so a degree sign arrives as `Â°`. */
internal fun String.repaired(): String =
    if (contains('Â')) replace("Â°", "°").replace("Â", "") else this

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

/**
 * Internal rather than private because the socket paths need it too. A JSON `null`, a missing key
 * and a non-numeric value all have to collapse to `null` rather than to `NaN`, and that decision
 * belongs in one place: a `NaN` that escapes into state is not a missing reading, it is a poison
 * value that survives every later arithmetic and renders as nothing at all.
 */
internal fun JSONObject.floatOrNull(key: String): Float? =
    if (isNull(key) || !has(key)) null else optDouble(key).takeIf { !it.isNaN() }?.toFloat()

private fun JSONObject.intOrNull(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key)

/**
 * `bright` arrives as a boolean from the ESP32 driver and as a number from others, so it is
 * normalised to 0-100 here. Reading it as a plain number silently yielded null for the real sensor.
 *
 * Also internal, and for a sharper reason than tidiness: `optDouble` on a JSON *boolean* returns
 * `NaN`, so every path that read `bright` as a number was wrong in a different way. The poll path
 * used this function and was correct; the two socket paths did not and were not.
 */
internal fun JSONObject.brightnessOrNull(key: String): Float? {
    if (!has(key) || isNull(key)) return null
    return when (val raw = opt(key)) {
        is Boolean -> if (raw) 100f else 0f
        is Number -> raw.toFloat()
        else -> null
    }
}

private inline fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(transform) }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotEmpty() } }
}

@Immutable
data class Person(
    val alias: String,
    val devices: List<String>,
    val present: Boolean,
    /** Non-null for a public-dataset person, who is never in the room and never controls anything. */
    val dataset: String?,
) {
    val isDataset: Boolean get() = dataset != null
}

@Immutable
data class Device(
    val id: String,
    val kind: String,
    val name: String,
    val room: String,
    val power: String?,
    val setpoint: Int?,
    val level: Int?,
    val brightness: Int?,
    val min: Int?,
    val max: Int?,
    val temperature: Float?,
    val humidity: Float?,
    val bright: Float?,
    /** Set while an agent holds a lease on this device. */
    val leasedBy: String?,
) {
    val isOn: Boolean get() = power == "on"
    val isSensor: Boolean get() = kind == "sensor"
}

@Immutable
data class LiveBody(val hr: Float?, val rmssd: Float?, val onBody: Boolean)

@Immutable
data class LiveRoom(
    val at: Long = 0L,
    val temperature: Float? = null,
    val humidity: Float? = null,
    val bright: Float? = null,
)

@Immutable
data class FeedItem(
    val at: Long,
    val agent: String,
    val kind: String,
    val person: String?,
    val text: String,
)

@Immutable
data class HomeState(
    val graphEnabled: Boolean = true,
    val voice: Boolean = false,
    val people: List<Person> = emptyList(),
    val devices: List<Device> = emptyList(),
    val body: Map<String, LiveBody> = emptyMap(),
    val room: LiveRoom = LiveRoom(),
    val feed: List<FeedItem> = emptyList(),
) {
    /**
     * The room's temperature, preferring the live buffer but falling back to the `env` device.
     *
     * This fallback is not defensive padding: on the running backend `live.room` was all null while
     * the `env` device still carried 25.6 C, because the live buffer only fills from a websocket
     * push. Without the fallback the ambient background would sit neutral on a warm room.
     */
    val roomTemperature: Float?
        get() = room.temperature ?: devices.firstOrNull { it.isSensor }?.temperature

    val roomHumidity: Float?
        get() = room.humidity ?: devices.firstOrNull { it.isSensor }?.humidity

    val roomBright: Float?
        get() = room.bright ?: devices.firstOrNull { it.isSensor }?.bright

    fun device(id: String): Device? = devices.firstOrNull { it.id == id }

    companion object {
        fun parse(json: JSONObject): HomeState {
            val live = json.optJSONObject("live")
            val bodies = live?.optJSONObject("people")
            val body = buildMap {
                bodies?.keys()?.forEach { alias ->
                    val o = bodies.optJSONObject(alias) ?: return@forEach
                    put(
                        alias,
                        LiveBody(
                            hr = o.floatOrNull("hr"),
                            rmssd = o.floatOrNull("rmssd"),
                            onBody = o.optBoolean("onBody"),
                        ),
                    )
                }
            }
            val roomJson = live?.optJSONObject("room")
            return HomeState(
                graphEnabled = json.optBoolean("graphEnabled", true),
                voice = json.optBoolean("voice"),
                people = json.optJSONArray("people").map { p ->
                    Person(
                        alias = p.optString("alias"),
                        devices = p.optJSONArray("devices").strings(),
                        present = p.optBoolean("present"),
                        dataset = p.stringOrNull("dataset"),
                    )
                },
                devices = json.optJSONArray("devices").map { d ->
                    val s = d.optJSONObject("state") ?: JSONObject()
                    Device(
                        id = d.optString("id"),
                        kind = s.optString("kind"),
                        name = s.optString("name").ifEmpty { d.optString("id") },
                        room = d.optString("room"),
                        power = s.stringOrNull("power"),
                        setpoint = s.intOrNull("setpoint"),
                        level = s.intOrNull("level"),
                        brightness = s.intOrNull("brightness"),
                        min = s.intOrNull("min"),
                        max = s.intOrNull("max"),
                        temperature = s.floatOrNull("temperature"),
                        humidity = s.floatOrNull("humidity"),
                        bright = s.brightnessOrNull("bright"),
                        leasedBy = d.stringOrNull("leased_by"),
                    )
                },
                body = body,
                room = LiveRoom(
                    at = roomJson?.optLong("t") ?: 0L,
                    temperature = roomJson?.floatOrNull("temperature"),
                    humidity = roomJson?.floatOrNull("humidity"),
                    bright = roomJson?.brightnessOrNull("bright"),
                ),
                feed = json.optJSONArray("feed").map { it.toFeedItem() },
            )
        }
    }
}

internal fun JSONObject.toFeedItem() = FeedItem(
    at = optLong("at"),
    agent = optString("agent"),
    kind = optString("kind"),
    person = stringOrNull("person"),
    text = optString("text").repaired(),
)

@Immutable
data class Decision(
    val id: String,
    val at: Long,
    val kind: String,
    val summary: String,
    val chose: List<String>,
    val rationale: String,
    val arbitration: String?,
) {
    companion object {
        fun parse(o: JSONObject) = Decision(
            id = o.optString("id"),
            at = o.optLong("at"),
            kind = o.optString("kind"),
            summary = o.optString("summary").repaired(),
            chose = o.optJSONArray("chose").strings(),
            rationale = o.optString("rationale").repaired(),
            arbitration = o.stringOrNull("arbitration"),
        )

        fun parseList(a: JSONArray?) = a.map { parse(it) }
    }
}

/** An option the arbiter weighed, chosen or not. */
@Immutable
data class Option(val name: String, val label: String, val score: Float?)

/** The evidence behind an option: how often it was tried and how well it went. */
@Immutable
data class EvidenceRow(
    val intervention: String,
    val label: String,
    val n: Int,
    /** How much of `n` came from this person's own measured outcomes rather than a prior. */
    val learnedN: Int,
    val mean: Float,
) {
    companion object {
        fun parseList(a: JSONArray?) = a.map {
            EvidenceRow(
                intervention = it.optString("intervention"),
                label = it.optString("label").repaired().ifEmpty { it.optString("intervention") },
                n = it.optInt("n"),
                learnedN = it.optInt("learned_n"),
                mean = it.optDouble("mean", 0.0).toFloat(),
            )
        }
    }
}

/**
 * A comfort bound that shaped a decision.
 *
 * [mine] and [reason] together *are* the privacy boundary. Your own constraint arrives with its
 * reason attached; someone else's arrives as a bare range with `reason` null, because the reason
 * lives in a graph your agent cannot read. The Why sheet shows this as it is rather than describing
 * it.
 */
@Immutable
data class Constraint(
    val variable: String,
    val min: Float?,
    val max: Float?,
    val mine: Boolean,
    val reason: String?,
)

@Immutable
data class AppliedSetting(val device: String, val setting: String)

@Immutable
data class Explanation(
    val decision: String,
    val at: Long,
    val rationale: String,
    val episode: String,
    val episodeKind: String,
    val context: List<String>,
    val chosen: List<Option>,
    val rejected: List<Option>,
    val evidence: List<EvidenceRow>,
    val constraints: List<Constraint>,
    val sharedSummary: String,
    val applied: List<AppliedSetting>,
) {
    /** Your own bounds, which carry a reason. */
    val myConstraints: List<Constraint> get() = constraints.filter { it.mine }

    /** Everyone else's: a range, never a reason. */
    val otherConstraints: List<Constraint> get() = constraints.filter { !it.mine }

    companion object {
        fun parse(json: JSONObject): Explanation {
            val p = json.optJSONObject("private") ?: JSONObject()
            val s = json.optJSONObject("shared") ?: JSONObject()
            return Explanation(
                decision = json.optString("decision"),
                at = p.optLong("at"),
                rationale = p.optString("rationale").repaired(),
                episode = p.optString("episode").repaired(),
                episodeKind = p.optString("episode_kind"),
                context = p.optJSONArray("context").strings(),
                chosen = p.optJSONArray("chosen").map { o ->
                    Option(
                        name = o.optString("name"),
                        label = o.optString("label").repaired(),
                        score = o.floatOrNull("score"),
                    )
                },
                // The backend emits a single all-null row when nothing was rejected, so anything
                // without a name is dropped rather than rendered as an empty option.
                rejected = p.optJSONArray("rejected").map { o ->
                    Option(
                        name = o.stringOrNull("name") ?: "",
                        label = (o.stringOrNull("because") ?: "").repaired(),
                        score = null,
                    )
                }.filter { it.name.isNotEmpty() },
                evidence = p.optJSONArray("evidence").map { o ->
                    EvidenceRow(
                        intervention = o.optString("intervention"),
                        label = o.optString("intervention"),
                        n = o.optInt("n"),
                        learnedN = if (o.optString("source") == "literature_prior") 0 else o.optInt("n"),
                        mean = o.optDouble("mean", 0.0).toFloat(),
                    )
                },
                constraints = p.optJSONArray("constraints").map { o ->
                    Constraint(
                        variable = o.optString("var"),
                        min = o.floatOrNull("min"),
                        max = o.floatOrNull("max"),
                        mine = o.optBoolean("mine"),
                        reason = o.stringOrNull("reason"),
                    )
                },
                sharedSummary = s.optString("summary").repaired(),
                applied = s.optJSONArray("applied").map { o ->
                    AppliedSetting(device = o.optString("device"), setting = o.optString("setting"))
                },
            )
        }
    }
}

/**
 * A condition the home currently believes about you, and when it lapses.
 *
 * [implies] is the multi-hop expansion the graph does for free: logging a fever also makes you
 * sick, which is what actually shifts the room. Worth showing, because it is the clearest small
 * example of the graph reasoning rather than just storing.
 */
@Immutable
data class Condition(
    val kind: String,
    val label: String,
    val since: Long?,
    val until: Long?,
    val source: String?,
    val implies: List<String>,
) {
    val displayName: String get() = label.ifEmpty { kind.replace('_', ' ') }
}

@Immutable
data class ComfortRange(
    val variable: String,
    val min: Float?,
    val max: Float?,
    val feasible: Boolean,
    val priority: Int,
    val conditions: List<String>,
)

/**
 * What this person's own body usually does, as stored in their `Baseline` nodes.
 *
 * Often empty: a baseline needs a few nights of real data before it exists, and the running backend
 * had only `rmssd` for two of four people. Screens must therefore treat a missing baseline as a
 * normal state and show the bare reading with a note, never a fabricated band.
 */
@Immutable
data class SignalBaseline(val signal: String, val mean: Float, val sd: Float, val n: Int)

@Immutable
data class PersonContext(
    val person: String,
    val conditions: List<Condition>,
    val comfort: List<ComfortRange>,
    val baselines: List<SignalBaseline>,
    val present: List<String>,
) {
    fun baseline(signal: String): SignalBaseline? =
        baselines.firstOrNull { it.signal == signal && it.sd > 0f }

    companion object {
        fun parse(json: JSONObject) = PersonContext(
            person = json.optString("person"),
            conditions = json.optJSONArray("conditions").map { o ->
                Condition(
                    kind = o.optString("kind"),
                    label = o.optString("label"),
                    since = o.optLong("since").takeIf { it > 0 },
                    until = o.optLong("until").takeIf { it > 0 },
                    source = o.stringOrNull("source"),
                    implies = o.optJSONArray("implies").strings().filter { it.isNotEmpty() },
                )
            },
            comfort = json.optJSONArray("comfort").map { o ->
                ComfortRange(
                    variable = o.optString("var"),
                    min = o.floatOrNull("min"),
                    max = o.floatOrNull("max"),
                    feasible = o.optBoolean("feasible", true),
                    priority = o.optInt("priority"),
                    conditions = o.optJSONArray("conditions").strings(),
                )
            },
            baselines = json.optJSONArray("baselines").map { o ->
                SignalBaseline(
                    signal = o.optString("signal"),
                    mean = o.optDouble("mean", 0.0).toFloat(),
                    sd = o.optDouble("sd", 0.0).toFloat(),
                    n = o.optInt("n"),
                )
            },
            present = json.optJSONArray("present").strings(),
        )
    }
}

@Immutable
data class Episode(
    val id: String,
    val kind: String,
    val start: Long,
    val quality: String?,
    val summary: String,
    /** The measured outcome, once the evaluator has closed the episode. Null while still open. */
    val effect: Float?,
    val status: String?,
) {
    val wasGood: Boolean? get() = quality?.let { it == "good" }
    val isOpen: Boolean get() = status == "open"
}

/**
 * A driver of bad nights, from the Scientist's PageRank pass.
 *
 * [lift] is the interesting number: how much more often nights with this band went badly than this
 * person's own usual rate. On the public p01 dataset, `sleep_window:short` came out at 1.63x over
 * 63 nights.
 */
@Immutable
data class Driver(
    val band: String,
    val score: Float,
    val probabilityBad: Float,
    val lift: Float,
    val nights: Int,
    val badNights: Int,
) {
    companion object {
        fun parseList(a: JSONArray?) = a.map {
            Driver(
                band = it.optString("band"),
                score = it.optDouble("score", 0.0).toFloat(),
                probabilityBad = it.optDouble("p_bad", 0.0).toFloat(),
                lift = it.optDouble("lift", 1.0).toFloat(),
                nights = it.optInt("nights"),
                badNights = it.optInt("bad_nights"),
            )
        }
    }
}

/** Procedural memory: the ordered steps the home follows for a condition, and its version. */
@Immutable
data class Playbook(
    val name: String,
    val version: Int,
    val condition: String,
    val steps: List<String>,
    val earlierVersions: Int,
    val since: Long,
    val note: String?,
) {
    companion object {
        fun parseList(a: JSONArray?) = a.map {
            Playbook(
                name = it.optString("playbook"),
                version = it.optInt("version"),
                condition = it.optString("condition"),
                steps = it.optJSONArray("steps").strings().map { s -> s.repaired() },
                earlierVersions = it.optInt("earlier_versions"),
                since = it.optLong("since"),
                note = it.stringOrNull("note")?.repaired(),
            )
        }
    }
}

/** The bi-temporal belief diff: what the home changed its mind about, and when. */
@Immutable
data class BeliefChange(
    val kind: String,
    val about: String,
    val change: String,
    val min: Float?,
    val max: Float?,
    val source: String?,
    val at: Long,
) {
    companion object {
        fun parseList(a: JSONArray?) = a.map {
            BeliefChange(
                kind = it.optString("kind"),
                about = it.optString("about"),
                change = it.optString("change"),
                min = it.floatOrNull("min"),
                max = it.floatOrNull("max"),
                source = it.stringOrNull("source"),
                at = it.optLong("at"),
            )
        }
    }
}

@Immutable
data class GraphStat(val graph: String, val nodes: Int, val edges: Int)

/** A node as the graph view needs it: identity, its label, and something to call it. */
@Immutable
data class GraphNode(val id: String, val label: String, val caption: String)

@Immutable
data class GraphEdge(val source: String, val target: String, val type: String)

@Immutable
data class GraphData(val nodes: List<GraphNode>, val edges: List<GraphEdge>) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    companion object {
        val Empty = GraphData(emptyList(), emptyList())

        /**
         * Builds a drawable graph from `/api/graph/:alias`.
         *
         * Each node carries a numeric id and a `props` bag whose useful name differs by label, so
         * the caption falls back through the fields that actually exist: a Person has an alias, a
         * Band has a key, an Episode has a summary, and so on. Edges that point at a node outside
         * the returned page are dropped, because a line to nowhere is worse than a missing line.
         */
        fun parse(json: JSONObject): GraphData {
            val nodes = (json.optJSONArray("nodes")).map { n ->
                val props = n.optJSONObject("props") ?: JSONObject()
                val label = n.optString("label")
                val caption = listOf("alias", "name", "label", "kind", "key", "id", "signal", "var")
                    .firstNotNullOfOrNull { key -> props.optString(key).takeIf { it.isNotEmpty() } }
                    ?: label
                GraphNode(
                    id = n.optString("id"),
                    label = label,
                    caption = caption.repaired().take(42),
                )
            }.filter { it.id.isNotEmpty() }

            val known = nodes.mapTo(HashSet()) { it.id }
            val edges = (json.optJSONArray("edges")).map { e ->
                GraphEdge(
                    source = e.optString("source"),
                    target = e.optString("target"),
                    type = e.optString("type"),
                )
            }.filter { it.source in known && it.target in known && it.source != it.target }

            return GraphData(nodes, edges)
        }
    }
}

@Immutable
data class AskReply(val reply: String, val tools: List<String>)
