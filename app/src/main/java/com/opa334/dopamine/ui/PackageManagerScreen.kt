package com.opa334.dopamine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opa334.dopamine.R

/**
 * One package manager this build offers, with the artwork the original ships for it.
 *
 * The pictures are the iOS assets verbatim - `Package Managers/Sileo.imageset` and
 * `Package Managers/Zebra.imageset` from Dopamine's own catalog - rather than drawn stand-ins:
 * they are the exact 83.5@2x PNGs the iOS settings row shows.
 */
enum class PackageManagerOption(
    val key: String,
    val label: String,
    val icon: Int,
) {
    Sileo("sileo", "Sileo", R.drawable.pm_sileo),
    Zebra("zebra", "Zebra", R.drawable.pm_zebra),
    ;

    companion object {
        fun fromKey(key: String?): PackageManagerOption? = entries.firstOrNull { it.key == key }
    }
}

/** The label shown for a stored package-manager key, or a dash when nothing is set yet. */
@Composable
internal fun packageManagerLabel(key: String?): String =
    PackageManagerOption.fromKey(key)?.label ?: stringResource(R.string.settings_none)

/**
 * Package Manager and sudo password, as their own screen.
 *
 * On iOS the package manager is a choice made in the wizard and changeable afterwards, and the sudo
 * password is a preference; neither belongs inline on the settings page, where a text field pushed
 * everything below it off the screen and a list of names made an unrelated section look like the
 * most important thing on it.
 *
 * The row that opens this screen shows the current package manager, so the information is still
 * visible without opening anything.
 */
@Composable
fun PackageManagerScreen(
    packageManager: String?,
    onPackageManagerChange: (String) -> Unit,
    sudoPassword: String?,
    onSudoPasswordChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(
        title = stringResource(R.string.settings_section_package_manager),
        onBack = onBack,
    ) {
        SectionLabel(stringResource(R.string.settings_section_package_manager))
        SettingsCard {
            PackageManagerOption.entries.forEachIndexed { index, option ->
                if (index > 0) CardLine()
                PackageManagerRow(
                    option = option,
                    selected = packageManager == option.key,
                    onClick = { onPackageManagerChange(option.key) },
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
    }
}

/**
 * One package manager: its icon, its name, and a tick when it is the current choice.
 *
 * This is the row the setting was missing - the list used to be plain text, so two entries for two
 * very different apps looked interchangeable.
 */
@Composable
private fun PackageManagerRow(
    option: PackageManagerOption,
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
             * The asset is a full-colour app icon, drawn as-is inside a rounded box with a crop:
             * squaring it off keeps the row heights uniform without touching the artwork.
             */
            Image(
                painter = painterResource(option.icon),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = option.label,
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