package com.opa334.dopamine.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.opa334.dopamine.R

/**
 * One of Dopamine's themes, read from `Application/Dopamine/UI/Themes/Themes.plist`.
 *
 * Nothing here is invented. Each field is the value the iOS plist carries for that key:
 *
 *   key / name        the preference value and the name the settings list shows
 *   image             the background photograph (`Background_*.imageset`)
 *   actionMenuColor   the translucent fill used by cards and the jailbreak button
 *   windowColor       the fill used by the settings window
 *   blur              the blur radius applied to the background photo, in points
 *   titleShadow       whether the header text carries a shadow
 *   icon              the themed app icon (`AppIcon_*.appiconset`), null for the default theme
 *
 * The midnight theme in the original plist is deliberately not included: it was dropped on
 * request, so the list below is the five themes this build ships.
 */
enum class DopamineThemeKey(
    val key: String,
    val displayName: String,
    @DrawableRes val background: Int,
    @DrawableRes val icon: Int,
    /** `actionMenuColor`, verbatim (#AARRGGBB as written in the plist). */
    val actionMenuColor: Color,
    /** `windowColor`, verbatim. */
    val windowColor: Color,
    /** `blur`, in points. */
    val blurPoints: Int,
    /** `titleShadow`. */
    val titleShadow: Boolean,
) {
    Default(
        key = "default",
        displayName = "Dopamine",
        background = R.drawable.bg_green,
        icon = R.drawable.theme_icon_default,
        actionMenuColor = Color(0x723F3F3F),
        windowColor = Color(0x994C4C4C),
        blurPoints = 18,
        titleShadow = false,
    ),
    ElleKit(
        key = "ellekit",
        displayName = "ElleKit",
        background = R.drawable.bg_ellekit,
        icon = R.drawable.theme_icon_ellekit,
        actionMenuColor = Color(0x3F610C71),
        windowColor = Color(0x6009040A),
        blurPoints = 18,
        titleShadow = true,
    ),
    Blue(
        key = "blue",
        displayName = "Deep Blue Sky",
        background = R.drawable.bg_blue,
        icon = R.drawable.theme_icon_blue,
        actionMenuColor = Color(0x3F0C2571),
        windowColor = Color(0x6004050A),
        blurPoints = 40,
        titleShadow = false,
    ),
    Red(
        key = "red",
        displayName = "Blood Sky",
        background = R.drawable.bg_red,
        icon = R.drawable.theme_icon_red,
        actionMenuColor = Color(0x5F560808),
        windowColor = Color(0x600A0404),
        blurPoints = 25,
        titleShadow = false,
    ),
    Purple(
        key = "purple",
        displayName = "Desert Dusk",
        background = R.drawable.bg_purple,
        icon = R.drawable.theme_icon_purple,
        actionMenuColor = Color(0x4F150C71),
        windowColor = Color(0x6008040A),
        blurPoints = 35,
        titleShadow = false,
    ),
    ;

    companion object {
        /** The order shown in the settings list is the plist's order, so it is the declaration order. */
        val all: List<DopamineThemeKey> = entries.toList()

        /** `DOThemeManager -enabledTheme` falls back to the first theme when nothing is stored. */
        val fallback: DopamineThemeKey = Default

        fun fromKey(value: String?): DopamineThemeKey =
            all.firstOrNull { it.key == value } ?: fallback
    }
}

/**
 * The colours that change with the theme.
 *
 * The iOS code multiplies the theme's menu colour alpha by a factor per call site
 * (`+[DOThemeManager menuColorWithAlpha:]`), so the derived values below are that one colour at
 * the alphas the call sites pass rather than new colours.
 */
data class DopaminePalette(
    val menu: Color,
    val menuRaised: Color,
    val window: Color,
    val titleShadow: Boolean,
) {
    companion object {
        fun of(theme: DopamineThemeKey): DopaminePalette = DopaminePalette(
            menu = theme.actionMenuColor,
            // The same colour with its alpha lifted, used for the switch track and pressed rows.
            menuRaised = theme.actionMenuColor.copy(alpha = (theme.actionMenuColor.alpha * 1.35f).coerceAtMost(1f)),
            window = theme.windowColor,
            titleShadow = theme.titleShadow,
        )
    }
}
