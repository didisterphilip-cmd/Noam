package com.noam.gate

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.noam.gate.databinding.ActivityStatsBinding

/**
 * The last 24 hours at every gate: what was reached for, what was gone into, and
 * what was turned back — one row per app and per site.
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.statList.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()

        val prefs = Prefs(this)
        val log = UsageLog(this)

        // Everything currently guarded, plus anything the log remembers from
        // before it was taken off the list.
        val targets = prefs.guardedPackages +
            prefs.guardedSites.map { GateTarget.forSite(it) } +
            log.everyTarget()

        val rows = targets
            .map { log.statsFor(it) }
            .filter { it.attempts > 0 || prefs.isGuarded(it.target) || isGuardedSite(prefs, it.target) }
            .sortedWith(compareByDescending<UsageStats> { it.attempts }
                .thenBy { GateTarget.labelOf(this, it.target).lowercase() })

        binding.statList.adapter = StatAdapter(rows)

        binding.totalReached.text = rows.sumOf { it.attempts }.toString()
        binding.totalEntered.text = rows.sumOf { it.entries }.toString()
        binding.totalTurnedBack.text = rows.sumOf { it.declines }.toString()

        val nothingYet = rows.none { it.attempts > 0 }
        binding.emptyText.visibility = if (nothingYet) View.VISIBLE else View.GONE
    }

    private fun isGuardedSite(prefs: Prefs, target: String) =
        GateTarget.isSite(target) && prefs.guardedSites.contains(GateTarget.hostOf(target))
}
