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

/** Text letterforms (opsz 14). All body, labels and captions. */
val Inter = FontFamily(
    inter(400, 14), inter(500, 14), inter(600, 14), inter(700, 14),
)

/**
 * Fraunces: the display voice.
 *
 * A high-contrast variable serif with a real optical-size axis spanning 9-144, which is the reason
 * it is here rather than a one-size display face: the same family can be set at 40sp and at 16sp
 * and stay optically correct, where a single-master serif goes spindly when you scale it down.
 *
 * `WONK` stays at 0 — its quirky alternates are charming on a brand site and wrong on something
 * you read when you feel ill. `SOFT` stays at 0 for crisp terminals against the hard block borders.
 *
 * It carries headings, titles, monograms and the large readouts. It does *not* carry body text or
 * inline numbers: Fraunces has no tabular-figure feature, so anything that ticks stays in Inter,
 * where `tnum` keeps the digits from dancing.
 */
@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: Int, opsz: Int) = Font(
    resId = R.font.fraunces_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.opticalSizing(opsz.sp),
        FontVariation.Setting("SOFT", 0f),
        FontVariation.Setting("WONK", 0f),
    ),
)

private fun frauncesFamily(opsz: Int) = FontFamily(
    fraunces(400, opsz), fraunces(500, opsz), fraunces(600, opsz), fraunces(700, opsz),
)

/** 40sp hero readouts and the wordmark. */
val DisplayXL = frauncesFamily(72)

/** 28sp tile readouts. */
val DisplayL = frauncesFamily(44)

/** 20sp section headings. */
val DisplayM = frauncesFamily(28)

/** 16sp block titles. */
val DisplayS = frauncesFamily(20)

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
    // From here down the display serif takes over. Tracking goes neutral-to-negative, because a
    // high-contrast serif is already generously spaced and letter-spacing it looks amateur.
    blockTitle = TextStyle(
        fontFamily = DisplayS, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.em,
    ),
    section = TextStyle(
        fontFamily = DisplayM, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 29.sp, letterSpacing = (-0.005).em,
    ),
    readout = TextStyle(
        fontFamily = DisplayL, fontWeight = FontWeight.SemiBold,
        fontSize = 31.sp, lineHeight = 34.sp, letterSpacing = (-0.012).em,
    ),
    hero = TextStyle(
        fontFamily = DisplayXL, fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em,
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
