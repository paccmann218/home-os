package com.homeos.bridge.data

import androidx.compose.runtime.Immutable
import com.homeos.bridge.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import org.json.JSONObject
import java.time.LocalTime

@Immutable
data class Reading(val at: Long, val value: Float)

/** A question from the home that is waiting on an answer. */
@Immutable
data class PendingPrompt(val id: String, val text: String)

@Immutable
data class AppState(
    val home: HomeState = HomeState(),
    /** Who is holding the phone. */
    val alias: String = "",
    /** The `/ws/device` link for this person. */
    val linked: Boolean = false,
    /** Whether the most recent poll succeeded. */
    val reachable: Boolean = false,
    /** Consecutive failed polls. One blip should not flash a banner at someone. */
    val failures: Int = 0,
    val lastError: String? = null,
    val hr: Map<String, List<Reading>> = emptyMap(),
    val latestDecision: Decision? = null,
    val context: PersonContext? = null,
    val prompt: PendingPrompt? = null,
    /** Newest first, bounded. Dev mode renders this verbatim. */
    val log: List<String> = emptyList(),
) {
    val me: Person? get() = home.people.firstOrNull { it.alias == alias }
    val others: List<Person> get() = home.people.filter { it.alias != alias }
    val myBody: LiveBody? get() = home.body[alias]
    fun hrOf(alias: String): List<Reading> = hr[alias].orEmpty()

    val ambientTemperature: Float? get() = home.roomTemperature

    /**
     * Whether to tell the person the home is unreachable.
     *
     * Deliberately not the same as `!reachable`. A single dropped poll is common on a phone and
     * self-heals within six seconds; banner-flashing on every blip trains people to ignore the
     * banner, which is worse than showing it late.
     */
    val offline: Boolean get() = failures >= 2

    /** Active conditions, newest expiry last. Empty is a meaningful state, not a loading state. */
    val myConditions: List<Condition> get() = context?.conditions.orEmpty()
}

/**
 * The single source of truth for every screen.
 *
 * Shape of the thing: `/api/state` is polled on a slow timer for the full picture, and the two
 * websockets patch that picture between polls. Polling alone would make live heart rate useless;
 * sockets alone would mean a dropped frame is lost forever. Doing both means a missed event
 * self-heals within one poll.
 *
 * Reconnection is here rather than in the client so that a dropped socket is a state change the UI
 * can show, instead of silence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeOsRepository(
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    private val client = HomeOsClient { prefs.url }

    private val _state = MutableStateFlow(AppState(alias = prefs.alias))
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** `say` and `prompt` need a speaker and a notification, which are the activity's job. */
    private val _spoken = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 16)
    val spoken: SharedFlow<DeviceEvent> = _spoken

    /** Identity and server, together: changing either has to rebuild the device socket. */
    private val link = MutableStateFlow(prefs.url to prefs.alias)

    private var deviceSocket: WebSocket? = null
    private var started = false

    private companion object {
        const val POLL_MS = 6_000L
        const val HR_WINDOW_MS = 6 * 60_000L
        const val LOG_LINES = 120
        const val BACKOFF_MIN_MS = 1_000L
        const val BACKOFF_MAX_MS = 20_000L
    }

    fun start() {
        if (started) return
        started = true
        scope.launch { pollLoop() }
        scope.launch { uiLoop() }
        scope.launch { deviceLoop() }
    }

    fun log(line: String) {
        val stamped = "${LocalTime.now().withNano(0)}  $line"
        _state.update { it.copy(log = (listOf(stamped) + it.log).take(LOG_LINES)) }
    }

    /** Switch who is holding the phone. Rebuilds the device socket and reloads that person's view. */
    fun switchPerson(alias: String) {
        if (alias == _state.value.alias) return
        prefs.alias = alias
        _state.update { it.copy(alias = alias, latestDecision = null, context = null, prompt = null) }
        link.value = prefs.url to alias
        log("Now acting as $alias")
        scope.launch { refreshPerson(alias) }
    }

    /** Change the server. Used by dev mode. */
    fun setServer(url: String) {
        prefs.url = url
        link.value = prefs.url to _state.value.alias
        log("Server set to ${prefs.url}")
        scope.launch { refreshNow() }
    }

    suspend fun refreshNow() {
        runCatching { client.state() }
            .onSuccess { home ->
                _state.update { it.copy(home = home, reachable = true, failures = 0, lastError = null) }
                refreshPerson(_state.value.alias)
            }
            .onFailure { e ->
                _state.update {
                    it.copy(reachable = false, failures = it.failures + 1, lastError = e.message)
                }
            }
    }

    private suspend fun refreshPerson(alias: String) {
        if (alias.isEmpty()) return
        runCatching { client.decisions(alias, limit = 1).firstOrNull() }
            .onSuccess { decision -> _state.update { it.copy(latestDecision = decision) } }
        runCatching { client.context(alias) }
            .onSuccess { context -> _state.update { it.copy(context = context) } }
    }

    private suspend fun pollLoop() {
        while (true) {
            refreshNow()
            delay(POLL_MS)
        }
    }

    private suspend fun uiLoop() {
        var backoff = BACKOFF_MIN_MS
        while (true) {
            runCatching {
                client.uiEvents().collect { event ->
                    backoff = BACKOFF_MIN_MS
                    apply(event)
                }
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
        }
    }

    /**
     * Keeps exactly one `/ws/device` link open for the current identity.
     *
     * `collectLatest` cancels the inner loop the moment the person or server changes, so switching
     * identity can never leave two sockets claiming to be this phone. The inner loop handles the
     * ordinary case of the socket simply dropping — note that it cannot be done by re-setting
     * `link`, because a StateFlow drops a value equal to the one it already holds.
     */
    private suspend fun deviceLoop() {
        link.collectLatest { (_, alias) ->
            if (alias.isEmpty()) return@collectLatest
            var backoff = BACKOFF_MIN_MS
            while (true) {
                runCatching {
                    client.deviceLink(alias) { socket -> deviceSocket = socket }
                        .collect { event ->
                            if (event is DeviceEvent.Opened) backoff = BACKOFF_MIN_MS
                            handleDeviceEvent(event)
                        }
                }
                _state.update { it.copy(linked = false) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
            }
        }
    }

    private suspend fun handleDeviceEvent(event: DeviceEvent) {
        when (event) {
            is DeviceEvent.Opened -> {
                _state.update { it.copy(linked = true) }
                log("Linked to HomeOS as ${_state.value.alias}")
            }
            is DeviceEvent.Closed -> {
                _state.update { it.copy(linked = false) }
                log("Link closed${event.reason?.let { r -> ": $r" } ?: ""}")
            }
            is DeviceEvent.Hello -> log("Hello: voice=${event.voice} graph=${event.graph}")
            is DeviceEvent.Say -> {
                log("HomeOS: ${event.text}")
                _spoken.emit(event)
            }
            is DeviceEvent.Prompt -> {
                _state.update { it.copy(prompt = PendingPrompt(event.id, event.text)) }
                _spoken.emit(event)
            }
            is DeviceEvent.Heard -> log("Heard: ${event.text}")
            is DeviceEvent.Failed -> log("Error: ${event.message}")
        }
    }

    private fun apply(event: UiEvent) {
        when (event) {
            is UiEvent.Feed -> _state.update { current ->
                current.copy(home = current.home.copy(feed = (current.home.feed + event.item).takeLast(200)))
            }

            is UiEvent.Signal -> {
                val hr = event.hr ?: return
                val cutoff = event.at - HR_WINDOW_MS
                _state.update { current ->
                    val series = current.hrOf(event.person)
                        .filter { it.at >= cutoff } + Reading(event.at, hr)
                    current.copy(hr = current.hr + (event.person to series))
                }
            }

            is UiEvent.Room -> _state.update { current ->
                current.copy(home = current.home.copy(room = event.room))
            }

            is UiEvent.DeviceChanged -> _state.update { current ->
                current.copy(home = current.home.copy(devices = current.home.devices.patch(event)))
            }

            // A decision for me means my view is stale in a way polling would take 6s to notice.
            is UiEvent.DecisionMade -> if (event.person == _state.value.alias) {
                scope.launch { refreshPerson(event.person) }
            }

            is UiEvent.Learned -> if (event.person == _state.value.alias) {
                log("Learned something new about ${event.kind}")
            }
        }
    }

    suspend fun sendChip(chip: String) {
        val alias = _state.value.alias
        runCatching { client.chip(alias, chip) }
            .onSuccess {
                log("Checked in: $chip")
                refreshPerson(alias)
            }
            .onFailure { e -> log("Check-in failed: ${e.message}") }
    }

    suspend fun ask(text: String): Result<AskReply> {
        val alias = _state.value.alias
        return runCatching { client.ask(alias, text) }
            .onFailure { e -> log("Ask failed: ${e.message}") }
    }

    suspend fun answerPrompt(answer: String) {
        val prompt = _state.value.prompt ?: return
        _state.update { it.copy(prompt = null) }
        runCatching { client.answerPrompt(_state.value.alias, prompt.id, answer) }
            .onFailure { e -> log("Prompt answer failed: ${e.message}") }
    }

    suspend fun explain(decision: String): Result<Explanation> =
        runCatching { client.explain(_state.value.alias, decision) }

    suspend fun evidence(kind: String): Result<List<EvidenceRow>> =
        runCatching { client.evidence(_state.value.alias, kind) }

    suspend fun episodes(): Result<List<Episode>> =
        runCatching { client.episodes(_state.value.alias) }

    suspend fun stats(): Result<List<GraphStat>> = runCatching { client.stats() }

    suspend fun drivers(): Result<List<Driver>> = runCatching { client.drivers(_state.value.alias) }

    suspend fun playbooks(condition: String): Result<List<Playbook>> =
        runCatching { client.playbooks(_state.value.alias, condition) }

    suspend fun beliefDiff(): Result<List<BeliefChange>> =
        runCatching { client.beliefDiff(_state.value.alias) }

    /** [which] is a person's alias for their private graph, or "home" for the shared one. */
    suspend fun graph(which: String): Result<GraphData> = runCatching { client.graph(which) }

    /** Push a chip up the device socket when it is open; the REST route is the fallback. */
    fun sendOverSocket(payload: JSONObject): Boolean =
        deviceSocket?.send(payload.toString()) == true
}

/** Merge a `device` event into the cached list without dropping fields the event omits. */
private fun List<Device>.patch(event: UiEvent.DeviceChanged): List<Device> = map { device ->
    if (device.id != event.device) {
        device
    } else {
        val s = event.state
        device.copy(
            power = if (s.has("power")) s.optString("power") else device.power,
            setpoint = if (s.has("setpoint")) s.optInt("setpoint") else device.setpoint,
            level = if (s.has("level")) s.optInt("level") else device.level,
            brightness = if (s.has("brightness")) s.optInt("brightness") else device.brightness,
            // `?:` rather than `if (has)`, because these three had no NaN guard at all: a device
            // delta for the env sensor carries `bright` as a boolean, `optDouble` returned NaN,
            // and NaN went straight into state. Falling back to the previous value also gives the
            // behaviour this function is for - a delta omits what has not changed.
            temperature = s.floatOrNull("temperature") ?: device.temperature,
            humidity = s.floatOrNull("humidity") ?: device.humidity,
            bright = s.brightnessOrNull("bright") ?: device.bright,
        )
    }
}
