package com.noam.gate

import android.content.Context
import android.content.SharedPreferences

/**
 * All persisted state: which apps are guarded and the short-lived passes handed
 * out when the user chooses to continue into one of them.
 */
class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("gate", Context.MODE_PRIVATE)

    var guardedPackages: Set<String>
        get() = prefs.getStringSet(KEY_GUARDED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_GUARDED, value).apply()

    fun isGuarded(packageName: String) = guardedPackages.contains(packageName)

    fun setGuarded(packageName: String, guarded: Boolean) {
        val updated = guardedPackages.toMutableSet()
        if (guarded) updated.add(packageName) else updated.remove(packageName)
        guardedPackages = updated
        if (!guarded) clearPass(packageName)
    }

    /**
     * How long a pass lasts at most, in minutes. It is a ceiling, not the usual
     * case: with [resetOnLeave] on, leaving the app ends the pass much sooner.
     */
    var passMinutes: Int
        get() = prefs.getInt(KEY_PASS_MINUTES, 15)
        set(value) = prefs.edit().putInt(KEY_PASS_MINUTES, value.coerceIn(1, 60)).apply()

    /** Show the gate again once the user has properly left the app. */
    var resetOnLeave: Boolean
        get() = prefs.getBoolean(KEY_RESET_ON_LEAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_RESET_ON_LEAVE, value).apply()

    fun grantPass(packageName: String) {
        val expiry = System.currentTimeMillis() + passMinutes * 60_000L
        prefs.edit().putLong(passKey(packageName), expiry).apply()
    }

    fun hasValidPass(packageName: String): Boolean =
        prefs.getLong(passKey(packageName), 0L) > System.currentTimeMillis()

    fun clearPass(packageName: String) {
        prefs.edit().remove(passKey(packageName)).apply()
    }

    private fun passKey(packageName: String) = "pass_$packageName"

    companion object {
        private const val KEY_GUARDED = "guarded_packages"
        private const val KEY_PASS_MINUTES = "pass_minutes"
        private const val KEY_RESET_ON_LEAVE = "reset_on_leave"
    }
}
