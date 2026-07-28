package com.example.hmi

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.hmi.model.*

class ControlFragment : Fragment() {

    private lateinit var tvCurrentState: TextView
    private lateinit var tvRequestedState: TextView
    private lateinit var etTargetX: EditText
    private lateinit var etTargetY: EditText
    private lateinit var mapZoom: MapView
    private lateinit var joystick: JoystickView

    private val statusListener: (RobotStatus) -> Unit = { status ->
        activity?.runOnUiThread { updateFromStatus(status) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_control, container, false)
        (activity as? MainActivity)?.setupTopBar(view, "Control Center")
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvCurrentState = view.findViewById(R.id.tvCurrentState)
        tvRequestedState = view.findViewById(R.id.tvRequestedState)
        etTargetX = view.findViewById(R.id.etTargetX)
        etTargetY = view.findViewById(R.id.etTargetY)
        mapZoom = view.findViewById(R.id.mapViewZoom)
        joystick = view.findViewById(R.id.joystickView)

        mapZoom.isZoomMode = true

        view.findViewById<Button>(R.id.btnStop).setOnClickListener { requestState(CommandState.BIT_STOP) }
        view.findViewById<Button>(R.id.btnKey).setOnClickListener { requestState(CommandState.BIT_KEY) }
        view.findViewById<Button>(R.id.btnCali).setOnClickListener { requestState(CommandState.BIT_CAL) }
        view.findViewById<Button>(R.id.btnAlign).setOnClickListener { requestState(CommandState.BIT_ALIGN) }
        view.findViewById<Button>(R.id.btnRun).setOnClickListener { requestState(CommandState.BIT_RUN) }
        view.findViewById<Button>(R.id.btnMove).setOnClickListener { sendMoveCommand() }

        joystick.onMoveListener = { x, y -> updateKeyBitsFromJoystick(x, y) }
        syncUi()
    }

    private fun updateFromStatus(status: RobotStatus) {
        val tx = status.tagX?.toFloat() ?: return
        val ty = status.tagY?.toFloat() ?: return
        val tag = MapView.Pt(tx, ty)
        mapZoom.setRobotState(tag, status.tagOri?.toFloat() ?: 0f, status.tagVel?.toFloat() ?: 0f, CommandState.getHistory(), true)
        syncUi()
    }

    private fun updateKeyBitsFromJoystick(x: Float, y: Float) {
        val threshold = 0.3f
        var bits = 0b0000
        val ax = Math.abs(x); val ay = Math.abs(y)
        if (ax > threshold || ay > threshold) {
            if (ay >= ax) bits = if (y < 0) 0b1000 else 0b0100
            else bits = if (x < 0) 0b0010 else 0b0001
        }
        CommandState.keyBits = bits
    }

    private fun requestState(bits: Int) {
        CommandState.requestedSwBits = bits
        syncUi()
    }

    private fun sendMoveCommand() {
        val x = etTargetX.text.toString().toDoubleOrNull() ?: 0.0
        val y = etTargetY.text.toString().toDoubleOrNull() ?: 0.0
        SocketManager.send(MoveRequest(msgId = SocketManager.generateId(), x = x, y = y))
    }

    private fun syncUi() {
        if (!isAdded) return
        val mode = CommandState.currentMode
        tvCurrentState.text = "State: $mode"
        tvCurrentState.setTextColor(if (CommandState.inError) Color.RED else Color.BLACK)
        tvRequestedState.text = "Requesting: ${CommandState.bitsToModeName(CommandState.requestedSwBits)}"
        
        val buttons = mapOf(R.id.btnStop to "STOP", R.id.btnKey to "KEY", R.id.btnCali to "CAL", R.id.btnAlign to "ALIGN", R.id.btnRun to "RUN")
        buttons.forEach { (id, name) ->
            view?.findViewById<Button>(id)?.apply {
                val active = (mode == name || (name == "CAL" && mode == "CALI"))
                setBackgroundColor(if (active) Color.parseColor("#4CAF50") else Color.parseColor("#E0E0E0"))
                setTextColor(if (active) Color.WHITE else Color.BLACK)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SocketManager.setRobotStatusListener(statusListener)
        SocketManager.setMapDataListener { data ->
            val anchors = data.anchors?.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } ?: emptyList()
            val walls = data.walls?.map { wall -> wall.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } } ?: emptyList()
            mapZoom.setMapData(anchors, walls, emptyList())
        }
        syncUi()
    }

    override fun onPause() {
        super.onPause()
        SocketManager.setRobotStatusListener(null)
        SocketManager.setMapDataListener(null)
    }
}
