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

    /** Last host seen in a browser address bar, and when it was last read. */
    private var lastHost: String? = null
    private var lastUrlRead = 0L

    /** Uptime at which each guarded app was last left, used to decide when a pass is spent. */
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
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        if (SiteGuard.isBrowser(packageName)) checkBrowser(packageName)

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Keyboards, the notification shade and other overlays sit on top of the app
        // that is really in the foreground; they should not count as leaving it.
        if (isTransientWindow(packageName)) return

        val previous = lastPackage
        if (packageName != previous) {
            if (previous != null) leftAt[previous] = SystemClock.elapsedRealtime()
            lastPackage = packageName
        }

        if (!prefs.isGuarded(packageName)) return

        // A pass lasts for as long as the user stays in the app. Coming back after
        // a real absence is a new visit, so the pass is spent.
        if (packageName != previous) {
            val away = leftAt[packageName]?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE
            if (away > AWAY_GRACE_MS) prefs.clearPass(packageName)
        }

        if (prefs.hasPass(packageName)) return
        if (GateActivity.isShowing) return

        val since = gatedAt[packageName]?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE
        if (since < GATE_COOLDOWN_MS) return

        gatedAt[packageName] = SystemClock.elapsedRealtime()
        startActivity(GateActivity.intentFor(this, packageName))
    }

    override fun onInterrupt() = Unit

    /**
     * Read the address bar of the browser in front and gate the page if its host is
     * on the list.
     *
     * Nothing is read at all while no sites are guarded: the window content is only
     * ever touched once the user has asked for a site to be watched.
     */
    private fun checkBrowser(browserPackage: String) {
        val guarded = prefs.guardedSites
        if (guarded.isEmpty()) return
        if (GateActivity.isShowing) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastUrlRead < URL_READ_INTERVAL_MS) return
        lastUrlRead = now

        val host = SiteGuard.hostOf(readAddressBar(browserPackage)) ?: return
        if (host != lastHost) {
            lastHost?.let { leftAt[it] = now }
            lastHost = host
        }

        val match = SiteGuard.firstMatch(host, guarded) ?: return

        // Coming back to the site after a real absence is a new visit.
        val away = leftAt[host]?.let { now - it } ?: Long.MAX_VALUE
        if (away > AWAY_GRACE_MS) prefs.clearPass(match)
        if (prefs.hasPass(match)) return

        val since = gatedAt[match]?.let { now - it } ?: Long.MAX_VALUE
        if (since < GATE_COOLDOWN_MS) return

        gatedAt[match] = now
        startActivity(GateActivity.intentFor(this, browserPackage, site = match))
    }

    /** The text in the browser's address bar, or null if it could not be found. */
    private fun readAddressBar(browserPackage: String): String? {
        val root = rootInActiveWindow ?: return null
        try {
            for (id in SiteGuard.URL_BAR_IDS) {
                val nodes = root.findAccessibilityNodeInfosByViewId("$browserPackage:id/$id")
                if (nodes.isNullOrEmpty()) continue
                try {
                    return nodes.firstNotNullOfOrNull { it.text?.toString() }
                } finally {
                    nodes.forEach { it.recycle() }
                }
            }
            return null
        } finally {
            root.recycle()
        }
    }

    private fun isTransientWindow(packageName: String): Boolean =
        packageName == "com.android.systemui" || packageName.contains("inputmethod")

    /**
     * Step back off the page and leave the browser. The browser itself is left
     * running: killing it would take every other tab with it.
     */
    private fun leaveSite() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, BACK_DELAY_MS)
    }

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
        private const val BACK_DELAY_MS = 250L
        private const val URL_READ_INTERVAL_MS = 400L

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

        /** Back out of the page and leave the browser, without closing it. */
        fun closeSite(): Boolean {
            val service = instance ?: return false
            service.leaveSite()
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
