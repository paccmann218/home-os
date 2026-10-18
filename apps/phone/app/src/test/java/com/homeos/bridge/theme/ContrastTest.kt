package com.homeos.bridge.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * The palette's contrast floors, asserted against the real token values.
 *
 * This exists because a hand-authored palette is exactly the kind of thing that drifts: someone
 * nudges a lightness to make a screenshot look nicer and a caption quietly drops under 4.5:1. The
 * design is only defensible if the numbers are checked by something other than an eye.
 *
 * Floors follow WCAG 2.2 AA as the compliance baseline: 4.5:1 for body text, 3:1 for large text and
 * for any non-text mark that has to be identifiable.
 *
 * **These tests were inverted once, deliberately.** An earlier version of this design put cream
 * blocks on a near-black shell, and this file asserted the consequence: that a black hard shadow
 * *must fail* there, which is why that version used chromatic ones. The field is bright now, the
 * black shadow measures better than ten to one, and the old assertions have been replaced rather
 * than deleted quietly — the reasoning they encoded is kept in `the black shadow works now`.
 */
class ContrastTest {

    private val c = HomeOsPalette

    private fun channel(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, floor: Double) {
        val got = ratio(fg, bg)
        assertTrue(
            "$name needs $floor:1 but measures ${"%.2f".format(got)}:1",
            got >= floor,
        )
    }

    /** Lays [tint] over [backdrop] at [alpha], the way a translucent surface composites. */
    private fun over(tint: Color, alpha: Double, backdrop: Color) = Color(
        red = (tint.red * alpha + backdrop.red * (1 - alpha)).toFloat(),
        green = (tint.green * alpha + backdrop.green * (1 - alpha)).toFloat(),
        blue = (tint.blue * alpha + backdrop.blue * (1 - alpha)).toFloat(),
    )

    // ------------------------------------------------------------------------------- the field

    @Test
    fun `the field carries text, blocks and their shadows`() {
        // Everything this style needs from its background, in three numbers.
        assertContrast("ink on the field", c.onShell, c.shell, 4.5)
        assertContrast("muted ink on the field", c.onShellMuted, c.shell, 4.5)
        assertContrast("faint ink on the field", c.onShellFaint, c.shell, 3.0)

        val block = ratio(c.paper, c.shell)
        assertTrue(
            "a block must read as raised off the field, but measures ${"%.2f".format(block)}:1",
            block >= 1.6,
        )
        assertContrast("the hard shadow on the field", c.plateShadow, c.shell, 3.0)
    }

    @Test
    fun `the black shadow works now, which it did not before`() {
        // The whole reason earlier versions of this design used chromatic plates: against a
        // near-black shell a black shadow measured about 1.38:1 and simply was not there. Both
        // halves are asserted, because the first is the justification for the second.
        val onOldShell = ratio(c.plateShadow, Color(0xFF070F0F))
        assertTrue(
            "against the old near-black shell a black shadow measured " +
                "${"%.2f".format(onOldShell)}:1, which is why that design could not use one",
            onOldShell < 3.0,
        )
        assertContrast("the same shadow on the bright field", c.plateShadow, c.shell, 3.0)
    }

    @Test
    fun `a coloured block would be invisible on this field, so blocks are not coloured`() {
        // The guard against the obvious-looking idea. Every accent, used as a block fill, sits
        // within a hair of the field's own luminance; the hue reads but the *shape* does not, and
        // a block whose edge you cannot find is not a block. Colour goes inside, on the cream.
        listOf(
            "teal" to c.teal, "violet" to c.violet, "amber" to c.amber,
            "green" to c.green, "red" to c.red,
        ).forEach { (name, accent) ->
            val got = ratio(accent, c.shell)
            assertTrue(
                "if $name ever clears 1.6:1 on the field a coloured block becomes possible and " +
                    "this rule should be revisited; it measures ${"%.2f".format(got)}:1",
                got < 1.6,
            )
        }
    }

    // ------------------------------------------------------------------------------ the blocks

    @Test
    fun `text on a block clears AA`() {
        assertContrast("ink on paper", c.ink, c.paper, 4.5)
        assertContrast("inkMuted on paper", c.inkMuted, c.paper, 4.5)
        assertContrast("ink on a well", c.ink, c.paperSunk, 4.5)
        assertContrast("inkMuted on a well", c.inkMuted, c.paperSunk, 4.5)
    }

    @Test
    fun `the border is the hardest line on screen`() {
        assertContrast("border on paper", c.border, c.paper, 3.0)
        assertContrast("border against the field", c.border, c.shell, 3.0)
    }

    @Test
    fun `the semantic hue reads inside a block, at its paper tone`() {
        // Colour lives on the cream now rather than on the shadow, which means the *ink* tone of
        // each pair is the one doing the work and the bright tone is the one that must not be
        // reached for. Both directions are asserted, as they were before.
        listOf(
            "teal" to (c.teal to c.tealInk),
            "violet" to (c.violet to c.violetInk),
            "amber" to (c.amber to c.amberInk),
            "green" to (c.green to c.greenInk),
            "red" to (c.red to c.redInk),
        ).forEach { (name, tones) ->
            val (bright, inkTone) = tones
            assertContrast("$name rail and icon on paper", inkTone, c.paper, 4.5)
            assertTrue(
                "the bright $name tone measures ${"%.2f".format(ratio(bright, c.paper))}:1 on " +
                    "paper, so it cannot carry anything there",
                ratio(bright, c.paper) < 3.0,
            )
        }
    }

    // ------------------------------------------------------------------------------- the glass

    @Test
    fun `glass over the field stays legible at every level`() {
        // A translucent surface has no fixed backdrop, so the only way to certify it is to
        // composite over the worst cases and require the floor in all of them. The glass tints
        // toward cream now, so the text on it is ink rather than the near-white it used to be.
        listOf(
            "control" to 0.58, "chrome" to 0.55, "dock" to 0.58, "sheet" to 0.60,
        ).forEach { (name, alpha) ->
            listOf(
                "the field" to c.shell,
                "white" to Color.White,
                "black" to Color.Black,
            ).forEach { (bdName, backdrop) ->
                val glass = over(c.shellRaised, alpha, backdrop)
                assertContrast("ink on $name glass over $bdName", c.ink, glass, 4.5)
            }
        }
    }

    @Test
    fun `a pane inside a sheet is still legible, over the worst backdrop there is`() {
        // The modals are glass all the way down now: a translucent pane on a translucent sheet on
        // whatever the user happened to be looking at. Two alphas multiply, which is exactly the
        // kind of thing that is fine on the screen it was designed against and fails on the one
        // dark one — so it is composited here over black, which is the worst this app can produce
        // (the graph viewport), and required to clear AA at the pane's darkest point.
        val sheetAlpha = 0.60
        listOf("foot of a pane" to 0.36, "head of a pane" to 0.54).forEach { (name, paneAlpha) ->
            listOf("the field" to c.shell, "black" to Color.Black).forEach { (bdName, backdrop) ->
                val sheet = over(c.shellRaised, sheetAlpha, backdrop)
                val pane = over(c.paper, paneAlpha, sheet)
                assertContrast("ink at the $name over $bdName", c.ink, pane, 4.5)
                assertContrast("muted ink at the $name over $bdName", c.inkMuted, pane, 4.5)
            }
        }
    }

    // ------------------------------------------------------------------------------ everything

    @Test
    fun `sleep stages are visible on a well and separable without colour`() {
        val ramp = listOf(
            "awake" to c.stageAwake,
            "rem" to c.stageRem,
            "light" to c.stageLight,
            "deep" to c.stageDeep,
        )
        ramp.forEach { (name, color) ->
            assertContrast("stage $name on a well", color, c.paperSunk, 3.0)
        }
        // Strictly monotonic luminance, so the ramp survives greyscale and colour-blind viewing,
        // and each step is far enough apart to read as a different band.
        ramp.zipWithNext().forEach { (a, b) ->
            val la = luminance(a.second)
            val lb = luminance(b.second)
            assertTrue("${a.first} must be lighter than ${b.first}", la > lb)
            assertTrue(
                "${a.first} vs ${b.first} separation is only ${"%.2f".format(ratio(a.second, b.second))}:1",
                ratio(a.second, b.second) >= 1.4,
            )
        }
    }

    @Test
    fun `tokens are opaque`() {
        // Every token is a solid colour; translucency is applied at the call site, never baked in,
        // so that the glass floors above remain the single place alpha is decided.
        listOf(c.shell, c.paper, c.ink, c.teal, c.violet, c.amber, c.red, c.green).forEach {
            assertTrue("token alpha must be 1", abs(it.alpha - 1f) < 1e-6)
        }
    }
}
