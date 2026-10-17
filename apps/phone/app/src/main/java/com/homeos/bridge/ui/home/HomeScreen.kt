package com.homeos.bridge.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.AppState
import com.homeos.bridge.data.Decision
import com.homeos.bridge.data.Device
import com.homeos.bridge.data.Episode
import com.homeos.bridge.data.Person
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.theme.ink
import com.homeos.bridge.ui.components.AblationBanner
import com.homeos.bridge.ui.components.Baseline
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.ui.components.PersonBadge
import com.homeos.bridge.ui.components.SectionHeader
import com.homeos.bridge.ui.components.Sparkline
import com.homeos.bridge.ui.components.StatReadout
import com.homeos.bridge.ui.components.StatusDot
import com.homeos.bridge.ui.components.readoutValue
import com.homeos.bridge.ui.icons.HomeOsIcons
import kotlin.math.roundToInt

private val SLEEP_HOURS = Regex("""([\d.]+)\s*h sleep""")
private val DEEP_MINUTES = Regex("""(\d+)\s*min deep""")

/**
 * Home: your body, your room, what the home did for you, and who else is in.
 *
 * Two rules shape this screen. There are **no charts** here beyond sparklines — anything needing an
 * axis lives one tap deeper, following the three-tier hierarchy that data-dense health apps settle
 * on. And **no number appears bare**: every readout carries the band of what is usual for *this
 * person*, because that is exactly how the backend computes meaning, and a figure without it would
 * be less honest than the data behind it.
 *
 * The blocks are deliberately unequal. A uniform grid of identical cards is the single clearest
 * sign that nobody decided what mattered.
 */
@Composable
fun HomeScreen(
    state: AppState,
    lastSleep: Episode?,
    contentPadding: PaddingValues,
    onWhy: (Decision) -> Unit,
    onOpenMemory: () -> Unit,
    onAnswerPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val hrSeries = state.hrOf(state.alias)
    val liveHr = state.myBody?.hr ?: hrSeries.lastOrNull()?.value
    val hrBaseline = state.context?.baseline("hr") ?: state.context?.baseline("hr_rest")
    val rmssd = state.myBody?.rmssd
    val rmssdBaseline = state.context?.baseline("rmssd")

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = HomeOsDims.screenMargin,
            end = HomeOsDims.screenMargin,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter),
    ) {
        state.prompt?.let { prompt ->
            item(key = "prompt") {
                PaperBlock(plate = Plate.Attention) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            HomeOsIcons.bolt,
                            contentDescription = null,
                            tint = colors.amberInk,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("HOMEOS ASKS", style = type.micro, color = colors.inkMuted)
                    }
                    Text(prompt.text, style = type.blockTitle, color = colors.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf("yes", "no").forEach { answer ->
                            PaperBlock(
                                modifier = Modifier.weight(1f),
                                plate = if (answer == "yes") Plate.Good else Plate.None,
                                onClick = { onAnswerPrompt(answer) },
                                padding = PaddingValues(vertical = 12.dp),
                            ) {
                                Text(
                                    answer.uppercase(),
                                    style = type.label,
                                    color = colors.ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!state.home.graphEnabled) {
            item(key = "ablation") { AblationBanner() }
        }

        if (!state.reachable) {
            item(key = "offline") {
                PaperBlock(plate = Plate.Failure) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            HomeOsIcons.cloudOff,
                            contentDescription = null,
                            tint = colors.redInk,
                            modifier = Modifier.size(20.dp),
                        )
                        Column {
                            Text("Can't reach HomeOS", style = type.blockTitle, color = colors.ink)
                            Text(
                                state.lastError ?: "Check the server address in dev mode.",
                                style = type.caption,
                                color = colors.inkMuted,
                            )
                        }
                    }
                }
            }
        }

        // The hero. One number leads the screen, at display size, with its band underneath.
        item(key = "body") {
            PaperBlock(
                plate = Plate.Body,
                padding = PaddingValues(HomeOsDims.heroPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatReadout(
                    label = if (state.myBody?.onBody == true) "Heart rate · live" else "Heart rate",
                    value = readoutValue(liveHr),
                    unit = "bpm",
                    icon = HomeOsIcons.monitorHeart,
                    plate = Plate.Body,
                    hero = true,
                    current = liveHr,
                    baseline = hrBaseline?.let { Baseline(it.mean, it.sd) },
                    note = when {
                        liveHr == null -> "No wearable reporting. The watch or ring has to be on."
                        hrBaseline == null -> "Not enough nights yet to know what is usual for you."
                        else -> null
                    },
                )
                Sparkline(values = hrSeries.map { it.value }, plate = Plate.Body)
            }
        }

        // Two unequal-weight companions. Sleep gets more room than HRV because it has more to say.
        item(key = "night-and-hrv") {
            Row(horizontalArrangement = Arrangement.spacedBy(HomeOsDims.gutter)) {
                PaperBlock(modifier = Modifier.weight(1.15f), plate = Plate.Body, onClick = onOpenMemory) {
                    val hours = lastSleep?.summary?.let { SLEEP_HOURS.find(it)?.groupValues?.get(1) }
                    val deep = lastSleep?.summary?.let { DEEP_MINUTES.find(it)?.groupValues?.get(1) }
                    StatReadout(
                        label = "Last night",
                        value = hours ?: "—",
                        unit = if (hours != null) "h" else null,
                        icon = HomeOsIcons.bedtime,
                        plate = Plate.Body,
                        note = when {
                            lastSleep == null -> "No night synced yet."
                            deep != null -> "$deep min deep"
                            else -> lastSleep.kind
                        },
                    )
                    lastSleep?.quality?.let { quality ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusDot(on = quality == "good", onColor = if (quality == "good") colors.greenInk else colors.amberInk)
                            Text(
                                if (quality == "good") "good for you" else "worse than your usual",
                                style = type.caption,
                                color = colors.inkMuted,
                            )
                        }
                    }
                }
                PaperBlock(modifier = Modifier.weight(1f), plate = Plate.Body) {
                    StatReadout(
                        label = "HRV",
                        value = readoutValue(rmssd),
                        unit = if (rmssd != null) "ms" else null,
                        icon = HomeOsIcons.ecgHeart,
                        plate = Plate.Body,
                        current = rmssd,
                        baseline = rmssdBaseline?.let { Baseline(it.mean, it.sd) },
                        note = when {
                            rmssd == null -> "Needs a night with the ring on."
                            rmssdBaseline == null -> "Building your baseline."
                            else -> null
                        },
                    )
                }
            }
        }

        item(key = "room") {
            RoomBlock(state = state)
        }

        state.latestDecision?.let { decision ->
            item(key = "decision") {
                DecisionBlock(decision = decision, onWhy = { onWhy(decision) })
            }
        }

        item(key = "household-header") {
            SectionHeader(
                title = "Who's in",
                trailing = "${state.home.people.count { it.present }} of ${state.home.people.size} home",
            )
        }
        item(key = "household") {
            HouseholdStrip(people = state.home.people, me = state.alias, state = state)
        }
    }
}

/**
 * The room, from the ESP32 and the virtual devices.
 *
 * The block itself is not a button — the device rows inside it are the affordances, so tapping to
 * read does not accidentally change the house.
 */
@Composable
private fun RoomBlock(state: AppState, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val temperature = state.home.roomTemperature
    val humidity = state.home.roomHumidity

    PaperBlock(modifier = modifier, plate = Plate.Home) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                HomeOsIcons.thermostat,
                contentDescription = null,
                tint = colors.violetInk,
                modifier = Modifier.size(18.dp),
            )
            Text("THE ROOM", style = type.micro, color = colors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(
                state.home.devices.firstOrNull()?.room?.ifEmpty { "bedroom" } ?: "bedroom",
                style = type.micro,
                color = colors.inkMuted,
            )
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (temperature != null) "%.1f".format(temperature) else "—",
                        style = type.readout,
                        color = colors.ink,
                    )
                    Text(" °C", style = type.caption, color = colors.inkMuted)
                }
                Text("temperature", style = type.micro, color = colors.inkMuted)
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (humidity != null) humidity.roundToInt().toString() else "—",
                        style = type.readout,
                        color = colors.ink,
                    )
                    Text(" %", style = type.caption, color = colors.inkMuted)
                }
                Text("humidity", style = type.micro, color = colors.inkMuted)
            }
        }

        if (temperature == null) {
            Text(
                "The room sensor hasn't reported yet.",
                style = type.caption,
                color = colors.inkMuted,
            )
        }

        Spacer(Modifier.height(2.dp))
        state.home.devices.filter { !it.isSensor }.forEach { device ->
            DeviceRow(device)
        }
    }
}

@Composable
private fun DeviceRow(device: Device, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val icon = when (device.kind) {
        "ac" -> HomeOsIcons.acUnit
        "fan" -> HomeOsIcons.modeFan
        "light" -> HomeOsIcons.lightbulb
        else -> HomeOsIcons.bolt
    }
    val value = when {
        device.kind == "ac" && device.setpoint != null -> "${device.setpoint} °C"
        device.kind == "fan" && device.level != null -> "level ${device.level}"
        device.kind == "light" && device.brightness != null -> "${device.brightness}%"
        else -> device.power ?: "—"
    }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (device.isOn) colors.violetInk else colors.inkMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(device.name, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
        if (device.leasedBy != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    HomeOsIcons.lock,
                    contentDescription = "held by ${device.leasedBy}",
                    tint = colors.amberInk,
                    modifier = Modifier.size(13.dp),
                )
                Text(device.leasedBy, style = type.micro, color = colors.amberInk)
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (device.isOn) value else "off",
            style = type.label,
            color = if (device.isOn) colors.ink else colors.inkMuted,
        )
    }
}

/** What the home did for you, in one sentence, with the way into its reasoning. */
@Composable
private fun DecisionBlock(decision: Decision, onWhy: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    PaperBlock(modifier = modifier, plate = Plate.Home, onClick = onWhy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WHAT THE HOME DID FOR YOU", style = type.micro, color = colors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(relativeTime(decision.at), style = type.micro, color = colors.inkMuted)
        }
        Text(decision.summary, style = type.blockTitle, color = colors.ink)
        if (decision.chose.isNotEmpty()) {
            Text(
                decision.chose.joinToString(" · "),
                style = type.body,
                color = colors.violetInk,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Why", style = type.label, color = colors.ink)
            Icon(
                HomeOsIcons.arrowOutward,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * The household strip.
 *
 * Other people show presence and nothing else. That is not a simplification: their conditions and
 * reasons live in graphs this person's agent genuinely cannot read, so showing them would be a
 * promise the backend does not make.
 */
@Composable
private fun HouseholdStrip(
    people: List<Person>,
    me: String,
    state: AppState,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        people.forEach { person ->
            val body = state.home.body[person.alias]
            PaperBlock(
                plate = if (person.alias == me) Plate.Body else Plate.None,
                padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PersonBadge(alias = person.alias, dim = person.isDataset)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                person.alias + if (person.alias == me) " (you)" else "",
                                style = type.label,
                                color = colors.ink,
                            )
                        }
                        Text(
                            when {
                                person.isDataset -> "public dataset · never in the room"
                                person.present -> "in the bedroom"
                                else -> "not home"
                            },
                            style = type.caption,
                            color = colors.inkMuted,
                        )
                    }
                    if (!person.isDataset) {
                        StatusDot(
                            on = person.present,
                            onColor = if (person.present) colors.greenInk else colors.inkMuted,
                        )
                    }
                    if (body?.hr != null) {
                        Text(
                            "${body.hr.roundToInt()}",
                            style = type.label,
                            color = Plate.Body.ink(colors),
                        )
                        Text("bpm", style = type.micro, color = colors.inkMuted)
                    }
                }
            }
        }
    }
}

/** Relative time, short enough for a label. */
internal fun relativeTime(at: Long): String {
    if (at <= 0L) return ""
    val delta = System.currentTimeMillis() - at
    val minutes = delta / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1_440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1_440}d ago"
    }
}
