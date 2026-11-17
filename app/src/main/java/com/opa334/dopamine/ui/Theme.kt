package com.opa334.dopamine.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dopamine's palette.
 *
 * iOS's Dopamine is a near-black screen with two greys: one for the menu card and one for the
 * jailbreak card. The same two values are used here so the Android build reads as the same app
 * rather than merely a similar one.
 */
object DopamineColors {
    /** Background: not pure black, which would flatten the cards into the wallpaper. */
    val Background = Color(0xFF08080A)

    /** The menu / action card. */
    val Menu = Color(0xFF1C1C1E)

    /** A slightly lighter grey for the jailbreak card at the bottom. */
    val MenuRaised = Color(0xFF2C2C2E)

    /** Hairline separators inside a card. */
    val Separator = Color(0xFF38383A)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E8E93)

    /** The tick / success green. */
    val Success = Color(0xFF30D158)

    val Warning = Color(0xFFFF9F0A)
    val Danger = Color(0xFFFF453A)
}

private val darkScheme = darkColorScheme(
    primary = DopamineColors.TextPrimary,
    onPrimary = DopamineColors.Background,
    background = DopamineColors.Background,
    onBackground = DopamineColors.TextPrimary,
    surface = DopamineColors.Menu,
    onSurface = DopamineColors.TextPrimary,
    surfaceVariant = DopamineColors.MenuRaised,
    onSurfaceVariant = DopamineColors.TextSecondary,
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF1C1C1E),
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE5E5EA),
)

/**
 * Typography.
 *
 * The top wordmark is a very large heavy sans — iOS uses the system font, and Roboto is the closest
 * Android equivalent, so the default family is kept and only the weight and size are forced.
 */
private val DopamineTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 44.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.5).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun DopamineTheme(
    /*
     * Always dark. Dopamine has no light appearance on iOS, and the darker cards are what makes the
     * white "Jailbreak" card read as the primary action; honouring the system light theme would
     * invert the one thing the interface is built around.
     */
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme else lightScheme,
        typography = DopamineTypography,
        content = content,
    )
}