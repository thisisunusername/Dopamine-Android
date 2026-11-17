package com.opa334.dopamine.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import com.opa334.dopamine.R

/**
 * The colours, taken from the iOS app rather than invented.
 *
 * The window is not black. Dopamine paints a photograph (`Background_Green.jpg`) edge
 * to edge and blurs it, then draws translucent grey panels on top. Everything below is
 * read from `Themes.plist` and `DONavigationController.m`:
 *
 *   image Background_Green.jpg, ScaleAspectFill, blur 18
 *   actionMenuColor 723F3F3F -> rgb(63,63,63) at 0x72/255 = 45% alpha   (cards/buttons)
 *   windowColor     994C4C4C -> rgb(76,76,76) at 0x99/255 = 60% alpha   (settings window)
 *
 * The iOS code multiplies the menu colour's alpha by a factor per use site
 * (`menuColorWithAlpha:`), so the constants below are that colour at the alphas the
 * call sites pass, not new colours.
 */
object DopamineColors {

    /** `actionMenuColor` = #3F3F3F at 45%. The card and button fill. */
    val Menu = Color(0x723F3F3F)

    /** The same colour raised for things that must read above a card (e.g. a pressed row). */
    val MenuRaised = Color(0x8C3F3F3F)

    /** `windowColor` = #4C4C4C at 60%. The settings/about sheet. */
    val Window = Color(0x994C4C4C)

    /** Hairlines: the iOS separator is white at 10-15%. */
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
 * Wraps the whole app.
 *
 * The background lives here rather than in each screen so switching screens cannot flash
 * a different colour, and so the blackout overlay can cover it without fighting it.
 */
@Composable
fun DopamineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = DopamineColors.Menu,
            onBackground = DopamineColors.TextPrimary,
            onSurface = DopamineColors.TextPrimary,
        ),
        typography = Typography(),
        content = content,
    )
}

/**
 * The themed background: the iOS photo, scaled to fill, blurred, sitting behind everything.
 *
 * The source asset is 3000x2000, so it is decoded once and remembered; blurring is a
 * render-time effect and costs nothing on the bitmap itself. `scale(1.08)` hides the
 * softening of the edges that a blur otherwise leaves visible.
 */
@Composable
fun ThemeBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Decoded once and remembered; asImageBitmap() is a zero-copy view over the same pixels.
    val bitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.bg_green)?.asImageBitmap()
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
                    .blur(18.dp),
            )
        }
    }
}
