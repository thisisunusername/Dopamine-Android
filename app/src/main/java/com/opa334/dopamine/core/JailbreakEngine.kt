package com.opa334.dopamine.core

import android.content.Context
import android.os.Build
import com.opa334.dopamine.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    /** Where the bootstrap would be unpacked, mirroring `"/basebin"` on iOS. */
    private const val BOOTSTRAP_DIR = "basebin"

    /** The mount points the bootstrap would be bound over; only their names are ever used. */
    private const val MOUNT_DIR = "mounts"

    /**
     * The exploit Dopamine picks when nothing has been chosen.
     *
     * Kept here rather than reaching into the UI package: the engine must be able to name the
     * default on its own, and a UI enum would make the core depend on the screens that render it.
     * The UI list (`com.opa334.dopamine.ui.Exploits`) is the same set in the same order, so the
     * first entry is the recommended one on both sides.
     */
    const val DEFAULT_EXPLOIT = "ClearSword"

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

/**
         * The staged flow, in the order the real thing performs it.
         *
         * iOS `-DOJailbreaker jailbreak` runs: exploit the kernel, elevate, bootstrap, install the
         * package manager, start the daemon. The extra steps here are the parts that exist as real
         * phases on iOS but have no single `sendLog` of their own - the boot-constant initialisation
         * inside `-doExploitation`, and the mount of the bootstrap over `/usr` that `-mountBootstrap`
         * performs - plus the two first-run questions the wizard answers, which the run otherwise
         * silently depends on.
         *
         * [removing] reuses the same list because a removal goes through the same phases: the exploit
         * has to run again to get the privileges needed to tear the environment down. Only the wording
         * of the mount step changes, and [Done] reports a different outcome.
         */
        fun steps(
            context: Context,
            detailed: Boolean,
            exploit: String? = null,
            removing: Boolean = false,
        ): List<ExploitStep> {
            fun s(id: Int): String = context.getString(id)

            val steps = mutableListOf<ExploitStep>()

            steps += ExploitStep(s(R.string.step_download_kernel), 2_200L) { ctx ->
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

            steps += ExploitStep(s(R.string.step_find_address), 2_200L) { ctx ->
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

            steps += ExploitStep(
                // iOS: `sendLog:[NSString stringWithFormat:DOLocalizedString(@"Exploiting Kernel (%@)"),
                // kernelExploit.name]` - the label is built from the selected exploit at run time.
                // This used to be the fixed resource `Exploiting Kernel (ClearSword)`, so the log
                // always claimed ClearSword even when Settings had a different exploit selected.
                label = context.getString(
                    R.string.step_exploit_kernel_fmt,
                    exploit ?: JailbreakState.selectedExploit(context) ?: DEFAULT_EXPLOIT,
                ),
                durationMs = 3_200L,
            ) { ctx ->
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

            steps += ExploitStep(s(R.string.step_prepare_environment), 1_800L) {
                val shell = Daemon.resolveShell(context) ?: "none"
                if (!detailed) {
                    "ok"
                } else {
                    "shell=$shell · ldlp=${context.applicationInfo.nativeLibraryDir} · " +
                        "files=${context.filesDir.absolutePath}"
                }
            }

            /*
             * Initialisation: what iOS does between the exploit returning and the first privileged
             * operation.
             *
             * `-doExploitation` ends with `jbinfo_initialize_boot_constants()`,
             * `libjailbreak_translation_init()` and `libjailbreak_IOSurface_primitives_init()`, all in
             * one block that cannot fail individually. Its Android counterpart is the set of tables and
             * directories the rest of the run reads - which is what this step actually creates.
             */
            steps += ExploitStep(s(R.string.step_initialize), 1_600L) {
                val dir = File(context.filesDir, STAGE_DIR).apply { mkdirs() }
                val bootstrap = File(dir, BOOTSTRAP_DIR).apply { mkdirs() }
                val mounts = File(dir, MOUNT_DIR).apply { mkdirs() }
                if (!detailed) {
                    "ok"
                } else {
                    "base=${dir.name} · bootstrap=${bootstrap.name} · mounts=${mounts.name} · " +
                        "page=${kernelPageSize()}K"
                }
            }

            /*
             * Sudo password: a first-run question, reported every run.
             *
             * The wizard asks for it exactly once - the value is stored under `dopamine_prefs` and the
             * wizard checks for it - so on a later run this step has nothing to ask and says so. It is
             * still a step because the environment genuinely depends on the value, and because a run
             * whose wizard was cancelled would otherwise leave no trace of the default.
             */
            steps += ExploitStep(
                label = if (JailbreakState.sudoPassword(context) == null) {
                    context.getString(R.string.step_set_sudo_password_only_first_run)
                } else {
                    context.getString(
                        R.string.step_set_sudo_password_fmt,
                        if (JailbreakState.sudoPassword(context).isNullOrEmpty()) "default(alpine)" else "set",
                    )
                },
                durationMs = 900L,
            ) {
                if (!detailed) {
                    "ok"
                } else {
                    // Never the password itself: only whether one is in effect and where it came from.
                    "sudo=" + (if (JailbreakState.sudoPassword(context).isNullOrEmpty()) {
                        "default(alpine)"
                    } else {
                        "user"
                    }) + " · prefs=dopamine_prefs"
                }
            }

            steps += ExploitStep(
                label = if (JailbreakState.packageManager(context) == null) {
                    context.getString(R.string.step_set_package_manager_only_first_run)
                } else {
                    context.getString(
                        R.string.step_set_package_manager_fmt,
                        JailbreakState.packageManager(context).orEmpty(),
                    )
                },
                durationMs = 1_000L,
            ) {
                val pm = JailbreakState.packageManager(context)
                    ?: context.getString(R.string.step_package_manager_unset)
                if (!detailed) {
                    pm
                } else {
                    "pkg=$pm · firstRun=${JailbreakState.packageManager(context) == null} · " +
                        "prefs=dopamine_prefs"
                }
            }

            /*
             * Bind mounts.
             *
             * `-mountBootstrap` is where iOS overlays the bootstrap over /usr, and it is the phase that
             * makes the environment coherent. On Android that mount is impossible without root, so the
             * step records the mount points the environment would use and, when the run is a removal,
             * says so in its label instead of pretending to apply anything.
             */
            steps += ExploitStep(
                label = if (removing) {
                    context.getString(R.string.step_removing_jailbreak)
                } else {
                    context.getString(R.string.step_mount_bindfs)
                },
                durationMs = 1_500L,
            ) { ctx ->
                val dir = File(context.filesDir, STAGE_DIR)
                val mounts = File(dir, MOUNT_DIR).apply { mkdirs() }
                if (!detailed) {
                    if (removing) "removing" else "ok"
                } else {
                    val pairs = listOf("/usr", "/var", "/etc", "/Applications").joinToString(" · ") {
                        "$it->${File(mounts, it.trim('/')).path.substringAfterLast('/')}"
                    }
                    "${if (removing) "unmount" else "mount"}: $pairs · abi=${ctx.device.abi}"
                }
            }

            /*
             * Daemon last.
             *
             * On a removal the daemon is not started - it is what is being taken away - so this step
             * stops it instead. Both leave [Done] reading the real state rather than trusting a
             * return value.
             */
            steps += ExploitStep(
                label = if (removing) {
                    context.getString(R.string.step_removing_jailbreak)
                } else {
                    context.getString(R.string.step_start_daemon)
                },
                durationMs = 1_300L,
            ) {
                if (removing) {
                    JailbreakEngine.stopDaemon(context)
                    if (!detailed) {
                        "stopped"
                    } else {
                        "daemon=stopped · pid=${JailbreakState.daemonPid(context)}"
                    }
                } else {
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
     * The counterpart of `DOEnvironmentManager -versionSupportString`.
     *
     * On iOS that method returns a supported *span* plus the architecture it applies to, and the
     * span depends on what the CPU can do - `"iOS 15.0 - 18.7.1 (arm64)"` for a non-arm64e chip,
     * narrower ranges otherwise. The Android answer has the same shape:
     *
     *  - the span is what this build targets, which is fixed rather than read from the running
     *    device, because it is a statement about coverage and not about one install;
     *  - the architecture is read from the device, because arm64-v8a and armeabi-v7a are genuinely
     *    different targets for an exploit and the original varies its answer for the same reason.
     */

    fun versionSupportString(): String = runCatching {
        /*
         * The shape of this line is `-[DOEnvironmentManager versionSupportString]`, which on iOS
         * returns strings such as "iOS 15.0 - 18.7.1 (arm64)": a supported span followed by the
         * architecture the answer applies to. This is the same statement for Android.
         *
         * The span is fixed rather than derived from the running device, because it is a claim
         * about what this build targets, not about this device - the earlier version printed the
         * device's own release, which read like a compatibility promise of one exact version.
         *
         * The architecture still comes from the device: "arm64" and "arm" are genuinely different
         * targets for an exploit, and the iOS original varies the string by CPU family for the
         * same reason. The lowest Android the exploits cover is 8.0 (API 26).
         */
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val arch = when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "arm"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") || abi.startsWith("i686") -> "x86"
            abi.isEmpty() -> "unknown"
            else -> abi
        }

        "Android 8.0 - 17.0 ($arch)"
    }.getOrDefault("Android 8.0 - 17.0 (unknown)")

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
        exploit: String? = null,
        removing: Boolean = false,
        onEvent: (StepEvent) -> Unit,
    ) {
        val device = DeviceProfile.read()
        val ctx = ExploitContext(device)
        val chosen = exploit ?: JailbreakState.selectedExploit(context) ?: DEFAULT_EXPLOIT
        val list = steps(context, detailed, chosen, removing)

        onEvent(
            StepEvent.Log(
                "${device.manufacturer} ${device.model} · Android ${device.androidRelease} " +
                    "(API ${device.sdk}) · ${device.abi}",
            ),
        )
        onEvent(StepEvent.Log("kernel ${device.kernel.ifEmpty { "unknown" }}"))
        // The exploit actually in use, on its own line: the step label repeats it, but a run that
        // fails before reaching that step would otherwise leave no record of which one was chosen.
        onEvent(StepEvent.Log("exploit=$chosen · ${list.size} steps · detailed=$detailed"))

        list.forEachIndexed { index, step ->
            onEvent(StepEvent.Started(index, step.label))
            onEvent(StepEvent.Progress(index.toFloat() / list.size))

            // The delay is what makes the log readable; the action itself is instant.
            delay(step.durationMs)

            /*
             * Each step runs off the main thread.
             *
             * This is not tidiness. `suspend` on its own does not move work off the caller's
             * thread, and the steps call ProcessBuilder.start(), readLine(), waitFor() and file
             * I/O - all blocking. Running them on the Compose coroutine's dispatcher (Main) meant
             * the UI thread sat inside those calls while the run was going, and the visible symptom
             * was that the notification shade could not be pulled down: the system's gesture and
             * status-bar handling had no frame to work with. With the work on Dispatchers.IO the
             * run still takes the same time, but the app keeps pumping frames and the shade works.
             */
            val result = withContext(Dispatchers.IO) {
                runCatching { step.action(ctx) }
                    .getOrElse { "error: ${it.message ?: it::class.java.simpleName}" }
            }
            onEvent(StepEvent.Finished(index, step.label, result))
            onEvent(StepEvent.Progress((index + 1).toFloat() / list.size))
        }

        /*
         * A removal has nothing to prove except that the daemon is gone.
         *
         * The jailbreak path reads the daemon back because a step can report a pid that dies a
         * moment later; the removal path has the mirror-image check, and it is the one that
         * matters: if the process is still alive the environment was not removed, whatever the
         * steps said.
         */
        if (removing) {
            val stillUp = withContext(Dispatchers.IO) {
                val pid = JailbreakState.daemonPid(context)
                pid > 0 && Daemon.isAlive(context, pid)
            }
            if (stillUp) {
                onEvent(StepEvent.Done(false, context.getString(R.string.result_daemon_failed)))
            } else {
                JailbreakState.clearEverJailbroken(context)
                onEvent(StepEvent.Done(true, context.getString(R.string.action_jailbreak)))
            }
            return
        }

        // Trust the daemon, not the step: it can report success and die a moment later.
        val alive = withContext(Dispatchers.IO) {
            val pid = JailbreakState.daemonPid(context)
            pid > 0 && Daemon.isAlive(context, pid)
        }
        if (alive) {
            JailbreakState.markEverJailbroken(context)
            onEvent(StepEvent.Done(true, context.getString(R.string.action_jailbroken)))
        } else {
            /*
             * The daemon did not prove itself alive, but the staged files exist and the step that
             * started it reported a pid. Rather than throwing the whole run away - which is what
             * made a first attempt fail while a second one worked - retry once before deciding.
             *
             * A freshly spawned shell loop can miss the very first liveness window, and reporting
             * that as a failed jailbreak is both wrong and confusing: the device is fine a moment
             * later, and the only symptom is that the user has to tap twice.
             */
            val retried = withContext(Dispatchers.IO) {
                delay(700L)
                val pid = JailbreakState.daemonPid(context)
                pid > 0 && Daemon.isAlive(context, pid)
            }
            if (retried) {
                JailbreakState.markEverJailbroken(context)
                onEvent(StepEvent.Done(true, context.getString(R.string.action_jailbroken)))
            } else {
                JailbreakState.clearDaemonPid(context)
                onEvent(StepEvent.Done(false, context.getString(R.string.result_daemon_failed)))
            }
        }
    }

    /**
     * Stops the daemon without touching anything else.
     *
     * Used by a removal run: the daemon is going away, but the staging directory and the
     * preferences are still needed until the run finishes, so this is deliberately not
     * [removeJailbreak].
     */
    fun stopDaemon(context: Context) {
        val pid = JailbreakState.daemonPid(context)
        if (pid > 0) runCatching { Daemon.stop(pid) }
        JailbreakState.clearDaemonPid(context)
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
    // The UI instead shows a black screen for a few seconds and returns to the main screen, which
    // is the observable part of a respring. It deliberately does not close the app: an earlier
    // revision did, and the app disappearing looked exactly like a crash.

    /**
     * "Restart userspace": kill the daemon, wait, bring it back.
     *
     * This one is real. Killing the process and confirming the replacement is alive is exactly
     * what a userspace reboot does to the daemon; the only thing missing is the rest of userspace.
     */
    suspend fun restartUserspace(context: Context): Boolean {
        /*
         * All of this touches processes and files, so it runs on Dispatchers.IO for the same reason
         * the run does: kill/waitFor/start are blocking, and on the main thread they freeze the UI
         * - including the notification shade - for as long as they take.
         */
        val old = JailbreakState.daemonPid(context)
        if (old > 0) withContext(Dispatchers.IO) { Daemon.stop(old) }
        JailbreakState.clearDaemonPid(context)

        // Give the kernel a moment to reap the old process so the liveness check cannot see it.
        delay(600L)

        val pid = withContext(Dispatchers.IO) { Daemon.start(context) } ?: return false
        JailbreakState.setDaemonPid(context, pid)
        delay(400L)
        return withContext(Dispatchers.IO) { Daemon.isAlive(context, pid) }
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