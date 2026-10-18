package com.homeos.bridge.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homeos.bridge.R

/**
 * Space Grotesk for anything that carries weight, Inter for text, JetBrains Mono for machine output.
 *
 * The display face used to be Fraunces, a high-contrast serif. It was a defensible choice and it
 * was quietly working against the whole design: a serif at display size pulls a screen editorial,
 * and this one is meant to look built rather than published. Space Grotesk is a grotesque with
 * enough oddity in it — the flattened terminals, the squared bowls — to carry an identity without
 * the help of a serif, which is the register this style actually wants.
 *
 * Inter stays for body text and **all inline numbers**, because it has `tnum` and Space Grotesk's
 * figures are proportional: a live heart rate set in the display face would change width as it
 * ticks. Mono is spent deliberately, on dev mode and graph identifiers, where looking like machine
 * output is the point.
 *
 * Features throughout: `tnum` (tabular figures), `zero` (slashed zero), `ss02` (Inter's
 * disambiguation set, which separates I/l/1 and earns its place in a UI full of aliases like p01
 * and body:naomi), and `calt`.
 */
private const val FEATURES = "tnum, zero, ss02, calt"

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int, opsz: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.opticalSizing(opsz.sp),
    ),
)

/** Text letterforms (opsz 14). All body, labels and captions. */
val Inter = FontFamily(
    inter(400, 14), inter(500, 14), inter(600, 14), inter(700, 14),
)

/**
 * Space Grotesk, variable on `wght` 300-700.
 *
 * Everything display-sized is set at 700, the top of the axis, because this style has no use for a
 * medium weight in a heading — the whole point of the face here is mass.
 */
@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(
    resId = R.font.space_grotesk_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(
    grotesk(500), grotesk(600), grotesk(700),
)

@OptIn(ExperimentalTextApi::class)
val Mono = FontFamily(
    Font(
        resId = R.font.jetbrains_mono_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.jetbrains_mono_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

/**
 * The scale.
 *
 * Tracking tightens as size rises and loosens as it falls, matching Material's own typescale
 * (display negative, 11-12sp labels around +0.03em). Getting that relationship backwards is the
 * most visible typographic tell there is. The display sizes are tracked harder negative than that
 * convention asks for, because a heavy grotesque set tight is the look and a heavy grotesque set
 * loose is a road sign.
 */
@Immutable
class HomeOsTypography(
    /** Axis ticks, unit suffixes, the smallest labels. Set uppercase at the call site. */
    val micro: TextStyle,
    val caption: TextStyle,
    val body: TextStyle,
    /** Control labels, set heavier than body per the style's weight-500 base. */
    val label: TextStyle,
    val blockTitle: TextStyle,
    val section: TextStyle,
    /** A tile's number. */
    val readout: TextStyle,
    /** The one number that leads the screen. */
    val hero: TextStyle,
    val mono: TextStyle,
    val monoMicro: TextStyle,
)

val HomeOsType = HomeOsTypography(
    micro = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.06.em,
        fontFeatureSettings = FEATURES,
    ),
    caption = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.015.em,
        fontFeatureSettings = FEATURES,
    ),
    body = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em,
        fontFeatureSettings = FEATURES,
    ),
    label = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.004.em,
        fontFeatureSettings = FEATURES,
    ),
    // From here down the grotesque takes over, at the top of its weight axis.
    blockTitle = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.01).em,
    ),
    section = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.025).em,
    ),
    readout = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-0.035).em,
    ),
    hero = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-0.045).em,
    ),
    mono = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.em,
        fontFeatureSettings = "zero, calt",
    ),
    monoMicro = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.em,
        fontFeatureSettings = "zero, calt",
    ),
)
