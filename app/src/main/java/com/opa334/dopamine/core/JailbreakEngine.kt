package com.opa334.dopamine.core

import android.content.Context
import com.opa334.dopamine.R
import kotlinx.coroutines.delay

/** Emitted as the run progresses so the UI can render each line and tick. */
sealed interface StepEvent {
    /** A step started; [index] is 0-based. */
    data class Started(val index: Int, val label: String) : StepEvent

    /** A step finished; [result] is the line shown under it. */
    data class Finished(val index: Int, val label: String, val result: String) : StepEvent

    /** Overall progress in 0..1, for the bar. */
    data class Progress(val fraction: Float) : StepEvent

    /** A line that is not tied to a step, e.g. the header or a hint. */
    data class Log(val text: String) : StepEvent

    /** The run is over; [ok] says whether the daemon came up. */
    data class Done(val ok: Boolean, val message: String) : StepEvent
}

/**
 * Builds and runs the staged flow.
 *
 * The step list is intentionally the same wording Dopamine shows, because the point of this build is
 * the interface: "Downloading Kernel…", "Finding offsets", "Exploiting the kernel (ClearSword)". The
 * bodies behind them are honest about what they can do without a kernel exploit — they read the
 * device, create the files a jailbroken environment would have, and start the daemon — and nothing
 * here claims to have gained privileges it did not.
 */
object JailbreakEngine {

    private const val STAGE_DIR = "dopamine"

    fun steps(context: Context, detailed: Boolean): List<ExploitStep> {
        // Labels come from resources rather than being baked in here, so the run log follows the
        // device language like the rest of the interface. The engine has no Context of its own, but
        // every caller passes one.
        fun s(id: Int): String = context.getString(id)
        val steps = mutableListOf<ExploitStep>()

        steps += ExploitStep(s(R.string.step_download_kernel), 900L) { ctx ->
            // Everything this build can legitimately "download" is the device's own kernel string;
            // pretending to fetch a payload would be a lie with no upside.
            val kernel = ctx.device.kernel.ifEmpty { "unknown" }
            if (detailed) "kernel $kernel · ${ctx.device.abi}" else kernel
        }

        steps += ExploitStep(s(R.string.step_find_address), 900L) { ctx ->
            // Offsets depend on the exact kernel build; this reports the one it would need to match.
            val build = ctx.device.androidRelease
            if (detailed) "Android $build (API ${ctx.device.sdk}) · kernel ${ctx.device.kernel}"
            else "Android $build"
        }

        steps += ExploitStep(s(R.string.step_exploit_kernel), 1_400L) { ctx ->
            // The step that the real thing would spend its time in. Here it does the only privileged
            // -looking work available to an unprivileged app: create its own staging directory.
            val dir = java.io.File(context.filesDir, STAGE_DIR).apply { mkdirs() }
            if (detailed) {
                "staged in ${dir.absolutePath} · ${ctx.device.manufacturer} ${ctx.device.model}"
            } else {
                "done"
            }
        }

        steps += ExploitStep(s(R.string.step_prepare_environment), 800L) {
            val shell = Daemon.resolveShell(context) ?: "none"
            if (detailed) "shell: $shell" else "ok"
        }

        steps += ExploitStep(s(R.string.step_install_package_manager), 700L) {
            // The wizard already recorded the choice; this step only reports it so the log is complete.
            val pm = JailbreakState.packageManager(context)
                ?: context.getString(R.string.step_package_manager_unset)
            if (detailed) "package manager: $pm" else pm
        }

        steps += ExploitStep(s(R.string.step_start_daemon), 600L) {
            val pid = Daemon.start(context)
            if (pid == null) {
                // Surfaced as a failure by the caller; returning the reason keeps it in the log.
                "daemon did not start"
            } else {
                JailbreakState.setDaemonPid(context, pid)
                if (detailed) "daemon pid $pid" else "pid $pid"
            }
        }

        return steps
    }

    /**
     * Runs every step, emitting events.
     *
     * The daemon is started by its own step rather than here, so that "the daemon is up" is the last
     * thing that happens and [Done] can report it truthfully: [ok] is read back from the real pid.
     */
    suspend fun run(
        context: Context,
        detailed: Boolean,
        onEvent: (StepEvent) -> Unit,
    ) {
        val device = DeviceProfile.read()
        val ctx = ExploitContext(device)
        val list = steps(context, detailed)

        onEvent(StepEvent.Log("${device.manufacturer} ${device.model} · Android ${device.androidRelease}"))
        onEvent(StepEvent.Log("${list.size} steps"))

        list.forEachIndexed { index, step ->
            onEvent(StepEvent.Started(index, step.label))
            onEvent(StepEvent.Progress(index.toFloat() / list.size))

            // The delay is what makes the log readable; the action itself is instant.
            delay(step.durationMs)

            val result = runCatching { step.action(ctx) }
                .getOrElse { "error: ${it.message ?: it::class.java.simpleName}" }
            onEvent(StepEvent.Finished(index, step.label, result))
            onEvent(StepEvent.Progress((index + 1).toFloat() / list.size))
        }

        // Trust the daemon, not the step: it can report success and die a moment later.
        val pid = JailbreakState.daemonPid(context)
        val alive = pid > 0 && Daemon.isAlive(context, pid)
        if (alive) {
            JailbreakState.markEverJailbroken(context)
            onEvent(StepEvent.Done(true, context.getString(R.string.action_jailbroken)))
        } else {
            JailbreakState.clearDaemonPid(context)
            onEvent(StepEvent.Done(false, context.getString(R.string.result_daemon_failed)))
        }
    }

    /**
     * "Remove jailbreak".
     *
     * Stops the daemon and deletes the staging directory. The `ever_jailbroken` flag is deliberately
     * kept so the app does not re-run the first-run wizard, which is what Dopamine does too.
     */
    fun removeJailbreak(context: Context) {
        val pid = JailbreakState.daemonPid(context)
        Daemon.stop(pid)
        Daemon.clearHeartbeat(context)
        JailbreakState.clearDaemonPid(context)
        runCatching {
            java.io.File(context.filesDir, STAGE_DIR).deleteRecursively()
        }
    }

    // "Restart SpringBoard" is handled entirely by the UI.
    //
    // It used to live here as a configuration-change broadcast to the launcher, which was dishonest:
    // the return value said "sent" while nothing was guaranteed to happen, and an app cannot restart
    // the launcher anyway. The menu row now holds a black screen for eight seconds and finishes the
    // activity, which is the part of a respring a user can actually observe.

    /**
     * "Restart userspace": kill the daemon, wait, bring it back.
     *
     * This is a real restart of the thing whose presence *is* the jailbreak state, so it is the most
     * honest of the three imitation actions: the UI's blinks cover the gap, and [isJailbroken] would
     * genuinely report false during it.
     *
     * The UI plays the blink over this, which is why it is suspend: the animation and the restart run
     * concurrently and the caller gets one signal when both are done.
     */
    suspend fun restartUserspace(context: Context): Boolean {
        val old = JailbreakState.daemonPid(context)
        Daemon.stop(old)
        Daemon.clearHeartbeat(context)
        delay(1_200L)
        val pid = Daemon.start(context) ?: return false
        JailbreakState.setDaemonPid(context, pid)
        return Daemon.isAlive(context, pid)
    }
}