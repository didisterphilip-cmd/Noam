package com.noam.gate

import android.content.Context

/** "2 hours", "3 days" — the elapsed part of a "last used …" line. */
object Ago {

    fun format(context: Context, since: Long): String {
        val minutes = (System.currentTimeMillis() - since) / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        val res = context.resources
        return when {
            minutes < 1L -> context.getString(R.string.ago_moments)
            hours < 1L -> res.getQuantityString(R.plurals.ago_minutes, minutes.toInt(), minutes.toInt())
            days < 1L -> res.getQuantityString(R.plurals.ago_hours, hours.toInt(), hours.toInt())
            else -> res.getQuantityString(R.plurals.ago_days, days.toInt(), days.toInt())
        }
    }
}
