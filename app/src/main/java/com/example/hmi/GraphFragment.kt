package com.example.hmi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class GraphFragment : Fragment() {

    private lateinit var graphViewLinear: VelocityGraphView
    private lateinit var graphViewAngular: VelocityGraphView
    private var currentTimeRangeMs = 90_000L

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            val history = CommandState.getVelocityHistory()
            graphViewLinear.setData(history, currentTimeRangeMs, VelocityGraphView.GraphType.LINEAR)
            graphViewAngular.setData(history, currentTimeRangeMs, VelocityGraphView.GraphType.ANGULAR)
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)
        (activity as? MainActivity)?.setupTopBar(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        graphViewLinear = view.findViewById(R.id.graphViewLinear)
        graphViewAngular = view.findViewById(R.id.graphViewAngular)
        
        view.findViewById<Button>(R.id.btnRange10).setOnClickListener { currentTimeRangeMs = 10_000L }
        view.findViewById<Button>(R.id.btnRange30).setOnClickListener { currentTimeRangeMs = 30_000L }
        view.findViewById<Button>(R.id.btnRange60).setOnClickListener { currentTimeRangeMs = 60_000L }
        view.findViewById<Button>(R.id.btnRange90).setOnClickListener { currentTimeRangeMs = 90_000L }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }
}
