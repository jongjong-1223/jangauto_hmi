package com.example.hmi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class GraphFragment : Fragment() {

    private lateinit var graphView: LatencyGraphView
    private lateinit var tvStatus: TextView
    private val history = mutableListOf<Long>()

    private val pingListener: (Long) -> Unit = { ping ->
        activity?.runOnUiThread { updateGraph(ping) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)
        (activity as? MainActivity)?.setupTopBar(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        graphView = view.findViewById(R.id.graphView)
        tvStatus = view.findViewById(R.id.tvPingStatus)
    }

    private fun updateGraph(ping: Long) {
        if (!isAdded) return
        graphView.addData(ping)
        history.add(ping)
        if (history.size > 100) history.removeAt(0)
        tvStatus.text = "Curr: ${ping}ms | Avg: ${history.average().toInt()}ms | Max: ${history.maxOrNull() ?: 0}ms"
    }

    override fun onResume() {
        super.onResume()
        SocketManager.setOnPingUpdateListener(pingListener)
    }

    override fun onPause() {
        super.onPause()
        SocketManager.setOnPingUpdateListener(null)
    }
}
