package com.noam.gate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.noam.gate.databinding.ActivityAppPickerBinding

/** The predetermined list: every launchable app, with the guarded ones ticked. */
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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

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
}
