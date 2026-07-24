package com.example.hmi

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class LogFragment : Fragment() {

    private lateinit var container: LinearLayout
    private lateinit var header: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        header = view.findViewById(R.id.tvListHeader)
        header.text = "System Logs"
        container = view.findViewById(R.id.listContainer)
        
        view.findViewById<View>(R.id.tvListHeader).setOnClickListener {
            AppLogger.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        AppLogger.setListener { logs ->
            activity?.runOnUiThread {
                updateUi(logs)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        AppLogger.setListener(null)
    }

    private fun updateUi(logs: List<String>) {
        if (!isAdded) return
        container.removeAllViews()
        for (log in logs) {
            val tv = TextView(requireContext()).apply {
                text = log
                textSize = 14f
                setPadding(0, 8, 0, 8)
                setTextColor(if (log.contains("Error") || log.contains("failed")) Color.RED else Color.BLACK)
            }
            container.addView(tv)
        }
    }
}
