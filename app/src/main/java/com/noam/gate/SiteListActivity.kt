package com.noam.gate

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.noam.gate.databinding.ActivitySiteListBinding

/** Websites to stop at, entered as addresses and kept as bare hosts. */
class SiteListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySiteListBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: SiteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySiteListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SiteAdapter { host ->
            prefs.removeSite(host)
            refresh()
        }
        binding.siteList.layoutManager = LinearLayoutManager(this)
        binding.siteList.adapter = adapter

        binding.addButton.setOnClickListener { add() }
        binding.siteInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) add()
            true
        }

        refresh()
    }

    private fun add() {
        val typed = binding.siteInput.text?.toString().orEmpty()
        val host = prefs.addSite(typed)
        if (host == null) {
            binding.siteError.visibility = View.VISIBLE
            return
        }
        binding.siteError.visibility = View.GONE
        binding.siteInput.text?.clear()
        refresh()
    }

    private fun refresh() {
        val sites = prefs.guardedSites.sorted()
        adapter.submit(sites)
        binding.emptyText.visibility = if (sites.isEmpty()) View.VISIBLE else View.GONE
    }
}
