package com.homeos.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes

/**
 * A lit window onto something drawn in three dimensions.
 *
 * This app has two such things — the room and the graph — and they should look like two views of
 * one product rather than two experiments. So the treatment lives here rather than being written
 * out twice: a dark well, a vignette that pulls the eye to the middle and keeps the corners from
 * reading as four empty right angles, the same top-lit sheen every other surface carries, a
 * hairline rim, and enough lift to sit it off the page.
 *
 * It is a window, not a card: nothing that has to be read precisely goes in here.
 */
@Composable
fun Viewport(
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    hint: String? = null,
    trailing: String? = null,
    /**
     * Whether this window is a lit screen or a daylit stage.
     *
     * The two 3D views want opposite things and it is worth saying why rather than picking one.
     * The graph is a network of glowing nodes and needs a dark field to glow against — it is a
     * screen. The house is a physical model of a room, and a physical model on a black void looks
     * like it is floating in space; it wants daylight. So the house passes `dark = false` and gets
     * a deeper tone of the field, which keeps it unmistakably part of this app.
     */
    dark: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type

    Box(modifier.fillMaxWidth().height(height + HomeOsDims.plateOffset)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(start = HomeOsDims.plateOffset, top = HomeOsDims.plateOffset)
                .background(colors.plateShadow, HomeOsShapes.block),
        )
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(HomeOsShapes.block)
            .background(if (dark) colors.shellSunk else colors.well)
            .drawBehind {
                // A light sky at the top falling to the stage colour, so a model standing in it
                // has somewhere to stand; on the dark variant the same gradient reads as a
                // vignette pulling the eye to the middle of the network.
                if (dark) {
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
                } else {
                    // Sky in the field's own light rather than in white. At 0.42 of white the
                    // stage came out the same value as the model standing on it, and a cream
                    // house on a cream stage has no silhouette — which is the one thing the
                    // daylit variant exists to give it.
                    drawRect(
                        Brush.verticalGradient(
                            0.00f to colors.shellBloom.copy(alpha = 0.55f),
                            0.58f to Color.Transparent,
                            1.00f to colors.ink.copy(alpha = 0.11f),
                        ),
                    )
                }
            }
            // The window onto the 3D views is a block like every other: ink border, hard offset.
            // It is the only dark surface in the app, which is exactly why it needs the same frame
            // as everything around it.
            .border(HomeOsDims.border, colors.border, HomeOsShapes.block),
        contentAlignment = Alignment.Center,
    ) {
        content()

        // Both labels sit on whichever backdrop this window is, and the two backdrops are at
        // opposite ends of the ramp — so a single token for them was legible on neither. Dark
        // ink on the shaded foot of the daylit stage was the worse of the two.
        val onWindow = if (dark) colors.paper.copy(alpha = 0.70f) else colors.ink.copy(alpha = 0.82f)

        hint?.let {
            Text(
                it,
                style = type.micro,
                color = onWindow,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
        trailing?.let {
            Text(
                it,
                style = type.monoMicro,
                color = onWindow,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
    }
    }
}
