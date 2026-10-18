package com.homeos.bridge.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.BeliefChange
import com.homeos.bridge.data.Driver
import com.homeos.bridge.data.Episode
import com.homeos.bridge.data.EvidenceRow
import com.homeos.bridge.data.GraphData
import com.homeos.bridge.data.GraphStat
import com.homeos.bridge.data.Playbook
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.ui.components.EvidenceBars
import com.homeos.bridge.ui.components.GRAPH_FAMILIES
import com.homeos.bridge.ui.components.GlassSegmented
import com.homeos.bridge.ui.components.GraphView
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.ui.components.SectionHeader
import com.homeos.bridge.ui.components.Segment
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
    /** The subgraph currently drawn. */
    val graph: GraphData = GraphData.Empty,
    /** Which graph that is: a person's alias, or "home". */
    val graphOf: String = "",
    val graphLoading: Boolean = false,
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
    onSelectGraph: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = HomeOsDims.screenMargin,
            end = HomeOsDims.screenMargin - HomeOsDims.plateOffset,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter),
    ) {
        item(key = "header") {
            SectionHeader(title = "Memory", trailing = "body:$alias")
        }

        item(key = "graph") {
            GraphPanel(
                alias = alias,
                data = data,
                onSelectGraph = onSelectGraph,
            )
        }

        item(key = "isolation") {
            IsolationPanel(alias = alias, stats = data.stats)
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
                // No count while loading: claiming "0 EPISODES" over a "reading the graph" block
                // is the screen contradicting itself.
                if (data.loading) {
                    "TIMELINE"
                } else {
                    "TIMELINE · ${data.episodes.size} EPISODE${if (data.episodes.size == 1) "" else "S"}"
                },
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
 * The graph itself, turning.
 *
 * It sits in a bordered dark viewport rather than on paper, and that is a deliberate exception to
 * the layering rule: a glowing depth-sorted graph needs a dark field to read at all, and this is a
 * window onto the memory rather than a surface carrying numbers. It keeps the 2dp border so it
 * still belongs to the same family as the blocks above and below it.
 */
@Composable
private fun GraphPanel(
    alias: String,
    data: MemoryData,
    onSelectGraph: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val showingHome = data.graphOf == "home"

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Two graphs, one switch. Putting them side by side is the clearest statement the product
        // can make: your body graph and the shared home graph are different objects.
        GlassSegmented(
            segments = listOf(
                Segment(label = "body:$alias", sub = "private", mono = true),
                Segment(label = "home:flat1", sub = "shared", mono = true),
            ),
            selected = if (showingHome) 1 else 0,
            onSelect = { index -> onSelectGraph(if (index == 1) "home" else alias) },
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = HomeOsShapes.block,
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
                .clip(HomeOsShapes.block)
                .background(colors.shellSunk)
                // A lens rather than a box. The vignette pulls the eye to the middle of the graph
                // and keeps the corners from reading as four empty right angles, and the sheen is
                // the same top-lit edge every other surface in the app carries, so the viewport
                // belongs to the design instead of being a hole cut in it.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0.45f to Color.Transparent,
                            1.00f to colors.shellSunk.copy(alpha = 0.85f),
                            center = Offset(size.width / 2f, size.height * 0.46f),
                            radius = size.maxDimension * 0.72f,
                        ),
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0.00f to colors.onShell.copy(alpha = 0.05f),
                            0.30f to Color.Transparent,
                        ),
                    )
                }
                .border(
                    HomeOsDims.glassBorder,
                    Brush.linearGradient(
                        0.00f to colors.onShell.copy(alpha = 0.32f),
                        0.45f to colors.onShell.copy(alpha = 0.14f),
                        1.00f to colors.onShell.copy(alpha = 0.07f),
                    ),
                    HomeOsShapes.block,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                data.graphLoading -> Text("Reading the graph…", style = type.caption, color = colors.onShellMuted)
                data.graph.isEmpty -> Text(
                    "This graph is empty.",
                    style = type.caption,
                    color = colors.onShellMuted,
                )
                else -> GraphView(data = data.graph, modifier = Modifier.fillMaxSize())
            }
            if (!data.graph.isEmpty && !data.graphLoading) {
                Text(
                    "drag to turn · tap a node",
                    style = type.micro,
                    color = colors.onShellMuted,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                )
                // `/api/graph` returns a bounded page while `/api/stats` counts the whole graph, so
                // say which is which rather than printing two different totals on one screen.
                val whole = data.stats.firstOrNull {
                    it.graph == if (showingHome) "home:flat1" else "body:$alias"
                }
                Text(
                    if (whole != null && whole.nodes > data.graph.nodes.size) {
                        "${data.graph.nodes.size} of ${whole.nodes} nodes"
                    } else {
                        "${data.graph.nodes.size} nodes · ${data.graph.edges.size} edges"
                    },
                    style = type.monoMicro,
                    color = colors.onShellMuted,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
        }

        GraphLegend()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphLegend(modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Driven by the same list the renderer colours from, so the key can never describe a
        // palette the picture is not using.
        GRAPH_FAMILIES.forEach { (caption, pick) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(pick(colors)))
                Text(caption, style = HomeOs.type.micro, color = colors.onShellMuted)
            }
        }
    }
}

/**
 * The isolation statement, with the numbers behind it.
 *
 * This is the project's Track 2 claim made checkable rather than asserted: one private graph per
 * person, a separate shared graph for the home, and a per-person FalkorDB ACL user that cannot read
 * anyone else's. The node and edge counts come from `/api/stats`, so the sizes on screen are the
 * real graphs.
 */
@Composable
private fun IsolationPanel(
    alias: String,
    stats: List<GraphStat>,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val mine = stats.firstOrNull { it.graph == "body:$alias" }
    val home = stats.firstOrNull { it.graph.startsWith("home:") }
    val others = stats.filter { it.graph.startsWith("body:") && it.graph != "body:$alias" }

    PaperBlock(modifier = modifier, plate = Plate.Body) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                HomeOsIcons.shieldLock,
                contentDescription = null,
                tint = colors.tealInk,
                modifier = Modifier.size(18.dp),
            )
            Text("ONE GRAPH PER PERSON", style = type.micro, color = colors.inkMuted)
        }
        Text(
            "Your memory is its own graph, and the agent that reads it logs in as you. " +
                "The home has a separate graph that only ever holds ranges, never reasons.",
            style = type.body,
            color = colors.ink,
        )
        Spacer(Modifier.height(2.dp))
        GraphRow("body:$alias", mine, "yours · private", colors.tealInk)
        home?.let { GraphRow(it.graph, it, "shared · ranges only", colors.violetInk) }
        others.forEach { other ->
            GraphRow(other.graph, other, "not readable by you", colors.inkMuted)
        }
    }
}

@Composable
private fun GraphRow(
    name: String,
    stat: GraphStat?,
    note: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Column(Modifier.weight(1f)) {
            Text(name, style = type.mono, color = colors.ink)
            Text(note, style = type.micro, color = colors.inkMuted)
        }
        Text(
            if (stat != null) "${stat.nodes} / ${stat.edges}" else "–",
            style = type.mono,
            color = colors.inkMuted,
        )
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
