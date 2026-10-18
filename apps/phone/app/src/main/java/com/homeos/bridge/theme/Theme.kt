package com.homeos.bridge.theme

import android.app.UiModeManager
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.homeos.bridge.ui.components.LocalOnGlass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalHomeOsColors = staticCompositionLocalOf { HomeOsPalette }
val LocalHomeOsType = staticCompositionLocalOf { HomeOsType }

/**
 * True when the system animation scale is zero, which is Android's real "remove animations" signal.
 * Translation, scale and the ambient drift drop out; cross-fades, colour and progress stay.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * False when the user has asked for more contrast than default. A translucent surface cannot
 * certify its contrast against an unknown backdrop, so when someone has said they need contrast the
 * honest response is to stop being translucent rather than to tune an alpha.
 */
val LocalGlassEnabled = staticCompositionLocalOf { true }

/** Shorthand so screens read `HomeOs.colors.paper` rather than threading the locals by hand. */
object HomeOs {
    val colors: HomeOsColors
        @Composable get() = LocalHomeOsColors.current
    val type: HomeOsTypography
        @Composable get() = LocalHomeOsType.current
    val reducedMotion: Boolean
        @Composable get() = LocalReducedMotion.current
    val glass: Boolean
        @Composable get() = LocalGlassEnabled.current
}

/**
 * The user's contrast preference, live.
 *
 * `UiModeManager.getContrast()` landed in API 34 and returns -1..1 with 0 as default. The platform
 * docs say to query it when an app does its own rendering rather than leaning on the Material
 * pipeline, which is exactly this app: the glass chrome and every chart are drawn by hand. On
 * API 31-33 there is no such signal, so this reports 0.
 */
@Composable
private fun rememberSystemContrast(): Float {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0f
    val uiMode = remember(context) { context.getSystemService(UiModeManager::class.java) }
        ?: return 0f
    var contrast by remember { mutableFloatStateOf(uiMode.contrast) }
    DisposableEffect(uiMode) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        uiMode.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { uiMode.removeContrastChangeListener(listener) }
    }
    return contrast
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * Raises every low-contrast token to its full-contrast sibling, so nothing in the UI is carried by
 * a muted tone. Hues are left alone: the plate semantics still have to mean what they meant.
 */
private fun HomeOsColors.boosted() = HomeOsColors(
    shell = shell,
    shellRaised = shellRaised,
    surfaceHi = surfaceHi,
    overlay = overlay,
    shellSunk = shellSunk,
    onShell = onShell,
    onShellMuted = onShell,
    onShellFaint = onShell,
    paper = paper,
    paperSunk = paperSunk,
    ink = ink,
    inkMuted = ink,
    teal = teal, tealInk = tealInk,
    violet = violet, violetInk = violetInk,
    amber = amber, amberInk = amberInk,
    red = red, redInk = redInk,
    green = green, greenInk = greenInk,
    stageAwake = stageAwake, stageRem = stageRem,
    stageLight = stageLight, stageDeep = stageDeep,
)

@Composable
fun HomeOsTheme(content: @Composable () -> Unit) {
    val contrast = rememberSystemContrast()
    val reducedMotion = rememberReducedMotion()
    val colors = remember(contrast) {
        if (contrast > 0f) HomeOsPalette.boosted() else HomeOsPalette
    }

    // Material 3 underneath, so the stock components dev mode uses (text fields, buttons) inherit
    // the palette instead of defaulting to purple.
    val scheme = remember(colors) {
        darkColorScheme(
            primary = colors.teal,
            onPrimary = colors.ink,
            secondary = colors.violet,
            onSecondary = colors.ink,
            tertiary = colors.amber,
            onTertiary = colors.ink,
            background = colors.shell,
            onBackground = colors.onShell,
            surface = colors.shellRaised,
            onSurface = colors.onShell,
            surfaceVariant = colors.shellRaised,
            onSurfaceVariant = colors.onShellMuted,
            outline = colors.onShellMuted,
            error = colors.red,
            onError = colors.paper,
        )
    }

    CompositionLocalProvider(
        LocalHomeOsColors provides colors,
        LocalHomeOsType provides HomeOsType,
        LocalReducedMotion provides reducedMotion,
        LocalGlassEnabled provides (contrast <= 0f),
        // Glass everywhere, not only in the modals. It was scoped to sheets first because that is
        // where the hard offset was actively wrong; it turned out the answer was right for the
        // field too. `PaperBlock` still falls back to opaque paper when the system asks for more
        // contrast, which is the one case where translucency is the wrong answer at any price.
        LocalOnGlass provides true,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                bodyLarge = HomeOsType.body,
                bodyMedium = HomeOsType.body,
                bodySmall = HomeOsType.caption,
                labelLarge = HomeOsType.label,
                labelMedium = HomeOsType.caption,
                labelSmall = HomeOsType.micro,
                titleMedium = HomeOsType.blockTitle,
                titleLarge = HomeOsType.section,
            ),
            content = content,
        )
    }
}
