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
 * for any non-text mark that has to be identifiable (control borders, chart lines, plates).
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

    @Test
    fun `shell text clears AA`() {
        assertContrast("onShell on shell", c.onShell, c.shell, 4.5)
        assertContrast("onShell on shellRaised", c.onShell, c.shellRaised, 4.5)
        assertContrast("onShellMuted on shell", c.onShellMuted, c.shell, 4.5)
    }

    @Test
    fun `paper text clears AA`() {
        assertContrast("ink on paper", c.ink, c.paper, 4.5)
        assertContrast("inkMuted on paper", c.inkMuted, c.paper, 4.5)
        assertContrast("ink on paperSunk", c.ink, c.paperSunk, 4.5)
        assertContrast("inkMuted on paperSunk", c.inkMuted, c.paperSunk, 4.5)
    }

    @Test
    fun `block silhouette and border are the strongest edges on screen`() {
        // The paper block against the shell, and the 2dp border against the paper it encloses.
        assertContrast("paper on shell", c.paper, c.shell, 3.0)
        assertContrast("border on paper", c.border, c.paper, 3.0)
    }

    @Test
    fun `every plate is identifiable on the shell`() {
        // This is the test that the canonical black hard shadow fails: black on this shell measures
        // about 1.38:1. A plate has to be seen, so it has to clear the non-text floor.
        assertContrast("teal plate", c.teal, c.shell, 3.0)
        assertContrast("violet plate", c.violet, c.shell, 3.0)
        assertContrast("amber plate", c.amber, c.shell, 3.0)
        assertContrast("green plate", c.green, c.shell, 3.0)
        assertContrast("red plate", c.red, c.shell, 3.0)
    }

    @Test
    fun `the canonical black plate would fail, which is why plates are chromatic`() {
        val black = ratio(Color(0xFF000000), c.shell)
        assertTrue(
            "A black hard shadow measures ${"%.2f".format(black)}:1 on the shell; if this ever " +
                "clears 3:1 the shell got light enough to use the reference implementation's black.",
            black < 3.0,
        )
    }

    @Test
    fun `accent ink tones are readable on paper`() {
        // The two-tone rule. The plate tone of the same hue would measure about 1.55:1 here.
        assertContrast("tealInk on paper", c.tealInk, c.paper, 4.5)
        assertContrast("violetInk on paper", c.violetInk, c.paper, 4.5)
        assertContrast("amberInk on paper", c.amberInk, c.paper, 4.5)
        assertContrast("redInk on paper", c.redInk, c.paper, 4.5)
        assertContrast("greenInk on paper", c.greenInk, c.paper, 4.5)
    }

    @Test
    fun `plate tones are not usable as paper ink`() {
        // Guards against the easiest mistake in this palette: grabbing the bright tone for text.
        assertTrue(
            "teal plate tone should be unreadable on paper, so nobody is tempted to use it there",
            ratio(c.teal, c.paper) < 3.0,
        )
    }

    @Test
    fun `sleep stages are visible on paper and separable without colour`() {
        val ramp = listOf(
            "awake" to c.stageAwake,
            "rem" to c.stageRem,
            "light" to c.stageLight,
            "deep" to c.stageDeep,
        )
        ramp.forEach { (name, color) ->
            assertContrast("stage $name on paperSunk", color, c.paperSunk, 3.0)
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
    fun `glass opacity floors keep chrome text legible over any backdrop`() {
        // A translucent surface has no fixed background, so the only way to certify it is to
        // composite over the two worst cases and require the floor to hold in both.
        fun over(tint: Color, alpha: Double, backdrop: Color) = Color(
            red = (tint.red * alpha + backdrop.red * (1 - alpha)).toFloat(),
            green = (tint.green * alpha + backdrop.green * (1 - alpha)).toFloat(),
            blue = (tint.blue * alpha + backdrop.blue * (1 - alpha)).toFloat(),
        )
        listOf("chrome" to 0.72, "sheet" to 0.86).forEach { (name, alpha) ->
            listOf("white" to Color.White, "black" to Color.Black).forEach { (bdName, backdrop) ->
                val composited = over(c.shellRaised, alpha, backdrop)
                assertContrast("onShell over $name glass on $bdName", c.onShell, composited, 4.5)
            }
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
