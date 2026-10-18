package com.homeos.bridge.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.AppState
import com.homeos.bridge.data.Condition
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.theme.color
import com.homeos.bridge.theme.ink
import com.homeos.bridge.ui.components.blockEndMargin
import com.homeos.bridge.ui.components.GlassChip
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.ui.components.SectionHeader
import com.homeos.bridge.ui.icons.HomeOsIcons
import java.util.concurrent.TimeUnit

/** One thing you can tell the home about yourself. */
private data class Chip(val key: String, val label: String, val icon: ImageVector)

private data class ChipGroup(
    val title: String,
    val caption: String,
    val plate: Plate,
    val chips: List<Chip>,
)

/**
 * The chip catalogue, matching `CHIPS` in `backend/src/agents/guardian.ts` exactly.
 *
 * Grouped rather than laid out as one undifferentiated grid, and sized by how often each is
 * actually reached for. Note `fever` and `cold` — the backend has always accepted them and the old
 * screen never offered them, so two real inputs were simply unreachable.
 */
private val GROUPS = listOf(
    ChipGroup(
        title = "Not feeling right",
        caption = "Stays private. The home only learns a temperature range.",
        plate = Plate.Attention,
        chips = listOf(
            Chip("sick", "Sick", HomeOsIcons.sick),
            Chip("fever", "Fever", HomeOsIcons.thermometer),
            Chip("cold", "Cold symptoms", HomeOsIcons.acUnit),
            Chip("headache", "Headache", HomeOsIcons.neurology),
            Chip("period", "Period", HomeOsIcons.bloodtype),
        ),
    ),
    ChipGroup(
        title = "The room",
        caption = "Changes the room now, for everyone in it.",
        plate = Plate.Home,
        chips = listOf(
            Chip("too_hot", "Too hot", HomeOsIcons.localFireDepartment),
            Chip("too_cold", "Too cold", HomeOsIcons.acUnit),
        ),
    ),
    ChipGroup(
        title = "Right now",
        caption = "Opens an episode, so the home can measure what helped.",
        plate = Plate.Body,
        chips = listOf(
            Chip("workout", "Just worked out", HomeOsIcons.directionsRun),
            Chip("stressed", "Stressed", HomeOsIcons.selfImprovement),
            Chip("sleep", "Going to sleep", HomeOsIcons.darkMode),
        ),
    ),
    ChipGroup(
        title = "How did that go",
        caption = "This is what the home learns from. It changes what it tries next time.",
        plate = Plate.Good,
        chips = listOf(
            Chip("better", "Better", HomeOsIcons.thumbUp),
            Chip("worse", "Worse", HomeOsIcons.thumbDown),
            Chip("fine", "All fine now", HomeOsIcons.sentimentSatisfied),
        ),
    ),
)

/**
 * Check in: the main daily action.
 *
 * Pressing a chip is a physical event — the block travels its plate's distance, lands flush, and
 * fires a haptic. What is currently believed about you sits at the top with its expiry, because a
 * condition that silently lapses after eighteen hours is the kind of thing an interface should say
 * out loud.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(
    state: AppState,
    contentPadding: PaddingValues,
    onChip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val active = state.myConditions

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = HomeOsDims.screenMargin,
            end = blockEndMargin(),
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter),
    ) {
        item(key = "header") {
            SectionHeader(title = "How are you?", trailing = state.alias)
        }

        item(key = "active") {
            if (active.isEmpty()) {
                PaperBlock(
                    plate = if (state.offline) Plate.Failure else Plate.None,
                    lead = state.offline,
                ) {
                    Text(
                        // Not knowing and knowing-there-is-nothing are different claims, and only
                        // one of them is true when the home is unreachable.
                        if (state.offline) {
                            "Can't reach the home, so what it believes about you is unknown."
                        } else {
                            "The home believes nothing about you right now."
                        },
                        style = type.body,
                        color = colors.inkMuted,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RIGHT NOW THE HOME BELIEVES", style = type.micro, color = colors.onShellMuted)
                    active.forEach { condition -> ConditionRow(condition) }
                }
            }
        }

        GROUPS.forEach { group ->
            item(key = group.title) {
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.title, style = type.section, color = colors.onShell)
                    Text(group.caption, style = type.caption, color = colors.onShellMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.chips.forEach { chip ->
                            val isActive = active.any { it.kind == chip.key || it.implies.contains(chip.key) }
                            GlassChip(
                                label = chip.label,
                                icon = chip.icon,
                                selected = isActive,
                                accent = group.plate.color(colors),
                                onClick = { onChip(chip.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One active condition, with what it implied and when it lapses.
 *
 * The `implies` row is the multi-hop step made visible: a fever also makes you sick, and it is
 * *sick* that moves the room. That is the project's central claim shown rather than asserted.
 */
@Composable
private fun ConditionRow(condition: Condition, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    PaperBlock(
        modifier = modifier,
        plate = Plate.Attention,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                HomeOsIcons.schedule,
                contentDescription = null,
                tint = Plate.Attention.ink(colors),
                modifier = Modifier.size(16.dp),
            )
            Text(
                condition.displayName,
                style = type.label,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(expiry(condition.until), style = type.micro, color = colors.inkMuted)
        }
        if (condition.implies.isNotEmpty()) {
            Text(
                "also means: ${condition.implies.joinToString(", ") { it.replace('_', ' ') }}",
                style = type.caption,
                color = colors.inkMuted,
            )
        }
        condition.source?.let { source ->
            Text("from $source", style = type.micro, color = colors.inkMuted)
        }
    }
}

private fun expiry(until: Long?): String {
    if (until == null) return ""
    val remaining = until - System.currentTimeMillis()
    if (remaining <= 0) return "lapsed"
    val hours = TimeUnit.MILLISECONDS.toHours(remaining)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
    return when {
        hours >= 24 -> "${hours / 24}d left"
        hours >= 1 -> "${hours}h left"
        else -> "${minutes}m left"
    }
}
