package com.opa334.dopamine.core

import android.content.Context
import android.os.Build
import com.opa334.dopamine.R
import kotlinx.coroutines.delay
import java.io.File

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
 * The step wording is Dopamine's, because the point of this build is the interface. What sits
 * behind each step is honest: it reports the device it would need to match, creates the files a
 * jailbroken environment would have, and starts the daemon — and nothing here claims a privilege
 * it did not get.
 *
 * The `detailed` flag is not cosmetic. When it is on, each step returns a multi-part line with
 * the real values it touched (kernel release, API level, ABI, staging path, resolved shell, pid);
 * when it is off, each step returns one short word. That difference is what the "Detailed log"
 * switch in Settings controls.
 */
object JailbreakEngine {

    private const val STAGE_DIR = "dopamine"

    /**
     * Kernel releases the shipped exploits target and the iOS span each covers.
     *
     * Used only to tell the user whether their kernel is one the real Dopamine would recognise. The
     * offsets themselves live in the exploit binaries and are not reproduced here.
     */
    private val KERNEL_TABLE = linkedMapOf(
        "5.10" to ("iOS 15.0" to "15.8"),
        "5.15" to ("iOS 15.0" to "15.8"),
        "6.0" to ("iOS 16.0" to "16.6"),
        "6.1" to ("iOS 16.0" to "16.6"),
        "6.2" to ("iOS 16.0" to "16.6"),
        "6.3" to ("iOS 16.0" to "16.6"),
    )

    fun steps(context: Context, detailed: Boolean): List<ExploitStep> {
        fun s(id: Int): String = context.getString(id)

        val steps = mutableListOf<ExploitStep>()

        steps += ExploitStep(s(R.string.step_download_kernel), 900L) { ctx ->
            // The only thing this build can legitimately "download" is the device's own kernel
            // string; faking a payload fetch would be a lie with no upside.
            val kernel = ctx.device.kernel.ifEmpty { "unknown" }
            if (!detailed) {
                kernel
            } else {
                buildString {
                    append("krnl=")
                    append(kernel)
                    append(" · rel=")
                    append(ctx.device.androidRelease)
                    append(" · sdk=")
                    append(ctx.device.sdk)
                    append(" · abi=")
                    append(ctx.device.abi)
                }
            }
        }

        steps += ExploitStep(s(R.string.step_find_address), 900L) { ctx ->
            /*
             * A real exploit walks the kernel's Mach-O load commands to locate its symbols, then
             * computes slide-adjusted addresses. None of that can happen without an exploit, but
             * the search it would perform can be described in full: which kernel release, which
             * sysctl nodes were readable, what the KASLR slide would be applied to, and how many
             * candidate offsets the release maps to.
             *
             * Reporting the actual probes here is the difference between "detailed" meaning "more
             * words" and meaning "the values this run really read".
             */
            if (!detailed) {
                "opt"
            } else {
                val release = ctx.device.kernel.ifEmpty { "unknown" }
                val matched = KERNEL_TABLE.entries.firstOrNull { (prefix, _) ->
                    release.startsWith(prefix) || release.contains(prefix)
                }

                val probes = listOf(
                    "/proc/sys/kernel/kptr_restrict",
                    "/proc/sys/kernel/randomize_va_space",
                    "/proc/sys/kernel/osrelease",
                    "/proc/kallsyms",
                ).joinToString(" · ") { path ->
                    val state = runCatching {
                        val f = File(path)
                        when {
                            !f.exists() -> "absent"
                            !f.canRead() -> "denied"
                            (f.length() > 4L * 1024 * 1024) -> "huge" // /proc/kallsyms is not seekable
                            else -> "readable"
                        }
                    }.getOrDefault("error")
                    "$path=$state"
                }

                buildString {
                    append("kernel=")
                    append(release)
                    append(" · range=")
                    append(matched?.value?.first ?: "unmapped")
                    append("..")
                    append(matched?.value?.second ?: "unmapped")
                    append(" · slide=unresolved")
                    append("\nprobes: ")
                    append(probes)
                    // Everything below is read from the device, not asserted: the page size, the
                    // architecture of this process, and how much of /proc/kallsyms is actually
                    // visible. On stock Android the symbol table is present but every address in
                    // it reads as zero, which is precisely why an exploit would be needed.
                    append("\npage=")
                    append(kernelPageSize())
                    append("K · arch=")
                    append(ctx.device.abi)
                    append(" · kallsyms=")
                    append(kallsymsState())
                    if (matched == null) {
                        append("\nnote: kernel release does not match any known Dopamine target")
                    }
                }
            }
        }

        steps += ExploitStep(s(R.string.step_exploit_kernel), 1_400L) { ctx ->
            // The step the real thing would spend its time in. Here it performs the only
            // privileged-looking work an unprivileged app can do: create its own staging area.
            val dir = File(context.filesDir, STAGE_DIR).apply { mkdirs() }
            if (!detailed) {
                "done"
            } else {
                val entries = dir.list()?.size ?: 0
                "staged=${dir.absolutePath} · entries=$entries · " +
                    "uid=${android.os.Process.myUid()} · " +
                    "dev=${ctx.device.manufacturer}/${ctx.device.model}"
            }
        }

        steps += ExploitStep(s(R.string.step_prepare_environment), 800L) {
            val shell = Daemon.resolveShell(context) ?: "none"
            if (!detailed) {
                "ok"
            } else {
                "shell=$shell · ldlp=${context.applicationInfo.nativeLibraryDir} · " +
                    "files=${context.filesDir.absolutePath}"
            }
        }

        steps += ExploitStep(s(R.string.step_install_package_manager), 700L) {
            val pm = JailbreakState.packageManager(context)
                ?: context.getString(R.string.step_package_manager_unset)
            if (!detailed) {
                pm
            } else {
                "pkg=$pm · sudo=${if (JailbreakState.sudoPassword(context).isNullOrEmpty()) "default(alpine)" else "set"} · " +
                    "prefs=dopamine_prefs"
            }
        }

        steps += ExploitStep(s(R.string.step_start_daemon), 600L) {
            val pid = Daemon.start(context)
            if (pid == null) {
                // Surfaced as a failure by the caller; returning the reason keeps it in the log.
                "daemon did not start"
            } else {
                JailbreakState.setDaemonPid(context, pid)
                if (!detailed) {
                    "pid $pid"
                } else {
                    val hb = Daemon.heartbeatFile(context)
                    "pid=$pid · hb=${hb.absolutePath} · alive=${Daemon.isAlive(pid)}"
                }
            }
        }

        return steps
    }

    /**
     * The kernel's page size, in KiB, as reported by the running system.
     *
     * Read rather than assumed: 16 KiB pages are the default on arm64 Apple silicon and on many
     * Android devices, but a 4 KiB kernel is still common, and which one is in use decides what
     * every address in an exploit means.
     */
    private fun kernelPageSize(): String = runCatching {
        // KernelPageSize in smaps is the MMU's page size for this mapping, which is what an exploit
        // cares about. smaps_rollup is one file instead of hundreds of per-mapping entries.
        val rollup = File("/proc/self/smaps_rollup")
        val line = if (rollup.exists()) {
            rollup.useLines { lines ->
                lines.firstOrNull { it.startsWith("KernelPageSize:") }
            }
        } else {
            null
        }
        if (line != null) {
            "${line.substringAfter(':').trim().removeSuffix("kB").trim()}K"
        } else {
            // No smaps access on this kernel; say so rather than printing a made-up number.
            "unknown"
        }
    }.getOrDefault("unknown")

    /**
     * Whether /proc/kallsyms exposes real addresses.
     *
     * On an unprivileged Android process the file exists and lists every symbol, but the address
     * column reads as all zeros because `kptr_restrict` is 2. That is worth showing in the log: it
     * is the concrete reason a kernel exploit is required, rather than a claim about it.
     */
    private fun kallsymsState(): String = runCatching {
        val file = File("/proc/kallsyms")
        if (!file.exists()) return@runCatching "absent"
        if (!file.canRead()) return@runCatching "denied"
        file.useLines { lines ->
            val first = lines.firstOrNull { it.isNotBlank() } ?: return@useLines "empty"
            val addr = first.substringBefore(' ')
            when {
                addr.all { it == '0' } -> "zeroed"
                else -> "visible"
            }
        }
    }.getOrDefault("error")

    /**
     * Runs every step, emitting events.
     *
     * The daemon is started by its own step rather than here, so that "the daemon is up" is the
     * last thing that happens and [Done] can report it truthfully: [ok] is read back from the real
     * pid rather than trusted from the step.
     */
    suspend fun run(
        context: Context,
        detailed: Boolean,
        onEvent: (StepEvent) -> Unit,
    ) {
        val device = DeviceProfile.read()
        val ctx = ExploitContext(device)
        val list = steps(context, detailed)

        onEvent(
            StepEvent.Log(
                "${device.manufacturer} ${device.model} · Android ${device.androidRelease} " +
                    "(API ${device.sdk}) · ${device.abi}",
            ),
        )
        onEvent(StepEvent.Log("kernel ${device.kernel.ifEmpty { "unknown" }}"))
        onEvent(StepEvent.Log("${list.size} steps · detailed=$detailed"))

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

    /** "Remove jailbreak": stop the daemon and delete the staging directory. */
    fun removeJailbreak(context: Context) {
        val pid = JailbreakState.daemonPid(context)
        if (pid > 0) Daemon.stop(pid)
        JailbreakState.clearDaemonPid(context)
        JailbreakState.clearEverJailbroken(context)
        runCatching { File(context.filesDir, STAGE_DIR).deleteRecursively() }
    }

    // "Restart SpringBoard" is handled entirely by the UI.
    //
    // An app cannot restart the launcher, and there is no SpringBoard to reload on Android.
    // Sending ACTION_CONFIGURATION_CHANGED to the launcher package, as an earlier revision did,
    // returns "sent" while guaranteeing nothing — it would be a claim this app cannot back up.
    // The UI instead shows a black screen for eight seconds and calls finish(), which is the
    // observable part of a respring: everything disappears, and the app comes back on
    // "Jailbroken" because the daemon outlived it.

    /**
     * "Restart userspace": kill the daemon, wait, bring it back.
     *
     * This one is real. Killing the process and confirming the replacement is alive is exactly
     * what a userspace reboot does to the daemon; the only thing missing is the rest of userspace.
     */
    suspend fun restartUserspace(context: Context): Boolean {
        val old = JailbreakState.daemonPid(context)
        if (old > 0) Daemon.stop(old)
        JailbreakState.clearDaemonPid(context)

        // Give the kernel a moment to reap the old process so the liveness check cannot see it.
        delay(600L)

        val pid = Daemon.start(context) ?: return false
        JailbreakState.setDaemonPid(context, pid)
        delay(400L)
        return Daemon.isAlive(context, pid)
    }
}

/** What a step can see. Passed in rather than read from globals so steps stay testable. */
data class ExploitContext(val device: DeviceProfile)

/** One step of the run: a label, how long to pretend it takes, and what it actually does. */
class ExploitStep(
    val label: String,
    val durationMs: Long,
    val action: (ExploitContext) -> String,
)

/** Reads what the steps need off the device. */
data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdk: Int,
    val kernel: String,
    val abi: String,
    val is64Bit: Boolean,
) {
    companion object {
        fun read(): DeviceProfile = DeviceProfile(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE ?: "?",
            sdk = Build.VERSION.SDK_INT,
            // /proc/version is more accurate than os.version, which Android truncates to the
            // build number on some vendor kernels.
            kernel = runCatching {
                File("/proc/version").readText().trim().split(" ").getOrNull(2).orEmpty()
            }.getOrDefault(System.getProperty("os.version").orEmpty()),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
        )
    }
}