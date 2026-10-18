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

    /** Lays [tint] over [backdrop] at [alpha], the way a translucent surface composites. */
    private fun over(tint: Color, alpha: Double, backdrop: Color) = Color(
        red = (tint.red * alpha + backdrop.red * (1 - alpha)).toFloat(),
        green = (tint.green * alpha + backdrop.green * (1 - alpha)).toFloat(),
        blue = (tint.blue * alpha + backdrop.blue * (1 - alpha)).toFloat(),
    )

    /**
     * The four glass levels, mirroring `GlassLevel`: surface alpha, and the sheen laid over its
     * top edge.
     *
     * The sheen is in this table rather than ignored by it, because the sheen is what makes the
     * material look like glass and it also *lightens* the surface — which works against light text
     * and therefore has to be paid for out of the same budget as the alpha. Control carries the
     * highest sheen and so needs the second-highest alpha despite being the smallest surface: it is
     * the only level with no blur to help it.
     */
    private val glassLevels = listOf(
        Triple("control", 0.74, 0.055),
        Triple("chrome", 0.72, 0.050),
        Triple("dock", 0.80, 0.050),
        Triple("sheet", 0.86, 0.045),
    )

    private fun glass(alpha: Double, sheen: Double, backdrop: Color) =
        over(c.onShell, sheen, over(c.shellRaised, alpha, backdrop))

    private val worstBackdrops = listOf("white" to Color.White, "black" to Color.Black)

    @Test
    fun `glass opacity floors keep chrome text legible over any backdrop`() {
        // A translucent surface has no fixed background, so the only way to certify it is to
        // composite over the two worst cases and require the floor to hold in both. White is not
        // hypothetical here: a paper block scrolls under the dock on every screen.
        glassLevels.forEach { (name, alpha, sheen) ->
            worstBackdrops.forEach { (bdName, backdrop) ->
                val composited = glass(alpha, sheen, backdrop)
                assertContrast("onShell over $name glass on $bdName", c.onShell, composited, 4.5)
            }
        }
    }

    @Test
    fun `the selection lens is visible and is never the only channel`() {
        // The lens is a brighter capsule of glass that slides between options, and it replaced a
        // hard outline drawn around whichever option was current. A tint on glass is a weak signal
        // on its own, so what is asserted here is the division of labour: the *tone* carries the
        // state at the 3:1 an identifiable mark needs, the lens only has to be visible as a shape,
        // and the filled glyph is the third channel. Nothing is carried by hue.
        worstBackdrops.forEach { (bdName, backdrop) ->
            val dock = glass(0.80, 0.050, backdrop)
            val lens = over(c.teal, 0.14, over(c.onShell, 0.15, dock))

            assertContrast("selected dock icon on the lens over $bdName", c.onShell, lens, 3.0)
            assertContrast("selected dock label on the lens over $bdName", c.onShell, lens, 3.0)

            val shape = ratio(lens, dock)
            assertTrue(
                "the lens must read as a shape against the glass it sits on, but measures " +
                    "${"%.2f".format(shape)}:1 over $bdName",
                shape >= 1.4,
            )

            // And the trap: the bright accent as a *glyph* on the lens does not clear the floor
            // once a paper block is behind the dock, which is exactly why the selected icon is set
            // in onShell and the teal is spent on the lens instead.
            if (backdrop == Color.White) {
                assertTrue(
                    "if teal ever clears 3:1 on the lens the surfaces changed; re-check whether " +
                        "the selected glyph could take the accent again",
                    ratio(c.teal, lens) < 3.0,
                )
            }
        }
    }

    @Test
    fun `accent marks on a control sit on the shell, where the accent tone belongs`() {
        // Chips and tiles are laid out on the ambient background, never over paper, so an accent
        // glyph on them is measured against what is actually behind them. The lightest the ambient
        // ever gets is its warm blob at full strength, which is these three literals from Shell.kt.
        val ambient = listOf(
            "cool" to Color(0xFF0E293D),
            "neutral" to Color(0xFF0C2628),
            "warm" to Color(0xFF3F1F10),
        )
        val accents = listOf(
            "teal" to c.teal, "violet" to c.violet, "amber" to c.amber,
            "green" to c.green, "red" to c.red,
        )
        ambient.forEach { (bdName, backdrop) ->
            val control = glass(0.74, 0.055, backdrop)
            accents.forEach { (name, accent) ->
                assertContrast("$name on control glass over the $bdName ambient", accent, control, 3.0)
            }
            assertContrast("onShell on control glass over the $bdName ambient", c.onShell, control, 4.5)
        }
    }

    @Test
    fun `the paper sheen does not cost the text anything`() {
        // Paper is a vertical gradient rather than a flat fill, because a single value across a
        // 300dp card reads as paint rather than as a sheet with light on it. The darkest stop is
        // still paper, so the text floors have to hold there and not just at the top.
        val bottom = over(c.paperSunk, 0.55, c.paper)
        assertContrast("ink on the paper gradient bottom", c.ink, bottom, 4.5)
        assertContrast("inkMuted on the paper gradient bottom", c.inkMuted, bottom, 4.5)
    }

    @Test
    fun `the accent rail uses the paper tone, which is the whole point of the two-tone rule`() {
        // Supporting blocks carry their hue as a rail inside the left edge instead of a second hard
        // plate. The rail is on paper, so it is the dark member of each pair; this test is here to
        // stop anyone reaching for `Plate.color()` because it is the brighter, nicer-looking one.
        val bottom = over(c.paperSunk, 0.55, c.paper)
        val pairs = listOf(
            "teal" to (c.teal to c.tealInk),
            "violet" to (c.violet to c.violetInk),
            "amber" to (c.amber to c.amberInk),
            "green" to (c.green to c.greenInk),
            "red" to (c.red to c.redInk),
        )
        pairs.forEach { (name, tones) ->
            val (plateTone, inkTone) = tones
            assertContrast("$name rail on paper", inkTone, c.paper, 3.0)
            assertContrast("$name rail on the paper gradient bottom", inkTone, bottom, 3.0)
            // The darker stop of the rail gradient.
            val darker = Color(
                red = inkTone.red * 0.84f,
                green = inkTone.green * 0.84f,
                blue = inkTone.blue * 0.84f,
            )
            assertContrast("$name rail, darker stop", darker, bottom, 3.0)
            assertTrue(
                "the $name plate tone measures ${"%.2f".format(ratio(plateTone, c.paper))}:1 on " +
                    "paper, so it cannot be used for the rail",
                ratio(plateTone, c.paper) < 3.0,
            )
        }
    }

    @Test
    fun `the plate gradient is still identifiable at its darkest stop`() {
        // The plate is lit top-to-bottom like everything else rather than being flat paint, so the
        // bottom of it is the plate colour at 80 percent. That is the stop that has to clear 3:1.
        listOf(
            "teal" to c.teal, "violet" to c.violet, "amber" to c.amber,
            "green" to c.green, "red" to c.red,
        ).forEach { (name, plate) ->
            val bottom = Color(
                red = plate.red * 0.80f,
                green = plate.green * 0.80f,
                blue = plate.blue * 0.80f,
            )
            assertContrast("$name plate, darkest stop", bottom, c.shell, 3.0)
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
