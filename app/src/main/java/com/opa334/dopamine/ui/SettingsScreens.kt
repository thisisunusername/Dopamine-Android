package com.opa334.dopamine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opa334.dopamine.R
import com.opa334.dopamine.core.DeviceProfile

/** A titled screen with a back button, used by settings and about. */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        /*
         * The blurred photo first, then the theme's `windowColor` on top of it.
         *
         * On iOS the settings window is not opaque: it is `windowColor`, a translucent fill, drawn
         * over the same blurred background photo the main screen uses. Painting only the fill gave
         * a flat near-black page where every theme looked identical, because the fill's alpha had
         * nothing behind it to reveal. Both layers are needed for the theme to be visible here.
         */
        ThemeBackground()
        Box(Modifier.fillMaxSize().background(dopaminePalette.window))

        Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = DopamineColors.TextPrimary,
                )
            }
            Text(
                text = title,
                color = DopamineColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        /*
         * weight(1f), never fillMaxSize().
         *
         * Inside a Column that already fills the screen, fillMaxSize() passes an unbounded
         * maximum height down, and a scrollable child cannot measure its content against an
         * infinite constraint - Compose throws instead of laying out. weight(1f) hands the
         * remaining height to the list and leaves it free to grow, which is what a scrolling
         * page needs. This is what made both Settings and About crash on open.
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
        }
    }
}

/**
 * Settings.
 *
 * Only one switch here is functional, and it is functional for real: "detailed log" changes what the
 * run writes into each row (see JailbreakEngine), rather than being a decoration that does nothing.
 */
@Composable
fun SettingsScreen(
    detailedLog: Boolean,
    onDetailedLogChange: (Boolean) -> Unit,
    packageManager: String?,
    jailbroken: Boolean,
    hidden: Boolean,
    selectedExploit: String,
    theme: DopamineThemeKey,
    bootlogoEnabled: Boolean,
    onBootlogoEnabledChange: (Boolean) -> Unit,
    customBootlogoEnabled: Boolean,
    onCustomBootlogoEnabledChange: (Boolean) -> Unit,
    customBootlogoPath: String?,
    onPickBootlogoImage: () -> Unit,
    onToggleHidden: () -> Unit,
    onRemoveJailbreak: () -> Unit,
    onOpenExploits: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenPackageManager: () -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        /*
         * Exploits first, as on iOS. The list is the set of exploits Dopamine actually ships
         * (Application/Dopamine/Exploits) minus RootHide, which was excluded on request. These are
         * names, not buttons: nothing here selects or runs anything, and the note says so rather
         * than letting the screen imply a capability.
         */
        SectionLabel(stringResource(R.string.settings_section_exploits))
        SettingsCard {
            /*
             * One entry point, as on iOS: the settings screen shows which kernel exploit is
             * selected, and tapping it opens a list to choose from. The full list does not belong
             * on this page - nine rows of names would bury everything else.
             */
            ChoiceRow(
                title = stringResource(R.string.settings_kernel_exploit),
                value = selectedExploit,
                selected = true,
                onClick = onOpenExploits,
            )
            CardLine()
            ChoiceRow(
                title = stringResource(R.string.settings_pac_bypass),
                value = stringResource(R.string.settings_none),
                selected = false,
                onClick = {},
                enabled = false,
            )
            CardLine()
            ChoiceRow(
                title = stringResource(R.string.settings_ppl_bypass),
                value = stringResource(R.string.settings_none),
                selected = false,
                onClick = {},
                enabled = false,
            )
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_section_jailbreak))
        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.settings_detailed_log),
                subtitle = stringResource(R.string.settings_detailed_log_subtitle),
                checked = detailedLog,
                onCheckedChange = onDetailedLogChange,
            )
        }

        Spacer(Modifier.height(24.dp))

        /*
         * Hide / Unhide Jailbreak, mirroring the button on the iOS settings screen. The long grey
         * paragraph under it is the original iOS hint, kept verbatim: it is the only place that
         * explains what the option does not do, and trimming it would make the button look like a
         * stronger guarantee than it is.
         *
         * The row is always present but disabled until there is a jailbreak to hide. Hiding it
         * instead made the section look like it did not exist.
         */
        SectionLabel(stringResource(R.string.settings_section_actions))
        SettingsCard {
            ChoiceRow(
                title = stringResource(
                    if (hidden) R.string.settings_unhide_jailbreak
                    else R.string.settings_hide_jailbreak,
                ),
                selected = false,
                enabled = jailbroken,
                onClick = onToggleHidden,
            )
            CardLine()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_hide_jailbreak_hint),
                    color = DopamineColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_hide_jailbreak_note),
                    color = DopamineColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        /*
         * Package Manager and Password were two sections of switches and a text field sitting
         * inline. On iOS both live one level down - the package manager choice is its own list,
         * the sudo password is set during the wizard - and keeping them inline buried everything
         * below them under a keyboard-sized block.
         *
         * They are now one entry point that opens a sub-screen, showing the current values so the
         * row is still informative without being opened.
         */
        SectionLabel(stringResource(R.string.settings_section_package_manager))
        SettingsCard {
            ChoiceRow(
                title = stringResource(R.string.settings_section_package_manager),
                value = packageManagerLabel(packageManager),
                selected = false,
                onClick = onOpenPackageManager,
            )
        }

        /*
         * Remove Jailbreak, moved here from the main screen. Always listed so the option is
         * discoverable; disabled until there is a jailbreak to remove.
         */
        Spacer(Modifier.height(24.dp))
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = jailbroken, onClick = onRemoveJailbreak),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.settings_remove_jailbreak),
                        color = if (jailbroken) DopamineColors.Danger else DopamineColors.TextDisabled,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_remove_jailbreak_note),
                    color = DopamineColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        /*
         * Customization, at the bottom of Settings.
         *
         * DOSettingsController builds this after the Actions group: `Section_Customization` holding
         * a single `Theme` PSLinkListCell, whose detail page is the list of every theme in
         * Themes.plist. The row here shows the current theme's name and opens that list, which is
         * the same two levels as the original rather than a picker inlined into Settings.
         */
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_section_customization))
        SettingsCard {
            ChoiceRow(
                title = stringResource(R.string.settings_theme),
                value = theme.displayName,
                selected = false,
                onClick = onOpenThemes,
            )
        }

        /*
         * Boot Logo, directly under Customization.
         *
         * The original groups are `Section_Boot_Logo` with an `Enabled` switch, then
         * `Custom_Boot_Logo` and `Select_Image`. Both of the latter are only added to the list while
         * `bootlogoEnabled` is on, and `Select_Image` only while `customBootlogoEnabled` is on - so
         * the rows appear and disappear rather than sitting there disabled. That is reproduced
         * below, because the switches are nested: a custom logo with no boot logo has nothing to
         * replace.
         */
        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_section_bootlogo))
        SettingsCard {
            SwitchRow(
                title = stringResource(R.string.settings_bootlogo_enabled),
                subtitle = stringResource(R.string.settings_bootlogo_enabled_subtitle),
                checked = bootlogoEnabled,
                onCheckedChange = onBootlogoEnabledChange,
            )

            if (bootlogoEnabled) {
                CardLine()
                SwitchRow(
                    title = stringResource(R.string.settings_custom_bootlogo),
                    subtitle = stringResource(R.string.settings_custom_bootlogo_subtitle),
                    checked = customBootlogoEnabled,
                    onCheckedChange = onCustomBootlogoEnabledChange,
                )

                if (customBootlogoEnabled) {
                    CardLine()
                    /*
                     * "Select Image" on iOS is a PSButtonCell holding the picture it currently has.
                     * Here the row carries the chosen file name so the user can tell whether a
                     * picture is actually set, and tapping it opens the system picker.
                     */
                    ChoiceRow(
                        title = stringResource(R.string.settings_select_image),
                        value = customBootlogoPath?.let { java.io.File(it).name }
                            ?: stringResource(R.string.settings_select_image_none),
                        selected = false,
                        onClick = onPickBootlogoImage,
                    )
                }
            }
        }
    }
}

/**
 * The exploits that ship with Dopamine, taken from the framework bundles in
 * `Application/Dopamine/Exploits`. RootHide is intentionally not among them.
 *
 * No iOS version ranges: this app runs on Android, so "iOS 15.0 - 15.8" next to each name would be
 * information about a device the user is not holding. The first entry is the one Dopamine picks by
 * default (highest priority that is supported), and the UI marks it as recommended.
 */
private val KernelExploits = listOf(
    "ClearSword",
    "DarkSword",
    "Titan",
    "badRecovery",
    "dmaFail",
    "kfd",
    "momentarius",
    "multicast_bytecopy",
    "weightBufs",
)

/**
 * The same list, public, because both the picker and the settings row that names the current
 * choice have to agree on what a valid selection is - and the default selection is the first
 * entry, which is the recommended one.
 */
val Exploits: List<String> = KernelExploits

/**
 * The kernel-exploit picker, opened from Settings.
 *
 * On iOS this is `DOPSExploitListItemsController`, a plain list where the current selection is
 * ticked and the default carries a "(Recommended)" suffix. Same idea here.
 */
@Composable
fun ExploitPickerScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = stringResource(R.string.settings_kernel_exploit), onBack = onBack) {
        SettingsCard {
            KernelExploits.forEachIndexed { index, name ->
                if (index > 0) CardLine()
                ChoiceRow(
                    title = if (index == 0) {
                        "$name (${stringResource(R.string.settings_recommended)})"
                    } else {
                        name
                    },
                    selected = name == selected,
                    onClick = { onSelect(name) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = stringResource(R.string.settings_exploit_note),
                color = DopamineColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
/**
 * The theme list, opened from Settings.
 *
 * The equivalent of `DOPSListItemsController` with `themeIdentifiers` / `themeNames` as its data
 * sources: every theme in `Themes.plist`, ticked where it is the current one. Each row also shows
 * the theme's own app icon, because the icon is part of what the theme changes and picking
 * "Desert Dusk" without seeing its icon would leave the effect of the choice invisible until the
 * launcher caught up.
 */
@Composable
fun ThemePickerScreen(
    selected: DopamineThemeKey,
    onSelect: (DopamineThemeKey) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = stringResource(R.string.settings_theme), onBack = onBack) {
        SettingsCard {
            DopamineThemeKey.all.forEachIndexed { index, item ->
                if (index > 0) CardLine()
                ThemeRow(
                    theme = item,
                    selected = item == selected,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

/** One theme row: its app icon, its name, and a tick when it is the current theme. */
@Composable
private fun ThemeRow(
    theme: DopamineThemeKey,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            /*
             * The theme's app icon, not a colour swatch: these are the artwork files the theme
             * switches the launcher icon to, so the picture here is exactly what the home screen
             * will show. They are PNGs, so painterResource is safe (an adaptive-icon XML would not
             * be).
             */
            Image(
                painter = painterResource(theme.icon),
                contentDescription = stringResource(R.string.cd_theme_preview),
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = theme.displayName,
                color = DopamineColors.TextPrimary,
                fontSize = 15.sp,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = DopamineColors.Success,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * About.
 *
 * This screen carries the honesty the rest of the app deliberately does not spell out on every
 * screen: nothing here gains privileges. Saying so in one obvious place is better than repeating it
 * in the log, and it keeps the main screen looking like the thing it is imitating.
 */
@Composable
fun AboutScreen(
    device: DeviceProfile,
    onBack: () -> Unit,
) {
    DetailScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            /*
             * The wordmark again, not the launcher icon. This row sits directly above the
             * "Dopamine" title text, and on iOS the About screen shows the same header artwork,
             * so a square icon here would read as two different products stacked.
             */
            Image(
                painter = painterResource(R.drawable.dopamine_wordmark),
                contentDescription = "Dopamine",
                modifier = Modifier.height(34.dp),
                contentScale = ContentScale.FillHeight,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = "Dopamine",
                    color = DopamineColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.about_subtitle),
                    color = DopamineColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.about_section_device))
        SettingsCard {
            val unknown = stringResource(R.string.about_unknown)
            InfoRow(
                stringResource(R.string.about_model),
                "${device.manufacturer} ${device.model}",
            )
            CardLine()
            InfoRow(
                stringResource(R.string.about_system),
                "Android ${device.androidRelease} (API ${device.sdk})",
            )
            CardLine()
            InfoRow(stringResource(R.string.about_kernel), device.kernel.ifEmpty { unknown })
            CardLine()
            InfoRow(stringResource(R.string.about_arch), device.abi.ifEmpty { unknown })
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.about_section_note))
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = DopamineColors.Warning,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.about_note),
                        color = DopamineColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.about_section_credits))
        SettingsCard {
            InfoRow(
                stringResource(R.string.about_original_authors),
                "opa334, évelyne (ElleKit)",
            )
            CardLine()
            InfoRow(stringResource(R.string.about_ui_reference), "Dopamine (iOS)")
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        color = DopamineColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            // `actionMenuColor` from the active theme, at full strength: the cards follow the theme
            // rather than staying grey while the background changes underneath them.
            .background(dopaminePalette.menu),
    ) {
        content()
    }
}

@Composable
internal fun CardLine() {
    HorizontalDivider(
        color = DopamineColors.Separator,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = DopamineColors.TextPrimary,
                fontSize = 15.sp,
            )
            Text(
                text = subtitle,
                color = DopamineColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DopamineColors.TextPrimary,
                checkedTrackColor = DopamineColors.Success,
                uncheckedThumbColor = DopamineColors.TextSecondary,
                uncheckedTrackColor = dopaminePalette.menuRaised,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    value: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = if (enabled) DopamineColors.TextPrimary else DopamineColors.TextDisabled,
            fontSize = 15.sp,
        )
        // A read-only row (value != null) shows its current setting instead of a tick.
        if (value != null) {
            Text(
                text = value,
                color = DopamineColors.TextSecondary,
                fontSize = 14.sp,
            )
        } else if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = DopamineColors.Success,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = DopamineColors.TextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            color = DopamineColors.TextPrimary,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}