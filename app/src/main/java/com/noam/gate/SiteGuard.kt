package com.noam.gate

/**
 * Turning what the user types into a host, spotting browsers, and finding the
 * address bar inside one.
 */
object SiteGuard {

    /**
     * The host of whatever was typed or read from an address bar: "www.YouTube.com/feed"
     * and "https://youtube.com" both come back as "youtube.com". Null if there is no
     * plausible host in it.
     */
    fun hostOf(input: String?): String? {
        var text = input?.trim()?.lowercase() ?: return null
        if (text.isEmpty()) return null

        text = text.substringBefore(' ')            // address bars can show a title too
        text = text.substringAfter("://")
        text = text.substringBefore('/')
        text = text.substringBefore('?')
        text = text.substringBefore('#')
        text = text.substringAfter('@')             // credentials, if any
        text = text.substringBefore(':')            // port
        text = text.removePrefix("www.")

        // A host has a dot and nothing exotic in it. Search terms will not match.
        if (!text.contains('.')) return null
        if (text.any { it !in "abcdefghijklmnopqrstuvwxyz0123456789.-" }) return null
        if (text.startsWith('.') || text.endsWith('.')) return null
        return text
    }

    /** youtube.com guards m.youtube.com and music.youtube.com, but not myyoutube.com. */
    fun matches(host: String, guarded: String): Boolean =
        host == guarded || host.endsWith(".$guarded")

    fun firstMatch(host: String, guarded: Set<String>): String? =
        guarded.firstOrNull { matches(host, it) }

    /**
     * Whether this package is a browser worth reading an address bar from. The
     * explicit list covers the common ones; the name check catches forks.
     */
    fun isBrowser(packageName: String): Boolean =
        packageName in KNOWN_BROWSERS ||
            NAME_HINTS.any { packageName.contains(it, ignoreCase = true) }

    /** Address bar view ids, tried in turn against whichever browser is in front. */
    val URL_BAR_IDS = listOf(
        "url_bar",                          // Chrome and most Chromium forks
        "mozac_browser_toolbar_url_view",   // Firefox and its relatives
        "url_field",                        // Opera
        "location_bar_edit_text",           // Samsung Internet
        "omnibarTextInput"                  // DuckDuckGo
    )

    private val KNOWN_BROWSERS = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.fenix",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "org.torproject.torbrowser"
    )

    private val NAME_HINTS = listOf("browser", "chrome", "firefox", "opera")
}
