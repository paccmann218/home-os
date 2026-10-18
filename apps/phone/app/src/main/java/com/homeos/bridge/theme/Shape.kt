package com.homeos.bridge.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Canonical neobrutalism uses a 5px radius with a 2px border. It is gently rounded, not sharp,
 * which is the detail most imitations get wrong. The border weight is kept exactly; the radius
 * scales up for phone-sized surfaces, with children at `outer - padding` so curves stay concentric.
 */
object HomeOsShapes {
    val block = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(10.dp)

    /** Inset wells and chart plots: 14 - 16 padding clamps to a small positive radius. */
    val well = RoundedCornerShape(6.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

object HomeOsDims {
    /** 2dp, not 1dp. The weight is the style. */
    val border = 2.dp

    /** The hard offset. A pressed block travels exactly this far and lands flush. */
    val plateOffset = 4.dp

    /**
     * The accent rail: how a supporting block carries its hue without a second hard offset.
     *
     * The plate is the loudest device in the design and it is only honest on the one block a screen
     * is actually about. Ten plates stacked down a scroll is not ten emphases, it is none — so
     * every other block keeps the same semantic colour as a bar inside its left edge.
     */
    val rail = 5.dp

    val screenMargin = 16.dp
    val gutter = 12.dp
    val blockPadding = 16.dp
    val heroPadding = 24.dp

    /** Android's floor. The reference library's 40px control would fail it. */
    val minTouch = 48.dp
    val focusRing = 2.dp
    val focusOffset = 2.dp

    /** Glass keeps a 1dp stroke at every elevation; only its alpha rises. */
    val glassBorder = 1.dp

    /** The lit inner edge that reads as the thickness of the glass. */
    val glassEdge = 1.5.dp

    /** How far the selection lens sits inside its track. */
    val lensInset = 6.dp
}
