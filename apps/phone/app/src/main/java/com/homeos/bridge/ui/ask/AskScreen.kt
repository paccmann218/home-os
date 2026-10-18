package com.homeos.bridge.ui.ask

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.ui.components.GlassAction
import com.homeos.bridge.ui.components.GlassIconButton
import com.homeos.bridge.ui.components.GlassLevel
import com.homeos.bridge.ui.components.GlassSurface
import com.homeos.bridge.ui.components.PaperBlock
import com.homeos.bridge.ui.components.ThinkingDots
import com.homeos.bridge.ui.components.blockMargin
import com.homeos.bridge.ui.components.frostedSurface
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
 *
 * The two voices are deliberately different materials. What you said is glass: it is your side of a
 * conversation, it scrolls away, and nothing in it needs to be read twice. What HomeOS answered is
 * paper, because it is an answer with reasons attached and it may well be long.
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
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 86.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter),
        ) {
            if (turns.isEmpty()) {
                item(key = "intro") {
                    PaperBlock(
                        modifier = Modifier.blockMargin(),
                        plate = Plate.Home,
                        lead = true,
                        padding = PaddingValues(HomeOsDims.heroPadding),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                HomeOsIcons.autoAwesome,
                                contentDescription = null,
                                tint = colors.violetInk,
                                modifier = Modifier.size(17.dp),
                            )
                            Text("ASK HOMEOS", style = type.micro, color = colors.inkMuted)
                        }
                        Text("What would you like to know?", style = type.section, color = colors.ink)
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
                        modifier = Modifier.padding(horizontal = HomeOsDims.screenMargin),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        STARTERS.forEach { starter ->
                            GlassAction(label = starter, onClick = { onAsk(starter) })
                        }
                    }
                }
            }

            itemsIndexed(turns, key = { index, _ -> index }) { _, turn ->
                TurnRow(turn)
            }

            if (pending) {
                item(key = "pending") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = HomeOsDims.screenMargin)) {
                        ThinkingDots()
                    }
                }
            }
        }

        // A composer built from the design's own parts rather than a stock Material field: thick
        // glass with its lit edge, a sparkle to say the answer comes from the graph, and the send
        // key as the one emphatic control on the screen.
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
                    bottom = contentPadding.calculateBottomPadding() + 6.dp,
                )
                .fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .padding(start = 18.dp, end = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    HomeOsIcons.autoAwesome,
                    contentDescription = null,
                    tint = colors.teal.copy(alpha = 0.75f),
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(12.dp))
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
                GlassIconButton(
                    icon = HomeOsIcons.arrowOutward,
                    contentDescription = "Send",
                    onClick = send,
                    enabled = canSend,
                    filled = canSend,
                    diameter = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun TurnRow(turn: Turn, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type

    if (turn.mine) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(start = HomeOsDims.screenMargin * 3, end = HomeOsDims.screenMargin),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                Modifier
                    // Asymmetric corners: the one nearest the speaker is tightened, which is the
                    // oldest trick in chat typography and still the clearest.
                    .frostedSurface(
                        shape = RoundedCornerShape(16.dp, 16.dp, 5.dp, 16.dp),
                        selection = 1f,
                        accent = colors.teal,
                    )
                    .padding(horizontal = 15.dp, vertical = 11.dp),
            ) {
                Text(turn.text, style = type.body, color = colors.onShell)
            }
        }
        return
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(start = HomeOsDims.screenMargin, end = HomeOsDims.screenMargin * 2),
    ) {
        PaperBlock(
            plate = if (turn.failed) Plate.Failure else Plate.Home,
            lead = turn.failed,
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
