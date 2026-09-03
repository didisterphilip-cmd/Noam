package com.noam.gate

import android.content.Context
import android.content.SharedPreferences

/**
 * All persisted state: which apps are guarded, whether the first-run setup has
 * been done, and the passes handed out when the user chooses to continue.
 *
 * A pass has no clock on it. Once it is granted the gate stays out of the way for
 * as long as the user is in that app; the service revokes it only after they have
 * properly left (see [GateAccessibilityService]).
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

    /** False until the user has been through the pick-your-apps screen once. */
    var setupDone: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_DONE, value).apply()

    /**
     * Set once the first-run walkthrough has finished — either because both
     * permissions are on, or because the user chose not to grant one. After this
     * the app stops prompting and leaves the buttons on the main screen to it.
     */
    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    fun grantPass(packageName: String) {
        prefs.edit().putBoolean(passKey(packageName), true).apply()
    }

    fun hasPass(packageName: String): Boolean = prefs.getBoolean(passKey(packageName), false)

    fun clearPass(packageName: String) {
        prefs.edit().remove(passKey(packageName)).apply()
    }

    private fun passKey(packageName: String) = "pass_$packageName"

    companion object {
        private const val KEY_GUARDED = "guarded_packages"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
