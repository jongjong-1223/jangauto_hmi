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

class TopicFragment : Fragment() {

    private lateinit var container: LinearLayout
    private lateinit var header: TextView

    private val socketListener = { text: String ->
        if (text.contains("\"topics\"")) {
            render(text)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        header = view.findViewById(R.id.tvListHeader)
        header.text = "토픽 상태"
        container = view.findViewById(R.id.listContainer)
    }

    private fun showError(msg: String) {
        container.removeAllViews()
        container.addView(row(msg, "", Color.GRAY))
    }

    private fun render(body: String) {
        try {
            val o = JSONObject(body)
            val arr = o.getJSONArray("topics")
            container.removeAllViews()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val name = t.getString("name")
                val ok = t.getBoolean("ok")
                val age = t.optDouble("age", -1.0)
                val info = t.optString("info", "")
                val ageStr = if (age < 0) "-" else "${"%.1f".format(age)}s"
                val color = if (ok) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                val sub = if (ok) "수신중 · ${ageStr} 전 · $info" else "끊김 · $info"
                container.addView(row(name, sub, color))
            }
        } catch (e: Exception) {
            showError("파싱 오류: ${e.message}")
        }
    }

    private fun row(title: String, sub: String, dotColor: Int): View {
        val ll = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 16, 8, 16)
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = TextView(requireContext()).apply {
            text = "●"; setTextColor(dotColor); textSize = 18f
            setPadding(0, 0, 24, 0)
        }
        val textCol = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(requireContext()).apply {
            text = title; textSize = 16f; setTypeface(null, Typeface.BOLD)
        })
        if (sub.isNotEmpty()) textCol.addView(TextView(requireContext()).apply {
            text = sub; textSize = 13f; setTextColor(Color.DKGRAY)
        })
        ll.addView(dot); ll.addView(textCol)
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
