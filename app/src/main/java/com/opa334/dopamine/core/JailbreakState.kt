package com.opa334.dopamine.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent jailbreak state.
 *
 * Two separate questions have to be answered differently:
 *
 *  - **Has this install ever been jailbroken?** That is a one-way flag. It stays true after "Remove
 *    Jailbreak" so the UI keeps its history, and it is what decides whether the first-run wizard
 *    (package manager + sudo password) has to be shown.
 *  - **Is the device jailbroken right now?** That is not a flag at all: it is whether the daemon is
 *    alive. Storing it would let the two disagree, which is the bug where the UI says "jailbroken"
 *    after the daemon has died.
 *
 * A theme switch used to be required to see a fresh result because the state was cached in a
 * remember{} and nothing re-read it. Here every screen derives its state from [isJailbroken], which
 * asks the daemon, and callers refresh on resume and after every action.
 */
object JailbreakState {

    private const val PREFS = "dopamine"
    private const val KEY_EVER = "ever_jailbroken"
    private const val KEY_PKG_MANAGER = "pkg_manager"
    private const val KEY_SUDO_PASSWORD = "sudo_password"
    private const val KEY_DETAILED_LOG = "detailed_log"
    private const val KEY_DAEMON_PID = "daemon_pid"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once the exploit has completed at least once; never reset. */
    fun hasEverJailbroken(context: Context): Boolean = prefs(context).getBoolean(KEY_EVER, false)

    fun markEverJailbroken(context: Context) {
        prefs(context).edit().putBoolean(KEY_EVER, true).apply()
    }

    /**
     * Reset after "Remove Jailbreak".
     *
     * The flag is cleared so the next jailbreak asks for the package manager and sudo password
     * again — those describe the environment that was just torn down, and asking again is the
     * honest behaviour rather than silently reusing stale choices.
     */
    fun clearEverJailbroken(context: Context) {
        prefs(context).edit().putBoolean(KEY_EVER, false).apply()
    }

    /**
     * Whether the daemon is alive right now.
     *
     * `kill -0` is used rather than a file check because it answers "is this process still running"
     * exactly, with no stale-file window. The daemon is spawned by [Daemon] and records its pid.
     */
    fun isJailbroken(context: Context): Boolean {
        val pid = prefs(context).getInt(KEY_DAEMON_PID, -1)
        if (pid <= 0) return false
        return Daemon.isAlive(pid)
    }

    fun daemonPid(context: Context): Int = prefs(context).getInt(KEY_DAEMON_PID, -1)

    fun setDaemonPid(context: Context, pid: Int) {
        prefs(context).edit().putInt(KEY_DAEMON_PID, pid).apply()
    }

    fun clearDaemonPid(context: Context) {
        prefs(context).edit().remove(KEY_DAEMON_PID).apply()
    }

    /** The package manager chosen on first run: "sileo", "zebra" or "installer". */
    fun packageManager(context: Context): String? =
        prefs(context).getString(KEY_PKG_MANAGER, null)

    fun setPackageManager(context: Context, value: String) {
        prefs(context).edit().putString(KEY_PKG_MANAGER, value).apply()
    }

    /** The sudo password the user typed on first run. Stored locally only. */
    fun sudoPassword(context: Context): String? =
        prefs(context).getString(KEY_SUDO_PASSWORD, null)

    fun setSudoPassword(context: Context, value: String) {
        prefs(context).edit().putString(KEY_SUDO_PASSWORD, value).apply()
    }

    /** Whether the user wants every exploit step echoed into the log view. */
    fun detailedLog(context: Context): Boolean = prefs(context).getBoolean(KEY_DETAILED_LOG, false)

    fun setDetailedLog(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DETAILED_LOG, value).apply()
    }
}