package com.example.hmi

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import com.example.hmi.model.*

class GPathFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var layoutCandidates: LinearLayout
    private lateinit var layoutSafetyDistances: LinearLayout
    private lateinit var tvResultsInfo: TextView
    
    private lateinit var etRobotRadius: EditText
    private lateinit var etRidgeSpacing: EditText
    private lateinit var etHeadlandLen: EditText
    private lateinit var etRidgeYaw: EditText

    private var lastMapPoints: List<Point>? = null

    private val mapDataListener: (MapData) -> Unit = { data ->
        activity?.runOnUiThread {
            updateSafetyDistanceInputs(data.map)
        }
    }

    private val statusListener: (RobotStatus) -> Unit = { status ->
        activity?.runOnUiThread { 
            tvStatus.text = "Coverage: ${if (status.pathSelected == true) "READY" else "NOT SELECTED"}"
            updateGating(status.state)
        }
    }

    private val coveragePathListener: (CoveragePathResult) -> Unit = { result ->
        activity?.runOnUiThread {
            CommandState.lastGeneratedPaths = result.paths
            CommandState.lastResultMsgId = result.msgId
            showCandidates(result.paths)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_gpath, container, false)
        (activity as? MainActivity)?.setupTopBar(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvStatus = view.findViewById(R.id.tvGpathStatus)
        layoutCandidates = view.findViewById(R.id.layoutCandidates)
        layoutSafetyDistances = view.findViewById(R.id.layoutSafetyDistances)
        tvResultsInfo = view.findViewById(R.id.tvResultsInfo)
        
        etRobotRadius = view.findViewById(R.id.etRobotRadius)
        etRidgeSpacing = view.findViewById(R.id.etRidgeSpacing)
        etHeadlandLen = view.findViewById(R.id.etHeadlandLen)
        etRidgeYaw = view.findViewById(R.id.etRidgeYaw)

        view.findViewById<Button>(R.id.btnGenerateCoverage).setOnClickListener { requestCoveragePath() }
        view.findViewById<Button>(R.id.btnResetDefaults).setOnClickListener { resetToDefaults() }

        restoreValues()
        CommandState.lastGeneratedPaths?.let { showCandidates(it) }
        CommandState.lastMapData?.map?.let { updateSafetyDistanceInputs(it) }
    }

    private fun updateGating(state: String) {
        val isGated = state.uppercase() == "ALIGN" || state.uppercase() == "RUN"
        val isEnabled = !isGated
        
        view?.findViewById<Button>(R.id.btnGenerateCoverage)?.isEnabled = isEnabled
        view?.findViewById<Button>(R.id.btnResetDefaults)?.isEnabled = isEnabled
        
        // Gating for dynamic candidate buttons
        for (i in 0 until layoutCandidates.childCount) {
            val card = layoutCandidates.getChildAt(i) as? LinearLayout
            val btn = card?.getChildAt(card.childCount - 1) as? Button
            btn?.isEnabled = isEnabled
        }
    }

    private fun resetToDefaults() {
        CommandState.robotRadius = 1.1
        CommandState.ridgeSpacing = 0.8
        CommandState.headlandLen = 2.0
        CommandState.ridgeYaw = 0.0
        CommandState.lastSafetyDistances.clear()
        restoreValues()
        CommandState.lastMapData?.map?.let { 
            lastMapPoints = null // Force redraw
            updateSafetyDistanceInputs(it) 
        }
        Toast.makeText(requireContext(), "Defaults Restored", Toast.LENGTH_SHORT).show()
    }

    private fun saveValues() {
        CommandState.robotRadius = etRobotRadius.text.toString().toDoubleOrNull() ?: 1.1
        CommandState.ridgeSpacing = etRidgeSpacing.text.toString().toDoubleOrNull() ?: 0.8
        CommandState.headlandLen = etHeadlandLen.text.toString().toDoubleOrNull() ?: 2.0
        CommandState.ridgeYaw = etRidgeYaw.text.toString().toDoubleOrNull() ?: 0.0
        
        lastMapPoints?.let { map ->
            for (i in map.indices) {
                val et = layoutSafetyDistances.findViewWithTag<EditText>("safety_dist_$i")
                et?.text?.toString()?.toDoubleOrNull()?.let { 
                    CommandState.lastSafetyDistances[i] = it 
                }
            }
        }
    }

    private fun restoreValues() {
        etRobotRadius.setText(CommandState.robotRadius.toString())
        etRidgeSpacing.setText(CommandState.ridgeSpacing.toString())
        etHeadlandLen.setText(CommandState.headlandLen.toString())
        etRidgeYaw.setText(CommandState.ridgeYaw.toString())
    }

    private fun updateSafetyDistanceInputs(map: List<Point>?) {
        if (map == null || map.isEmpty()) return
        
        // If map hasn't changed (points and count), don't reset inputs to preserve user focus and values
        if (map == lastMapPoints) return
        lastMapPoints = map
        
        layoutSafetyDistances.removeAllViews()
        for (i in map.indices) {
            val nextIdx = (i + 1) % map.size
            val label = "Edge M${i+1}-M${nextIdx+1} (m)"
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(0, 4, 0, 4)
            }
            val tv = TextView(requireContext()).apply {
                text = label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
            }
            val et = EditText(requireContext()).apply {
                tag = "safety_dist_$i"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                inputType = 8194 // TYPE_CLASS_NUMBER | TYPE_NUMBER_FLAG_DECIMAL
                background = AppCompatResources.getDrawable(context, android.R.drawable.edit_text)
                
                // Restore from cache if available, otherwise default to 0.5
                val cached = CommandState.lastSafetyDistances[i]
                setText(cached?.toString() ?: "0.5")
                
                layoutParams = LinearLayout.LayoutParams(0, -2, 1.0f)
            }
            row.addView(tv); row.addView(et); layoutSafetyDistances.addView(row)
        }
    }

    private fun requestCoveragePath() {
        saveValues()
        val map = CommandState.lastMapData?.map ?: return
        val safetyDistances = map.indices.map { i ->
            layoutSafetyDistances.findViewWithTag<EditText>("safety_dist_$i")?.text?.toString()?.toDoubleOrNull() ?: 0.5
        }

        SocketManager.send(GenerateCoveragePathRequest(
            msgId = SocketManager.generateId(), polygon = map,
            robotRadius = CommandState.robotRadius, ridgeSpacing = CommandState.ridgeSpacing,
            headlandLength = CommandState.headlandLen, edgeSafetyDist = safetyDistances,
            yawDeg = CommandState.ridgeYaw
        ))
        layoutCandidates.visibility = View.GONE; tvResultsInfo.visibility = View.GONE
    }

    private fun showCandidates(paths: List<CoveragePath>) {
        layoutCandidates.removeAllViews()
        tvResultsInfo.visibility = View.VISIBLE
        layoutCandidates.visibility = View.VISIBLE

        val displayMetrics = resources.displayMetrics
        val previewHeight = (300 * displayMetrics.density).toInt()

        paths.forEachIndexed { i, path ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 32) }
                background = AppCompatResources.getDrawable(context, android.R.drawable.dialog_holo_light_frame)
            }

            val title = TextView(requireContext()).apply {
                text = "Path ${i + 1}"; textSize = 16f; setTextColor(context.getColor(R.color.uos_blue))
                setPadding(0, 0, 0, 8)
            }
            
            // Selection Indicator
            val isSelected = CommandState.selectedCoveragePath == path
            if (isSelected) {
                val selectedMark = TextView(requireContext()).apply {
                    text = "✓ SELECTED"; setTextColor(Color.RED); textSize = 14f; setTypeface(null, Typeface.BOLD)
                    gravity = android.view.Gravity.END
                }
                card.addView(selectedMark)
                card.setBackgroundColor(Color.parseColor("#FFF1F1")) // Very light red
            }
            
            val info = TextView(requireContext()).apply {
                val totalLen = path.nRidges * path.workLen
                text = "${path.nRidges} ridges, Work: ${path.workLen.toInt()}m, Total: ${totalLen.toInt()}m"
                textSize = 14f
                setPadding(0, 0, 0, 16)
            }

            val mapPreview = MapView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(-1, previewHeight)
                isZoomMode = false
                val currentMap = CommandState.lastMapData
                setMapData(currentMap?.map?.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } ?: emptyList(),
                           currentMap?.obstacles?.map { o -> o.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } } ?: emptyList(),
                           emptyList())
                setHeadlandCorners(CommandState.lastHeadlandCorners?.map { list -> list.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } } ?: emptyList())
                setSingleCoveragePath(path)
            }

            val btn = Button(requireContext()).apply {
                text = "Select This Path"; setBackgroundColor(context.getColor(R.color.uos_blue)); setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 16, 0, 0) }
                setOnClickListener { selectPath(i) }
            }

            card.addView(title); card.addView(info); card.addView(mapPreview); card.addView(btn)
            layoutCandidates.addView(card)
        }
    }

    private fun selectPath(index: Int) {
        val refId = CommandState.lastResultMsgId ?: ""
        val newMsgId = SocketManager.generateId()
        
        Log.d("GPath", "Selecting path $index with ref_msg_id: $refId")
        
        // Update local selection for UI reflection across screens
        CommandState.selectedCoveragePath = CommandState.lastGeneratedPaths?.getOrNull(index)
        
        // Refresh UI immediately to show the checkmark
        activity?.runOnUiThread {
            CommandState.lastGeneratedPaths?.let { showCandidates(it) }
        }

        SocketManager.send(SelectCoveragePathRequest(
            msgId = newMsgId,
            refMsgId = refId,
            pathIndex = index
        ))
        Toast.makeText(requireContext(), "Path $index selected. Syncing...", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        SocketManager.addRobotStatusListener(statusListener)
        SocketManager.addMapDataListener(mapDataListener)
        SocketManager.addCoveragePathListener(coveragePathListener)
    }

    override fun onPause() {
        super.onPause()
        saveValues()
        SocketManager.removeRobotStatusListener(statusListener)
        SocketManager.removeMapDataListener(mapDataListener)
        SocketManager.removeCoveragePathListener(coveragePathListener)
    }
}
