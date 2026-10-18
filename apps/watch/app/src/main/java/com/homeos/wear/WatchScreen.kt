package com.homeos.wear

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import kotlinx.coroutines.delay

// Phone tokens, unchanged. Field is shell, blocks are paper, the rule is ink,
// the hard offset is plateShadow, the one accent is the lens.
private val Field = Color(0xFFEDA85C)
private val Paper = Color(0xFFFFFBF5)
private val Ink = Color(0xFF1E1813)
private val InkMuted = Color(0xFF4A423A)
private val Shadow = Color(0xFF2A1E14)
private val Accent = Color(0xFF9C4713)
private val OnAccent = Color(0xFFFFFBF5)

private val BlockShape = RoundedCornerShape(6.dp)
private val BorderW = 2.dp
private val ShadowOff = 3.dp
private val Inset = 16.dp
private val SafeWidth = 148.dp

private val Tight = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

private val Title = Tight.copy(
    fontWeight = FontWeight.Black,
    fontSize = 15.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.4).sp,
)
private val Label = Tight.copy(
    fontWeight = FontWeight.Black,
    fontSize = 13.sp,
    lineHeight = 15.sp,
)
private val Body = Tight.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 14.sp,
)
private val Micro = Tight.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    lineHeight = 13.sp,
)

private val CHIPS = listOf(
    "sick" to "Feeling sick",
    "too_hot" to "Too hot",
    "too_cold" to "Too cold",
    "workout" to "Workout done",
    "stressed" to "Stressed",
    "headache" to "Headache",
    "sleep" to "Going to sleep",
    "fine" to "I'm fine",
    "better" to "That helped",
    "worse" to "Didn't help",
)

private fun Modifier.safeWidth() = widthIn(max = SafeWidth).fillMaxWidth()

@Composable
fun WatchScreen(
    ui: WatchUi,
    onChip: (String) -> Unit,
    onMic: () -> Unit,
    onPrompt: (String) -> Unit,
    onStopPacer: () -> Unit,
    onAlarmYes: () -> Unit,
    onAlarmNo: () -> Unit,
    onAlarmReplay: () -> Unit,
    onAlarmDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Field)) {
        when {
            ui.alarm != null -> AlarmDemoView(ui, onAlarmYes, onAlarmNo, onAlarmReplay, onAlarmDismiss)
            ui.prompt != null -> PromptView(ui.prompt, onPrompt)
            ui.pacerBpm != null -> PacerView(ui.pacerBpm, ui.pacerEndsAt, onStopPacer)
            else -> Home(ui, onChip, onMic, onAlarmReplay)
        }
    }
}

@Composable
private fun Home(ui: WatchUi, onChip: (String) -> Unit, onMic: () -> Unit, onAlarmReplay: () -> Unit) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val hr = ui.hr?.toString() ?: "--"
    val meta = "${ui.alias} · HR $hr${if (ui.ibi) " · HRV" else ""}"
    val (detail, detailColor) = when {
        ui.thinking -> "Thinking…" to Accent
        ui.lastSaid.isNotBlank() -> ui.lastSaid to Ink
        else -> ui.status to Ink
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Field),
        state = listState,
        contentPadding = PaddingValues(horizontal = Inset, vertical = Inset),
        autoCentering = AutoCenteringParams(itemIndex = 1),
        // No edge fade and no fisheye. Chips stay one size; the 16dp inset and the
        // 148dp column keep the shadow inside the round bezel.
        scalingParams = ScalingLazyColumnDefaults.scalingParams(
            edgeScale = 1f,
            edgeAlpha = 1f,
            minElementHeight = 0.2f,
            maxElementHeight = 0.8f,
            minTransitionArea = 0.5f,
            maxTransitionArea = 0.5f,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Column(
                Modifier.safeWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Ink)) { append("Home") }
                        withStyle(SpanStyle(color = Accent)) { append("OS") }
                    },
                    style = Title,
                )
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(if (ui.connected) Accent else Ink))
                    BasicText(
                        meta,
                        modifier = Modifier.padding(start = 5.dp),
                        style = Micro.copy(color = Ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(key = "mic") {
            BrutalButton(
                label = if (ui.recording) "Send" else "Speak",
                onClick = onMic,
                accent = ui.recording,
                fill = true,
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(2.dp),
                style = Label.copy(fontSize = 12.sp),
            )
        }
        item(key = "detail") {
            Column(Modifier.safeWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                WatchText(detail, detailColor, Body, Modifier.fillMaxWidth(), maxLines = 2)
                if (ui.heard.isNotBlank()) {
                    WatchText(
                        ui.heard,
                        InkMuted,
                        Micro,
                        Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
        }
        item(key = "alarm") {
            BrutalButton(
                "Morning alarm",
                onClick = onAlarmReplay,
                accent = true,
                modifier = Modifier.safeWidth(),
            )
        }
        CHIPS.forEach { (key, label) ->
            item(key = key) {
                BrutalButton(label, onClick = { onChip(key) }, modifier = Modifier.safeWidth())
            }
        }
    }
}

@Composable
private fun AlarmDemoView(
    ui: WatchUi,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onReplay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val alarm = ui.alarm ?: return
    SafeCenter {
        WatchText(alarm.title, Ink, Title, Modifier.fillMaxWidth(), maxLines = 2)
        if (alarm.done) {
            TimePair(alarm.detail)
        } else if (alarm.detail.isNotBlank()) {
            WatchText(alarm.detail, Ink, Body, Modifier.fillMaxWidth(), maxLines = 2)
        }
        when {
            alarm.listening -> {
                val cue = if (ui.heard.isNotBlank()) ui.heard else "Listening…"
                WatchText(
                    cue,
                    if (ui.heard.isNotBlank()) InkMuted else Accent,
                    Body,
                    Modifier.fillMaxWidth(),
                )
                ActionRow(
                    listOf(
                        Triple("Yes", true, onYes),
                        Triple("No", false, onNo),
                    ),
                )
            }
            alarm.done -> ActionRow(
                listOf(
                    Triple("Again", false, onReplay),
                    Triple("Home", false, onDismiss),
                ),
            )
            else -> {
                val status = if (ui.thinking) "Speaking…" else ui.status
                if (status.isNotBlank() && status != alarm.title && status != alarm.detail) {
                    WatchText(status, Accent, Body, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TimePair(detail: String) {
    val meridian = when {
        detail.contains("PM", ignoreCase = true) -> "PM"
        detail.contains("AM", ignoreCase = true) -> "AM"
        else -> null
    }
    val times = detail
        .split('·', '•')
        .map { it.replace("AM", "", ignoreCase = true).replace("PM", "", ignoreCase = true).trim() }
        .filter { it.isNotEmpty() }
    if (times.size < 2) {
        WatchText(detail, Ink, Title, Modifier.fillMaxWidth())
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            times.take(2).forEach { time ->
                Brutal(stretch = false, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    WatchText(time, Ink, Title.copy(fontSize = 16.sp, lineHeight = 18.sp))
                }
            }
        }
        if (meridian != null) {
            WatchText(meridian, Ink, Micro, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ActionRow(actions: List<Triple<String, Boolean, () -> Unit>>) {
    if (actions.size <= 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { (label, accent, onClick) ->
                BrutalButton(label, onClick, accent = accent, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actions.forEach { (label, accent, onClick) ->
                BrutalButton(label, onClick, accent = accent, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PromptView(p: PromptUi, onAnswer: (String) -> Unit) {
    SafeCenter {
        WatchText(p.text, Ink, Body, Modifier.fillMaxWidth(), maxLines = 4)
        ActionRow(
            p.options.map { option ->
                val accent = option.equals("yes", true) || option.equals("better", true)
                Triple(option.replaceFirstChar { it.uppercase() }, accent) { onAnswer(option) }
            },
        )
    }
}

@Composable
private fun PacerView(bpm: Double, endsAt: Long, onStop: () -> Unit) {
    val breathMs = (60_000 / bpm).toInt().coerceAtLeast(400)
    val t = rememberInfiniteTransition(label = "breath")
    val s by t.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween((breathMs * 0.4).toInt(), easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "scale",
    )
    var left by remember { mutableLongStateOf((endsAt - System.currentTimeMillis()) / 1000) }
    LaunchedEffect(endsAt) {
        while (left > 0) {
            delay(1000)
            left = (endsAt - System.currentTimeMillis()) / 1000
        }
        onStop()
    }
    val rate = if (kotlin.math.abs(bpm - bpm.toInt()) < 0.05) bpm.toInt().toString() else "%.1f".format(bpm)
    SafeCenter {
        Brutal(
            modifier = Modifier.scale(s).size(48.dp),
            accent = true,
            fill = true,
            contentPadding = PaddingValues(0.dp),
        ) {}
        WatchText("Breathe", Ink, Title)
        WatchText("$rate / min · ${left.coerceAtLeast(0)}s", Ink, Body)
        BrutalButton("Stop", onStop, stretch = false, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SafeCenter(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Inset),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.safeWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun BrutalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    fill: Boolean = false,
    stretch: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    style: TextStyle = Label,
) {
    Brutal(
        modifier = modifier,
        accent = accent,
        onClick = onClick,
        fill = fill,
        stretch = stretch,
        contentPadding = contentPadding,
    ) {
        WatchText(label, if (accent) OnAccent else Ink, style)
    }
}

/**
 * Cream block, 2dp ink rule, 3dp hard offset. The offset is inside the layout
 * (padding on the end and bottom) so the shadow cannot leave the circle.
 * A press travels onto the shadow, the same move as a phone block.
 */
@Composable
private fun Brutal(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    fill: Boolean = false,
    stretch: Boolean = true,
    shape: Shape = BlockShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateDpAsState(
        targetValue = if (onClick != null && pressed) ShadowOff else 0.dp,
        animationSpec = tween(durationMillis = 70),
        label = "press",
    )
    val grow = when {
        fill -> Modifier.fillMaxSize()
        stretch -> Modifier.fillMaxWidth()
        else -> Modifier
    }
    Box(modifier.padding(end = ShadowOff, bottom = ShadowOff)) {
        Box(
            Modifier
                .matchParentSize()
                .plateShift(ShadowOff)
                .background(Shadow, shape),
        )
        Box(
            Modifier
                .then(grow)
                .plateShift(travel)
                .clip(shape)
                .background(if (accent) Accent else Paper)
                .border(BorderW, Ink, shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** Places a block without changing its measured size, so the shadow stays in the padding. */
private fun Modifier.plateShift(amount: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val px = amount.roundToPx()
    layout(placeable.width, placeable.height) {
        placeable.place(px, px)
    }
}

@Composable
private fun WatchText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = TextAlign.Center),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
