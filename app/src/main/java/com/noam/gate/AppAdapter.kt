package com.noam.gate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.noam.gate.databinding.ItemAppBinding

class AppAdapter(
    private val selected: MutableSet<String>,
    private val onToggle: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private var items: List<AppInfo> = emptyList()

    fun submit(apps: List<AppInfo>) {
        items = apps
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = items[position]
        holder.binding.label.text = app.label
        holder.binding.icon.setImageDrawable(app.icon)
        holder.binding.checkbox.isChecked = selected.contains(app.packageName)
        holder.binding.root.setOnClickListener {
            val guarded = !selected.contains(app.packageName)
            if (guarded) selected.add(app.packageName) else selected.remove(app.packageName)
            holder.binding.checkbox.isChecked = guarded
            onToggle(app, guarded)
        }
    }
}
