package com.example.hmi

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject

class NodeFragment : Fragment() {

    private lateinit var container: LinearLayout
    private lateinit var header: TextView

    private val socketListener = { text: String ->
        if (text.contains("\"nodes\"")) {
            render(text)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        header = view.findViewById(R.id.tvListHeader)
        container = view.findViewById(R.id.listContainer)
    }

    private fun showError(msg: String) {
        header.text = "노드 상태"
        container.removeAllViews()
        container.addView(row(msg, Color.GRAY))
    }

    private fun render(body: String) {
        try {
            val o = JSONObject(body)
            val arr = o.getJSONArray("nodes")
            var aliveCount = 0
            container.removeAllViews()
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i)
                val name = n.getString("name")
                val alive = n.getBoolean("alive")
                if (alive) aliveCount++
                val color = if (alive) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                container.addView(row(name, color))
            }
            header.text = "노드 상태  ($aliveCount/${arr.length()} 살아있음)"
        } catch (e: Exception) {
            showError("파싱 오류: ${e.message}")
        }
    }

    private fun row(title: String, dotColor: Int): View {
        val ll = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 14, 8, 14)
            gravity = Gravity.CENTER_VERTICAL
        }
        ll.addView(TextView(requireContext()).apply {
            text = "●"; setTextColor(dotColor); textSize = 18f; setPadding(0, 0, 24, 0)
        })
        ll.addView(TextView(requireContext()).apply {
            text = title; textSize = 16f; setTypeface(null, Typeface.BOLD)
        })
        return ll
    }

    override fun onResume() {
        super.onResume()
        SocketManager.addListener(socketListener)
    }

    override fun onPause() {
        super.onPause()
        SocketManager.removeListener(socketListener)
    }
}
