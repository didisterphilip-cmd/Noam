package com.noam.gate

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * Passes and the usage record are kept under a single string for both kinds of
 * thing the gate stops: an app package, or a site written as "site:youtube.com".
 * The prefix is what tells the two apart afterwards.
 */
object GateTarget {

    private const val SITE_PREFIX = "site:"

    fun forSite(host: String) = "$SITE_PREFIX$host"

    fun isSite(target: String) = target.startsWith(SITE_PREFIX)

    fun hostOf(target: String) = target.removePrefix(SITE_PREFIX)

    /** What to call this on screen: the host for a site, the app's name for an app. */
    fun labelOf(context: Context, target: String): String =
        if (isSite(target)) hostOf(target) else AppRepository.labelOf(context, target)

    fun iconOf(context: Context, target: String): Drawable? =
        if (isSite(target)) ContextCompat.getDrawable(context, R.drawable.ic_site)
        else AppRepository.iconOf(context, target)
}
