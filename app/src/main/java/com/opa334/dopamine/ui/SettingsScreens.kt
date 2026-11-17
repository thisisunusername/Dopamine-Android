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
import androidx.compose.material.icons.filled.ArrowBack
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The iOS settings window is `windowColor` (#4C4C4C at 60%) drawn over the same
            // blurred photo as the main screen, so the photo is painted behind it here.
            .background(DopamineColors.Window),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
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
    onPackageManagerChange: (String) -> Unit,
    sudoPassword: String?,
    onSudoPasswordChange: (String) -> Unit,
    jailbroken: Boolean,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onRemoveJailbreak: () -> Unit,
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
             * The same three boxes iOS shows: the kernel exploit in use, plus the PAC and PPL
             * bypasses where the target needs them. The picked value is the first entry of each
             * list, which is how Dopamine chooses a default (highest priority that is supported),
             * and "None" is used where no bypass applies.
             */
            ChoiceRow(
                title = stringResource(R.string.settings_kernel_exploit),
                value = KernelExploit.entries.first().displayName,
                selected = true,
                onClick = {},
            )
            CardLine()
            ChoiceRow(
                title = stringResource(R.string.settings_pac_bypass),
                value = stringResource(R.string.settings_none),
                selected = false,
                onClick = {},
            )
            CardLine()
            ChoiceRow(
                title = stringResource(R.string.settings_ppl_bypass),
                value = stringResource(R.string.settings_none),
                selected = false,
                onClick = {},
            )
            CardLine()
            KernelExploit.entries.forEachIndexed { index, exploit ->
                if (index > 0) CardLine()
                InfoRow(exploit.displayName, exploit.platforms)
            }
            CardLine()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_exploit_note),
                    color = DopamineColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
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
         */
        if (jailbroken) {
            SectionLabel(stringResource(R.string.settings_section_actions))
            SettingsCard {
                ChoiceRow(
                    title = stringResource(
                        if (hidden) R.string.settings_unhide_jailbreak
                        else R.string.settings_hide_jailbreak,
                    ),
                    selected = false,
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
        }

        SectionLabel(stringResource(R.string.settings_section_package_manager))
        SettingsCard {
            // Installer is deliberately absent: only the two package managers Dopamine offers for
            // a normal bootstrap are listed, so the choice matches what the rest of the app claims.
            listOf(
                "sileo" to "Sileo",
                "zebra" to "Zebra",
            ).forEachIndexed { index, (key, label) ->
                if (index > 0) {
                    HorizontalDivider(
                        color = DopamineColors.Separator,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                ChoiceRow(
                    title = label,
                    selected = packageManager == key,
                    onClick = { onPackageManagerChange(key) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel(stringResource(R.string.settings_section_sudo))
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                var draft by remember(sudoPassword) { mutableStateOf(sudoPassword.orEmpty()) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        onSudoPasswordChange(it)
                    },
                    label = { Text(stringResource(R.string.settings_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_sudo_note),
                    color = DopamineColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        /*
         * Remove Jailbreak moved here from the main screen, and it is only offered while there is
         * something to remove.
         */
        if (jailbroken) {
            Spacer(Modifier.height(24.dp))
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onRemoveJailbreak),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_remove_jailbreak),
                            color = DopamineColors.Danger,
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
        }
    }
}

/**
 * The exploits that ship with Dopamine, taken from the framework bundles in
 * `Application/Dopamine/Exploits`. RootHide is intentionally not among them.
 *
 * The platform column is what each exploit targets on iOS; it is shown because the iOS screen shows
 * the same kind of information, not because it applies to the device this runs on.
 */
private enum class KernelExploit(val displayName: String, val platforms: String) {
    ClearSword("ClearSword", "iOS 15.0 - 15.8"),
    DarkSword("DarkSword", "iOS 16.0 - 16.6"),
    Titan("Titan", "iOS 15.0 - 15.8"),
    BadRecovery("badRecovery", "iOS 15.0 - 15.8"),
    DmaFail("dmaFail", "iOS 15.0 - 15.4"),
    Kfd("kfd", "iOS 16.0 - 16.6"),
    Momentarius("momentarius", "iOS 16.0 - 16.6"),
    MulticastBytecopy("multicast_bytecopy", "iOS 14.0 - 14.8"),
    WeightBufs("weightBufs", "iOS 15.0 - 15.8"),
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
             * The real app icon, not the drawn mark. About is where the app states what it is, so
             * it shows the same artwork the launcher does - and that artwork is a PNG here rather
             * than a vector, because the vector icon rendered as a white block.
             */
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(16.dp))
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = DopamineColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DopamineColors.Menu),
    ) {
        content()
    }
}

@Composable
private fun CardLine() {
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
                uncheckedTrackColor = DopamineColors.MenuRaised,
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = DopamineColors.TextPrimary,
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