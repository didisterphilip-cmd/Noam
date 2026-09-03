package com.noam.gate

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * Sends the user to the exact system settings page a permission lives on.
 *
 * Neither permission Gate needs can be granted from inside the app — Android
 * only lets the user turn them on themselves — so the most an app can do is
 * land them on the right screen.
 */
object SettingsNavigator {

    // Undocumented but long-standing extras that let a caller highlight one row
    // on a settings page. Stock Android and most skins honour them; where they
    // are ignored the user simply gets the plain list, which still works.
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"

    /** Accessibility settings, scrolled to Gate's own row where the phone allows it. */
    fun openAccessibilitySettings(context: Context) {
        val component = ComponentName(context, GateAccessibilityService::class.java)
            .flattenToString()
        val args = Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, component) }

        val deepLink = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args)

        start(context, deepLink) {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** The "Display over other apps" toggle for this app specifically. */
    fun openOverlaySettings(context: Context) {
        val direct = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        start(context, direct) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** App info — where "Allow restricted settings" hides on Android 13 and later. */
    fun openAppInfo(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(context, intent) { Intent(Settings.ACTION_SETTINGS) }
    }

    private inline fun start(context: Context, intent: Intent, fallback: () -> Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(fallback()) } }
    }
}
