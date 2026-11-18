package com.opa334.dopamine.ui

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Colours that do not change with the theme.
 *
 * Everything that does come from the theme (the card fill, the window fill, the shadow flag) is
 * carried by [DopaminePalette] instead, because the theme can change while the app is running.
 *
 * The values below are read from `Themes.plist` and `DONavigationController.m`; the default
 * theme's `actionMenuColor 723F3F3F` is #3F3F3F at 45% and its `windowColor 994C4C4C` is #4C4C4C
 * at 60%, which is where the comments in [DopaminePalette] come from.
 */
object DopamineColors {

    /** Hairlines: the iOS separator is white at 10-15%. Theme-independent. */
    val Separator = Color(0x1AFFFFFF)

    /** `mainSubtitleString`: solid white. */
    val TextPrimary = Color.White

    /** `secondarySubtitleString`: white at 60%. */
    val TextSecondary = Color(0x99FFFFFF)

    /** Button titles use `baseForegroundColor = white`, dimmed to 75% when inactive. */
    val TextDisabled = Color(0xBF7A7A7A)

    /** The switch tint in DOPSListController: rgb(71,169,135). */
    val Success = Color(0xFF47A987)

    /** Destructive text on the remove-jailbreak row. */
    val Danger = Color(0xFFFF453A)

    /** The warning glyph in the About notice. */
    val Warning = Color(0xFFFFB340)
}

/**
 * The active theme's palette, readable from any composable.
 *
 * A CompositionLocal rather than a parameter on every screen: the theme is genuinely global state,
 * and threading it through every call site would add an argument to a dozen composables that only
 * forward it.
 */
val LocalDopaminePalette = staticCompositionLocalOf { DopaminePalette.of(DopamineThemeKey.fallback) }

/** The active theme itself, for the pieces that need its artwork (the boot ring, the icons). */
val LocalDopamineTheme = staticCompositionLocalOf { DopamineThemeKey.fallback }

/** Shorthand for the current palette. */
val dopaminePalette: DopaminePalette
    @Composable get() = LocalDopaminePalette.current

/**
 * Wraps the whole app.
 *
 * The theme is passed in rather than read from storage here so the settings screen can switch it
 * and have every screen recompose from that one value.
 */
@Composable
fun DopamineTheme(
    theme: DopamineThemeKey = DopamineThemeKey.fallback,
    content: @Composable () -> Unit,
) {
    val palette = remember(theme) { DopaminePalette.of(theme) }
    CompositionLocalProvider(
        LocalDopaminePalette provides palette,
        LocalDopamineTheme provides theme,
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Color.Black,
                surface = palette.menu,
                onBackground = DopamineColors.TextPrimary,
                onSurface = DopamineColors.TextPrimary,
            ),
            typography = Typography(),
            content = content,
        )
    }
}

/**
 * The themed background: the theme's photograph, scaled to fill, blurred, behind everything.
 *
 * The blur radius is the theme's own `blur` value from the plist, not a constant: Midnight Neon
 * used 30 and Deep Blue Sky uses 40, and flattening them to one number would make four themes look
 * more alike than the original does.
 *
 * `scale(1.08)` hides the softening of the edges that a blur otherwise leaves visible.
 */
@Composable
fun ThemeBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val theme = LocalDopamineTheme.current

    /*
     * Decoded once, at the size it is blurred at.
     *
     * This used to decode the full-resolution JPEG and put Modifier.blur() on it. Modifier.blur()
     * re-runs its gaussian pass every time the layer is redrawn, and this layer sits under the
     * whole app, so every log line and every progress tick re-blurred a full-screen 40dp image.
     * The main thread never got a clean frame, which is why the notification shade could not be
     * pulled down while a run was going - the symptom reported as "the progress still cannot be
     * dragged".
     *
     * The image is now decoded at a small size (inSampleSize) and blurred at that size. A gaussian
     * of radius r at half resolution is the same picture as radius 2r at full resolution, so the
     * filter runs on roughly a sixteenth of the pixels for an identical result, and the cost stops
     * mattering.
     */
    val bitmap: ImageBitmap? = remember(theme, uiScaleBucket()) {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(context.resources, theme.background, maxEdgePx = 540)
        }
        runCatching {
            BitmapFactory.decodeResource(context.resources, theme.background, opts)
                ?.asImageBitmap()
        }.getOrNull()
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.08f)
                    .blur(theme.blurPoints.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            )
        }
    }
}

/**
 * The smallest power-of-two `inSampleSize` that keeps the decoded image under [maxEdgePx].
 *
 * Measured from the encoded bounds rather than assumed, so replacing a background with a larger
 * photograph cannot silently reintroduce the full-resolution decode.
 */
private fun sampleSizeFor(res: Resources, resId: Int, maxEdgePx: Int): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(res, resId, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return 1
    var sample = 1
    while ((longest / sample) > maxEdgePx) sample *= 2
    return sample
}

/** Recomputes the background only when the display density class changes, not on rotation. */
private fun uiScaleBucket(): Int = Resources.getSystem().displayMetrics.densityDpi