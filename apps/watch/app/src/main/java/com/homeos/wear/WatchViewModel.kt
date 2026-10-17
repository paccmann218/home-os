package com.homeos.wear

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class PromptUi(val id: String, val text: String, val options: List<String>)

data class WatchUi(
    val alias: String = BuildConfig.HOMEOS_ALIAS,
    val connected: Boolean = false,
    val hr: Int? = null,
    val ibi: Boolean = false,
    val status: String = "Connecting…",
    val lastSaid: String = "",
    val heard: String = "",
    val recording: Boolean = false,
    val thinking: Boolean = false,
    val prompt: PromptUi? = null,
    val pacerBpm: Double? = null,
    val pacerEndsAt: Long = 0,
    val serverVoice: Boolean = false,
)

class WatchViewModel(app: Application) : AndroidViewModel(app) {
    private val client = HomeOsClient()
    private val audio = AudioBridge(app)
    private val pacer = Pacer(app)
    private var tts: TextToSpeech? = null
    private var lastHrSent = 0L

    private val _ui = MutableStateFlow(WatchUi())
    val ui: StateFlow<WatchUi> = _ui.asStateFlow()

    private val hr = HeartRateStream(app, onHr = { bpm -> onHeartRate(bpm, null) }, onBody = { client.sendOnBody(it) })
    private val ibi = SamsungIbiStream(app) { bpm, ibis -> onHeartRate(bpm, ibis) }

    init {
        tts = TextToSpeech(app) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN") }
        viewModelScope.launch {
            client.events.collect { e ->
                when (e) {
                    is Inbound.Connected -> _ui.update { it.copy(connected = true, status = "Connected as ${client.alias}") }
                    is Inbound.Disconnected -> _ui.update { it.copy(connected = false, status = "Reconnecting…") }
                    is Inbound.Hello -> _ui.update { it.copy(serverVoice = e.voice) }
                    is Inbound.Say -> onSay(e)
                    is Inbound.Pacer -> startPacer(e.bpm, e.seconds)
                    is Inbound.Prompt -> _ui.update { it.copy(prompt = PromptUi(e.id, e.text, e.options)) }.also { pacer.stop(); buzz() }
                    is Inbound.Heard -> _ui.update { it.copy(heard = e.text) }
                    is Inbound.Error -> _ui.update { it.copy(status = e.message, thinking = false) }
                }
            }
        }
    }

    /**
     * Wear OS routes traffic over the phone's Bluetooth proxy (or turns Wi-Fi off) unless an app asks for
     * Wi-Fi. HomeOS talks to a laptop on the LAN, so hold a Wi-Fi request and bind the process to it.
     */
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            connectivity.bindProcessToNetwork(network)
            client.connect()
        }

        override fun onLost(network: Network) {
            connectivity.bindProcessToNetwork(null)
        }
    }

    fun start() {
        runCatching {
            connectivity.requestNetwork(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback,
            )
        }.onFailure { Log.w("HomeOS", "wifi request failed: ${it.message}") }
        client.connect()
        hr.start()
        val samsung = ibi.start()
        _ui.update { it.copy(ibi = samsung) }
    }

    fun stop() {
        hr.stop()
        ibi.stop()
    }

    private fun onHeartRate(bpm: Double?, ibis: List<Int>?) {
        if (bpm != null) _ui.update { it.copy(hr = bpm.toInt()) }
        val now = System.currentTimeMillis()
        if (!ibis.isNullOrEmpty() || now - lastHrSent >= 1000) {
            lastHrSent = now
            client.sendHr(bpm ?: (_ui.value.hr?.toDouble() ?: return), ibis)
        }
    }

    fun chip(chip: String) {
        _ui.update { it.copy(thinking = true, status = "Telling HomeOS…") }
        client.sendChip(chip)
    }

    fun ask(text: String) {
        if (text.isBlank()) return
        _ui.update { it.copy(heard = text, thinking = true) }
        client.sendAsk(text)
    }

    fun answerPrompt(answer: String) {
        val p = _ui.value.prompt ?: return
        client.sendPromptAnswer(p.id, answer)
        if (p.id.startsWith("fb-")) client.sendChip(answer)
        _ui.update { it.copy(prompt = null) }
    }

    /** Server-side Sarvam STT: record a WAV, send it on the second tap. */
    fun toggleRecording() {
        if (!_ui.value.recording) {
            if (audio.startRecording()) _ui.update { it.copy(recording = true, status = "Listening… tap to send") }
        } else {
            val b64 = audio.stopRecordingToWavBase64()
            _ui.update { it.copy(recording = false, thinking = b64 != null, status = if (b64 != null) "Thinking…" else "Didn't catch that") }
            if (b64 != null) client.sendAudio(b64)
        }
    }

    private fun onSay(e: Inbound.Say) {
        _ui.update { it.copy(lastSaid = e.text, thinking = false, status = "Connected as ${client.alias}") }
        viewModelScope.launch(Dispatchers.IO) {
            val played = e.audioUrl?.let { url -> runCatching { audio.playUrl(url) }.onFailure { Log.w("HomeOS", "tts url: ${it.message}") }.isSuccess } ?: false
            if (!played) tts?.speak(e.text, TextToSpeech.QUEUE_FLUSH, null, "homeos")
        }
    }

    private fun startPacer(bpm: Double, seconds: Int) {
        _ui.update { it.copy(pacerBpm = bpm, pacerEndsAt = System.currentTimeMillis() + seconds * 1000L) }
        pacer.start(bpm, seconds)
    }

    fun stopPacer() {
        pacer.stop()
        _ui.update { it.copy(pacerBpm = null) }
    }

    private fun buzz() = runCatching { Pacer(getApplication()).start(30.0, 1) }

    override fun onCleared() {
        runCatching { connectivity.unregisterNetworkCallback(wifiCallback) }
        stop()
        client.disconnect()
        tts?.shutdown()
        super.onCleared()
    }
}
