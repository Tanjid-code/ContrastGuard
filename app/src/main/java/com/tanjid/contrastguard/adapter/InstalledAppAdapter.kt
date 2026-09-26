package com.tanjid.contrastguard.adapter

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tanjid.contrastguard.R

class InstalledAppAdapter(
    private val apps: List<ApplicationInfo>,
    private val packageManager: PackageManager,
    private val onScanClick: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<InstalledAppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvInstalledAppName)
        val tvPackage: TextView = view.findViewById(R.id.tvInstalledPackage)
        val btnScan: Button = view.findViewById(R.id.btnScanApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_installed_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.tvName.text = packageManager.getApplicationLabel(app).toString()
        holder.tvPackage.text = app.packageName

        try {
            holder.ivIcon.setImageDrawable(packageManager.getApplicationIcon(app))
        } catch (e: Exception) {
            holder.ivIcon.setImageResource(R.drawable.ic_apps)
        }

        holder.btnScan.setOnClickListener { onScanClick(app) }
    }

    override fun getItemCount() = apps.size
}