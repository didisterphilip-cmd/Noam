package com.noam.gate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.noam.gate.databinding.ItemStatBinding

class StatAdapter(private val items: List<UsageStats>) :
    RecyclerView.Adapter<StatAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemStatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemStatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stats = items[position]
        val context = holder.itemView.context
        val b = holder.binding

        b.name.text = GateTarget.labelOf(context, stats.target)
        b.icon.setImageDrawable(GateTarget.iconOf(context, stats.target))

        if (stats.attempts == 0) {
            b.counts.setText(R.string.stats_untouched)
            b.bar.visibility = View.INVISIBLE
            b.lastUsed.visibility = View.GONE
            return
        }

        b.counts.text = context.getString(
            R.string.stats_counts, stats.attempts, stats.entries, stats.declines
        )

        // One measure: how much of the reaching-for was turned back. The rest of
        // the bar is the track it sits on, not a second series.
        b.bar.visibility = View.VISIBLE
        b.bar.weightSum = stats.attempts.toFloat()
        (b.barFill.layoutParams as LinearLayout.LayoutParams).weight = stats.declines.toFloat()
        b.barFill.requestLayout()

        b.lastUsed.visibility = View.VISIBLE
        b.lastUsed.text = stats.lastEntry
            ?.let { context.getString(R.string.stat_last_used, Ago.format(context, it)) }
            ?: context.getString(R.string.stat_last_never)
    }
}
