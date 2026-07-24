package com.example.hmi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import org.json.JSONObject

class GPathFragment : Fragment() {

    private lateinit var map: MapView
    private lateinit var tvStatus: TextView

    private val socketListener = { text: String ->
        if (text.contains("\"anchors\"") || text.contains("\"global_path\"")) {
            parseAndDraw(text)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_gpath, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        map = view.findViewById(R.id.mapView)
        tvStatus = view.findViewById(R.id.tvGpathStatus)

        view.findViewById<Button>(R.id.btnGenerate).setOnClickListener {
            generatePath(view)
        }
    }

    private fun txt(view: View, id: Int) =
        view.findViewById<EditText>(id).text.toString().trim()

    private fun generatePath(view: View) {
        val body = JSONObject().apply {
            put("command", "generate_path")
            put("map_min_x", txt(view, R.id.etMapMinX))
            put("map_max_x", txt(view, R.id.etMapMaxX))
            put("map_top_y", txt(view, R.id.etMapTopY))
            put("map_bottom_y", txt(view, R.id.etMapBottomY))
            put("field_top_y", txt(view, R.id.etFieldTopY))
            put("field_bottom_y", txt(view, R.id.etFieldBottomY))
            put("crop_x", txt(view, R.id.etCropX))
        }.toString()

        SocketManager.send(body)
        Toast.makeText(requireContext(), "경로 생성 요청 전송됨", Toast.LENGTH_SHORT).show()
    }

    private fun parseAndDraw(body: String) {
        try {
            val o = JSONObject(body)
            val anchorsArr = o.optJSONArray("anchors")
            val anchors = ArrayList<MapView.Pt>()
            if (anchorsArr != null) for (i in 0 until anchorsArr.length()) {
                val a = anchorsArr.getJSONObject(i)
                anchors.add(MapView.Pt(a.optDouble("x").toFloat(), a.optDouble("y").toFloat()))
            }

            val pathArr = o.optJSONArray("global_path")
            val path = ArrayList<MapView.Pt>()
            if (pathArr != null) for (i in 0 until pathArr.length()) {
                val p = pathArr.getJSONArray(i)
                path.add(MapView.Pt(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()))
            }

            val tagObj = o.optJSONObject("tag")
            val hasTag = o.optInt("stop_flag", 1) == 0 && tagObj != null
            val tag = tagObj?.let { MapView.Pt(it.optDouble("x").toFloat(), it.optDouble("y").toFloat()) }

            map.setData(anchors, path, tag, hasTag)
            val vel = o.optDouble("tag_vel", 0.0)
            val ori = o.optDouble("tag_ori", 0.0)
            tvStatus.text = "waypoints ${path.size} · vel ${"%.2f".format(vel)} · ori ${"%.1f".format(ori)}°"
        } catch (e: Exception) {
            tvStatus.text = "파싱 오류: ${e.message}"
        }
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
