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

    /**
     * Hosts to stop at, stored bare: "youtube.com" guards every page on it and on
     * its subdomains. Empty means the service never looks at a browser at all.
     */
    var guardedSites: Set<String>
        get() = prefs.getStringSet(KEY_SITES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_SITES, value).apply()

    /** Returns the host that was added, or null if there was no host in the text. */
    fun addSite(typed: String): String? {
        val host = SiteGuard.hostOf(typed) ?: return null
        guardedSites = guardedSites + host
        return host
    }

    fun removeSite(host: String) {
        guardedSites = guardedSites - host
        clearPass(GateTarget.forSite(host))
    }

    /** Which task the gate asks for. */
    var taskType: TaskType
        get() = TaskType.from(prefs.getString(KEY_TASK, null))
        set(value) = prefs.edit().putString(KEY_TASK, value.key).apply()

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
        private const val KEY_SITES = "guarded_sites"
        private const val KEY_TASK = "task_type"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
