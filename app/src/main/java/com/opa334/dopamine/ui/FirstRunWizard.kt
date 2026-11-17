package com.opa334.dopamine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opa334.dopamine.R

/**
 * First-run wizard.
 *
 * Shown once, before the first jailbreak, because that is when the choices it collects are actually
 * needed: the package manager is named in the run log, and the sudo password is what a jailbroken
 * environment would prompt for. Both are stored locally and the wizard never runs again once
 * `ever_jailbroken` is set, so the second launch goes straight to the main screen.
 *
 * Steps are one per screen rather than a scrolling form: there are only two, and a single button per
 * step keeps the continue action unambiguous.
 */
@Composable
fun FirstRunWizard(
    onFinished: (packageManager: String, sudoPassword: String) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var packageManager by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DopamineColors.Background)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(72.dp))

        DopamineWordmark()

        Spacer(Modifier.height(8.dp))

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
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(28.dp))

        if (step == 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DopamineColors.Menu),
            ) {
                listOf(
                    "sileo" to "Sileo",
                    "zebra" to "Zebra",
                    "installer" to "Installer",
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
                                contentDescription = null,
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wizard_password_hint),
                color = DopamineColors.TextSecondary,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        val canContinue = step == 0 && packageManager.isNotEmpty()
        Text(
            text = if (step == 0) {
                stringResource(R.string.wizard_continue)
            } else {
                stringResource(R.string.wizard_start)
            },
            color = if (step == 0 && !canContinue) {
                DopamineColors.TextSecondary
            } else {
                DopamineColors.Background
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (step == 0 && !canContinue) 0.5f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (step == 0 && !canContinue) DopamineColors.MenuRaised else Color.White,
                )
                .clickable(enabled = step == 1 || canContinue) {
                    if (step == 0) {
                        step = 1
                    } else {
                        onFinished(packageManager, password.ifBlank { "alpine" })
                    }
                }
                .padding(vertical = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))
    }
}