package com.opa334.dopamine.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.opa334.dopamine.MainActivity
import com.opa334.dopamine.core.JailbreakState

/**
 * Switches the launcher icon with the theme.
 *
 * Android has no API to repaint an app's icon at runtime - the launcher reads the icon attached to
 * whichever component carries the MAIN/LAUNCHER intent filter, and caches it. The workable
 * mechanism is a set of `activity-alias` entries in the manifest, one per theme, each pointing at
 * [MainActivity] but carrying that theme's artwork and label. Exactly one of them is enabled at a
 * time; enabling another is what changes the icon.
 *
 * Two consequences worth stating, because they are properties of the platform rather than choices
 * made here:
 *
 *  - The change is not always immediate. Most launchers refresh within a frame or two, but some
 *    cache icons until the package is next re-read. Nothing in this app can force that.
 *  - The alias must carry the LAUNCHER category itself, otherwise enabling it would remove the
 *    app from the launcher entirely.
 */
object ThemeIcon {

    /** The alias class name for a theme, matching the `<activity-alias>` entries in the manifest. */
    fun aliasFor(theme: DopamineThemeKey): String =
        "com.opa334.dopamine.Launcher." + theme.key.replaceFirstChar { it.uppercase() }

    /**
     * Enables [theme]'s alias and disables every other one.
     *
     * Disabling the component that currently handles the launch intent while enabling its
     * replacement has to happen in one transaction, or the package manager can be left with no
     * enabled LAUNCHER component for a moment and the launcher drops the shortcut. That is why both
     * halves run inside a single [PackageManager.setComponentEnabledSetting] pair with
     * DONT_KILL_APP - killing the app mid-switch would take the settings screen down with it.
     */
    fun apply(context: Context, theme: DopamineThemeKey): Boolean {
        val pm = context.packageManager
        val target = aliasFor(theme)
        var ok = true

        DopamineThemeKey.all.forEach { item ->
            val name = aliasFor(item)
            val enable = name == target
            val state = if (enable) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val result = runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, name),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
            if (result.isFailure) ok = false
        }

        if (ok) JailbreakState.setIconAlias(context, theme.key)
        return ok
    }

    /**
     * Re-applies whatever alias was stored, on launch.
     *
     * Cheap and idempotent: it exists so that a restore, a reinstall or a package-manager hiccup
     * cannot leave the launcher icon showing a theme the app no longer has selected.
     */
    fun applyStored(context: Context) {
        val stored = JailbreakState.theme(context)
        if (stored == null) return
        val theme = DopamineThemeKey.fromKey(stored)
        if (JailbreakState.iconAlias(context) == theme.key) return
        apply(context, theme)
    }
}