package com.tanjid.contrastguard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tanjid.contrastguard.R
import com.tanjid.contrastguard.model.RiskIndicator

class RiskAdapter(private val risks: List<RiskIndicator>) :
    RecyclerView.Adapter<RiskAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewSeverity: View = view.findViewById(R.id.viewSeverity)
        val tvTitle: TextView = view.findViewById(R.id.tvRiskTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvRiskDescription)
        val tvBadge: TextView = view.findViewById(R.id.tvSeverityBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_risk, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val risk = risks[position]
        val context = holder.itemView.context

        holder.tvTitle.text = risk.title
        holder.tvDescription.text = risk.description
        holder.tvBadge.text = risk.severity.uppercase()

        val (color, bg) = when (risk.severity) {
            "critical" -> Pair(R.color.critical, R.drawable.bg_risk_critical)
            "high" -> Pair(R.color.danger, R.drawable.bg_risk_high)
            "medium" -> Pair(R.color.warning, R.drawable.bg_risk_medium)
            else -> Pair(R.color.info, R.drawable.bg_risk_low)
        }

        holder.viewSeverity.setBackgroundColor(context.getColor(color))
        holder.tvBadge.setTextColor(context.getColor(color))
        holder.tvBadge.setBackgroundResource(bg)
    }

    override fun getItemCount() = risks.size
}