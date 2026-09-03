package com.noam.gate

import android.content.Context
import android.util.Log
import org.json.JSONArray

data class Psalm(val chapter: Int, val reference: String, val text: String)

/**
 * The 150 chapters of Tehillim, from the Westminster Leningrad Codex, bundled as
 * an asset. Only the short ones are ever shown — see [MAX_CHARS].
 */
object PsalmRepository {

    /**
     * Longest chapter the gate will show, counted with the vowel points removed.
     * At 250 this is ten chapters; Psalm 117 alone is under 100.
     */
    const val MAX_CHARS = 250

    private const val ASSET = "psalms_he.json"
    private const val TAG = "PsalmRepository"

    @Volatile
    private var shortChapters: List<Psalm>? = null

    fun random(context: Context): Psalm? = load(context).randomOrNull()

    /** Every chapter inside the length limit, read from the asset once. */
    private fun load(context: Context): List<Psalm> {
        shortChapters?.let { return it }

        val parsed = try {
            val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            (0 until array.length())
                .map { array.getJSONObject(it) }
                .filter { it.getInt("len") < MAX_CHARS }
                .map { Psalm(it.getInt("c"), it.getString("ref"), it.getString("text")) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read $ASSET", e)
            emptyList()
        }

        shortChapters = parsed
        return parsed
    }
}
