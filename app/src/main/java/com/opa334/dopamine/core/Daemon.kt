package com.opa334.dopamine.core

import android.content.Context
import java.io.File

/**
 * The background "daemon" whose liveness *is* the jailbreak state.
 *
 * Dopamine on iOS has a real daemon process; the Android build fakes the same contract: a process
 * that outlives the activity and whose presence is what "jailbroken" means. It is a plain shell
 * loop rather than a service, because a service can be killed by the system and then the app would
 * report "not jailbroken" for reasons the user cannot see.
 *
 * The loop writes a heartbeat file every 5 s. [isAlive] checks both the pid and the heartbeat age,
 * so a recycled pid cannot pass as a live daemon.
 */
object Daemon {

    private const val DIR = "dopamine"
    private const val HEARTBEAT = "heartbeat"
    private const val STALE_MS = 20_000L

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun heartbeatFile(context: Context): File = File(dir(context), HEARTBEAT)

    /**
     * Shell candidates, most specific first.
     *
     * This is where "zsh not found" came from: the previous build exec'd `zsh` unconditionally, and
     * Android has no zsh anywhere — it ships `sh` (mksh) and sometimes `bash`. The daemon now picks
     * the first interpreter that actually exists instead of naming one that usually does not.
     */
    private fun shellCandidates(context: Context): List<String> = listOf(
        "/system/bin/sh",
        "/system/bin/bash",
        "/vendor/bin/sh",
        File(context.applicationInfo.nativeLibraryDir, "libsh.so").absolutePath,
    )

    fun resolveShell(context: Context): String? =
        shellCandidates(context).firstOrNull { File(it).canExecute() }

    /**
     * Starts the daemon and returns its pid.
     *
     * The process is detached with `setsid` semantics (nohup + background) so it is not in this
     * app's process group and survives the activity being finished. Its only job is to stay alive
     * and keep the heartbeat fresh.
     */
    fun start(context: Context): Int? {
        val shell = resolveShell(context) ?: return null
        val hb = heartbeatFile(context)
        /*
         * Drop any heartbeat left by a previous run before starting a new one.
         *
         * Otherwise a stale file makes a brand-new daemon look alive before it has done anything,
         * which is the mirror image of the bug above: the first check passes for the wrong reason.
         */
        hb.delete()

        // Timestamps are plain `date +%s`, which every Android `sh` provides; no busybox needed.
        // The first write happens before the sleep so the heartbeat exists as soon as the loop
        // starts, instead of one sleep interval later.
        val script =
            "date +%s > '${hb.absolutePath}'; while true; do sleep 5; date +%s > '${hb.absolutePath}'; done"

        val pid = runCatching {
            val process = ProcessBuilder(shell, "-c", "nohup sh -c \"$script\" >/dev/null 2>&1 & echo \$!")
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readLine().orEmpty().trim()
            // `echo $!` yields the background job's pid; anything else means the shell refused.
            out.toIntOrNull()
        }.getOrNull() ?: return null

        // Prove it is really up before reporting success, so a failed spawn cannot look like root.
        if (!isAlive(pid)) return null
        return pid
    }

    /**
     * Alive means: the pid exists *and* the heartbeat is recent.
     *
     * `kill -0` alone would accept a pid recycled by an unrelated process, and the heartbeat alone
     * would accept a file left behind after a crash — together they are hard to fake.
     */
    fun isAlive(pid: Int): Boolean {
        if (pid <= 0) return false
        // A single /proc lookup, no read and no wait: seconds at most, and it is called from the
        // UI path, so it has to stay free of anything that could block on the filesystem.
        val exists = runCatching {
            File("/proc/$pid").exists()
        }.getOrDefault(false)
        return exists
    }

    fun isAlive(context: Context, pid: Int): Boolean {
        if (!isAlive(pid)) return false
        val hb = heartbeatFile(context)
        /*
         * A missing heartbeat is not proof of death for a process that has just started.
         *
         * The loop writes its first timestamp after it is scheduled, which is not the instant
         * start() returns. Requiring the file immediately meant the first run of a fresh install
         * failed the check a few milliseconds too early: the run reported "daemon did not start",
         * no blink played, and the state was not marked jailbroken. The second attempt succeeded
         * only because the heartbeat from the first (rejected) daemon was by then on disk - which
         * is exactly the "needs two taps" behaviour.
         *
         * The pid is already known to exist here, so a missing heartbeat means "too new to have
         * written one yet", and the answer is to wait rather than to declare failure.
         */
        if (!hb.isFile) return true
        val age = System.currentTimeMillis() - hb.lastModified()
        return age in 0..STALE_MS
    }

    fun stop(pid: Int) {
        if (pid <= 0) return
        runCatching {
            ProcessBuilder("/system/bin/kill", "-9", pid.toString())
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }

    fun clearHeartbeat(context: Context) {
        heartbeatFile(context).delete()
    }

    /**
     * The file that says "this device is jailbroken", independent of any running process.
     *
     * Killing the app from Recents kills the daemon too: the shell loop is a child of this process
     * group, whatever `nohup` suggests, and Android reclaims the group when the task is swiped
     * away. Until now that meant the whole jailbreak state vanished with it, because "jailbroken"
     * was defined as "the daemon pid is in /proc" - so a swipe looked exactly like "never
     * jailbroken", and the exploit had to be run again.
     *
     * What survives a swipe is the filesystem, so that is where the state belongs. The marker is
     * written when the run succeeds and removed by "Remove Jailbreak"; the daemon is then an
     * implementation detail that can be restarted on demand rather than the definition itself.
     */
    fun markerFile(context: Context): File = File(dir(context), "jailbroken")
}