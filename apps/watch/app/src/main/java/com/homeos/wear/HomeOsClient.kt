package com.homeos.wear

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Messages the HomeOS backend sends to the watch. */
sealed interface Inbound {
    data class Hello(val voice: Boolean, val graph: Boolean) : Inbound
    data class Say(val text: String, val audioUrl: String?, val lang: String) : Inbound
    data class Pacer(val bpm: Double, val seconds: Int) : Inbound
    data class Prompt(val id: String, val text: String, val options: List<String>) : Inbound
    data class Heard(val text: String) : Inbound
    data class Error(val message: String) : Inbound
    data object Connected : Inbound
    data object Disconnected : Inbound
}

/**
 * WebSocket link to the HomeOS backend (`/ws/device?alias=..&kind=watch`), with backoff reconnect.
 * Reconnect/backoff logic adapted from the team's Rakshak watch client.
 */
class HomeOsClient(
    private val baseWs: String = BuildConfig.HOMEOS_WS,
    val alias: String = BuildConfig.HOMEOS_ALIAS,
) {
    private val url = "$baseWs?alias=$alias&kind=watch"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val wantConnected = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    private val attempt = AtomicInteger(0)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "homeos-ws").apply { isDaemon = true } }
    private var reconnectFuture: ScheduledFuture<*>? = null

    @Volatile private var socket: WebSocket? = null
    @Volatile var connected = false
        private set

    private val _events = MutableSharedFlow<Inbound>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Inbound> = _events.asSharedFlow()

    fun connect() {
        wantConnected.set(true)
        open()
    }

    fun disconnect() {
        wantConnected.set(false)
        reconnectFuture?.cancel(false)
        socket?.close(1000, "bye")
        socket = null
        connected = false
    }

    fun httpOrigin(): String {
        val secure = baseWs.startsWith("wss://")
        val hostPort = baseWs.removePrefix("wss://").removePrefix("ws://").substringBefore('/')
        return (if (secure) "https://" else "http://") + hostPort
    }

    fun sendHr(hr: Double, ibi: List<Int>?) {
        val j = JSONObject().put("type", "hr").put("hr", hr).put("t", System.currentTimeMillis())
        if (!ibi.isNullOrEmpty()) j.put("ibi", JSONArray(ibi))
        send(j, quiet = true)
    }

    fun sendOnBody(on: Boolean) = send(JSONObject().put("type", "onbody").put("on", on), quiet = true)
    fun sendChip(chip: String) = send(JSONObject().put("type", "chip").put("chip", chip))
    fun sendAsk(text: String) = send(JSONObject().put("type", "ask").put("text", text))
    fun sendAudio(wavBase64: String) = send(JSONObject().put("type", "audio").put("wav_base64", wavBase64))
    fun sendPromptAnswer(id: String, answer: String) = send(JSONObject().put("type", "prompt").put("id", id).put("answer", answer))

    private fun send(j: JSONObject, quiet: Boolean = false) {
        val ws = socket
        if (ws == null || !connected) {
            if (!quiet) _events.tryEmit(Inbound.Error("Offline — reconnecting…"))
            scheduleReconnect()
            return
        }
        ws.send(j.toString())
    }

    private fun open() {
        if (!wantConnected.get() || connected || socket != null) return
        if (!connecting.compareAndSet(false, true)) return
        socket = try {
            client.newWebSocket(Request.Builder().url(url).build(), Listener())
        } catch (e: Exception) {
            connecting.set(false)
            _events.tryEmit(Inbound.Error("Bad HomeOS URL: ${e.message}"))
            null
        }
    }

    private fun scheduleReconnect() {
        if (!wantConnected.get()) return
        reconnectFuture?.cancel(false)
        val n = attempt.getAndIncrement().coerceAtMost(4)
        reconnectFuture = scheduler.schedule({ open() }, (500L shl n).coerceAtMost(8000L), TimeUnit.MILLISECONDS)
    }

    private fun resolve(path: String?): String? {
        if (path.isNullOrBlank() || path == "null") return null
        if (path.startsWith("http")) return path
        return httpOrigin() + (if (path.startsWith("/")) path else "/$path")
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connecting.set(false)
            connected = true
            attempt.set(0)
            _events.tryEmit(Inbound.Connected)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val j = JSONObject(text)
                when (j.optString("type")) {
                    "hello" -> _events.tryEmit(Inbound.Hello(j.optBoolean("voice"), j.optBoolean("graph", true)))
                    "say" -> _events.tryEmit(Inbound.Say(j.optString("text"), resolve(j.optString("audio_url", "")), j.optString("lang", "en-IN")))
                    "pacer" -> _events.tryEmit(Inbound.Pacer(j.optDouble("bpm", 6.0), j.optInt("seconds", 120)))
                    "prompt" -> {
                        val opts = j.optJSONArray("options") ?: JSONArray()
                        _events.tryEmit(Inbound.Prompt(j.optString("id"), j.optString("text"), List(opts.length()) { opts.getString(it) }))
                    }
                    "heard" -> _events.tryEmit(Inbound.Heard(j.optString("text")))
                    "error" -> _events.tryEmit(Inbound.Error(j.optString("error")))
                }
            } catch (e: Exception) {
                _events.tryEmit(Inbound.Error(e.message ?: "parse error"))
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = drop(null)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = drop(t.message)

        private fun drop(msg: String?) {
            connecting.set(false)
            connected = false
            socket = null
            _events.tryEmit(Inbound.Disconnected)
            if (msg != null) _events.tryEmit(Inbound.Error("Can't reach HomeOS ($msg)"))
            scheduleReconnect()
        }
    }
}
