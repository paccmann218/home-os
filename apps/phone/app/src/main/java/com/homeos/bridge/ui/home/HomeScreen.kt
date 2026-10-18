package com.homeos.bridge.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import com.homeos.bridge.ui.components.AppliancePanel
import com.homeos.bridge.ui.components.Baseline
import com.homeos.bridge.ui.components.PaperAction
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.ui.components.PersonAvatar
import com.homeos.bridge.ui.components.SectionHeader
import com.homeos.bridge.ui.components.Sparkline
import com.homeos.bridge.ui.components.StatReadout
import com.homeos.bridge.ui.components.StatusDot
import com.homeos.bridge.ui.components.blockMargin
import com.homeos.bridge.ui.components.readoutValue
import com.homeos.bridge.ui.icons.HomeOsIcons
import kotlin.math.roundToInt

/**
 * How tall a person card is.
 *
 * Fixed rather than intrinsic, because these live in a lazy row and a lazy layout cannot answer an
 * intrinsic measurement - `IntrinsicSize.Min` on a `LazyRow` throws rather than degrading.
 */
private val PERSON_CARD = 134.dp

private val SLEEP_HOURS = Regex("""([\d.]+)\s*h sleep""")
private val DEEP_MINUTES = Regex("""(\d+)\s*min deep""")

/**
 * Home: your body, your room, what the home did for you, and who else is in.
 *
 * Two rules shape this screen. There are **no charts** here beyond sparklines — anything needing an
 * axis lives one tap deeper, following the three-tier hierarchy that data-dense health apps settle
 * on. And **no number appears bare**: every readout carries the band of what is usual for *this*
 * person, because that is exactly how the backend computes meaning.
 *
 * The blocks are deliberately unequal in width, but side-by-side blocks are forced to equal
 * *height* through `IntrinsicSize.Min`, so their plates line up. Two hard offsets at different
 * depths is the fastest way to make a careful layout look careless.
 */
@Composable
fun HomeScreen(
    state: AppState,
    lastSleep: Episode?,
    contentPadding: PaddingValues,
    onWhy: (Decision) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenDevice: (Device) -> Unit,
    onAddAppliance: () -> Unit,
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
    // Horizontal insets live on the items, not on the list, so the appliance strip can run
    // edge to edge while everything else stays on the margin. Paper blocks get the short end
    // margin, because each of them reserves the plate offset on its right whether it draws one or
    // not; glass and headings get the true margin. Both land their visible edge in the same place.
    val blockGutter = Modifier.blockMargin()
    val edgeGutter = Modifier.padding(horizontal = HomeOsDims.screenMargin)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter),
    ) {
        state.prompt?.let { prompt ->
            item(key = "prompt") {
                PaperBlock(modifier = blockGutter, plate = Plate.Attention, lead = true) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        PaperAction(
                            label = "Yes",
                            accent = colors.greenInk,
                            onClick = { onAnswerPrompt("yes") },
                            modifier = Modifier.weight(1f),
                        )
                        PaperAction(
                            label = "No",
                            onClick = { onAnswerPrompt("no") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (!state.home.graphEnabled) {
            item(key = "ablation") { AblationBanner(edgeGutter) }
        }

        if (state.offline) {
            item(key = "offline") {
                PaperBlock(modifier = blockGutter, plate = Plate.Failure, lead = true) {
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
                modifier = blockGutter,
                plate = Plate.Body,
                lead = true,
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
                        // With no live reading, what is already known about this person is far
                        // more use than an apology. The baseline is in their graph either way.
                        liveHr == null && hrBaseline != null ->
                            "Nothing reporting right now. Usually " +
                                "${hrBaseline.mean.roundToInt()} bpm for you."
                        liveHr == null -> "No wearable reporting. The watch or ring has to be on."
                        hrBaseline == null -> "Not enough nights yet to know what is usual for you."
                        else -> null
                    },
                )
                Sparkline(values = hrSeries.map { it.value }, plate = Plate.Body)
            }
        }

        // Unequal widths, equal heights, so the two plates sit on the same line.
        item(key = "night-and-hrv") {
            Row(
                blockGutter.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(HomeOsDims.gutter),
            ) {
                val hours = lastSleep?.summary?.let { SLEEP_HOURS.find(it)?.groupValues?.get(1) }
                val deep = lastSleep?.summary?.let { DEEP_MINUTES.find(it)?.groupValues?.get(1) }
                PaperBlock(
                    modifier = Modifier.weight(1.15f),
                    plate = Plate.Body,
                    onClick = onOpenMemory,
                    fillHeight = true,
                ) {
                    StatReadout(
                        label = "Last night",
                        value = hours ?: readoutValue(null),
                        unit = "h",
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
                            StatusDot(
                                on = quality == "good",
                                onColor = if (quality == "good") colors.greenInk else colors.amberInk,
                            )
                            Text(
                                if (quality == "good") "good for you" else "worse than your usual",
                                style = type.caption,
                                color = colors.inkMuted,
                            )
                        }
                    }
                }
                PaperBlock(
                    modifier = Modifier.weight(1f),
                    plate = Plate.Body,
                    fillHeight = true,
                ) {
                    StatReadout(
                        label = "HRV",
                        value = readoutValue(rmssd),
                        unit = "ms",
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

        item(key = "room") { RoomBlock(state = state, modifier = blockGutter) }

        item(key = "appliances-header") {
            SectionHeader(
                title = "Appliances",
                modifier = edgeGutter,
                trailing = "${state.home.devices.count { it.isOn }} on",
            )
        }
        item(key = "appliances") {
            AppliancePanel(
                devices = state.home.devices,
                onOpenDevice = onOpenDevice,
                onAddAppliance = onAddAppliance,
            )
        }

        state.latestDecision?.let { decision ->
            item(key = "decision") {
                DecisionBlock(decision = decision, onWhy = { onWhy(decision) }, modifier = blockGutter)
            }
        }

        item(key = "household-header") {
            SectionHeader(
                title = "Who's in",
                modifier = edgeGutter,
                trailing = "${state.home.people.count { it.present }} of ${state.home.people.size} home",
            )
        }
        item(key = "household") {
            HouseholdStrip(
                people = state.home.people,
                me = state.alias,
                state = state,
            )
        }
    }
}

/**
 * The room itself: what the sensor reads.
 *
 * The appliances moved out into their own scrollable strip, so this block answers one question —
 * what is it like in here — instead of being a list with a header stapled on.
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

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (temperature != null) "%.1f".format(temperature) else readoutValue(null),
                        style = type.readout,
                        color = if (temperature != null) colors.ink else colors.inkMuted.copy(alpha = 0.45f),
                    )
                    if (temperature != null) Text(" °C", style = type.caption, color = colors.inkMuted)
                }
                Text("temperature", style = type.micro, color = colors.inkMuted)
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (humidity != null) humidity.roundToInt().toString() else readoutValue(null),
                        style = type.readout,
                        color = if (humidity != null) colors.ink else colors.inkMuted.copy(alpha = 0.45f),
                    )
                    if (humidity != null) Text(" %", style = type.caption, color = colors.inkMuted)
                }
                Text("humidity", style = type.micro, color = colors.inkMuted)
            }
        }

        if (temperature == null) {
            Text("The room sensor hasn't reported yet.", style = type.caption, color = colors.inkMuted)
        }
    }
}

/** What the home did for you, in one sentence, with the way into its reasoning. */
@Composable
private fun DecisionBlock(decision: Decision, onWhy: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    PaperBlock(modifier = modifier, plate = Plate.Home) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WHAT THE HOME DID FOR YOU", style = type.micro, color = colors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(relativeTime(decision.at), style = type.micro, color = colors.inkMuted)
        }
        Text(decision.summary, style = type.blockTitle, color = colors.ink)
        if (decision.chose.isNotEmpty()) {
            Text(decision.chose.joinToString("  ·  "), style = type.body, color = colors.violetInk)
        }
        // An explicit control rather than a whole-block tap. The block carries a sentence worth
        // reading and a reason worth opening, and those are two different intentions; making the
        // paragraph itself the button means you cannot read it without nearly leaving it.
        PaperAction(
            label = "Why",
            icon = HomeOsIcons.arrowOutward,
            onClick = onWhy,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The household strip.
 *
 * Sideways rather than as a stack of full-width rows, for two reasons. Presence is a glance, not a
 * reading, so it does not deserve four rows of a phone screen; and a row of faces is the only shape
 * that makes the household feel like a household instead of a list of accounts.
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

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = HomeOsDims.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(people, key = { it.alias }) { person ->
            val body = state.home.body[person.alias]
            val isMe = person.alias == me
            PaperBlock(
                modifier = Modifier.width(146.dp).height(PERSON_CARD),
                plate = if (isMe) Plate.Body else Plate.None,
                padding = PaddingValues(13.dp),
                fillHeight = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(
                        alias = person.alias,
                        size = 36.dp,
                        present = if (person.isDataset) null else person.present,
                        dim = person.isDataset,
                    )
                    Spacer(Modifier.weight(1f))
                    if (body?.hr != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${body.hr.roundToInt()}",
                                style = type.readout,
                                color = Plate.Body.ink(colors),
                            )
                            Text(
                                " bpm",
                                style = type.micro,
                                color = colors.inkMuted,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    person.alias + if (isMe) " (you)" else "",
                    style = type.blockTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        person.isDataset -> "public dataset"
                        person.present -> "in the bedroom"
                        else -> "not home"
                    },
                    style = type.caption,
                    color = colors.inkMuted,
                )
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
