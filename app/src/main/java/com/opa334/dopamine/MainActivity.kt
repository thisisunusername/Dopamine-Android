package com.opa334.dopamine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.opa334.dopamine.core.DeviceProfile
import com.opa334.dopamine.core.JailbreakEngine
import com.opa334.dopamine.core.JailbreakState
import com.opa334.dopamine.core.StepEvent
import com.opa334.dopamine.ui.AboutScreen
import com.opa334.dopamine.ui.BlackoutSequence
import com.opa334.dopamine.ui.DopamineColors
import com.opa334.dopamine.ui.DopamineTheme
import com.opa334.dopamine.ui.FirstRunWizard
import com.opa334.dopamine.ui.LogRow
import com.opa334.dopamine.ui.MainScreen
import com.opa334.dopamine.ui.MainUiState
import com.opa334.dopamine.ui.SettingsScreen
import com.opa334.dopamine.ui.UserspaceBlink
import com.opa334.dopamine.ui.applyEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single activity.
 *
 * Everything is one activity and a small enum of screens rather than a navigation library: there are
 * four destinations, none of them needs a back stack of its own, and the blackout sequence has to be
 * able to finish the activity outright.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DopamineTheme {
                DopamineApp(onFinish = { finish() })
            }
        }
    }
}

private enum class Screen { Wizard, Main, Settings, About }

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DopamineApp(onFinish: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Screens are derived from persisted state on every recomposition, so leaving the app and coming
    // back cannot show a stale answer — the bug this app is a rebuild of.
    var everJailbroken by remember { mutableStateOf(JailbreakState.hasEverJailbroken(context)) }
    var jailbroken by remember { mutableStateOf(JailbreakState.isJailbroken(context)) }
    var detailedLog by remember { mutableStateOf(JailbreakState.detailedLog(context)) }
    var packageManager by remember { mutableStateOf(JailbreakState.packageManager(context)) }

    var screen by remember {
        mutableStateOf(if (everJailbroken) Screen.Main else Screen.Wizard)
    }
    var overlay by remember { mutableStateOf(Overlay.None) }

    var ui by remember { mutableStateOf(MainUiState()) }

    // Resolved once per composition; both call sites below need the same two strings.
    val titleJailbroken = stringResource(R.string.action_jailbroken)
    val titleJailbreak = stringResource(R.string.action_jailbreak)

    // Re-read the daemon on every (re)entry; the activity may have been backgrounded for a long time.
    LaunchedEffect(Unit) {
        jailbroken = JailbreakState.isJailbroken(context)
        everJailbroken = JailbreakState.hasEverJailbroken(context)
        packageManager = JailbreakState.packageManager(context)
        ui = ui.copy(
            jailbroken = jailbroken,
            title = if (jailbroken) titleJailbroken else titleJailbreak,
            packageManager = packageManager,
            deviceLine = DeviceProfile.read().let { "${it.manufacturer} ${it.model} · Android ${it.androidRelease}" },
        )
        if (!everJailbroken) screen = Screen.Wizard
    }

    /** Recomputes the jailbreak flag from the daemon and pushes it into the UI state. */
    fun refreshState() {
        jailbroken = JailbreakState.isJailbroken(context)
        everJailbroken = JailbreakState.hasEverJailbroken(context)
        packageManager = JailbreakState.packageManager(context)
        ui = ui.copy(
            jailbroken = jailbroken,
            title = if (jailbroken) titleJailbroken else titleJailbreak,
            packageManager = packageManager,
            busy = false,
        )
    }

    Box(Modifier.fillMaxSize().background(DopamineColors.Background)) {

        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Wizard -> FirstRunWizard { pm, password ->
                    JailbreakState.setPackageManager(context, pm)
                    JailbreakState.setSudoPassword(context, password)
                    packageManager = pm
                    screen = Screen.Main
                    ui = ui.copy(packageManager = pm)
                }

                Screen.Main -> MainScreen(
                    state = ui.copy(
                        jailbroken = jailbroken,
                        packageManager = packageManager,
                    ),
                    onJailbreak = {
                        if (jailbroken) {
                            // Already rooted: the card becomes a userspace restart, which is what
                            // Dopamine does rather than re-running the exploit.
                            scope.launch {
                                // The overlay drives its own timing and clears itself when the blink
                                // finishes; adding a delay here would hold a second black screen.
                                overlay = Overlay.Userspace
                                JailbreakEngine.restartUserspace(context)
                            }
                        } else {
                            scope.launch {
                                ui = ui.copy(
                                    busy = true,
                                    expanded = true,
                                    progress = 0f,
                                    title = stringResource(R.string.action_jailbreaking),
                                    rows = JailbreakEngine
                                        .steps(context, detailedLog)
                                        .map { LogRow(it.label) },
                                )
                                val total = ui.rows.size
                                JailbreakEngine.run(
                                    context = context,
                                    detailed = detailedLog,
                                    onEvent = { event ->
                                        ui = ui.applyEvent(event, total)
                                        if (event is StepEvent.Done) {
                                            if (event.ok) {
                                                overlay = Overlay.Blackout
                                            } else {
                                                refreshState()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    },
                    onRestartSpringBoard = {
                        /*
                         * Eight seconds of black, then the app closes itself.
                         *
                         * An app cannot restart the launcher, so the closest honest behaviour is to
                         * disappear the way a respring makes everything disappear: the screen goes
                         * black and the app is gone. It is not reopened automatically — the user
                         * taps the icon, the daemon is still alive, and the app comes back on
                         * "Jailbroken", which is the observable result a respring would have.
                         */
                        scope.launch {
                            overlay = Overlay.SpringBoard
                            delay(8_000L)
                            onFinish()
                        }
                    },
                    onRestartUserspace = {
                        scope.launch {
                            overlay = Overlay.Userspace
                            JailbreakEngine.restartUserspace(context)
                        }
                    },
                    onSettings = { screen = Screen.Settings },
                    onAbout = { screen = Screen.About },
                    onRemoveJailbreak = {
                        JailbreakEngine.removeJailbreak(context)
                        refreshState()
                    },
                )

                Screen.Settings -> SettingsScreen(
                    detailedLog = detailedLog,
                    onDetailedLogChange = {
                        detailedLog = it
                        JailbreakState.setDetailedLog(context, it)
                    },
                    packageManager = packageManager,
                    onPackageManagerChange = {
                        packageManager = it
                        JailbreakState.setPackageManager(context, it)
                    },
                    sudoPassword = JailbreakState.sudoPassword(context),
                    onSudoPasswordChange = { JailbreakState.setSudoPassword(context, it) },
                    onBack = {
                        screen = Screen.Main
                        refreshState()
                    },
                )

                Screen.About -> AboutScreen(
                    device = DeviceProfile.read(),
                    onBack = { screen = Screen.Main },
                )
            }
        }

        // ---- Overlays: they cover the whole window, including the system bars, because the point of
        // the blackout is that the screen appears to switch off.
        when (overlay) {
            Overlay.None -> Unit

            Overlay.Blackout -> BlackoutSequence {
                // The daemon is already up; mark the run and kill the activity so the next launch
                // starts on "Jailbroken" exactly as it would on iOS.
                JailbreakState.markEverJailbroken(context)
                onFinish()
            }

            Overlay.Userspace -> UserspaceBlink {
                overlay = Overlay.None
                refreshState()
            }

            Overlay.SpringBoard -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }
}

/** Full-screen covers. */
private enum class Overlay { None, Blackout, Userspace, SpringBoard }