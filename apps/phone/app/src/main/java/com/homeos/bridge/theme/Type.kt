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
 * Inter v4 variable for the interface, JetBrains Mono variable for machine text.
 *
 * Inter is here for two axes this app genuinely uses. `wght` gives real weights from one file
 * instead of faux-bolding, and `opsz` (14-32 in this build) lets the hero readouts use display
 * letterforms while 11sp labels use text letterforms, so the type stays optically even across a
 * 4x size range. Mono is spent deliberately: it appears only in dev mode and on graph identifiers,
 * where looking like machine output is the point.
 *
 * Features applied throughout: `tnum` (tabular figures, so an animating number never reflows its
 * neighbours), `zero` (slashed zero), `ss02` (Inter's disambiguation set, which separates I/l/1 and
 * earns its place in a UI full of aliases like p01 and body:naomi), and `calt`.
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

/** Text letterforms (opsz 14). Everything below 28sp. */
val Inter = FontFamily(
    inter(400, 14), inter(500, 14), inter(600, 14), inter(700, 14),
)

/** Display letterforms (opsz 32). The readouts, which are large and mostly digits. */
val InterDisplay = FontFamily(
    inter(500, 32), inter(600, 32), inter(700, 32),
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
 * The scale. Tracking tightens as size rises and loosens as it falls, matching Material's own
 * typescale (display sizes negative, 11-12sp labels around +0.03em). Getting that relationship
 * backwards is the most visible typographic tell there is.
 */
@Immutable
class HomeOsTypography(
    /** Axis ticks, unit suffixes, the smallest labels. */
    val micro: TextStyle,
    val caption: TextStyle,
    val body: TextStyle,
    /** Control labels, set heavier than body per neobrutalism's weight-500 base. */
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
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.03.em,
        fontFeatureSettings = FEATURES,
    ),
    caption = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.025.em,
        fontFeatureSettings = FEATURES,
    ),
    body = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.015.em,
        fontFeatureSettings = FEATURES,
    ),
    label = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.006.em,
        fontFeatureSettings = FEATURES,
    ),
    blockTitle = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.009.em,
        fontFeatureSettings = FEATURES,
    ),
    section = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.em,
        fontFeatureSettings = FEATURES,
    ),
    readout = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em,
        fontFeatureSettings = FEATURES,
    ),
    hero = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.015).em,
        fontFeatureSettings = FEATURES,
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
