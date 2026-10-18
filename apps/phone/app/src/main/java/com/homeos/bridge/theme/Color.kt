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
    /**
     * The elevation ramp.
     *
     * Four steps, each perceptible (1.16-1.20:1) and none of them a slab. This replaced a design
     * that separated surfaces by raw value contrast — near-white blocks at 17:1 on a near-black
     * field — which is one of the two reliable tells that an interface was generated rather than
     * designed. The other is a border on every card, which is why [border] is now spent on the 3D
     * renderer and a hairline here is 8% of [onShell].
     *
     * Depth comes from luminance, not from lines.
     */
    val shell: Color,
    /** One step up: cards, list rows, the normal content surface. Also the glass tint base. */
    val shellRaised: Color,
    /** Two steps up: nested wells, inputs, chart plots, pressed states. */
    val surfaceHi: Color,
    /** Three steps up: sheets and dialogs, the only surfaces allowed to float. */
    val overlay: Color,
    /** Below the shell: the scrim behind a sheet, and the graph viewport's field. */
    val shellSunk: Color,
    /** Primary text. 14.14:1 on a card. Tone 93, never white — pure white haloes on a dark field. */
    val onShell: Color,
    /** Captions, labels, secondary values. 7.82:1 on a card. */
    val onShellMuted: Color,
    /** Micro labels, units, axis ticks. 4.73:1 on a card — still clears AA body text. */
    val onShellFaint: Color,

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
    /** The block border — the hardest line on screen. Same ink as body text: 16.10:1 on paper. */
    val border: Color get() = ink

    /**
     * The hard offset behind a block.
     *
     * Espresso rather than pure black, and that is the single change that pulls the style back
     * from cartoon. Canonical neobrutalism uses #000, which on a saturated field reads as a
     * sticker outline; a very dark warm brown is the same shape at the same weight and belongs to
     * the same light as the field, so it reads as a shadow something is casting. It measures
     * 7.98:1, so nothing is given up for it.
     */
    val plateShadow: Color get() = Color(0xFF2A1E14)

    /** Highlights on glass. White, because the glass now sits on a light field. */
    val specular: Color get() = Color.White

    /**
     * The daylit stage the 3D house stands in: the field, two steps deeper.
     *
     * Deeper rather than lighter because the model's walls are [paper], and a cream model on a
     * cream stage has no silhouette at all. This keeps 3.03:1 under the walls while still reading
     * as the same material as the rest of the app.
     */
    val well: Color get() = Color(0xFFB8894F)

    /**
     * The light at the back of the field, and the shadow at the front.
     *
     * The field is not one colour. It is a warm light behind the interface, falling away toward
     * the viewer, and these are its two ends: [shellBloom] where the light is, [shellDeep] where
     * it has gone. Both come from the same warm family as [shell], so the gradient is a change in
     * illumination rather than a change in hue — which is the difference between a background that
     * reads as lit and one that reads as a colour picker.
     */
    val shellBloom: Color get() = Color(0xFFFCE4C6)
    val shellDeep: Color get() = Color(0xFFD98F44)

    /**
     * The selection lens: the pill that marks the current tab or segment, and the wordmark.
     *
     * Burnt sienna with [onLens] reversed out of it — the field's own hue taken down to a tone
     * dark enough to carry text, rather than a separate colour introduced to mark a selection.
     *
     * It replaced a pastel mint capsule, which was the single most sweet-shop object the interface
     * owned and the last thing on screen still reading as a toy after the field turned warm. A
     * dark pill was going to be the answer either way; making it the dark end of the field means
     * the app now runs on exactly one hue, which is what reads as considered rather than decorated.
     *
     * Measured both ways round, because it is used both ways: [onLens] on the pill is 6.13:1, and
     * the pill — or the wordmark — against the field is 3.11:1, clearing the 3:1 floor for a large
     * mark with no room to spare, which is why it is this tone and not a lighter one.
     */
    val lens: Color get() = Color(0xFF9C4713)
    val onLens: Color get() = paper
}

/** oklch(L C H) -> sRGB, gamut-mapped. See theme docs for the audit. */
val HomeOsPalette = HomeOsColors(
    // The field: warm apricot, lit from behind. Sampled from the reference it was chosen against,
    // whose three bands are this, [shellBloom] and [shellDeep].
    //
    // The hue moved from cyan to apricot for one reason, worth writing down because it is the
    // fourth time this palette has been rebuilt. A saturated cyan field under cream blocks is a
    // *toy* palette: high chroma, cool, and the single combination every generated interface
    // reaches for. A warm field at the same lightness is what print has used for a century.
    // Nothing structural changed here — the app simply stopped looking like a plastic object.
    //
    // The lightness did not move, because three jobs depend on it: ink at 8.65:1, a block raised
    // at 1.97:1, and the hard shadow at 7.98:1.
    shell        = Color(0xFFEDA85C),
    shellRaised  = Color(0xFFFFFBF5), // glass tint, dev surface
    surfaceHi    = Color(0xFFF3EADD), // wells inside a block
    overlay      = Color(0xFFFFFCF7), // sheets
    // The dark well the two 3D views are windows onto. The only dark surface left, and warm now
    // as well, so the graph reads as a lit screen rather than a hole cut in the page.
    shellSunk    = Color(0xFF120E0B),
    onShell      = Color(0xFF1E1813), //  8.65:1 on the field
    onShellMuted = Color(0xFF4A423A), //  4.85:1 on the field
    onShellFaint = Color(0xFF655A50), //  3.30:1 on the field

    // Blocks, and the hardest line on screen. The same two colours do double duty as the 3D
    // renderer's materials, which is why the walls of the model are literally the app's paper and
    // the cut through the top of them is literally its ink.
    paper        = Color(0xFFFFFBF5), // block fill, and the walls of the room
    paperSunk    = Color(0xFFF3EADD), // wells, and the shaded face of a wall
    ink          = Color(0xFF1E1813), // borders, body text, and the cut through a wall
    inkMuted     = Color(0xFF4A423A),

    // The hues themselves are unchanged. They read warmer without being re-picked, because every
    // one of them now sits on a warm ground — and because the bright tones stopped being used as
    // slabs (see the blend in `frostedSurface`), which is what made a row of them read as sweets.
    teal      = Color(0xFF60D3CD), tealInk   = Color(0xFF0B625E), // 1.13:1 field / 6.97:1 paper
    violet    = Color(0xFFB6A2F3), violetInk = Color(0xFF543A8B), // 1.10:1 field / 8.32:1 paper
    amber     = Color(0xFFDDBA56), amberInk  = Color(0xFF7C550F), // 1.09:1 field / 6.44:1 paper
    red       = Color(0xFFF56F57), redInk    = Color(0xFFA32418), // 1.42:1 field / 7.07:1 paper
    green     = Color(0xFF67CFA5), greenInk  = Color(0xFF0B6548), // 1.11:1 field / 6.89:1 paper

    stageAwake = Color(0xFFAD7C2F), //  3.01:1 on paperSunk
    stageRem   = Color(0xFF6A51A4), //  5.14:1
    stageLight = Color(0xFF05464C), //  8.62:1
    stageDeep  = Color(0xFF01172E), // 14.73:1
)

/**
 * Avatar gradients, one per person, picked by a hash of the alias so the same person keeps the same
 * identity everywhere without the backend having to store one.
 *
 * Deep-to-mid pairs at a constant lightness step across six hues. The monogram is set in [paper],
 * which measures 7.2-8.1:1 on the deep stop and 3.6-4.1:1 on the mid stop, so a monogram placed
 * over the darker half of the gradient always clears AA.
 */
val AvatarGradients: List<Pair<Color, Color>> = listOf(
    Color(0xFF095955) to Color(0xFF158C87), // teal
    Color(0xFF543A8B) to Color(0xFF8264C8), // violet
    Color(0xFF684507) to Color(0xFFA26F11), // amber
    Color(0xFF852535) to Color(0xFFC24D5D), // rose
    Color(0xFF095C3B) to Color(0xFF159060), // green
    Color(0xFF294793) to Color(0xFF4E75D2), // indigo
)

/** Stable per-alias colour pair. */
fun avatarGradient(alias: String): Pair<Color, Color> {
    if (alias.isEmpty()) return AvatarGradients[0]
    val hash = alias.fold(7) { acc, c -> acc * 31 + c.code }
    return AvatarGradients[((hash % AvatarGradients.size) + AvatarGradients.size) % AvatarGradients.size]
}

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
