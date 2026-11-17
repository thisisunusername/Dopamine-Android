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
import com.opa334.dopamine.ui.ThemeBackground
import com.opa334.dopamine.ui.UserspaceBlink
import com.opa334.dopamine.ui.applyEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single activity.
 *
 * Everything is one activity and a small set of enums rather than a navigation library: there are
 * four destinations, none of them needs a back stack of its own, and the blackout sequence has to
 * be able to finish the activity outright.
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

private enum class Screen { Main, Settings, About }

/** Full-screen things drawn on top of the current screen. */
private enum class Overlay { None, Wizard, Blackout, Userspace, SpringBoard }

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DopamineApp(onFinish: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Screens are derived from persisted state on every recomposition, so leaving the app and
    // coming back cannot show a stale answer — the bug this app is a rebuild of.
    var jailbroken by remember { mutableStateOf(JailbreakState.isJailbroken(context)) }
    var detailedLog by remember { mutableStateOf(JailbreakState.detailedLog(context)) }
    var packageManager by remember { mutableStateOf(JailbreakState.packageManager(context)) }

    var screen by remember { mutableStateOf(Screen.Main) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var ui by remember { mutableStateOf(MainUiState()) }

    // Resolved during composition: the run is started from a coroutine, where stringResource()
    // is not allowed.
    val titleJailbroken = stringResource(R.string.action_jailbroken)
    val titleJailbreak = stringResource(R.string.action_jailbreak)
    val titleJailbreaking = stringResource(R.string.action_jailbreaking)

    /** Re-reads the daemon and pushes the result into the UI state. */
    fun refreshState() {
        jailbroken = JailbreakState.isJailbroken(context)
        packageManager = JailbreakState.packageManager(context)
        ui = ui.copy(
            jailbroken = jailbroken,
            title = if (jailbroken) titleJailbroken else titleJailbreak,
            packageManager = packageManager,
            busy = false,
        )
    }

    /** Starts the staged run. Called after the wizard (if any) has collected its answers. */
    fun startRun() {
        scope.launch {
            ui = ui.copy(
                busy = true,
                expanded = true,
                progress = 0f,
                title = titleJailbreaking,
                rows = JailbreakEngine.steps(context, detailedLog).map { LogRow(it.label) },
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
                            overlay = Overlay.None
                            refreshState()
                        }
                    }
                },
            )
        }
    }

    // Re-read the daemon on every (re)entry; the activity may have been backgrounded for a long time.
    LaunchedEffect(Unit) {
        jailbroken = JailbreakState.isJailbroken(context)
        packageManager = JailbreakState.packageManager(context)
        ui = ui.copy(
            jailbroken = jailbroken,
            title = if (jailbroken) titleJailbroken else titleJailbreak,
            packageManager = packageManager,
            deviceLine = DeviceProfile.read().let {
                "${it.manufacturer} ${it.model} · Android ${it.androidRelease} (API ${it.sdk})"
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Main -> MainScreen(
                    state = ui.copy(jailbroken = jailbroken, packageManager = packageManager),
                    onJailbreak = {
                        if (jailbroken) {
                            // Already jailbroken: the card means "restart userspace", which is
                            // what Dopamine does rather than re-running the exploit.
                            scope.launch {
                                overlay = Overlay.Userspace
                                JailbreakEngine.restartUserspace(context)
                            }
                        } else if (!JailbreakState.hasEverJailbroken(context) &&
                            JailbreakState.packageManager(context) == null
                        ) {
                            // First run: ask for the package manager and sudo password now, not
                            // at launch, and only then start.
                            overlay = Overlay.Wizard
                        } else {
                            startRun()
                        }
                    },
                    onRestartSpringBoard = {
                        /*
                         * Eight seconds of black, then the app closes itself.
                         *
                         * An app cannot restart the launcher, so the closest honest behaviour is
                         * to disappear the way a respring makes everything disappear: the screen
                         * goes black and the app is gone. It is not reopened automatically — the
                         * user taps the icon, the daemon is still alive, and the app comes back
                         * on "Jailbroken", which is the observable result a respring would have.
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
                    jailbroken = jailbroken,
                    hidden = JailbreakState.isHidden(context),
                    onToggleHidden = {
                        /*
                         * Hide and unhide change the daemon, not just a flag.
                         *
                         * Hiding stops it: "hidden" has to mean the process is gone, otherwise the
                         * device is still jailbroken while the UI says it is not. Unhiding starts a
                         * new daemon through the same call the real run uses, and only reports
                         * success if that process is actually alive afterwards.
                         */
                        scope.launch {
                            if (JailbreakState.isHidden(context)) {
                                val ok = JailbreakEngine.restartUserspace(context)
                                JailbreakState.setHidden(context, !ok)
                                refreshState()
                            } else {
                                JailbreakEngine.removeJailbreak(context)
                                // removeJailbreak clears the "ever jailed" flag; hiding is not the
                                // same as removing, so the history is kept.
                                JailbreakState.markEverJailbroken(context)
                                JailbreakState.setHidden(context, true)
                                refreshState()
                            }
                        }
                    },
                    onRemoveJailbreak = {
                        scope.launch {
                            JailbreakEngine.removeJailbreak(context)
                            JailbreakState.setHidden(context, false)
                            refreshState()
                            // Removing the jailbreak takes everything with it, so the settings
                            // screen has nothing left to act on.
                            screen = Screen.Main
                        }
                    },
                    onBack = { screen = Screen.Main },
                )

                Screen.About -> AboutScreen(
                    device = DeviceProfile.read(),
                    onBack = { screen = Screen.Main },
                )
            }
        }

        // ---- Overlays ----

        if (overlay == Overlay.Wizard) {
            // The wizard draws over the main screen, so cancelling returns to a screen that
            // already looks right instead of a blank transition.
            Box(Modifier.fillMaxSize().background(Color(0xCC000000))) {
                ThemeBackground()
                Box(Modifier.fillMaxSize().background(Color(0x99000000)))
                FirstRunWizard(
                    onCancel = { overlay = Overlay.None },
                    onFinished = { pm, password ->
                        JailbreakState.setPackageManager(context, pm)
                        JailbreakState.setSudoPassword(context, password)
                        packageManager = pm
                        overlay = Overlay.None
                        startRun()
                    },
                )
            }
        }

        if (overlay == Overlay.Blackout) {
            BlackoutSequence(onFinished = { onFinish() })
        }

        if (overlay == Overlay.Userspace) {
            UserspaceBlink(
                onFinished = {
                    overlay = Overlay.None
                    refreshState()
                },
            )
        }

        if (overlay == Overlay.SpringBoard) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}