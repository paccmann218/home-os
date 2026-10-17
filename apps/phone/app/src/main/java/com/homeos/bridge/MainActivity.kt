package com.homeos.bridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * HomeOS Bridge: the ring wearer's phone. Syncs Gabit data from Health Connect into HomeOS,
 * and gives the same quick inputs as the watch (chips, questions) plus prompts/notifications.
 */
class MainActivity : ComponentActivity() {
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private var ws: WebSocket? = null
    private var tts: TextToSpeech? = null
    private val log = mutableStateOf(listOf<String>())
    private val prompt = mutableStateOf<Pair<String, String>?>(null)
    private lateinit var prefs: Prefs

    private val hcPermissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        add("Health Connect: ${granted.size}/${HealthSync.PERMISSIONS.size} permissions granted")
        if (granted.containsAll(HealthSync.PERMISSIONS - HealthSync.PERMISSIONS.filter { it.contains("BACKGROUND") }.toSet())) SyncWorker.schedule(this)
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN") }
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("homeos", "HomeOS", NotificationManager.IMPORTANCE_HIGH))
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        connect()
        if (HealthSync.available(this)) {
            hcPermissions.launch(HealthSync.PERMISSIONS)
            SyncWorker.schedule(this)
        } else add("Health Connect not available on this phone")
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen() } }
    }

    private fun add(line: String) {
        log.value = (listOf(line) + log.value).take(40)
    }

    private fun connect() {
        val wsUrl = prefs.url.replaceFirst("http", "ws") + "/ws/device?alias=${prefs.alias}&kind=phone"
        ws?.close(1000, null)
        ws = http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = runOnUiThread { add("Connected to HomeOS as ${prefs.alias}") }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val j = JSONObject(text)
                runOnUiThread {
                    when (j.optString("type")) {
                        "say" -> {
                            add("HomeOS: ${j.optString("text")}")
                            tts?.speak(j.optString("text"), TextToSpeech.QUEUE_FLUSH, null, "homeos")
                            notify(j.optString("text"))
                        }
                        "prompt" -> {
                            prompt.value = j.optString("id") to j.optString("text")
                            notify(j.optString("text"))
                        }
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = runOnUiThread { add("HomeOS offline: ${t.message}") }
        })
    }

    private fun notify(text: String) {
        val n = NotificationCompat.Builder(this, "homeos").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("HomeOS").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build()
        runCatching { getSystemService(NotificationManager::class.java).notify(text.hashCode(), n) }
    }

    private suspend fun post(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(prefs.url + path).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { it.body?.string() ?: "" }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf(prefs.url) }
        var alias by remember { mutableStateOf(prefs.alias) }
        var question by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val lines by log
        val p by prompt
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0B1220)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("HomeOS Bridge", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF4FD1C5))
                Text("Ring → Health Connect → HomeOS · last sync: ${prefs.lastSync}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(url, { url = it }, label = { Text("Server") }, modifier = Modifier.weight(2f), singleLine = true)
                    OutlinedTextField(alias, { alias = it }, label = { Text("Person") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ prefs.url = url; prefs.alias = alias; connect() }) { Text("Save") }
                    Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val r = runCatching { withContext(Dispatchers.IO) { HealthSync.sync(this@MainActivity, prefs.url, prefs.alias, 2) } }
                            add(r.getOrElse { "Sync failed: ${it.message}" }); prefs.lastSync = "${java.time.LocalTime.now().withNano(0)} (manual)"; busy = false
                        }
                    }) { Text("Sync now") }
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val r = runCatching { withContext(Dispatchers.IO) { HealthSync.sync(this@MainActivity, prefs.url, prefs.alias, 120) } }
                            add(r.getOrElse { "Backfill failed: ${it.message}" }); busy = false
                        }
                    }) { Text("Backfill 120 d") }
                }
                if (p != null) Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p!!.second)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("yes", "no").forEach { a ->
                                Button({ scope.launch { post("/api/prompt/${prefs.alias}", JSONObject().put("id", p!!.first).put("answer", a)) }; prompt.value = null }) { Text(a) }
                            }
                        }
                    }
                }
                Text("How are you?", color = Color.White)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("sick" to "🤒 Sick", "too_hot" to "🥵 Too hot", "too_cold" to "🥶 Too cold", "workout" to "🏃 Workout", "stressed" to "😮‍💨 Stressed",
                        "headache" to "🤕 Headache", "sleep" to "🌙 Sleep", "period" to "🩸 Period", "fine" to "🙂 Fine", "better" to "👍 Better", "worse" to "👎 Worse").forEach { (k, l) ->
                        OutlinedButton({ scope.launch { add("You: $l"); runCatching { post("/api/chip/${prefs.alias}", JSONObject().put("chip", k).put("source", "phone")) } } }) { Text(l) }
                    }
                }
                OutlinedTextField(question, { question = it }, label = { Text("Ask HomeOS (why…? what helps me…?)") }, modifier = Modifier.fillMaxWidth())
                Button({
                    val q = question; question = ""
                    scope.launch { add("You: $q"); runCatching { post("/api/ask/${prefs.alias}", JSONObject().put("text", q).put("channel", "phone")) } }
                }) { Text("Ask") }
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1)) }
            }
        }
    }

    override fun onDestroy() {
        ws?.close(1000, null)
        tts?.shutdown()
        super.onDestroy()
    }
}
