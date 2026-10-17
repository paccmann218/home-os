package com.homeos.bridge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsColors
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.theme.ink
import kotlin.math.abs

/**
 * Charts are hand-drawn rather than taken from a library, for two reasons: only three shapes are
 * needed, and the colour and motion pipeline has to match the rest of the design exactly.
 *
 * Rules that apply to everything in this file:
 *  - charts sit on opaque paper, never on glass;
 *  - series lines clear 3:1 on their background, fills are allowed to be lighter;
 *  - nothing is carried by colour alone — direction, position or a label always carries it too;
 *  - live data animates the viewport, not each point, and nothing ever flashes.
 */

/**
 * Catmull-Rom through every point, converted to cubic Beziers.
 *
 * Catmull-Rom is the right spline here because it passes *through* its control points: a heart-rate
 * line must not invent a peak the body did not have. The 1/6 tangent scale is the uniform
 * (standard) parameterisation.
 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}

/**
 * A line of recent values, no axes and no gridlines.
 *
 * This is the only chart allowed on the Home screen. It answers "which way is this going" and
 * nothing else; the readout above it carries the value and the baseline band carries the meaning.
 * Anything that needs an axis belongs one tap deeper.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    plate: Plate = Plate.Body,
    fill: Boolean = true,
) {
    val colors = HomeOs.colors
    val line = plate.ink(colors)
    // Draw nothing at all rather than a faint rule. A near-invisible line in reserved space reads
    // as a block that failed to finish loading; the readout's own note already says there is no
    // data, so the honest thing is to take the space back.
    if (values.size < 2) return

    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        val min = values.min()
        val max = values.max()
        // A flat series should sit mid-height rather than divide by zero.
        val span = (max - min).takeIf { it > 1e-4f }
        val stroke = 2.dp.toPx()
        val top = stroke
        val usable = size.height - stroke * 2

        val points = values.mapIndexed { i, v ->
            val x = size.width * i / (values.size - 1).toFloat()
            val norm = if (span == null) 0.5f else (v - min) / span
            Offset(x, top + (1f - norm) * usable)
        }
        val path = smoothPath(points)

        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    0f to line.copy(alpha = 0.20f),
                    1f to line.copy(alpha = 0f),
                ),
            )
        }
        drawPath(path = path, color = line, style = Stroke(width = stroke))
        // The latest reading gets a dot, because on a live chart the end of the line is the point.
        drawCircle(color = line, radius = 3.dp.toPx(), center = points.last())
        drawCircle(color = colors.paper, radius = 1.4.dp.toPx(), center = points.last())
    }
}

/**
 * One bar per night, above or below this person's own mean.
 *
 * Direction carries the meaning as well as colour, so it survives greyscale: up is better than
 * usual, down is worse.
 */
@Composable
fun DeviationBars(
    zScores: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
) {
    val colors = HomeOs.colors
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        if (zScores.isEmpty()) return@Canvas
        val mid = size.height / 2f
        drawLine(
            color = colors.ink.copy(alpha = 0.22f),
            start = Offset(0f, mid),
            end = Offset(size.width, mid),
            strokeWidth = 1.dp.toPx(),
        )
        val slot = size.width / zScores.size
        val barWidth = minOf(slot * 0.5f, 10.dp.toPx())
        val cap = mid - 3.dp.toPx()
        zScores.forEachIndexed { i, z ->
            val clamped = z.coerceIn(-2.5f, 2.5f) / 2.5f
            val height = abs(clamped) * cap
            if (height < 1f) return@forEachIndexed
            val x = slot * i + (slot - barWidth) / 2f
            val top = if (clamped >= 0f) mid - height else mid
            drawRoundRect(
                color = if (clamped >= 0f) colors.greenInk else colors.amberInk,
                topLeft = Offset(x, top),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
    if (labels.isNotEmpty()) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = HomeOs.type.micro, color = colors.inkMuted) }
        }
    }
}

enum class SleepStage { Awake, Rem, Light, Deep }

@Immutable
data class SleepSegment(val stage: SleepStage, val minutes: Float)

private fun SleepStage.color(c: HomeOsColors): Color = when (this) {
    SleepStage.Awake -> c.stageAwake
    SleepStage.Rem -> c.stageRem
    SleepStage.Light -> c.stageLight
    SleepStage.Deep -> c.stageDeep
}

private val SleepStage.label: String
    get() = when (this) {
        SleepStage.Awake -> "awake"
        SleepStage.Rem -> "REM"
        SleepStage.Light -> "light"
        SleepStage.Deep -> "deep"
    }

/**
 * A night as a stacked bar, in chronological order.
 *
 * Five-minute buckets, which is the interval Oura settled on as the one that stays readable while
 * still showing stage trends. Adjacent segments are separated by a 1dp gap in the *surface* colour
 * rather than in white, which is the detail that keeps the bar from looking like it has highlights.
 */
@Composable
fun SleepStageBar(
    segments: List<SleepSegment>,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val total = segments.sumOf { it.minutes.toDouble() }.toFloat()

    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val radius = CornerRadius(5.dp.toPx())
        drawRoundRect(color = colors.paperSunk, size = size, cornerRadius = radius)
        if (total <= 0f) return@Canvas

        val gap = 1.dp.toPx()
        var x = 0f
        segments.forEach { segment ->
            val w = (segment.minutes / total) * size.width
            if (w > gap) {
                drawRoundRect(
                    color = segment.stage.color(colors),
                    topLeft = Offset(x, 0f),
                    size = Size(w - gap, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            x += w
        }
    }
}

/** The legend. Required: the stage ramp is monotonic but the hues still need naming once. */
@Composable
fun SleepStageLegend(modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SleepStage.entries.forEach { stage ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Canvas(Modifier.size(8.dp)) {
                    drawRoundRect(
                        color = stage.color(colors),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
                Text(stage.label, style = HomeOs.type.micro, color = colors.inkMuted)
            }
        }
    }
}

/** Shared by the charts above: a gridline tint that never competes with the series. */
internal fun DrawScope.gridline(colors: HomeOsColors, y: Float) {
    drawLine(
        color = colors.ink.copy(alpha = 0.06f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
    )
}
