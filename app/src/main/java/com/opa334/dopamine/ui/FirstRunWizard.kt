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
import androidx.compose.ui.draw.alpha
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

/**
 * The sheet shown before the first jailbreak — not at launch.
 *
 * Dopamine asks for these only when they are needed: the package manager is named in the run log
 * and the sudo password is what the resulting environment would prompt for. Showing the wizard on
 * a cold start made the app open onto a form the user could not yet act on, so it is now opened by
 * the jailbreak button, after that tap and before the run begins.
 *
 * The password is entered twice. A single field for a credential that cannot be read back is a
 * trap: one typo and the environment is set up with a password nobody knows. The confirm field
 * disables the button until the two match, and says why rather than silently refusing.
 */
@Composable
fun FirstRunWizard(
    onCancel: () -> Unit,
    onFinished: (packageManager: String, sudoPassword: String) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var packageManager by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val passwordsMatch = password == confirm
    val canContinue = step == 0 && packageManager.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dopaminePalette.window)
            .clickable(enabled = false, onClick = {}),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(64.dp))

            // The wordmark, not R.mipmap.ic_launcher.
            //
            // ic_launcher is an <adaptive-icon> XML on API 26+ (mipmap-anydpi-v26), and
            // painterResource() only understands VectorDrawables and real bitmaps - it throws
            // "Only VectorDrawables and rasterized asset types are supported" for anything else.
            // That exception fired the moment this sheet opened, which is what made tapping
            // "Jailbreak" crash on a first run. drawable-nodpi/dopamine_wordmark.png is a plain
            // PNG, so it is safe here.
            Image(
                painter = painterResource(R.drawable.dopamine_wordmark),
                contentDescription = "Dopamine",
                modifier = Modifier.height(34.dp),
                contentScale = ContentScale.FillHeight,
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = if (step == 0) {
                    stringResource(R.string.wizard_choose_package_manager)
                } else {
                    stringResource(R.string.wizard_set_sudo)
                },
                color = DopamineColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (step == 0) {
                    stringResource(R.string.wizard_choose_desc)
                } else {
                    stringResource(R.string.wizard_sudo_desc)
                },
                color = DopamineColors.TextSecondary,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(28.dp))

            if (step == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(dopaminePalette.menu),
                ) {
                    listOf(
                        "sileo" to "Sileo",
                        "zebra" to "Zebra",
                    ).forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { packageManager = key }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                color = DopamineColors.TextPrimary,
                                fontSize = 16.sp,
                            )
                            if (packageManager == key) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.cd_selected),
                                    tint = DopamineColors.Success,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.wizard_confirm_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirm.isNotEmpty() && !passwordsMatch,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (confirm.isNotEmpty() && !passwordsMatch) {
                        stringResource(R.string.wizard_password_mismatch)
                    } else {
                        stringResource(R.string.wizard_password_hint)
                    },
                    color = if (confirm.isNotEmpty() && !passwordsMatch) {
                        DopamineColors.Danger
                    } else {
                        DopamineColors.TextSecondary
                    },
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            // Primary action.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .alpha(if (if (step == 0) canContinue else passwordsMatch) 1f else 0.5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(dopaminePalette.menu)
                    .clickable(enabled = if (step == 0) canContinue else passwordsMatch) {
                        if (step == 0) {
                            step = 1
                        } else {
                            onFinished(packageManager, password.ifBlank { "alpine" })
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (step == 0) {
                        stringResource(R.string.wizard_continue)
                    } else {
                        stringResource(R.string.wizard_start)
                    },
                    color = DopamineColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.wizard_cancel),
                    color = DopamineColors.TextSecondary,
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}