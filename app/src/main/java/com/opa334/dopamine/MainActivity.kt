package com.opa334.dopamine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.opa334.dopamine.core.DeviceProfile
import com.opa334.dopamine.core.JailbreakEngine
import com.opa334.dopamine.core.JailbreakState
import com.opa334.dopamine.core.StepEvent
import com.opa334.dopamine.ui.AboutScreen
import com.opa334.dopamine.ui.AppleStage
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
import com.opa334.dopamine.ui.PackageManagerScreen
import com.opa334.dopamine.ui.SettingsScreen
import com.opa334.dopamine.ui.ThemeBackground
import com.opa334.dopamine.ui.ThemeIcon
import com.opa334.dopamine.ui.ThemedMarkBlink
import com.opa334.dopamine.ui.ThemePickerScreen
import com.opa334.dopamine.ui.applyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            /*
             * The theme is applied inside the composition, not outside it.
             *
             * An earlier revision held it as an activity field and wrapped this call in
             * `DopamineTheme(theme = appTheme)`. Changing that field invalidated the whole
             * `setContent` lambda, which rebuilt `DopamineApp` from scratch - and rebuilding it
             * discards every `remember`, so `screen` fell back to Main and the app appeared to
             * crash back to the home screen on every theme change. Hoisting the state here and
             * reading it in a composable keeps the recomposition scoped to the theme wrapper
             * instead of the entire app.
             */
            DopamineApp()
        }
    }
}

private enum class Screen { Main, Settings, Exploits, Themes, PackageManager, About }

/** Full-screen things drawn on top of the current screen. */
private enum class Overlay { None, Wizard, Blackout, Userspace, SpringBoard }

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DopamineApp() {
    /*
     * The theme lives here, at the top of the composition, and the whole app is wrapped in it.
     *
     * It has to sit above the screens to repaint all of them, but it must not sit above the
     * composition itself: when it did, changing it rebuilt DopamineApp and wiped its remembered
     * state, so the settings screen fell back to Main on every theme change. Declaring it here and
     * wrapping the content keeps the state and recomposes only downwards.
     */
    val context = LocalContext.current

    var theme by remember { mutableStateOf(DopamineThemeKey.fromKey(JailbreakState.theme(context))) }

    /*
     * The overlay state lives here, one level above the screens.
     *
     * It has to: the overlays are drawn as a sibling of the screens, so the screens cannot own
     * the flag that decides whether they are covered. Hoisting it also means an animation tick
     * (the Apple's alpha) recomposes only this scope, not the screen tree underneath.
     */
    var overlay by remember { mutableStateOf(Overlay.None) }
    var userspaceMarkDone by remember { mutableStateOf(false) }
    /*
     * The Apple's opacity for "Restart userspace".
     *
     * An Animatable rather than a Boolean because the restart coroutine animates it after the
     * userspace has actually come back - the Apple is what the system shows while it comes up, so
     * it cannot be on the same timer as the blink that covers the restart itself.
     */
    val userspaceAppleAlpha = remember { Animatable(0f) }

    /*
     * What the boot animation should draw, for both entry points.
     *
     * Boot logo off means no artwork at all - the switch deletes the logo on iOS and the system
     * paints its own, so there is nothing for the app to show.
     */
    val bootSequence = remember(bootlogoEnabled, customBootlogoEnabled, theme) {
        when {
            !bootlogoEnabled -> BootSequence.Off
            customBootlogoEnabled && !JailbreakState.customBootlogoPath(context).isNullOrEmpty() ->
                BootSequence.Custom(JailbreakState.customBootlogoPath(context))
            else -> BootSequence.Themed(theme)
        }
    }

    DopamineTheme(theme = theme) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            DopamineScreens(
                theme = theme,
                overlay = overlay,
                bootSequence = bootSequence,
                // The callbacks the screens and the overlays share.
                onStartBlackout = { overlay = Overlay.Blackout },
                // The first run's wizard is raised by the screens alone: the run it answers for
                // is started there.
                onStartWizard = { overlay = Overlay.Wizard },
                onStartUserspace = { overlay = Overlay.Userspace },
                onStartSpringBoard = { overlay = Overlay.SpringBoard },
                onClearOverlay = { overlay = Overlay.None },
                onMarkReset = { userspaceMarkDone = false },
                userspaceMarkDone = userspaceMarkDone,
                userspaceAppleAlpha = userspaceAppleAlpha,
                pendingRunAfterWizard = pendingRunAfterWizard,
                onThemeChange = { picked ->
                    theme = picked
                    // Stored as its plist identifier, which is what the original preference holds.
                    JailbreakState.setTheme(context, picked.key)
                    // Repaints the launcher icon; the package manager does the actual swap.
                    ThemeIcon.apply(context, picked)
                },
            )

            /*
             * The overlays sit on the theme's root, next to the screens, not inside them.
             *
             * While one is up it is the only thing painted, so the black stages are black: the
             * main screen's blurred photo is not underneath them to show through a fade.
             */
            Overlays(
                overlay = overlay,
                bootSequence = bootSequence,
                userspaceMarkDone = userspaceMarkDone,
                userspaceAppleAlpha = userspaceAppleAlpha,
                onMarkDone = { userspaceMarkDone = true },
                onWizardCancel = { overlay = Overlay.None },
                onWizardFinished = { pm, password ->
                    JailbreakState.setPackageManager(context, pm)
                    JailbreakState.setSudoPassword(context, password)
                    overlay = Overlay.None
                    // The run is started by the screens, which own the log; the request travels
                    // as state and is consumed by a LaunchedEffect there.
                    pendingRunAfterWizard = true
                },
                onBlackoutFinished = { overlay = Overlay.None },
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DopamineScreens(
    theme: DopamineThemeKey,
    /*
     * The overlay flag is owned by DopamineApp, because the overlays are drawn beside these
     * screens rather than on top of them in the same tree. The screens still need to read it and
     * to raise it, so it is passed in as state plus the three transitions they cause.
     */
    overlay: Overlay,
    bootSequence: BootSequence,
    onStartBlackout: () -> Unit,
    onStartWizard: () -> Unit,
    onStartUserspace: () -> Unit,
    onStartSpringBoard: () -> Unit,
    onClearOverlay: () -> Unit,
    onMarkReset: () -> Unit,
    userspaceMarkDone: Boolean,
    userspaceAppleAlpha: Animatable,
    pendingRunAfterWizard: Boolean,
    onThemeChange: (DopamineThemeKey) -> Unit,
) {
    val context = LocalContext.current
    // The image picker lives on MainActivity: it registers a PickVisualMedia launcher, which only
    // an activity can do. `activity` alone is too wide a type to call it on.
    val host = context as? MainActivity
    val scope = rememberCoroutineScope()

    // Screens are derived from persisted state on every recomposition, so leaving the app and
    // coming back cannot show a stale answer — the bug this app is a rebuild of.
    // Starts as false and is filled in by the LaunchedEffect below.
    //
    // isJailbroken can now spawn the daemon when the marker is present but the process is gone
    // (a swipe from Recents kills it), so it does real work and must not be called during
    // composition: composing has to stay cheap and repeatable.
    var jailbroken by remember { mutableStateOf(false) }
    var detailedLog by remember { mutableStateOf(JailbreakState.detailedLog(context)) }
    var packageManager by remember { mutableStateOf(JailbreakState.packageManager(context)) }

    var screen by remember { mutableStateOf(Screen.Main) }
    /*
     * Set when the wizard finishes: the answers were saved by the overlay above, and the run they
     * were collected for is started here, where the log and the engine call live.
     */
    var pendingRunAfterWizard by remember { mutableStateOf(false) }
    var ui by remember { mutableStateOf(MainUiState()) }

    /*
     * Boot-logo preferences.
     *
     * Held here as well as in SharedPreferences because the UI has to react to a change
     * immediately; the stored copy is what survives a restart.
     */
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
    suspend fun refreshState() {
        // Same reason as the initial read: isJailbroken may have to start the daemon, which is a
        // blocking spawn rather than a lookup.
        jailbroken = withContext(Dispatchers.IO) { JailbreakState.isJailbroken(context) }
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
                        /*
                         * The log collapses when the run is over.
                         *
                         * `expanded` was only ever set to true, never back to false, so the panel
                         * stayed on screen after the jailbreak finished and the main screen no
                         * longer looked like the one the app starts on. The log belongs to the run;
                         * when the run ends it goes away.
                         *
                         * The state is refreshed on both branches. It used to happen only on
                         * failure, so after a successful run `jailbroken` stayed false and `busy`
                         * stayed true - the button still read "Jailbreak" and tapping it re-ran the
                         * whole flow, which is why the blink appeared to need two taps.
                         */
                        ui = ui.copy(expanded = false)
                        if (event.ok) {
                            onStartBlackout()
                        } else {
                            onClearOverlay()
                        }
                        // onEvent is a plain callback, not a suspend one, and refreshState now has
                        // to hop to the IO dispatcher, so it is launched rather than called.
                        scope.launch { refreshState() }
                    }
                },
            )
        }
    }

    /*
     * The wizard's answers are saved by DopamineApp (it owns the wizard overlay), and the run that
     * follows has to be started here, where the log lives. The flag is what carries the request
     * across; it is cleared as soon as the run has been handed off.
     */
    LaunchedEffect(pendingRunAfterWizard) {
        if (pendingRunAfterWizard) {
            pendingRunAfterWizard = false
            startRun()
        }
    }

    // Re-read the daemon on every (re)entry; the activity may have been backgrounded for a long time.
    LaunchedEffect(Unit) {
        /*
         * Off the main thread, because isJailbroken may now have to start the daemon.
         *
         * Coming back to the app after it was swiped away from Recents is exactly the case where
         * the marker file is present and the process is not, so this call can spawn a shell and
         * wait for it. That is a blocking operation and the first frame must not wait for it.
         */
        jailbroken = withContext(Dispatchers.IO) { JailbreakState.isJailbroken(context) }
        packageManager = JailbreakState.packageManager(context)
        ui = ui.copy(
            jailbroken = jailbroken,
            title = if (jailbroken) titleJailbroken else titleJailbreak,
            packageManager = packageManager,
            deviceLine = DeviceProfile.read().let {
                /*
                 * The same span as the header, then the device itself.
                 *
                 * The header states what the build targets; this line states what is in the
                 * user's hand. Keeping the target span here as well means the log does not
                 * contradict the screen it is drawn on.
                 */
                "${JailbreakEngine.versionSupportString()} · ${it.manufacturer} ${it.model}"
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
                    jailbroken = jailbroken,
                    busy = ui.busy,
                    title = ui.title,
                    expanded = ui.expanded,
                    deviceLine = ui.deviceLine,
                    rows = ui.rows,
                    progress = ui.progress,
                    onJailbreak = {
                        if (jailbroken) {
                            // Already jailbroken: the card means "restart userspace", which is
                            // what Dopamine does rather than re-running the exploit.
                            scope.launch {
                                onStartUserspace()
                                /*
                                 * The restart is awaited before the overlay is dismissed, not
                                 * fired and forgotten.
                                 *
                                 * The blackout sequence matters because it hides the moment the
                                 * daemon is killed and replaced. Starting the restart without
                                 * waiting let the sequence finish first on a fast run, so the app
                                 * came back on screen while the userspace was still being torn
                                 * down - the blink then looked disconnected from the action it was
                                 * supposed to cover. Awaiting it keeps the two in step, and the
                                 * overlay is cleared here rather than from the animation callback.
                                 */
                                runCatching { JailbreakEngine.restartUserspace(context) }
                                refreshState()
                                // Cleared here, not by the animation: the blink has to outlast the
                                // restart it is covering.
                                onClearOverlay()
                            }
                        } else if (!JailbreakState.hasEverJailbroken(context) &&
                            JailbreakState.packageManager(context) == null
                        ) {
                            /*
                             * First run: ask for the package manager and sudo password now, not
                             * at launch, and only then start.
                             *
                             * Read from the store rather than from the `packageManager` field this
                             * screen was composed with. "Remove Jailbreak" clears the stored value
                             * from a coroutine, and the field only catches up on the next refresh;
                             * checking the field meant this branch could still be skipped right
                             * after a removal, which is the "no password, no package manager"
                             * second run.
                             */
                            onStartWizard()
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
                            onStartSpringBoard()
                            delay(3_000L)
                            onClearOverlay()
                            refreshState()
                        }
                    },
                    onRestartUserspace = {
                        /*
                         * The Apple is the part that has to land after the restart, not before it.
                         *
                         * This used to fire the restart and the whole blink at the same time, so
                         * the sequence played on schedule while the daemon was still coming back:
                         * the Apple appeared over a userspace that was not there yet, and the
                         * overlay was removed the moment the animation ended. Splitting the two
                         * lets each part happen when it is actually true:
                         *
                         *   mark blinks (the restart runs underneath it)
                         *   the userspace is back
                         *   the Apple fade-in begins
                         *
                         * The Apple is held up afterwards and only taken down once it has been
                         * on screen long enough to read as the system coming up.
                         */
                        scope.launch {
                            onStartUserspace()
                            userspaceAppleAlpha.snapTo(0f)
                            onMarkReset()
                            runCatching { JailbreakEngine.restartUserspace(context) }
                            // Wait for the mark to finish its two blinks before showing the Apple:
                            // covering the restart is the whole reason the mark is there.
                            while (!userspaceMarkDone) delay(50L)
                            refreshState()
                            userspaceAppleAlpha.animateTo(1f, tween(1_600, easing = LinearEasing))
                            delay(2_500L)
                            userspaceAppleAlpha.animateTo(0f, tween(900, easing = LinearEasing))
                            onClearOverlay()
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
                        host?.pickBootlogoImage { uri ->
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
                            /*
                             * Forget the wizard's answers as well.
                             *
                             * The next run is a first run in the only sense that matters: there
                             * is no bootstrap, no package manager installed, and no sudo
                             * password in effect. Keeping the stored package manager meant the
                             * wizard was skipped on the next tap - it checks
                             * `packageManager == null` - so the user was dropped straight into a
                             * run that never asked where to install anything, which is the
                             * "no password and no package manager" behaviour this fixes.
                             */
                            JailbreakState.clearPackageManager(context)
                            JailbreakState.clearSudoPassword(context)
                        }
                        /*
                         * Removal closes the app.
                         *
                         * Until now the screen simply went back to Main with the exploit still
                         * marked as done and the daemon gone, so the app sat there in a state it
                         * cannot describe. A jailbreak that has been removed has nothing left
                         * running, and the honest thing is to leave: the next launch starts from
                         * the beginning, which is what "Remove Jailbreak" means on iOS too - it
                         * reboots the device.
                         *
                         * finish() rather than killProcess: this activity owns no other state,
                         * and letting the system tear it down is the supported way out.
                         */
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_MAIN)
                                .addCategory(android.content.Intent.CATEGORY_HOME)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        (context as? MainActivity)?.finish()
                    },
                    onBack = { screen = Screen.Main },
                    onOpenExploits = { screen = Screen.Exploits },
                    onOpenPackageManager = { screen = Screen.PackageManager },
                )

                Screen.PackageManager -> PackageManagerScreen(
                    packageManager = packageManager,
                    onPackageManagerChange = {
                        packageManager = it
                        JailbreakState.setPackageManager(context, it)
                    },
                    sudoPassword = JailbreakState.sudoPassword(context),
                    onSudoPasswordChange = { JailbreakState.setSudoPassword(context, it) },
                    onBack = { screen = Screen.Settings },
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
                        /*
                         * One call does everything: it stores the choice, swaps the launcher icon,
                         * and notifies the theme wrapper above, which repaints the background and
                         * the cards without touching this screen's state. Nothing here has to be
                         * pushed up to the activity any more - doing that rebuilt the composition
                         * and dropped the user back on the main screen.
                         */
                        onThemeChange(picked)
                        // The picker stays open so another theme can be tried immediately; the
                        // settings list behind it already shows the new name.
                    },
                    onBack = { screen = Screen.Settings },
                )

                Screen.About -> AboutScreen(
                    device = DeviceProfile.read(),
                    onBack = { screen = Screen.Main },
                )
            }
        }
    }
}

/**
 * The full-screen overlays: the first-run wizard, the boot animation, and the black screens.
 *
 * Drawn by `DopamineApp` as a sibling of the screens rather than nested in them, so that while an
 * overlay is up it is the only thing painted - the black stages have to be black, not black over
 * a blurred photo. Everything it needs is passed in; it owns no state of its own, which is what
 * keeps the screen underneath from recomposing when the animation ticks.
 */
@Composable
private fun Overlays(
    overlay: Overlay,
    bootSequence: BootSequence,
    userspaceMarkDone: Boolean,
    userspaceAppleAlpha: Animatable,
    onMarkDone: () -> Unit,
    onWizardCancel: () -> Unit,
    onWizardFinished: (packageManager: String, sudoPassword: String) -> Unit,
    onBlackoutFinished: () -> Unit,
) {
    if (overlay == Overlay.Wizard) {
        // The wizard draws over the main screen, so cancelling returns to a screen that
        // already looks right instead of a blank transition.
        Box(Modifier.fillMaxSize().background(Color(0xCC000000))) {
            ThemeBackground()
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
            FirstRunWizard(
                onCancel = onWizardCancel,
                onFinished = onWizardFinished,
            )
        }
    }

    if (overlay == Overlay.Blackout) {
        /*
         * The blink is the "respring" moment, but the app must survive it.
         *
         * This used to call onFinish() and close the activity, copying what iOS does - where the
         * process really is replaced and SpringBoard comes back. On Android there is no
         * SpringBoard to come back, so the only visible effect was that tapping "Jailbreak" made
         * the app disappear a few seconds later, which reads exactly like a crash and is what got
         * reported as one. Nothing crashed: no FATAL EXCEPTION is ever logged, the activity is
         * simply finished by its own code.
         *
         * The screen now goes black, blinks, and returns to the main screen in its jailbroken
         * state. That is the observable outcome the user actually wants from the button.
         */
        BlackoutSequence(
            sequence = bootSequence,
            onFinished = onBlackoutFinished,
        )
    }

    if (overlay == Overlay.Userspace) {
        /*
         * Restarting userspace blinks the same artwork as a fresh run, but split in two.
         *
         * The mark is what covers the restart, and the Apple is what the system shows while it
         * comes up - so the mark plays first, the restart is awaited, and only then does the
         * Apple fade in. Both are opaque full-screen layers; nothing behind them can show through
         * while they are on screen.
         */
        if (bootSequence is BootSequence.Off) {
            // Boot logo off: black, no artwork, and no Apple either - exactly what the sequence
            // does on a fresh run.
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            if (!userspaceMarkDone) {
                ThemedMarkBlink(
                    sequence = bootSequence,
                    onFinished = onMarkDone,
                )
            }
            // Always drawn over the mark, on its own black fill; fully transparent until the
            // restart has finished and the mark is gone.
            AppleStage(alpha = userspaceAppleAlpha.value)
        }
    }

    if (overlay == Overlay.SpringBoard) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}