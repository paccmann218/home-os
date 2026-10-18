package com.homeos.bridge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.theme.ink
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What this person's own body usually does, from their `Baseline` node in the graph.
 *
 * HomeOS computes everything as a deviation from the person's own mean and sd, never against a
 * population norm. The interface has to say so, or the number on screen is less honest than the
 * data behind it.
 */
@Immutable
data class Baseline(val mean: Float, val sd: Float)

/** How far from usual, in the person's own words rather than in standard deviations. */
private fun deviationText(current: Float, baseline: Baseline): String {
    if (baseline.sd <= 0f) return "no baseline yet"
    val z = (current - baseline.mean) / baseline.sd
    val magnitude = abs(z)
    return when {
        magnitude < 1f -> "normal for you"
        else -> {
            val direction = if (z > 0) "above" else "below"
            "${"%.1f".format(magnitude)} SD $direction your usual"
        }
    }
}

/**
 * The band: the person's own mean plus or minus one standard deviation, with a marker for where
 * they are now.
 *
 * The band is the context and the marker is the reading. Drawn in that order, because a number
 * floating with no band is the thing this app is trying not to be.
 */
@Composable
fun BaselineBand(
    current: Float,
    baseline: Baseline,
    plate: Plate,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val markerColor = plate.ink(colors)
    val sd = if (baseline.sd > 0f) baseline.sd else 1f
    // Three sd either side covers essentially everything; anything beyond that pins to the edge
    // rather than rescaling, so the band does not silently change meaning on an outlier.
    val low = baseline.mean - 3f * sd
    val high = baseline.mean + 3f * sd
    val position = ((current - low) / (high - low)).coerceIn(0f, 1f)
    val bandStart = ((baseline.mean - sd - low) / (high - low)).coerceIn(0f, 1f)
    val bandEnd = ((baseline.mean + sd - low) / (high - low)).coerceIn(0f, 1f)

    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val radius = CornerRadius(5.dp.toPx())
        // The rail: everything three sd either side of usual.
        drawRoundRect(color = colors.paperSunk, size = size, cornerRadius = radius)
        // The usual range. A tint of ink rather than a hue, so it never competes with the marker.
        drawRoundRect(
            color = colors.ink.copy(alpha = 0.10f),
            topLeft = Offset(bandStart * size.width, 0f),
            size = Size((bandEnd - bandStart) * size.width, size.height),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        // Dead centre: this person's mean.
        drawLine(
            color = colors.ink.copy(alpha = 0.35f),
            start = Offset(0.5f * size.width, size.height * 0.18f),
            end = Offset(0.5f * size.width, size.height * 0.82f),
            strokeWidth = 1.dp.toPx(),
        )
        // Where they are now.
        val markerWidth = 4.dp.toPx()
        val x = (position * size.width).coerceIn(markerWidth / 2, size.width - markerWidth / 2)
        drawRoundRect(
            color = markerColor,
            topLeft = Offset(x - markerWidth / 2, 0f),
            size = Size(markerWidth, size.height),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

/**
 * A number, with everything needed to read it: what it is, its unit, where it sits against this
 * person's own baseline, and a one-line reading of that position.
 *
 * @param hero the single number that leads a screen. 40sp display letterforms; everything else
 *   uses the 28sp tile size.
 */
@Composable
fun StatReadout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    plate: Plate = Plate.Body,
    hero: Boolean = false,
    current: Float? = null,
    baseline: Baseline? = null,
    note: String? = null,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = plate.ink(colors),
                modifier = Modifier.size(if (hero) 20.dp else 18.dp),
            )
        }
        Text(label.uppercase(), style = type.micro, color = colors.inkMuted)
    }

    // A missing value is set in the muted tone, and drops its unit. At 40sp SemiBold a dark dash
    // reads as a redaction bar rather than as an absence, which is the opposite of the intent.
    val missing = value == NO_VALUE
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Text(
            text = value,
            // A missing value drops out of hero size as well as out of ink. At 46sp a placeholder
            // dash is a stroke the width of a thumb sitting in the middle of an empty card, which
            // reads as something broken rather than as something not yet known.
            style = if (hero && !missing) type.hero else type.readout,
            color = if (missing) colors.inkMuted.copy(alpha = 0.45f) else colors.ink,
        )
        if (unit != null && !missing) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                style = type.caption,
                color = colors.inkMuted,
                modifier = Modifier.height(if (hero) 30.dp else 22.dp),
            )
        }
    }

    if (current != null && baseline != null) {
        BaselineBand(current = current, baseline = baseline, plate = plate)
        Text(
            text = deviationText(current, baseline),
            style = type.caption,
            color = colors.inkMuted,
        )
    } else if (note != null) {
        Text(note, style = type.caption, color = colors.inkMuted)
    }
}

/** A quiet row label above a group of blocks, on the shell rather than on paper. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val colors = HomeOs.colors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = HomeOs.type.section, color = colors.onShell)
        if (trailing != null) {
            Text(
                trailing,
                style = HomeOs.type.micro,
                color = colors.onShellMuted,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** What a readout shows when there is nothing to show. An en dash, not an em dash. */
const val NO_VALUE = "–"

/** Formats a value for a readout: one decimal under 10, whole numbers above, [NO_VALUE] when absent. */
fun readoutValue(v: Float?): String = when {
    v == null -> NO_VALUE
    abs(v) < 10f -> "%.1f".format(v)
    else -> v.roundToInt().toString()
}

/**
 * A status dot. Colour is never the only channel here — it always sits beside a label.
 *
 * [onColor] wins when given, for both states, so a caller can say "off means red" rather than
 * being forced into the muted default.
 */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier, onColor: Color? = null) {
    val colors = HomeOs.colors
    val fill = onColor ?: if (on) colors.green else colors.onShellMuted
    Canvas(modifier.size(8.dp)) {
        drawCircle(fill)
    }
}
