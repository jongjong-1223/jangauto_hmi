package com.example.hmi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.hmi.model.*

class GPathFragment : Fragment() {

    private lateinit var mapFull: MapView
    private lateinit var mapZoom: MapView
    private lateinit var tvStatus: TextView

    private val statusListener: (RobotStatus) -> Unit = { status ->
        activity?.runOnUiThread { updateFromStatus(status) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_gpath, container, false)
        (activity as? MainActivity)?.setupTopBar(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mapFull = view.findViewById(R.id.mapViewFull)
        mapZoom = view.findViewById(R.id.mapViewZoom)
        tvStatus = view.findViewById(R.id.tvGpathStatus)
        mapFull.isZoomMode = false
        mapZoom.isZoomMode = true

        view.findViewById<Button>(R.id.btnGenerate).setOnClickListener { generatePath(view) }
    }

    private fun updateFromStatus(status: RobotStatus) {
        val tx = status.tagX?.toFloat() ?: return
        val ty = status.tagY?.toFloat() ?: return
        val tag = MapView.Pt(tx, ty)
        val h = CommandState.getHistory()
        val o = status.tagOri?.toFloat() ?: 0f
        val v = status.tagVel?.toFloat() ?: 0f
        mapFull.setRobotState(tag, o, v, h, true)
        mapZoom.setRobotState(tag, o, v, h, true)
        tvStatus.text = "Pos: ($tx, $ty) | Ori: ${o}° | Vel: $v m/s"
    }

    private fun generatePath(view: View) {
        fun txt(id: Int) = view.findViewById<EditText>(id).text.toString().trim()
        SocketManager.send(GeneratePathRequest(
            msgId = SocketManager.generateId(), mapMinX = txt(R.id.etMapMinX), mapMaxX = txt(R.id.etMapMaxX),
            mapTopY = txt(R.id.etMapTopY), mapBottomY = txt(R.id.etMapBottomY),
            fieldTopY = txt(R.id.etFieldTopY), fieldBottomY = txt(R.id.etFieldBottomY), cropX = txt(R.id.etCropX)
        ))
        Toast.makeText(requireContext(), "Requested", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        SocketManager.setRobotStatusListener(statusListener)
        SocketManager.setMapDataListener { data ->
            val a = data.anchors?.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } ?: emptyList()
            val w = data.walls?.map { wall -> wall.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } } ?: emptyList()
            mapFull.setMapData(a, w, emptyList()); mapZoom.setMapData(a, w, emptyList())
        }
    }

    override fun onPause() {
        super.onPause()
        SocketManager.setRobotStatusListener(null)
        SocketManager.setMapDataListener(null)
    }
}
