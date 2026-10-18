package com.homeos.bridge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.EvidenceRow
import com.homeos.bridge.theme.HomeOs
import kotlin.math.max

/**
 * What works for this person, and how much of it they actually learned.
 *
 * Each bar is split: the solid part is the share of `n` that came from this person's own measured
 * outcomes, and the pale remainder is the literature prior the system started from. That split is
 * the honest version of "personalised" — on the running backend `ac_cooler` had n=9 with
 * learned_n=7, while `fan_high` had n=1 with learned_n=0, and a single-tone bar would have made
 * those look like the same kind of claim.
 */
@Composable
fun EvidenceBars(
    rows: List<EvidenceRow>,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val well = wellFill()
    if (rows.isEmpty()) {
        Text(
            "Nothing measured yet. Evidence appears once the home has tried something and seen how it went.",
            style = type.caption,
            color = colors.inkMuted,
            modifier = modifier,
        )
        return
    }
    val peak = max(rows.maxOf { it.mean }, 0.0001f)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(
                        if (row.learnedN > 0) "n=${row.n} · ${row.learnedN} yours" else "n=${row.n} · prior only",
                        style = type.micro,
                        color = colors.inkMuted,
                    )
                }
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val radius = CornerRadius(3.dp.toPx())
                    drawRoundRect(color = well, size = size, cornerRadius = radius)
                    val width = (row.mean / peak).coerceIn(0f, 1f) * size.width
                    if (width <= 0f) return@Canvas
                    // The whole measured effect, in the pale tone.
                    drawRoundRect(
                        color = colors.tealInk.copy(alpha = 0.35f),
                        size = Size(width, size.height),
                        cornerRadius = radius,
                    )
                    // The share of it that is this person's own evidence.
                    val ownShare = if (row.n > 0) row.learnedN.toFloat() / row.n else 0f
                    if (ownShare > 0f) {
                        drawRoundRect(
                            color = colors.tealInk,
                            topLeft = Offset(0f, 0f),
                            size = Size(width * ownShare, size.height),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
        Text(
            "Solid = learned from your own body. Pale = the prior it started from.",
            style = type.micro,
            color = colors.inkMuted,
        )
    }
}
