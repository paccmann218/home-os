package com.homeos.bridge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Something the home told everyone, over `/ws/ui`. */
sealed interface UiEvent {
    data class Feed(val item: FeedItem) : UiEvent
    data class Signal(val person: String, val at: Long, val hr: Float?, val rmssd: Float?) : UiEvent
    data class Room(val room: LiveRoom) : UiEvent
    data class DeviceChanged(val device: String, val state: JSONObject) : UiEvent
    data class DecisionMade(val person: String, val decision: String) : UiEvent
    data class Learned(val person: String, val kind: String) : UiEvent
}

/** Something the home told *this phone*, over `/ws/device`. */
sealed interface DeviceEvent {
    data class Hello(val alias: String, val voice: Boolean, val graph: Boolean) : DeviceEvent
    /** Speak this, show it, notify. */
    data class Say(val text: String) : DeviceEvent
    /** A yes/no question that expects `answerPrompt`. */
    data class Prompt(val id: String, val text: String) : DeviceEvent
    data class Heard(val text: String) : DeviceEvent
    data class Failed(val message: String) : DeviceEvent
    data object Opened : DeviceEvent
    data class Closed(val reason: String?) : DeviceEvent
}

/**
 * Everything this app knows how to say to HomeOS.
 *
 * The phone keeps **two** sockets open, which is the part that is easy to get wrong:
 *
 *  - `/ws/device?alias=&kind=phone` is this phone's own identity. It is bidirectional: chips, asks
 *    and prompt answers go up it, and `say` / `prompt` come back down it. Only this socket gets
 *    spoken replies, because they are meant for the person holding the phone.
 *  - `/ws/ui` is the household broadcast: live heart rate, room readings, device changes, decisions
 *    and learning. Read-only.
 *
 * The old single-socket app could only ever see its own traffic, which is why the room and the rest
 * of the household never appeared on screen.
 */
class HomeOsClient(private val baseUrl: () -> String) {

    private val rest = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Sockets must never time out on an idle read; the ping interval is what detects a dead link.
    private val sockets = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        rest.newCall(Request.Builder().url(baseUrl() + path).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} on $path")
            response.body?.string().orEmpty()
        }
    }

    private suspend fun post(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl() + path)
            .post(body.toString().toRequestBody(json))
            .build()
        rest.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} on $path")
            response.body?.string().orEmpty()
        }
    }

    suspend fun state(): HomeState = HomeState.parse(JSONObject(get("/api/state")))

    suspend fun stats(): List<GraphStat> {
        val o = JSONObject(get("/api/stats"))
        return o.keys().asSequence().mapNotNull { key ->
            o.optJSONObject(key)?.let { GraphStat(key, it.optInt("nodes"), it.optInt("edges")) }
        }.toList()
    }

    suspend fun decisions(alias: String, limit: Int = 5): List<Decision> =
        Decision.parseList(JSONArray(get("/api/decisions/$alias?limit=$limit")))

    suspend fun explain(alias: String, decision: String): Explanation =
        Explanation.parse(JSONObject(get("/api/tool/$alias/explain?decision=${decision.urlEncoded()}")))

    suspend fun evidence(alias: String, kind: String): List<EvidenceRow> =
        EvidenceRow.parseList(JSONArray(get("/api/evidence/$alias/$kind")))

    suspend fun context(alias: String): PersonContext =
        PersonContext.parse(JSONObject(get("/api/tool/$alias/get_context")))

    /** The Scientist's PageRank drivers of bad nights. */
    suspend fun drivers(alias: String): List<Driver> =
        Driver.parseList(JSONArray(get("/api/tool/$alias/drivers")))

    suspend fun playbooks(alias: String, condition: String): List<Playbook> =
        Playbook.parseList(JSONArray(get("/api/tool/$alias/playbooks_for?condition=$condition")))

    suspend fun beliefDiff(alias: String): List<BeliefChange> =
        BeliefChange.parseList(JSONArray(get("/api/tool/$alias/belief_diff")))

    /**
     * The person's episode timeline.
     *
     * There is no dedicated episodes route, so this filters the person's subgraph. It pulls more
     * than it needs; a `GET /api/episodes/:alias` would be tidier, but this needs no backend change
     * and the node list already arrives newest-first.
     */
    suspend fun episodes(alias: String, limit: Int = 350): List<Episode> {
        val graph = JSONObject(get("/api/graph/$alias?limit=$limit"))
        val nodes = graph.optJSONArray("nodes") ?: return emptyList()
        return (0 until nodes.length())
            .mapNotNull { nodes.optJSONObject(it) }
            .filter { it.optString("label") == "Episode" }
            .mapNotNull { node ->
                val props = node.optJSONObject("props") ?: node
                val id = props.optString("id").ifEmpty { return@mapNotNull null }
                Episode(
                    id = id,
                    kind = props.optString("kind"),
                    start = props.optLong("start"),
                    quality = props.optString("quality").takeIf { it.isNotEmpty() },
                    summary = props.optString("summary").repaired(),
                    effect = if (props.isNull("effect")) null else props.optDouble("effect").toFloat(),
                    status = props.optString("status").takeIf { it.isNotEmpty() },
                )
            }
            .sortedByDescending { it.start }
    }

    /**
     * The whole subgraph for a person, or `home` for the shared one.
     *
     * `limit` bounds the query by **paths**, not by nodes - the route is a `MATCH p=(a)-[r]->(b)
     * ... LIMIT n`, so it caps edges and the node count falls out of whatever those edges touch.
     * That is worth knowing before tuning it: 220 returned 61 of p01's 254 nodes, barely a quarter
     * of the picture, and 900 returns 158. Beyond that the layout stops being legible on a phone
     * long before it stops being fast.
     */
    suspend fun graph(alias: String, limit: Int = 900): GraphData =
        GraphData.parse(JSONObject(get("/api/graph/$alias?limit=$limit")))

    suspend fun chip(alias: String, chip: String) {
        post("/api/chip/$alias", JSONObject().put("chip", chip).put("source", "phone"))
    }

    suspend fun ask(alias: String, text: String): AskReply {
        val body = JSONObject().put("text", text).put("speak", false).put("channel", "phone")
        val o = JSONObject(post("/api/ask/$alias", body))
        val tools = o.optJSONArray("tools")
        return AskReply(
            reply = o.optString("reply").repaired(),
            tools = (0 until (tools?.length() ?: 0)).mapNotNull { i ->
                tools?.opt(i)?.let { item ->
                    when (item) {
                        is JSONObject -> item.optString("tool").ifEmpty { item.optString("name") }
                        else -> item.toString()
                    }
                }?.takeIf { it.isNotEmpty() }
            },
        )
    }

    suspend fun answerPrompt(alias: String, id: String, answer: String) {
        post("/api/prompt/$alias", JSONObject().put("id", id).put("answer", answer))
    }

    /** The household broadcast. Reconnection is the repository's job, not this flow's. */
    fun uiEvents(): Flow<UiEvent> = channelFlow {
        val url = baseUrl().replaceFirst("http", "ws") + "/ws/ui"
        val socket = sockets.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val envelope = runCatching { JSONObject(text) }.getOrNull() ?: return
                    val data = envelope.optJSONObject("data") ?: return
                    val event = when (envelope.optString("type")) {
                        "feed" -> UiEvent.Feed(data.toFeedItem())
                        "signal" -> UiEvent.Signal(
                            person = data.optString("person"),
                            at = data.optLong("t"),
                            hr = data.optDouble("hr").takeIf { !it.isNaN() }?.toFloat(),
                            rmssd = data.optDouble("rmssd").takeIf { !it.isNaN() }?.toFloat(),
                        )
                        // Through the same normalisers the poll path uses. `bright` is a JSON
                        // boolean from the ESP32 and `optDouble` on a boolean returns NaN, so
                        // reading it as a number nulled the live value on every single event and
                        // the only brightness that ever arrived came from the 6s poll behind it.
                        "room" -> UiEvent.Room(
                            LiveRoom(
                                at = data.optLong("t"),
                                temperature = data.floatOrNull("temperature"),
                                humidity = data.floatOrNull("humidity"),
                                bright = data.brightnessOrNull("bright"),
                            ),
                        )
                        "device" -> UiEvent.DeviceChanged(
                            device = data.optString("device"),
                            state = data.optJSONObject("state") ?: JSONObject(),
                        )
                        "decision" -> UiEvent.DecisionMade(
                            person = data.optString("person"),
                            decision = data.optString("decision"),
                        )
                        "learned" -> UiEvent.Learned(
                            person = data.optString("person"),
                            kind = data.optString("kind"),
                        )
                        else -> null
                    }
                    if (event != null) trySend(event)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }
            },
        )
        awaitClose { socket.close(1000, null) }
    }

    /**
     * This phone's own link. [send] is handed back so the caller can push chips and asks up the
     * same socket the replies arrive on.
     */
    fun deviceLink(alias: String, onSocket: (WebSocket?) -> Unit = {}): Flow<DeviceEvent> = channelFlow {
        val url = baseUrl().replaceFirst("http", "ws") + "/ws/device?alias=$alias&kind=phone"
        val socket = sockets.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onSocket(webSocket)
                    trySend(DeviceEvent.Opened)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val o = runCatching { JSONObject(text) }.getOrNull() ?: return
                    val event = when (o.optString("type")) {
                        "hello" -> DeviceEvent.Hello(
                            alias = o.optString("alias"),
                            voice = o.optBoolean("voice"),
                            graph = o.optBoolean("graph"),
                        )
                        "say" -> DeviceEvent.Say(o.optString("text").repaired())
                        "prompt" -> DeviceEvent.Prompt(o.optString("id"), o.optString("text").repaired())
                        "heard" -> DeviceEvent.Heard(o.optString("text"))
                        "error" -> DeviceEvent.Failed(o.optString("error"))
                        else -> null
                    }
                    if (event != null) trySend(event)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onSocket(null)
                    trySend(DeviceEvent.Closed(t.message))
                    close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onSocket(null)
                    trySend(DeviceEvent.Closed(reason.ifEmpty { null }))
                    close()
                }
            },
        )
        awaitClose {
            onSocket(null)
            socket.close(1000, null)
        }
    }
}

private fun String.urlEncoded(): String =
    java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
