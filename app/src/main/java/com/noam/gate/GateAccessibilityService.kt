package com.noam.gate

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app comes to the foreground. When it is one of the guarded apps
 * and there is no valid pass, the gate screen is put in front of it.
 */
class GateAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs

    /** Last app we saw in the foreground, so we can tell when the user leaves one. */
    private var lastPackage: String? = null

    /** Uptime at which each guarded app was last left, used by the reset-on-leave rule. */
    private val leftAt = mutableMapOf<String, Long>()

    /** Uptime of the last gate we opened, per package, to avoid re-gating in a loop. */
    private val gatedAt = mutableMapOf<String, Long>()

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Gate service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        // Keyboards, the notification shade and other overlays sit on top of the app
        // that is really in the foreground; they should not count as leaving it.
        if (isTransientWindow(packageName)) return

        val previous = lastPackage
        if (packageName != previous) {
            if (previous != null) leftAt[previous] = SystemClock.elapsedRealtime()
            lastPackage = packageName
        }

        if (!prefs.isGuarded(packageName)) return

        // Coming back after a real absence means the pass has been used up.
        if (prefs.resetOnLeave && packageName != previous) {
            val away = leftAt[packageName]?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE
            if (away > AWAY_GRACE_MS) prefs.clearPass(packageName)
        }

        if (prefs.hasValidPass(packageName)) return
        if (GateActivity.isShowing) return

        val since = gatedAt[packageName]?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE
        if (since < GATE_COOLDOWN_MS) return

        gatedAt[packageName] = SystemClock.elapsedRealtime()
        startActivity(GateActivity.intentFor(this, packageName))
    }

    override fun onInterrupt() = Unit

    private fun isTransientWindow(packageName: String): Boolean =
        packageName == "com.android.systemui" || packageName.contains("inputmethod")

    /** Go to the home screen and then kill what is left of [packageName]. */
    private fun leaveAndKill(packageName: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        // Android only kills processes that are already in the background, so the
        // home action has to land first.
        handler.postDelayed({
            runCatching {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(packageName)
            }.onFailure { Log.w(TAG, "Could not close $packageName", it) }
        }, KILL_DELAY_MS)
    }

    companion object {
        private const val TAG = "GateService"
        private const val GATE_COOLDOWN_MS = 2_000L
        private const val AWAY_GRACE_MS = 30_000L
        private const val KILL_DELAY_MS = 400L

        @Volatile
        private var instance: GateAccessibilityService? = null

        /**
         * Leave [packageName] and close it. Returns false when the service is not
         * running, so the caller can fall back to simply going home.
         */
        fun closeApp(packageName: String): Boolean {
            val service = instance ?: return false
            service.leaveAndKill(packageName)
            return true
        }

        /** True when the user has switched this service on in Android's settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${GateAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            return splitter.any { it.equals(expected, ignoreCase = true) }
        }

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
