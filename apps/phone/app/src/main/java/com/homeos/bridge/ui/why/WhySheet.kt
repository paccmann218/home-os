package com.homeos.bridge.ui.why

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.Constraint
import com.homeos.bridge.data.EvidenceRow
import com.homeos.bridge.data.Explanation
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.theme.ink
import com.homeos.bridge.ui.components.Disclosure
import com.homeos.bridge.ui.components.EvidenceBars
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.ui.icons.HomeOsIcons

/**
 * Why the home did what it did.
 *
 * Layered on purpose. The explainability literature converges on a brief summary with detail on
 * demand rather than one wall of reasoning, and a clinical decision-support study found that an
 * output plus a bare description was judged *least* informative about whether the advice could be
 * trusted. So: one sentence always visible, the alternatives and the evidence a tap away, and the
 * privacy boundary never hidden.
 *
 * The alternatives section is the counterfactual, which is the part that tends to beat the
 * explanation itself — it says what the decision was sensitive to, not just what it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhyContent(
    explanation: Explanation?,
    evidence: List<EvidenceRow>,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    var showAlternatives by remember { mutableStateOf(false) }
    var showEvidence by remember { mutableStateOf(false) }
    var showContext by remember { mutableStateOf(false) }

    if (loading) {
        Row(
            modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = colors.teal)
        }
        return
    }

    if (explanation == null) {
        PaperBlock(plate = Plate.Failure, modifier = modifier) {
            Text("Couldn't load the reasoning", style = type.blockTitle, color = colors.ink)
            Text(
                error ?: "The home could not explain this decision.",
                style = type.caption,
                color = colors.inkMuted,
            )
        }
        return
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter)) {

        // Layer 1: what happened, in one sentence, always visible.
        PaperBlock(plate = Plate.Home) {
            Text("WHAT HAPPENED", style = type.micro, color = colors.inkMuted)
            Text(explanation.episode, style = type.blockTitle, color = colors.ink)
            if (explanation.rationale.isNotEmpty()) {
                Text(explanation.rationale, style = type.body, color = colors.ink)
            }
            if (explanation.chosen.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text("IT CHOSE", style = type.micro, color = colors.inkMuted)
                explanation.chosen.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            HomeOsIcons.checkCircle,
                            contentDescription = null,
                            tint = colors.greenInk,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            option.label.ifEmpty { option.name },
                            style = type.body,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        option.score?.let {
                            Text("%.2f".format(it), style = type.micro, color = colors.inkMuted)
                        }
                    }
                }
            }
        }

        // Layer 2: the counterfactual. What else was on the table and why it lost.
        Disclosure(
            title = "What else it considered",
            subtitle = if (explanation.rejected.isEmpty()) {
                "Nothing was ruled out for this one"
            } else {
                "${explanation.rejected.size} option(s) ruled out"
            },
            expanded = showAlternatives,
            onToggle = { showAlternatives = !showAlternatives },
        ) {
            if (explanation.rejected.isEmpty()) {
                PaperBlock(plate = Plate.None) {
                    Text(
                        "Every option the home had was usable here, so nothing was rejected. " +
                            "When something is ruled out, the reason shows up in this list.",
                        style = type.body,
                        color = colors.inkMuted,
                    )
                }
            } else {
                explanation.rejected.forEach { option ->
                    PaperBlock(plate = Plate.Attention) {
                        Text(option.name, style = type.label, color = colors.ink)
                        if (option.label.isNotEmpty()) {
                            Text(option.label, style = type.caption, color = colors.inkMuted)
                        }
                    }
                }
            }
        }

        // Layer 3: the evidence, with how much of it is this person's own.
        Disclosure(
            title = "The evidence behind it",
            subtitle = "What has worked for you before",
            expanded = showEvidence,
            onToggle = { showEvidence = !showEvidence },
        ) {
            PaperBlock(plate = Plate.Body) {
                EvidenceBars(rows = evidence.ifEmpty { explanation.evidence })
            }
        }

        Disclosure(
            title = "What the home knew at the time",
            subtitle = "${explanation.context.size} context bands",
            expanded = showContext,
            onToggle = { showContext = !showContext },
        ) {
            PaperBlock(plate = Plate.None) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    explanation.context.forEach { band ->
                        Text(
                            band,
                            style = type.monoMicro,
                            color = colors.inkMuted,
                        )
                    }
                }
            }
        }

        PrivacySplit(explanation = explanation)
    }
}

/**
 * The privacy boundary, drawn from the data rather than described.
 *
 * `explain` returns the decision in two halves, and the constraint rows carry a `mine` flag. Your
 * own bound arrives with its reason attached; everyone else's arrives as a bare range with the
 * reason null — not redacted by this screen, but genuinely absent, because it lives in a graph this
 * person's agent cannot read. That asymmetry is the whole project, so it is shown as it is.
 */
@Composable
private fun PrivacySplit(explanation: Explanation, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                HomeOsIcons.lock,
                contentDescription = null,
                tint = colors.onShellMuted,
                modifier = Modifier.size(16.dp),
            )
            Text("WHAT STAYED PRIVATE", style = type.micro, color = colors.onShellMuted)
        }

        PaperBlock(plate = Plate.Body) {
            Text("Yours alone", style = type.blockTitle, color = colors.ink)
            Text(
                "In your private graph. No other person's agent can read any of this.",
                style = type.caption,
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(2.dp))
            if (explanation.myConstraints.isEmpty()) {
                Text("No constraint of yours applied here.", style = type.body, color = colors.inkMuted)
            } else {
                explanation.myConstraints.forEach { constraint ->
                    ConstraintRow(constraint = constraint, mine = true)
                }
            }
        }

        PaperBlock(plate = Plate.Home) {
            Text("What the home was told", style = type.blockTitle, color = colors.ink)
            Text(
                "A range and nothing else. The home never learns why anyone needs it.",
                style = type.caption,
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(2.dp))
            if (explanation.otherConstraints.isEmpty()) {
                Text(
                    "Nobody else's comfort range bounded this decision.",
                    style = type.body,
                    color = colors.inkMuted,
                )
            } else {
                explanation.otherConstraints.forEach { constraint ->
                    ConstraintRow(constraint = constraint, mine = false)
                }
            }
            if (explanation.sharedSummary.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(explanation.sharedSummary, style = type.caption, color = colors.inkMuted)
            }
            explanation.applied.forEach { setting ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(setting.device, style = type.label, color = colors.ink)
                    Text(setting.setting, style = type.monoMicro, color = colors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun ConstraintRow(constraint: Constraint, mine: Boolean, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val plate = if (mine) Plate.Body else Plate.Home
    val range = buildString {
        append(constraint.variable.replace('_', ' '))
        append("  ")
        val min = constraint.min
        val max = constraint.max
        when {
            min != null && max != null -> append("${trim(min)}–${trim(max)}")
            min != null -> append("≥ ${trim(min)}")
            max != null -> append("≤ ${trim(max)}")
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(range, style = type.body, color = plate.ink(colors))
        Text(
            when {
                constraint.reason != null -> "because: ${constraint.reason.replace('_', ' ')}"
                mine -> "no reason recorded"
                else -> "reason not visible to you"
            },
            style = type.caption,
            color = colors.inkMuted,
        )
    }
}

private fun trim(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

/** Padding the sheet applies around [WhyContent] when embedded elsewhere. */
val WhyContentPadding = PaddingValues(HomeOsDims.screenMargin)
