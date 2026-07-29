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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)
        (activity as? MainActivity)?.setupTopBar(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        graphView = view.findViewById(R.id.graphView)
        tvStatus = view.findViewById(R.id.tvPingStatus)
        tvStatus.text = "Latency tracking is currently disabled (Robot Protocol v1.1)"
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}
