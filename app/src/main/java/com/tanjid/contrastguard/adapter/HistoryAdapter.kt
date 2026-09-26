package com.tanjid.contrastguard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tanjid.contrastguard.R
import com.tanjid.contrastguard.database.ScanHistoryEntity
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(private val scans: List<ScanHistoryEntity>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewStatus: View = view.findViewById(R.id.viewStatus)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvScanDate: TextView = view.findViewById(R.id.tvScanDate)
        val tvPrediction: TextView = view.findViewById(R.id.tvPredictionBadge)
        val tvConfidence: TextView = view.findViewById(R.id.tvConfidenceBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scan = scans[position]
        val context = holder.itemView.context

        holder.tvAppName.text = scan.appName
        holder.tvConfidence.text = String.format("%.1f%%", scan.confidence)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.tvScanDate.text = dateFormat.format(Date(scan.timestamp))

        val isMalware = scan.prediction == "MALWARE"

        holder.tvPrediction.text = scan.prediction
        holder.tvPrediction.setTextColor(context.getColor(if (isMalware) R.color.danger else R.color.safe))
        holder.tvPrediction.setBackgroundResource(
            if (isMalware) R.drawable.bg_status_danger else R.drawable.bg_status_safe
        )
        holder.viewStatus.setBackgroundColor(
            context.getColor(if (isMalware) R.color.danger else R.color.safe)
        )
    }

    override fun getItemCount() = scans.size
}