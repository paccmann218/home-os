package com.homeos.bridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.homeos.bridge.data.AppState
import com.homeos.bridge.data.Decision
import com.homeos.bridge.data.Device
import com.homeos.bridge.data.DeviceEvent
import com.homeos.bridge.data.Episode
import com.homeos.bridge.data.EvidenceRow
import com.homeos.bridge.data.Explanation
import com.homeos.bridge.data.GraphData
import com.homeos.bridge.data.HomeOsRepository
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsTheme
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.ui.ask.AskScreen
import com.homeos.bridge.ui.ask.Turn
import com.homeos.bridge.ui.checkin.CheckInScreen
import com.homeos.bridge.ui.components.Ambience
import com.homeos.bridge.ui.components.ApplianceCatalogue
import com.homeos.bridge.ui.components.Destination
import com.homeos.bridge.ui.components.DeviceDetail
import com.homeos.bridge.ui.components.HomeOsDock
import com.homeos.bridge.ui.components.HomeOsSheet
import com.homeos.bridge.ui.components.HomeOsTopBar
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.ui.components.PersonAvatar
import com.homeos.bridge.ui.components.Shell
import com.homeos.bridge.ui.dev.DevScreen
import com.homeos.bridge.ui.home.HomeScreen
import com.homeos.bridge.ui.memory.MemoryData
import com.homeos.bridge.ui.memory.MemoryScreen
import com.homeos.bridge.ui.why.WhyContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * HomeOS Bridge.
 *
 * The activity owns only what has to live on the platform: permissions, text-to-speech,
 * notifications, and the Health Connect sync. Everything about the home's state belongs to
 * [HomeOsRepository], and everything about how it looks belongs to the screens.
 */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repository: HomeOsRepository
    private var tts: TextToSpeech? = null

    private val healthPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        repository.log("Health Connect: ${granted.size}/${HealthSync.PERMISSIONS.size} permissions granted")
        val needed = HealthSync.PERMISSIONS - HealthSync.PERMISSIONS.filter { it.contains("BACKGROUND") }.toSet()
        if (granted.containsAll(needed)) SyncWorker.schedule(this)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        repository = HomeOsRepository(prefs, lifecycleScope)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN")
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("homeos", "HomeOS", NotificationManager.IMPORTANCE_HIGH),
        )
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        repository.start()
        if (HealthSync.available(this)) {
            healthPermissions.launch(HealthSync.PERMISSIONS)
            SyncWorker.schedule(this)
        } else {
            repository.log("Health Connect not available on this phone")
        }

        // Spoken replies and prompts are the platform's job, not the repository's.
        lifecycleScope.launch {
            repository.spoken.collect { event ->
                when (event) {
                    is DeviceEvent.Say -> {
                        tts?.speak(event.text, TextToSpeech.QUEUE_FLUSH, null, "homeos")
                        notify(event.text)
                    }
                    is DeviceEvent.Prompt -> notify(event.text)
                    else -> Unit
                }
            }
        }

        setContent {
            HomeOsTheme {
                BridgeApp(
                    repository = repository,
                    healthConnectAvailable = HealthSync.available(this),
                    lastSync = { prefs.lastSync },
                    serverUrl = { prefs.url },
                    onRequestHealthPermissions = { healthPermissions.launch(HealthSync.PERMISSIONS) },
                    onSync = ::runSync,
                )
            }
        }
    }

    private fun notify(text: String) {
        val notification = NotificationCompat.Builder(this, "homeos")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("HomeOS")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(text.hashCode(), notification)
        }
    }

    /** Health Connect read plus POST, on the IO dispatcher. Returns the summary for dev mode. */
    private suspend fun runSync(days: Long): String {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                HealthSync.sync(this@MainActivity, prefs.url, prefs.alias, days)
            }
        }
        val summary = result.getOrElse { "Sync failed: ${it.message}" }
        prefs.lastSync = summary
        repository.log(summary.lineSequence().first())
        return summary
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

/** Which overlay, if any, is on top of the tabs. */
private enum class Overlay { None, Dev, People, Why, Device, AddAppliance }

@Composable
private fun BridgeApp(
    repository: HomeOsRepository,
    healthConnectAvailable: Boolean,
    lastSync: () -> String,
    serverUrl: () -> String,
    onRequestHealthPermissions: () -> Unit,
    onSync: suspend (Long) -> String,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Destination.Home) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var syncing by remember { mutableStateOf(false) }
    var syncSummary by remember { mutableStateOf(lastSync()) }

    // Ask keeps its thread for the session; switching tabs should not wipe a conversation.
    val turns = remember { mutableStateListOf<Turn>() }
    var asking by remember { mutableStateOf(false) }

    var memory by remember { mutableStateOf(MemoryData()) }
    var lastSleep by remember { mutableStateOf<Episode?>(null) }
    var graphOf by remember { mutableStateOf("") }
    var openDevice by remember { mutableStateOf<Device?>(null) }

    var why by remember { mutableStateOf<Explanation?>(null) }
    var whyEvidence by remember { mutableStateOf<List<EvidenceRow>>(emptyList()) }
    var whyLoading by remember { mutableStateOf(false) }
    var whyError by remember { mutableStateOf<String?>(null) }

    // The latest sleep night drives Home's "Last night" block, so it loads with the person.
    LaunchedEffect(state.alias, state.reachable) {
        if (state.alias.isEmpty() || !state.reachable) return@LaunchedEffect
        repository.episodes().onSuccess { episodes ->
            lastSleep = episodes.firstOrNull { it.kind == "sleep" }
        }
    }

    // Memory loads when its tab is opened, and reloads when the person changes.
    LaunchedEffect(tab, state.alias) {
        if (tab != Destination.Memory || state.alias.isEmpty()) return@LaunchedEffect
        memory = memory.copy(loading = true)
        val episodes = repository.episodes().getOrDefault(emptyList())
        val kind = episodes.firstOrNull()?.kind ?: "sleep"
        memory = MemoryData(
            episodes = episodes,
            evidence = repository.evidence(kind).getOrDefault(emptyList()),
            evidenceKind = kind,
            drivers = repository.drivers().getOrDefault(emptyList()),
            playbooks = listOf("sick", "recovery", "sleep")
                .flatMap { repository.playbooks(it).getOrDefault(emptyList()) }
                .distinctBy { it.name },
            changes = repository.beliefDiff().getOrDefault(emptyList()),
            stats = repository.stats().getOrDefault(emptyList()),
            loading = false,
            graph = memory.graph,
            graphOf = memory.graphOf,
            graphLoading = memory.graphLoading,
        )
        if (graphOf.isEmpty()) graphOf = state.alias
    }

    // The graph is loaded and laid out separately from the rest of Memory: it is the slowest thing
    // on the screen and should not hold up the text around it.
    LaunchedEffect(graphOf) {
        if (graphOf.isEmpty()) return@LaunchedEffect
        memory = memory.copy(graphLoading = true, graphOf = graphOf)
        val loaded = repository.graph(graphOf).getOrDefault(GraphData.Empty)
        memory = memory.copy(graph = loaded, graphOf = graphOf, graphLoading = false)
    }

    val openWhy: (Decision) -> Unit = { decision ->
        overlay = Overlay.Why
        whyLoading = true
        whyError = null
        why = null
        scope.launch {
            repository.explain(decision.id)
                .onSuccess { explanation ->
                    why = explanation
                    whyEvidence = repository.evidence(explanation.episodeKind).getOrDefault(emptyList())
                }
                .onFailure { whyError = it.message }
            whyLoading = false
        }
    }

    BackHandler(enabled = overlay != Overlay.None || tab != Destination.Home) {
        when {
            overlay != Overlay.None -> overlay = Overlay.None
            else -> tab = Destination.Home
        }
    }

    Shell(
        ambience = Ambience(
            temperatureC = state.ambientTemperature,
            occupied = state.home.people.any { it.present },
            // `bright` is 0-100 from the ESP32 light sensor; a dark room dims the gradient.
            dim = state.home.roomBright?.let { (1f - (it / 100f)).coerceIn(0f, 1f) } ?: 0f,
        ),
        topBar = {
            HomeOsTopBar(
                alias = state.alias,
                linked = state.linked,
                onSwitchPerson = { overlay = Overlay.People },
                onOpenDev = { overlay = Overlay.Dev },
            )
        },
        bottomBar = {
            if (overlay == Overlay.None) {
                HomeOsDock(current = tab, onSelect = { tab = it })
            }
        },
        overlay = {
            HomeOsSheet(
                visible = overlay == Overlay.Why,
                onDismiss = { overlay = Overlay.None },
                title = "Why",
            ) {
                WhyContent(
                    explanation = why,
                    evidence = whyEvidence,
                    loading = whyLoading,
                    error = whyError,
                )
            }

            HomeOsSheet(
                visible = overlay == Overlay.People,
                onDismiss = { overlay = Overlay.None },
                title = "Who's holding the phone?",
            ) {
                PeoplePicker(
                    state = state,
                    onPick = { alias ->
                        repository.switchPerson(alias)
                        graphOf = alias
                        overlay = Overlay.None
                    },
                )
            }

            HomeOsSheet(
                visible = overlay == Overlay.Device,
                onDismiss = { overlay = Overlay.None },
                title = openDevice?.name ?: "Appliance",
            ) {
                openDevice?.let { device ->
                    DeviceDetail(
                        device = device,
                        decision = state.latestDecision,
                        onWhy = { decision ->
                            overlay = Overlay.None
                            openWhy(decision)
                        },
                    )
                }
            }

            HomeOsSheet(
                visible = overlay == Overlay.AddAppliance,
                onDismiss = { overlay = Overlay.None },
                title = "Add appliance",
            ) {
                ApplianceCatalogue(connected = state.home.devices)
            }

            if (overlay == Overlay.Dev) {
                DevScreen(
                    state = state,
                    serverUrl = serverUrl(),
                    healthConnectAvailable = healthConnectAvailable,
                    lastSync = syncSummary,
                    busy = syncing,
                    onSetServer = { repository.setServer(it) },
                    onSwitchPerson = { repository.switchPerson(it) },
                    onSyncNow = {
                        syncing = true
                        scope.launch {
                            syncSummary = onSync(2L)
                            syncing = false
                        }
                    },
                    onBackfill = {
                        syncing = true
                        scope.launch {
                            syncSummary = onSync(120L)
                            syncing = false
                        }
                    },
                    onRequestPermissions = onRequestHealthPermissions,
                    onClose = { overlay = Overlay.None },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(120)) },
            label = "tab",
        ) { current ->
            when (current) {
                Destination.Home -> HomeScreen(
                    state = state,
                    lastSleep = lastSleep,
                    contentPadding = padding,
                    onWhy = openWhy,
                    onOpenMemory = { tab = Destination.Memory },
                    onOpenDevice = { device ->
                        openDevice = device
                        overlay = Overlay.Device
                    },
                    onAddAppliance = { overlay = Overlay.AddAppliance },
                    onAnswerPrompt = { answer -> scope.launch { repository.answerPrompt(answer) } },
                )

                Destination.CheckIn -> CheckInScreen(
                    state = state,
                    contentPadding = padding,
                    onChip = { chip -> scope.launch { repository.sendChip(chip) } },
                )

                Destination.Ask -> AskScreen(
                    turns = turns,
                    pending = asking,
                    contentPadding = padding,
                    onAsk = { text ->
                        turns.add(Turn(mine = true, text = text))
                        asking = true
                        scope.launch {
                            repository.ask(text)
                                .onSuccess { reply ->
                                    turns.add(Turn(mine = false, text = reply.reply, tools = reply.tools))
                                }
                                .onFailure { error ->
                                    turns.add(
                                        Turn(
                                            mine = false,
                                            text = "Couldn't reach HomeOS: ${error.message}",
                                            failed = true,
                                        ),
                                    )
                                }
                            asking = false
                        }
                    },
                )

                Destination.Memory -> MemoryScreen(
                    alias = state.alias,
                    data = memory,
                    contentPadding = padding,
                    onSelectGraph = { which -> graphOf = which },
                )
            }
        }
    }
}

/**
 * The person switcher.
 *
 * This is one phone a household shares, so identity is a product affordance rather than a settings
 * field. A dataset person is listed but marked, because they can be inspected and never acted as in
 * the room.
 */
@Composable
private fun PeoplePicker(
    state: AppState,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter)) {
        if (state.home.people.isEmpty()) {
            // The household list comes from the server, so with no connection there is genuinely
            // nobody to offer. Saying so beats an empty sheet with a caption under it.
            PaperBlock(plate = Plate.Failure) {
                Text("Can't list the household", style = type.blockTitle, color = colors.ink)
                Text(
                    "The home is unreachable, so the people it knows about can't be loaded. " +
                        "You are still acting as ${state.alias.ifEmpty { "nobody" }}.",
                    style = type.caption,
                    color = colors.inkMuted,
                )
            }
        }
        state.home.people.forEach { person ->
            val isMe = person.alias == state.alias
            PaperBlock(
                plate = if (isMe) Plate.Body else Plate.None,
                onClick = { onPick(person.alias) },
                padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PersonAvatar(
                        alias = person.alias,
                        size = 44.dp,
                        present = if (person.isDataset) null else person.present,
                        dim = person.isDataset,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(person.alias, style = type.blockTitle, color = colors.ink)
                        Text(
                            when {
                                person.isDataset -> "public dataset · ${person.dataset}"
                                person.present -> "in the bedroom"
                                else -> "not home"
                            },
                            style = type.caption,
                            color = colors.inkMuted,
                        )
                    }
                    if (isMe) {
                        Icon(
                            com.homeos.bridge.ui.icons.HomeOsIcons.checkCircle,
                            contentDescription = "current",
                            tint = colors.tealInk,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            "Your body data, conditions and reasons are only ever read from the graph of whoever is " +
                "selected here. Nobody else's agent can read them.",
            style = type.caption,
            color = colors.onShellMuted,
        )
    }
}
