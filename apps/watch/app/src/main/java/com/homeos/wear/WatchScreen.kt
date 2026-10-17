package com.homeos.wear

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

private val Accent = Color(0xFF4FD1C5)
private val Warm = Color(0xFFF6AD55)

private val CHIPS = listOf(
    "sick" to "🤒  Feeling sick",
    "too_hot" to "🥵  Too hot",
    "too_cold" to "🥶  Too cold",
    "workout" to "🏃  Workout done",
    "stressed" to "😮‍💨  Stressed",
    "headache" to "🤕  Headache",
    "sleep" to "🌙  Going to sleep",
    "fine" to "🙂  I'm fine now",
    "better" to "👍  That helped",
    "worse" to "👎  Didn't help",
)

@Composable
fun WatchScreen(
    ui: WatchUi,
    onChip: (String) -> Unit,
    onMic: () -> Unit,
    onPrompt: (String) -> Unit,
    onStopPacer: () -> Unit,
) {
    MaterialTheme {
        when {
            ui.prompt != null -> PromptView(ui.prompt, onPrompt)
            ui.pacerBpm != null -> PacerView(ui.pacerBpm, ui.pacerEndsAt, onStopPacer)
            else -> Home(ui, onChip, onMic)
        }
    }
}

@Composable
private fun Home(ui: WatchUi, onChip: (String) -> Unit, onMic: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HomeOS", color = Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (ui.connected) Accent else Color.Red))
                    Text(
                        "  ${ui.alias} · ${ui.hr?.let { "♥ $it" } ?: "♥ --"}${if (ui.ibi) " · HRV" else ""}",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                    )
                }
            }
        }
        item {
            Button(
                onClick = onMic,
                modifier = Modifier.size(64.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = if (ui.recording) Color.Red else Accent),
            ) { Text(if (ui.recording) "■" else "🎤", fontSize = 22.sp) }
        }
        item {
            Text(
                when {
                    ui.thinking -> "Thinking…"
                    ui.lastSaid.isNotBlank() -> ui.lastSaid
                    else -> ui.status
                },
                fontSize = 12.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        if (ui.heard.isNotBlank()) item {
            Text("“${ui.heard}”", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 18.dp))
        }
        CHIPS.forEach { (key, label) ->
            item {
                Chip(
                    onClick = { onChip(key) },
                    label = { Text(label, fontSize = 13.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
        item { Text(ui.status, fontSize = 9.sp, color = Color.DarkGray, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun PromptView(p: PromptUi, onAnswer: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(p.text, textAlign = TextAlign.Center, fontSize = 13.sp, color = Color.White)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.options.forEach { o ->
                Button(onClick = { onAnswer(o) }, colors = ButtonDefaults.buttonColors(backgroundColor = if (o == "yes" || o == "better") Accent else Color.DarkGray)) {
                    Text(o.replaceFirstChar { it.uppercase() }, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PacerView(bpm: Double, endsAt: Long, onStop: () -> Unit) {
    val breathMs = (60_000 / bpm).toInt()
    val t = rememberInfiniteTransition(label = "breath")
    val s by t.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((breathMs * 0.4).toInt(), easing = FastOutSlowInEasing), RepeatMode.Reverse),
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
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).scale(s).clip(CircleShape).background(Warm.copy(alpha = 0.35f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Breathe", fontSize = 18.sp, color = Color.White)
            Text("$bpm / min · ${left}s", fontSize = 11.sp, color = Color.LightGray)
            Chip(onClick = onStop, label = { Text("Stop", fontSize = 11.sp) }, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
