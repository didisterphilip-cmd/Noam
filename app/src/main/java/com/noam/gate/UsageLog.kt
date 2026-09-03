package com.noam.gate

import android.content.Context
import android.content.SharedPreferences

/**
 * A rolling 24-hour record of what happened at the gate, per app: every time it
 * appeared, every time the user turned back, and when they last went in.
 *
 * Timestamps are kept as a comma-separated list and pruned to the window on every
 * read and write, so the file cannot grow without bound.
 */
class UsageLog(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("gate_usage", Context.MODE_PRIVATE)

    /** The gate appeared: the user reached for the app. */
    fun recordAttempt(packageName: String) = append(KEY_ATTEMPTS, packageName)

    /** The user pressed the big button and did not go in. */
    fun recordDecline(packageName: String) = append(KEY_DECLINES, packageName)

    /** The user continued into the app. */
    fun recordEntry(packageName: String) {
        prefs.edit().putLong(key(KEY_LAST_ENTRY, packageName), System.currentTimeMillis()).apply()
    }

    fun attemptsInWindow(packageName: String) = timestamps(KEY_ATTEMPTS, packageName).size

    fun declinesInWindow(packageName: String) = timestamps(KEY_DECLINES, packageName).size

    /** When the user last went in, or null if they never have. */
    fun lastEntry(packageName: String): Long? =
        prefs.getLong(key(KEY_LAST_ENTRY, packageName), 0L).takeIf { it > 0L }

    private fun append(prefix: String, packageName: String) {
        val kept = timestamps(prefix, packageName) + System.currentTimeMillis()
        prefs.edit()
            .putString(key(prefix, packageName), kept.takeLast(MAX_ENTRIES).joinToString(","))
            .apply()
    }

    /** Everything inside the window, oldest first. */
    private fun timestamps(prefix: String, packageName: String): List<Long> {
        val raw = prefs.getString(key(prefix, packageName), null) ?: return emptyList()
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        return raw.split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { it >= cutoff }
    }

    private fun key(prefix: String, packageName: String) = "$prefix$packageName"

    companion object {
        const val WINDOW_MS = 24 * 60 * 60 * 1000L

        private const val KEY_ATTEMPTS = "attempts_"
        private const val KEY_DECLINES = "declines_"
        private const val KEY_LAST_ENTRY = "last_entry_"
        private const val MAX_ENTRIES = 200
    }
}
