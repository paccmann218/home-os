package com.homeos.bridge.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.ui.components.GlassLevel
import com.homeos.bridge.ui.components.GlassSurface
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.ui.icons.HomeOsIcons

/** One side of the conversation. */
@Immutable
data class Turn(
    val mine: Boolean,
    val text: String,
    val tools: List<String> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Starters, so the screen works without typing.
 *
 * These are not decoration: they are the three questions the graph is actually good at, which is
 * also how someone discovers that asking is worth doing at all.
 */
private val STARTERS = listOf(
    "Why is the room at this temperature?",
    "What helps me when I'm sick?",
    "What usually ruins my sleep?",
)

/**
 * Ask: the way in for anyone who does not want to read panels.
 *
 * Kept deliberately, because the rest of the app asks a fair amount of its reader, and on a bad day
 * a person should be able to type one sentence instead. When the agent reached for graph tools, the
 * tool names are shown under its reply — that is the project's "it reasoned over the graph" moment,
 * and it belongs in the product rather than only in the dashboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    turns: List<Turn>,
    pending: Boolean,
    contentPadding: PaddingValues,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size, pending) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size)
    }

    // fillMaxSize, not fillMaxWidth: the composer is aligned to this Box's bottom, and a Box that
    // wraps its content leaves it floating wherever the conversation happens to end.
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = HomeOsDims.screenMargin,
                end = HomeOsDims.screenMargin,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter),
        ) {
            if (turns.isEmpty()) {
                item(key = "intro") {
                    PaperBlock(plate = Plate.Home) {
                        Text("Ask HomeOS", style = type.blockTitle, color = colors.ink)
                        Text(
                            "It answers from your own graph, so it can say why it did something and " +
                                "what has actually worked for you.",
                            style = type.body,
                            color = colors.inkMuted,
                        )
                    }
                }
                item(key = "starters") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        STARTERS.forEach { starter ->
                            PaperBlock(
                                plate = Plate.Home,
                                shape = HomeOsShapes.chip,
                                onClick = { onAsk(starter) },
                                padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                            ) {
                                Text(starter, style = type.label, color = colors.ink)
                            }
                        }
                    }
                }
            }

            itemsIndexed(turns, key = { index, _ -> index }) { _, turn ->
                TurnRow(turn)
            }

            if (pending) {
                item(key = "pending") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        PaperBlock(plate = Plate.Home, modifier = Modifier.fillMaxWidth(0.55f)) {
                            Text("thinking…", style = type.body, color = colors.inkMuted)
                        }
                    }
                }
            }
        }

        // A composer built from the design's own parts rather than a stock Material field: a glass
        // pill with a hard rim, and the send control as a plated circle so it presses like every
        // other block in the app.
        val canSend = draft.isNotBlank() && !pending
        val send = {
            if (draft.isNotBlank()) {
                onAsk(draft.trim())
                draft = ""
            }
        }

        GlassSurface(
            level = GlassLevel.Dock,
            shape = HomeOsShapes.pill,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = HomeOsDims.screenMargin,
                    end = HomeOsDims.screenMargin,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                )
                .fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text("Ask anything", style = type.body, color = colors.onShellMuted)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = type.body.copy(color = colors.onShell),
                        cursorBrush = SolidColor(colors.teal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) colors.teal else colors.onShell.copy(alpha = 0.12f))
                        .border(
                            HomeOsDims.border,
                            if (canSend) colors.border else colors.onShell.copy(alpha = 0.18f),
                            CircleShape,
                        )
                        .clickable(enabled = canSend, onClick = send),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        HomeOsIcons.arrowOutward,
                        contentDescription = "Send",
                        tint = if (canSend) colors.shell else colors.onShellMuted,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TurnRow(turn: Turn, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(Modifier.fillMaxWidth(0.88f)) {
            PaperBlock(
                plate = when {
                    turn.failed -> Plate.Failure
                    turn.mine -> Plate.Body
                    else -> Plate.Home
                },
            ) {
                Text(turn.text, style = type.body, color = colors.ink)
                if (turn.tools.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            HomeOsIcons.hub,
                            contentDescription = null,
                            tint = colors.violetInk,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            turn.tools.joinToString("  "),
                            style = type.monoMicro,
                            color = colors.violetInk,
                        )
                    }
                }
            }
        }
    }
}
