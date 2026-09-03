package com.noam.gate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.noam.gate.databinding.ItemSiteBinding

class SiteAdapter(
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<SiteAdapter.ViewHolder>() {

    private var items: List<String> = emptyList()

    fun submit(sites: List<String>) {
        items = sites
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemSiteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSiteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val host = items[position]
        holder.binding.host.text = host
        holder.binding.remove.setOnClickListener { onRemove(host) }
    }
}
