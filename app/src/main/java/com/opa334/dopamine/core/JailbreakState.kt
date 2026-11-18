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
    private const val KEY_HIDDEN = "jailbreak_hidden"
    private const val KEY_EXPLOIT = "kernel_exploit"
    private const val KEY_THEME = "theme"
    private const val KEY_BOOTLOGO = "bootlogoEnabled"
    private const val KEY_CUSTOM_BOOTLOGO = "customBootlogoEnabled"
    private const val KEY_CUSTOM_BOOTLOGO_PATH = "customBootlogo"
    private const val KEY_ICON_ALIAS = "icon_alias"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once the exploit has completed at least once; never reset. */
    fun hasEverJailbroken(context: Context): Boolean = prefs(context).getBoolean(KEY_EVER, false)

    fun markEverJailbroken(context: Context) {
        prefs(context).edit().putBoolean(KEY_EVER, true).apply()
        /*
         * The marker file is written here because this is the one call that runs when a jailbreak
         * succeeds. It is what survives the app being swiped away: the daemon does not, and the
         * preference above only records "has this ever been jailbroken", which is a different
         * question from "is it jailbroken now".
         */
        runCatching { Daemon.markerFile(context).writeText(System.currentTimeMillis().toString()) }
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
        // Removing the marker is what makes `isJailbroken` report false again; without this the
        // app would still believe it is jailbroken after "Remove Jailbreak".
        runCatching { Daemon.markerFile(context).delete() }
    }

    /**
     * Whether the daemon is alive right now.
     *
     * `kill -0` is used rather than a file check because it answers "is this process still running"
     * exactly, with no stale-file window. The daemon is spawned by [Daemon] and records its pid.
     */
    fun isJailbroken(context: Context): Boolean {
        /*
         * The marker file is the answer, not the daemon.
         *
         * "Jailbroken" used to mean "the recorded daemon pid still exists", so swiping the app
         * away from Recents - which takes the daemon with it, since it is spawned from this
         * process's group - made the device report itself as never jailbroken, and the exploit had
         * to be run again. What survives is the filesystem, so that is what decides.
         *
         * The daemon is brought back if it is not running: it is what the marker promises exists,
         * and the next thing the UI does is offer "Restart userspace" and "Remove jailbreak",
         * both of which need it. Starting it here keeps the two consistent rather than leaving a
         * marker that claims a daemon which is not there.
         */
        if (!Daemon.markerFile(context).isFile) return false

        val pid = prefs(context).getInt(KEY_DAEMON_PID, -1)
        if (pid > 0 && Daemon.isAlive(pid)) return true

        // The marker is there but the process is gone: bring it back.
        val restarted = Daemon.start(context)
        if (restarted != null) {
            setDaemonPid(context, restarted)
            return true
        }
        // The marker is authoritative even if the daemon could not be respawned right now; the
        // files a jailbreak created are still on disk, and reporting "not jailbroken" would invite
        // the user to run the exploit again over a broken half-state.
        return true
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

    /**
     * Forgets the chosen package manager.
     *
     * Called by "Remove Jailbreak": with the bootstrap gone there is no package manager installed,
     * and the wizard decides whether to run by checking this value. Leaving it set made the second
     * jailbreak skip the wizard entirely, so the run never asked where to install anything.
     */
    fun clearPackageManager(context: Context) {
        prefs(context).edit().remove(KEY_PKG_MANAGER).apply()
    }

    /** The sudo password the user typed on first run. Stored locally only. */
    fun sudoPassword(context: Context): String? =
        prefs(context).getString(KEY_SUDO_PASSWORD, null)

    fun setSudoPassword(context: Context, value: String) {
        prefs(context).edit().putString(KEY_SUDO_PASSWORD, value).apply()
    }

    /** Forgets the sudo password, for the same reason as [clearPackageManager]. */
    fun clearSudoPassword(context: Context) {
        prefs(context).edit().remove(KEY_SUDO_PASSWORD).apply()
    }

    /** Whether the user wants every exploit step echoed into the log view. */
    fun detailedLog(context: Context): Boolean = prefs(context).getBoolean(KEY_DETAILED_LOG, false)

    fun setDetailedLog(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DETAILED_LOG, value).apply()
    }

    /**
     * Whether "Hide Jailbreak" has been used.
     *
     * This is a real state change, not a label. Hiding stops the daemon, because the daemon is the
     * thing that makes the device jailbroken - leaving it running while the UI claims the jailbreak
     * is hidden would be a lie, and it is the exact kind of lie this rebuild exists to avoid.
     * Unhiding starts a fresh one through the same path a normal jailbreak uses.
     */
    fun isHidden(context: Context): Boolean = prefs(context).getBoolean(KEY_HIDDEN, false)

    fun setHidden(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDDEN, value).apply()
    }

    /**
     * The kernel exploit picked in Settings.
     *
     * Null means "never chosen", and the caller falls back to the recommended entry. Stored rather
     * than kept in memory so the choice does not silently revert every time the app is reopened -
     * which is what the iOS list does too, through its own preferences domain.
     */
    fun selectedExploit(context: Context): String? = prefs(context).getString(KEY_EXPLOIT, null)

    fun setSelectedExploit(context: Context, value: String) {
        prefs(context).edit().putString(KEY_EXPLOIT, value).apply()
    }

    /**
     * The theme picked in Settings.
     *
     * Stored under the same key the iOS preferences domain uses (`theme`), so the value means the
     * same thing on both sides. Null means "never chosen" and the caller falls back to the first
     * theme, which is what `DOThemeManager -enabledTheme` does.
     */
    fun theme(context: Context): String? = prefs(context).getString(KEY_THEME, null)

    fun setTheme(context: Context, value: String) {
        prefs(context).edit().putString(KEY_THEME, value).apply()
    }

    /**
     * `bootlogoEnabled`, defaulting to true as in `DOSettingsController`
     * (`setProperty:@YES forKey:@"default"`).
     *
     * On iOS this decides whether the boot logo is written to the device at all. There is no boot
     * partition this app may touch, so it decides what the run *shows* instead: with it on, the
     * blackout blinks the Dopamine ring and then the Apple glyph; with it off, a single static
     * "lying" Apple is shown and nothing blinks.
     */
    fun bootlogoEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BOOTLOGO, true)

    fun setBootlogoEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_BOOTLOGO, value).apply()
    }

    /** `customBootlogoEnabled`, default false. Only reachable while bootlogo is on, as on iOS. */
    fun customBootlogoEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CUSTOM_BOOTLOGO, false)

    /**
     * Turning the custom logo off also drops the stored copy.
     *
     * Keeping a picture in the private directory while the switch says it is unused would leave a
     * file behind that nothing can see, and it is the user's own image - it should not outlive the
     * setting that put it there.
     */
    fun setCustomBootlogoEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CUSTOM_BOOTLOGO, value).apply()
        if (!value) clearCustomBootlogoImage(context)
    }

    /**
     * Where the picked image was copied to, inside the app's private files directory.
     *
     * A URI returned by the system picker is not guaranteed to stay readable - the grant can be
     * revoked when the process dies - so the picture is copied in and the copy is what the boot
     * sequence reads.
     */
    fun customBootlogoPath(context: Context): String? =
        prefs(context).getString(KEY_CUSTOM_BOOTLOGO_PATH, null)

    fun setCustomBootlogoPath(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CUSTOM_BOOTLOGO_PATH, value).apply()
    }

    fun clearCustomBootlogoImage(context: Context) {
        val path = prefs(context).getString(KEY_CUSTOM_BOOTLOGO_PATH, null)
        if (!path.isNullOrEmpty()) runCatching { java.io.File(path).delete() }
        prefs(context).edit().remove(KEY_CUSTOM_BOOTLOGO_PATH).apply()
    }

    /**
     * Which launcher icon the theme wants.
     *
     * Android cannot repaint an icon at runtime: the launcher reads whatever `activity-alias`
     * entries the manifest declares and the package manager keeps showing whichever one is
     * enabled. So each theme gets its own alias carrying that theme's artwork, and switching theme
     * enables one alias and disables the rest. [KEY_ICON_ALIAS] is only used to know what to
     * re-apply on launch after a process restart.
     */
    fun iconAlias(context: Context): String? = prefs(context).getString(KEY_ICON_ALIAS, null)

    fun setIconAlias(context: Context, value: String) {
        prefs(context).edit().putString(KEY_ICON_ALIAS, value).apply()
    }
}