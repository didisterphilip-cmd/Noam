package com.noam.gate

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.noam.gate.databinding.ActivityAppPickerBinding

/**
 * The list of apps installed on the phone, with the guarded ones ticked. Shown
 * once on first run and reachable from the main screen at any time afterwards;
 * every tick is saved straight away.
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: AppAdapter

    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        val onboarding = intent.getBooleanExtra(EXTRA_ONBOARDING, false)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(!onboarding)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.intro.visibility = if (onboarding) View.VISIBLE else View.GONE
        binding.doneButton.visibility = if (onboarding) View.VISIBLE else View.GONE
        binding.doneButton.setOnClickListener { finish() }

        adapter = AppAdapter(
            selected = prefs.guardedPackages.toMutableSet(),
            onToggle = { app, guarded -> prefs.setGuarded(app.packageName, guarded) }
        )
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter

        allApps = AppRepository.launchableApps(this)
        adapter.submit(allApps)

        binding.searchInput.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty()
            adapter.submit(
                if (query.isEmpty()) allApps
                else allApps.filter { it.label.contains(query, ignoreCase = true) }
            )
        }
    }

    companion object {
        const val EXTRA_ONBOARDING = "com.noam.gate.extra.ONBOARDING"
    }
}
