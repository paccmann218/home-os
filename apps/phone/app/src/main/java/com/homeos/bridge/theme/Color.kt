package com.homeos.bridge.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * HomeOS palette.
 *
 * Authored in OKLCH, because a perceptually uniform space is the only way a ramp keeps its
 * lightness as the hue changes. Every value was then gamut-mapped into sRGB by reducing chroma
 * while holding lightness and hue (the whole reason to author in OKLCH), and audited: the measured
 * WCAG ratio for each pairing the app actually draws is in the comment beside it.
 *
 * The app has two colour worlds and they are not interchangeable:
 *
 *  - **Shell** is the dark ambient room: the gradient background and the glass chrome.
 *  - **Paper** is the light block: everything you read or touch.
 *
 * An accent hue therefore ships as a *pair*. The bright tone is for plates and shell-level marks;
 * the dark tone is for ink on paper. They are not substitutable — teal at the plate tone measures
 * 10.83:1 on the shell and 1.55:1 on paper, so using the wrong one makes text vanish.
 */
@Immutable
class HomeOsColors(
    /** Behind everything. The ambient gradient is drawn over this. */
    val shell: Color,
    /** Glass tint base, and the dev-mode surface. */
    val shellRaised: Color,
    /** Below the shell: the scrim behind a sheet. */
    val shellSunk: Color,
    /** Shell-level text. 15.81:1. Tone 93, never white — pure white haloes on a dark field. */
    val onShell: Color,
    /** The floor for shell text that carries meaning. 7.29:1. */
    val onShellMuted: Color,

    /** Block fill. Warm off-white, never #FFF. */
    val paper: Color,
    /** Inset wells, chart plots, table stripes. */
    val paperSunk: Color,
    /** Body text and numerals on paper. 16.10:1. Near-black, never #000. */
    val ink: Color,
    /** Captions on paper. 6.90:1 — still clears AA body text. */
    val inkMuted: Color,

    val teal: Color, val tealInk: Color,
    val violet: Color, val violetInk: Color,
    val amber: Color, val amberInk: Color,
    val red: Color, val redInk: Color,
    val green: Color, val greenInk: Color,

    /**
     * Sleep stages, solved numerically rather than picked: strictly monotonic in lightness so the
     * ramp survives greyscale and colour-blind viewing, every stage at least 3:1 on paper, and the
     * adjacent separation evened out at about 1.70:1 across all three steps.
     */
    val stageAwake: Color, val stageRem: Color, val stageLight: Color, val stageDeep: Color,
) {
    /** The 2dp block border — the hardest line on screen. Same ink as body text: 16.10:1 on paper. */
    val border: Color get() = ink
}

/** oklch(L C H) -> sRGB, gamut-mapped. See theme docs for the audit. */
val HomeOsPalette = HomeOsColors(
    shell        = Color(0xFF070F0F), // oklch(0.16 0.012 200)
    shellRaised  = Color(0xFF141C1D), // oklch(0.22 0.013 200)
    shellSunk    = Color(0xFF030707), // oklch(0.12 0.011 200)
    onShell      = Color(0xFFE2EAEA), // oklch(0.93 0.008 200)  15.81:1 on shell
    onShellMuted = Color(0xFF96A1A1), // oklch(0.70 0.012 200)   7.29:1 on shell

    paper        = Color(0xFFF4F1ED), // oklch(0.96 0.006 85)   17.25:1 on shell
    paperSunk    = Color(0xFFEAE8E3), // oklch(0.93 0.007 85)
    ink          = Color(0xFF131719), // oklch(0.20 0.008 240)  16.10:1 on paper
    inkMuted     = Color(0xFF4F5357), // oklch(0.44 0.008 240)   6.90:1 on paper

    teal      = Color(0xFF60D3CD), tealInk   = Color(0xFF0B625E), // 10.83:1 shell / 6.37:1 paper
    violet    = Color(0xFFB6A2F3), violetInk = Color(0xFF543A8B), //  8.73:1 shell / 7.96:1 paper
    amber     = Color(0xFFDDBA56), amberInk  = Color(0xFF7C550F), // 10.35:1 shell / 5.92:1 paper
    red       = Color(0xFFF56F57), redInk    = Color(0xFFA32418), //  6.75:1 shell / 6.61:1 paper
    green     = Color(0xFF67CFA5), greenInk  = Color(0xFF0B6548), // 10.17:1 shell / 6.31:1 paper

    stageAwake = Color(0xFFAD7C2F), //  3.01:1 on paperSunk
    stageRem   = Color(0xFF6A51A4), //  5.14:1
    stageLight = Color(0xFF05464C), //  8.62:1
    stageDeep  = Color(0xFF01172E), // 14.73:1
)

/**
 * What a block's plate says.
 *
 * Canonical neobrutalism puts a black hard shadow under every block. On a dark shell that measures
 * 1.38:1 — invisible — which is why the reference implementations either ship no dark theme at all
 * or ship one with the shadow silently broken. Making the plate chromatic fixes the contrast
 * (6.75–10.83:1) and buys something black never could: the loudest element on screen carries
 * information instead of decoration.
 *
 * The plate is always redundant with the block's title and icon, so WCAG 1.4.1 holds — nothing here
 * is conveyed by colour alone.
 */
enum class Plate {
    /** Your body: heart rate, sleep, HRV. */
    Body,
    /** The home: the room, devices, anything shared. */
    Home,
    /** The home wants something from you, or a condition is active. */
    Attention,
    /** A measured-good outcome. */
    Good,
    /** Failure only: offline, sync failed. */
    Failure,
    /** No plate. For blocks that sit inside another block. */
    None,
}

/** The plate colour, for the hard offset drawn on the shell. */
fun Plate.color(c: HomeOsColors): Color = when (this) {
    Plate.Body -> c.teal
    Plate.Home -> c.violet
    Plate.Attention -> c.amber
    Plate.Good -> c.green
    Plate.Failure -> c.red
    Plate.None -> Color.Transparent
}

/** The same hue at its paper tone, for an icon or figure *inside* the block. */
fun Plate.ink(c: HomeOsColors): Color = when (this) {
    Plate.Body -> c.tealInk
    Plate.Home -> c.violetInk
    Plate.Attention -> c.amberInk
    Plate.Good -> c.greenInk
    Plate.Failure -> c.redInk
    Plate.None -> c.ink
}
