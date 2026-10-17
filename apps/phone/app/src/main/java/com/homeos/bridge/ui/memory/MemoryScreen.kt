package com.homeos.bridge.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.BeliefChange
import com.homeos.bridge.data.Driver
import com.homeos.bridge.data.Episode
import com.homeos.bridge.data.EvidenceRow
import com.homeos.bridge.data.GraphStat
import com.homeos.bridge.data.Playbook
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.ui.components.EvidenceBars
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.ui.components.SectionHeader
import com.homeos.bridge.ui.components.StatusDot
import com.homeos.bridge.ui.icons.HomeOsIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything Memory needs, loaded once per person. */
@Immutable
data class MemoryData(
    val episodes: List<Episode> = emptyList(),
    val evidence: List<EvidenceRow> = emptyList(),
    val evidenceKind: String = "sleep",
    val drivers: List<Driver> = emptyList(),
    val playbooks: List<Playbook> = emptyList(),
    val changes: List<BeliefChange> = emptyList(),
    val stats: List<GraphStat> = emptyList(),
    val loading: Boolean = true,
)

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/**
 * Memory: what the home remembers about you, and what it worked out from it.
 *
 * This is the deepest of the three tiers, so it is the one place charts and tables are welcome.
 * Everything here is per-person and read through that person's own graph, so an empty section means
 * their graph really is empty rather than that the screen failed.
 */
@Composable
fun MemoryScreen(
    alias: String,
    data: MemoryData,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type

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
        item(key = "header") {
            SectionHeader(
                title = "Memory",
                trailing = "body:$alias",
            )
        }

        if (data.drivers.isNotEmpty()) {
            item(key = "insights-header") {
                Text("WHAT THE GRAPH WORKED OUT", style = type.micro, color = colors.onShellMuted)
            }
            items(data.drivers, key = { "driver-${it.band}" }) { driver ->
                DriverBlock(driver)
            }
        }

        item(key = "evidence-header") {
            Spacer(Modifier.height(4.dp))
            Text(
                "WHAT WORKS FOR YOU · ${data.evidenceKind.uppercase()}",
                style = type.micro,
                color = colors.onShellMuted,
            )
        }
        item(key = "evidence") {
            PaperBlock(plate = Plate.Body) {
                EvidenceBars(rows = data.evidence)
            }
        }

        if (data.playbooks.isNotEmpty()) {
            item(key = "playbooks-header") {
                Spacer(Modifier.height(4.dp))
                Text("PLAYBOOKS", style = type.micro, color = colors.onShellMuted)
            }
            items(data.playbooks, key = { "pb-${it.name}" }) { playbook ->
                PlaybookBlock(playbook)
            }
        }

        item(key = "timeline-header") {
            Spacer(Modifier.height(4.dp))
            Text(
                "TIMELINE · ${data.episodes.size} EPISODE${if (data.episodes.size == 1) "" else "S"}",
                style = type.micro,
                color = colors.onShellMuted,
            )
        }
        if (data.episodes.isEmpty()) {
            item(key = "timeline-empty") {
                PaperBlock(plate = Plate.None) {
                    Text(
                        if (data.loading) {
                            "Reading the graph…"
                        } else {
                            "No episodes yet. One appears when you check in, or when a night syncs from the ring."
                        },
                        style = type.body,
                        color = colors.inkMuted,
                    )
                }
            }
        } else {
            items(data.episodes, key = { it.id }) { episode ->
                EpisodeBlock(episode)
            }
        }

        if (data.changes.isNotEmpty()) {
            item(key = "changes-header") {
                Spacer(Modifier.height(4.dp))
                Text("WHAT CHANGED", style = type.micro, color = colors.onShellMuted)
            }
            item(key = "changes") {
                PaperBlock(plate = Plate.None) {
                    data.changes.take(8).forEach { change ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                change.about.replace('_', ' '),
                                style = HomeOs.type.body,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                range(change.min, change.max),
                                style = HomeOs.type.label,
                                color = colors.violetInk,
                            )
                            Text(change.change, style = HomeOs.type.micro, color = colors.inkMuted)
                        }
                    }
                }
            }
        }

        if (data.stats.isNotEmpty()) {
            item(key = "stats") {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("GRAPHS", style = type.micro, color = colors.onShellMuted)
                    data.stats.forEach { stat ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stat.graph,
                                style = type.monoMicro,
                                color = if (stat.graph.endsWith(alias)) colors.teal else colors.onShellMuted,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${stat.nodes} nodes · ${stat.edges} edges",
                                style = type.monoMicro,
                                color = colors.onShellMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One PageRank driver.
 *
 * The lift is the headline, not the score: "1.6x your usual rate" is a sentence a person can act
 * on, where a PageRank score of 0.029 is not.
 */
@Composable
private fun DriverBlock(driver: Driver, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    PaperBlock(modifier = modifier, plate = Plate.Attention) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                HomeOsIcons.hub,
                contentDescription = null,
                tint = colors.amberInk,
                modifier = Modifier.size(16.dp),
            )
            Text(
                driver.band.replace(':', ' ').replace('_', ' '),
                style = type.blockTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text("%.1f×".format(driver.lift), style = type.readout, color = colors.amberInk)
        }
        Text(
            "${driver.badNights} of ${driver.nights} nights like this went badly " +
                "(${(driver.probabilityBad * 100).toInt()}%), against your own usual rate.",
            style = type.body,
            color = colors.inkMuted,
        )
    }
}

@Composable
private fun PlaybookBlock(playbook: Playbook, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    PaperBlock(modifier = modifier, plate = Plate.Home) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                playbook.name.replace('_', ' '),
                style = type.blockTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text("v${playbook.version}", style = type.micro, color = colors.inkMuted)
        }
        playbook.steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}", style = type.micro, color = colors.violetInk)
                Text(step, style = type.body, color = colors.ink)
            }
        }
        if (playbook.earlierVersions > 0) {
            Text(
                "rewritten ${playbook.earlierVersions} time(s) from your own outcomes",
                style = type.caption,
                color = colors.inkMuted,
            )
        }
        playbook.note?.let { Text(it, style = type.caption, color = colors.inkMuted) }
    }
}

@Composable
private fun EpisodeBlock(episode: Episode, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val plate = when {
        episode.isOpen -> Plate.Attention
        episode.wasGood == true -> Plate.Good
        episode.wasGood == false -> Plate.Attention
        else -> Plate.None
    }

    PaperBlock(modifier = modifier, plate = plate) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(episode.kind.uppercase(), style = type.micro, color = colors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(formatInstant(episode.start), style = type.micro, color = colors.inkMuted)
        }
        Text(episode.summary, style = type.body, color = colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                episode.isOpen -> Text("still open", style = type.caption, color = colors.amberInk)
                episode.quality != null -> {
                    StatusDot(
                        on = episode.wasGood == true,
                        onColor = if (episode.wasGood == true) colors.greenInk else colors.amberInk,
                    )
                    Text(
                        if (episode.wasGood == true) "good for you" else "worse than your usual",
                        style = type.caption,
                        color = colors.inkMuted,
                    )
                }
            }
            episode.effect?.let { effect ->
                Text(
                    "measured effect ${"%+.2f".format(effect)}",
                    style = type.caption,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

private fun formatInstant(epochMillis: Long): String =
    if (epochMillis <= 0L) {
        ""
    } else {
        DAY_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }

private fun range(min: Float?, max: Float?): String = when {
    min != null && max != null -> "${trim(min)}–${trim(max)}"
    min != null -> "≥ ${trim(min)}"
    max != null -> "≤ ${trim(max)}"
    else -> "—"
}

private fun trim(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)
