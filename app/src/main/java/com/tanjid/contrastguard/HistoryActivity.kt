package com.tanjid.contrastguard

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tanjid.contrastguard.adapter.HistoryAdapter
import com.tanjid.contrastguard.database.AppDatabase
import com.tanjid.contrastguard.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        binding.btnBackHistory.setOnClickListener { finish() }
        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Delete all scan history records?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.scanHistoryDao().clearHistory() }
                        loadHistory()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val scans = withContext(Dispatchers.IO) { db.scanHistoryDao().getAllScans() }
            if (scans.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                binding.rvHistory.layoutManager = LinearLayoutManager(this@HistoryActivity)
                binding.rvHistory.adapter = HistoryAdapter(scans)
            }
        }
    }
}