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
import com.opa334.dopamine.ui.BootSequence
import com.opa334.dopamine.ui.BootlogoStore
import com.opa334.dopamine.ui.DopamineColors
import com.opa334.dopamine.ui.DopamineTheme
import com.opa334.dopamine.ui.DopamineThemeKey
import com.opa334.dopamine.ui.ExploitPickerScreen
import com.opa334.dopamine.ui.Exploits
import com.opa334.dopamine.ui.FirstRunWizard
import com.opa334.dopamine.ui.LogRow
import com.opa334.dopamine.ui.MainScreen
import com.opa334.dopamine.ui.MainUiState
import com.opa334.dopamine.ui.SettingsScreen
import com.opa334.dopamine.ui.ThemeBackground
import com.opa334.dopamine.ui.ThemeIcon
import com.opa334.dopamine.ui.ThemePickerScreen
import com.opa334.dopamine.ui.UserspaceBlink
import com.opa334.dopamine.ui.applyEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single activity.
 *
 * Everything is one activity and a small set of enums rather than a navigation library: there are
 * five destinations, none of them needs a back stack of its own.
 *
 * Nothing here ever finishes the activity on its own. An earlier revision did - the blackout
 * sequence called finish() to imitate a respring - and the result was that tapping "Jailbreak"
 * made the app vanish, which is indistinguishable from a crash.
 */
class MainActivity : ComponentActivity() {

    /*
     * The image picker result.
     *
     * ActivityResult contracts need a launcher registered before the activity resumes, and the
     * callback has to reach the composable that knows where to copy the picture. A single mutable
     * holder owned by the activity is the simplest way to bridge the two without pulling in a
     * ViewModel: the composable sets [pendingBootlogoCallback] before launching, and the callback
     * consumes it.
     */
    private var pendingBootlogoCallback: ((android.net.Uri) -> Unit)? = null

    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val callback = pendingBootlogoCallback
        pendingBootlogoCallback = null
        if (uri != null && callback != null) callback(uri)
    }

    /** Opens the system photo picker; [onPicked] receives a readable URI. */
    fun pickBootlogoImage(onPicked: (android.net.Uri) -> Unit) {
        pendingBootlogoCallback = onPicked
        runCatching {
            pickImage.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                ),
            )
        }.onFailure {
            pendingBootlogoCallback = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Re-apply the stored theme's launcher icon. The package manager keeps whichever alias was
        // last enabled, so this is belt-and-braces against a restore or a reinstall; it is cheap
        // when nothing changed.
        ThemeIcon.applyStored(this)

        setContent {
            DopamineTheme(theme = appTheme) {
                DopamineApp()
            }
        }
    }

    /**
     * The theme currently in effect, held here so [DopamineTheme] can wrap the whole app.
     *
     * It is a plain mutableState rather than being read inside the composable so that a change from
     * Settings recomposes the theme wrapper itself - which is what re-paints every screen, the
     * background photo and the cards together.
     */
    private var appTheme by mutableStateOf(DopamineThemeKey.fallback)

    /** Read by [DopamineApp] so a change in Settings reaches the theme wrapper above it. */
    internal fun currentTheme(): DopamineThemeKey = appTheme

    /** Applies a theme: repaints the app and switches the launcher icon. */
    internal fun applyTheme(theme: DopamineThemeKey) {
        appTheme = theme
        JailbreakState.setTheme(this, theme.key)
        ThemeIcon.apply(this, theme)
    }

    /** Re-reads the persisted theme, used once during composition. */
    internal fun restoreTheme() {
        appTheme = DopamineThemeKey.fromKey(JailbreakState.theme(this))
    }
}

private enum class Screen { Main, Settings, Exploits, Themes, About }

/** Full-screen things drawn on top of the current screen. */
private enum class Overlay { None, Wizard, Blackout, Userspace, SpringBoard }

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DopamineApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()

    // Screens are derived from persisted state on every recomposition, so leaving the app and
    // coming back cannot show a stale answer — the bug this app is a rebuild of.
    var jailbroken by remember { mutableStateOf(JailbreakState.isJailbroken(context)) }
    var detailedLog by remember { mutableStateOf(JailbreakState.detailedLog(context)) }
    var packageManager by remember { mutableStateOf(JailbreakState.packageManager(context)) }

    var screen by remember { mutableStateOf(Screen.Main) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var ui by remember { mutableStateOf(MainUiState()) }

    /*
     * Theme and boot-logo preferences.
     *
     * These are held here as well as in SharedPreferences because the UI has to react to a change
     * immediately; the stored copy is what survives a restart. The theme is additionally pushed up
     * to the activity (see [MainActivity.applyTheme]) because DopamineTheme wraps DopamineApp -
     * repainting everything means recomposing from above, not below.
     */
    var theme by remember { mutableStateOf(DopamineThemeKey.fromKey(JailbreakState.theme(context))) }
    var bootlogoEnabled by remember { mutableStateOf(JailbreakState.bootlogoEnabled(context)) }
    var customBootlogoEnabled by remember { mutableStateOf(JailbreakState.customBootlogoEnabled(context)) }
    var customBootlogoPath by remember { mutableStateOf(JailbreakState.customBootlogoPath(context)) }

    // The selected exploit is a real preference: it survives leaving the app and is the name the
    // settings screen shows on its one-line entry point. Default is the first (recommended) one.
    var selectedExploit by remember {
        mutableStateOf(
            JailbreakState.selectedExploit(context) ?: Exploits.first(),
        )
    }

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
                rows = JailbreakEngine.steps(context, detailedLog, selectedExploit).map { LogRow(it.label) },
            )
            val total = ui.rows.size
            JailbreakEngine.run(
                context = context,
                detailed = detailedLog,
                // The exploit named in the log comes from this value; passing it explicitly keeps
                // the log and the Settings selection from ever disagreeing.
                exploit = selectedExploit,
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
                         * Same mistake as the blackout sequence had: this used to wait eight
                         * seconds and then close the activity.
                         *
                         * "Restart SpringBoard" cannot be honoured on Android - there is no
                         * SpringBoard, and an app cannot restart the launcher. Pretending to
                         * disappear was worse than doing nothing, because the app closing itself
                         * is indistinguishable from a crash. The black screen is kept (it is the
                         * part that reads as a respring) and the app comes back on its own.
                         */
                        scope.launch {
                            overlay = Overlay.SpringBoard
                            delay(3_000L)
                            overlay = Overlay.None
                            refreshState()
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
                    selectedExploit = selectedExploit,
                    theme = theme,
                    onOpenThemes = { screen = Screen.Themes },
                    bootlogoEnabled = bootlogoEnabled,
                    onBootlogoEnabledChange = {
                        bootlogoEnabled = it
                        JailbreakState.setBootlogoEnabled(context, it)
                    },
                    customBootlogoEnabled = customBootlogoEnabled,
                    onCustomBootlogoEnabledChange = { enabled ->
                        customBootlogoEnabled = enabled
                        JailbreakState.setCustomBootlogoEnabled(context, enabled)
                        // The stored copy is deleted when the switch goes off, so the row must
                        // stop naming a file that no longer exists.
                        customBootlogoPath = JailbreakState.customBootlogoPath(context)
                    },
                    customBootlogoPath = customBootlogoPath,
                    onPickBootlogoImage = {
                        activity?.pickBootlogoImage { uri ->
                            scope.launch {
                                val saved = BootlogoStore.copyInto(context, uri)
                                if (saved != null) {
                                    customBootlogoPath = saved
                                }
                            }
                        }
                    },
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
                    onOpenExploits = { screen = Screen.Exploits },
                )

                Screen.Exploits -> ExploitPickerScreen(
                    selected = selectedExploit,
                    onSelect = {
                        selectedExploit = it
                        JailbreakState.setSelectedExploit(context, it)
                    },
                    onBack = { screen = Screen.Settings },
                )

                Screen.Themes -> ThemePickerScreen(
                    selected = theme,
                    onSelect = { picked ->
                        theme = picked
                        // Pushed up to the activity as well: the theme wrapper sits above this
                        // composable, so only it can repaint the background and cards.
                        activity?.applyTheme(picked)
                        // The settings list shows the current name, so the choice is visible
                        // without backing out; the picker stays open to try another.
                    },
                    onBack = { screen = Screen.Settings },
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
            /*
             * The blink is the "respring" moment, but the app must survive it.
             *
             * This used to call onFinish() and close the activity, copying what iOS does - where
             * the process really is replaced and SpringBoard comes back. On Android there is no
             * SpringBoard to come back, so the only visible effect was that tapping "Jailbreak"
             * made the app disappear a few seconds later, which reads exactly like a crash and is
             * what got reported as one. Nothing crashed: no FATAL EXCEPTION is ever logged, the
             * activity is simply finished by its own code.
             *
             * The screen now goes black, blinks, and returns to the main screen in its jailbroken
             * state. That is the observable outcome the user actually wants from the button.
             */
            BlackoutSequence(
                sequence = when {
                    // Boot logo off: one lying Apple, no blinking at all.
                    !bootlogoEnabled -> BootSequence.Lying
                    // Custom logo on with a picture present: that picture blinks, then Apple.
                    customBootlogoEnabled && !customBootlogoPath.isNullOrEmpty() ->
                        BootSequence.Custom(customBootlogoPath)
                    // Otherwise the theme ring blinks, then Apple.
                    else -> BootSequence.Themed(theme)
                },
                onFinished = {
                    overlay = Overlay.None
                    refreshState()
                },
            )
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