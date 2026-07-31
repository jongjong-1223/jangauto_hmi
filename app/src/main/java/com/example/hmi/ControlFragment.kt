package com.example.hmi

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.hmi.model.*

class ControlFragment : Fragment() {

    private lateinit var tvCurrentState: TextView
    private lateinit var tvRequestedState: TextView
    private lateinit var tvCalibrationStatus: TextView
    private lateinit var tvPathStatus: TextView
    private lateinit var etTargetX: EditText
    private lateinit var etTargetY: EditText
    private lateinit var mapZoom: MapView
    private lateinit var joystick: JoystickView
    private lateinit var speedBarContainer: View
    private lateinit var speedFast: View
    private lateinit var speedMedium: View
    private lateinit var speedSlow: View

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutRunnable = object : Runnable {
        override fun run() {
            checkRequestTimeout()
            handler.postDelayed(this, 500L)
        }
    }

    private val statusListener: (RobotStatus) -> Unit = { status ->
        activity?.runOnUiThread { 
            updateFromStatus(status)
        }
    }

    private val mapDataListener: (MapData) -> Unit = { data ->
        activity?.runOnUiThread {
            val mapPoints = data.map?.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } ?: emptyList()
            val obstacles = data.obstacles?.map { obs -> obs.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } } ?: emptyList()
            mapZoom.setMapData(mapPoints, obstacles, emptyList())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_control, container, false)
        (activity as? MainActivity)?.setupTopBar(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvCurrentState = view.findViewById(R.id.tvCurrentState)
        tvRequestedState = view.findViewById(R.id.tvRequestedState)
        tvCalibrationStatus = view.findViewById(R.id.tvCalibrationStatus)
        tvPathStatus = view.findViewById(R.id.tvPathStatus)
        etTargetX = view.findViewById(R.id.etTargetX)
        etTargetY = view.findViewById(R.id.etTargetY)
        mapZoom = view.findViewById(R.id.mapViewZoom)
        joystick = view.findViewById(R.id.joystickView)
        speedBarContainer = view.findViewById(R.id.speedBarContainer)
        speedFast = view.findViewById(R.id.speedFast)
        speedMedium = view.findViewById(R.id.speedMedium)
        speedSlow = view.findViewById(R.id.speedSlow)

        mapZoom.isZoomMode = true

        view.findViewById<Button>(R.id.btnStop).setOnClickListener { requestState(CommandState.BIT_STOP) }
        view.findViewById<Button>(R.id.btnKey).setOnClickListener { requestState(CommandState.BIT_KEY) }
        view.findViewById<Button>(R.id.btnCali).setOnClickListener { requestState(CommandState.BIT_CAL) }
        view.findViewById<Button>(R.id.btnAlign).setOnClickListener { requestState(CommandState.BIT_ALIGN) }
        view.findViewById<Button>(R.id.btnRun).setOnClickListener { requestState(CommandState.BIT_RUN) }
        view.findViewById<Button>(R.id.btnMove).setOnClickListener { sendMoveCommand() }

        setupSpeedSlider()

        joystick.onMoveListener = { x, y -> updateKeyBitsFromJoystick(x, y) }
        syncUi()
    }

    private fun setupSpeedSlider() {
        speedBarContainer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val height = v.height.toFloat()
                    if (height > 0) {
                        val y = event.y.coerceIn(0f, height)
                        val ratio = y / height
                        
                        val newSpeed = when {
                            ratio < 0.33f -> 0b001 // Fast (Top)
                            ratio < 0.66f -> 0b010 // Medium (Middle)
                            else -> 0b100 // Slow (Bottom)
                        }
                        
                        if (CommandState.speedBits != newSpeed) {
                            CommandState.speedBits = newSpeed
                            syncUi()
                        }
                    }
                }
            }
            true
        }
    }

    private fun updateFromStatus(status: RobotStatus) {
        val tx = status.tagX?.toFloat() ?: return
        val ty = status.tagY?.toFloat() ?: return
        val tag = MapView.Pt(tx, ty)
        mapZoom.setRobotState(
            tag, 
            status.tagOri?.toFloat() ?: 0f, 
            status.tagVel?.toFloat() ?: 0f, 
            status.tagYawRate?.toFloat() ?: 0f,
            CommandState.getHistory(), 
            true
        )
        syncUi()
    }

    private fun updateKeyBitsFromJoystick(x: Float, y: Float) {
        val threshold = 0.3f
        var bits = 0b0000
        val ax = kotlin.math.abs(x)
        val ay = kotlin.math.abs(y)
        if (ax > threshold || ay > threshold) {
            bits = if (ay >= ax) {
                if (y < 0) 0b1000 else 0b0100
            } else {
                if (x < 0) 0b0010 else 0b0001
            }
        }
        CommandState.keyBits = bits
    }

    private fun requestState(bits: Int) {
        if (CommandState.isTransitionAllowed(bits)) {
            CommandState.requestedSwBits = bits
            CommandState.lastRequestTime = System.currentTimeMillis()
            syncUi()
        } else {
            val from = CommandState.currentState
            val to = CommandState.bitsToStateName(bits)
            Toast.makeText(context, "Cannot transition from $from to $to", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMoveCommand() {
        val x = etTargetX.text.toString().toDoubleOrNull() ?: 0.0
        val y = etTargetY.text.toString().toDoubleOrNull() ?: 0.0
        SocketManager.send(MoveRequest(msgId = SocketManager.generateId(), x = x, y = y))
    }

    private fun checkRequestTimeout() {
        if (CommandState.lastRequestTime > 0) {
            val now = System.currentTimeMillis()
            if (now - CommandState.lastRequestTime > 2000L) {
                val currentBits = CommandState.nameToBits(CommandState.currentState)
                if (CommandState.requestedSwBits != currentBits) {
                    AppLogger.log("State request timed out. Syncing to ${CommandState.currentState}")
                    CommandState.requestedSwBits = currentBits
                }
                CommandState.lastRequestTime = 0 
                syncUi()
            }
        }
    }

    private fun syncUi() {
        if (!isAdded) return
        
        val state = CommandState.currentState
        tvCurrentState.text = getString(R.string.state_format, state)
        tvCurrentState.setTextColor(if (CommandState.inError) Color.RED else Color.BLACK)
        tvRequestedState.text = getString(R.string.requesting_format, CommandState.bitsToStateName(CommandState.requestedSwBits))
        
        // Calibration Status
        val isCalOk = CommandState.isCalibrationComplete
        tvCalibrationStatus.text = if (isCalOk) "Calibration: OK" else "Calibration: REQUIRED"
        tvCalibrationStatus.setTextColor(if (isCalOk) Color.parseColor("#2E7D32") else Color.RED)
        
        val isPathOk = CommandState.isPathSelected
        tvPathStatus.text = if (isPathOk) "Coverage: READY" else "Coverage: NOT SELECTED"
        tvPathStatus.setTextColor(if (isPathOk) Color.parseColor("#2E7D32") else Color.RED)

        val btnMove = view?.findViewById<Button>(R.id.btnMove)
        btnMove?.isEnabled = isCalOk
        
        val isPathReady = CommandState.isPathSelected
        val btnRun = view?.findViewById<Button>(R.id.btnRun)
        btnRun?.isEnabled = isPathReady
        
        val buttons = mapOf(R.id.btnStop to "STOP", R.id.btnKey to "KEY", R.id.btnCali to "CAL", R.id.btnAlign to "ALIGN", R.id.btnRun to "RUN")
        buttons.forEach { (id, name) ->
            view?.findViewById<Button>(id)?.apply {
                val active = (state == name || (name == "CAL" && state == "CALI"))
                setBackgroundColor(if (active) Color.parseColor("#4CAF50") else Color.parseColor("#E0E0E0"))
                setTextColor(if (active) Color.WHITE else Color.BLACK)
            }
        }

        val activeColor = Color.parseColor("#005EB8")
        val inactiveColor = Color.parseColor("#F8F9FA")
        speedFast.setBackgroundColor(if (CommandState.speedBits == 0b001) activeColor else inactiveColor)
        speedMedium.setBackgroundColor(if (CommandState.speedBits == 0b010) activeColor else inactiveColor)
        speedSlow.setBackgroundColor(if (CommandState.speedBits == 0b100) activeColor else inactiveColor)
    }

    override fun onResume() {
        super.onResume()
        SocketManager.addRobotStatusListener(statusListener)
        SocketManager.addMapDataListener(mapDataListener)
        
        // Immediate apply cached map
        CommandState.lastMapData?.let { mapDataListener.invoke(it) }

        handler.post(timeoutRunnable)
        syncUi()
    }

    override fun onPause() {
        super.onPause()
        SocketManager.removeRobotStatusListener(statusListener)
        SocketManager.removeMapDataListener(mapDataListener)
        handler.removeCallbacks(timeoutRunnable)
    }
}
