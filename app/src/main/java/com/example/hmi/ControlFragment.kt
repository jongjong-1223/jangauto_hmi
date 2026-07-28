package com.example.hmi

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject

class ControlFragment : Fragment() {

    private lateinit var tvCurrentState: TextView
    private lateinit var tvLastJson: TextView
    private lateinit var logContainer: LinearLayout
    private lateinit var etTargetX: EditText
    private lateinit var etTargetY: EditText
    
    private val socketListener = { _: String ->
        syncUiWithRobotState()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_control, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvCurrentState = view.findViewById(R.id.tvCurrentState)
        tvLastJson = view.findViewById(R.id.tvLastJson)
        logContainer = view.findViewById(R.id.logContainer)
        etTargetX = view.findViewById(R.id.etTargetX)
        etTargetY = view.findViewById(R.id.etTargetY)

        // Settings Dialog
        view.findViewById<View>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }

        // Drive mode buttons
        view.findViewById<Button>(R.id.btnStop).setOnClickListener { requestState(CommandState.BIT_STOP) }
        view.findViewById<Button>(R.id.btnKey).setOnClickListener { requestState(CommandState.BIT_KEY) }
        view.findViewById<Button>(R.id.btnCali).setOnClickListener { requestState(CommandState.BIT_CAL) }
        view.findViewById<Button>(R.id.btnAlign).setOnClickListener { requestState(CommandState.BIT_ALIGN) }
        view.findViewById<Button>(R.id.btnRun).setOnClickListener { requestState(CommandState.BIT_RUN) }

        // Target Move
        view.findViewById<Button>(R.id.btnMove).setOnClickListener { sendMoveCommand() }

        // Manual hold buttons
        hold(view.findViewById(R.id.btnFront), 0b1000)
        hold(view.findViewById(R.id.btnBack), 0b0100)
        hold(view.findViewById(R.id.btnLeft), 0b0010)
        hold(view.findViewById(R.id.btnRight), 0b0001)

        syncUiWithRobotState()
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_control_settings, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnPoweroff).setOnClickListener { 
            confirmPoweroff()
            dialog.dismiss()
        }

        // Switches
        dialogView.findViewById<SwitchCompat>(R.id.swVideo).apply {
            isChecked = CommandState.isVideoOn
            setOnCheckedChangeListener { _, c -> CommandState.isVideoOn = c; refreshJson() }
        }
        dialogView.findViewById<SwitchCompat>(R.id.swSafe).apply {
            isChecked = CommandState.isSafeMode
            setOnCheckedChangeListener { _, c -> CommandState.isSafeMode = c; refreshJson() }
        }

        // Speed buttons
        dialogView.findViewById<Button>(R.id.btnSlow).setOnClickListener { CommandState.speedBits = 0b100; refreshJson() }
        dialogView.findViewById<Button>(R.id.btnMedium).setOnClickListener { CommandState.speedBits = 0b010; refreshJson() }
        dialogView.findViewById<Button>(R.id.btnFast).setOnClickListener { CommandState.speedBits = 0b001; refreshJson() }

        dialog.show()
    }

    private fun sendMoveCommand() {
        val x = etTargetX.text.toString().toDoubleOrNull() ?: 0.0
        val y = etTargetY.text.toString().toDoubleOrNull() ?: 0.0
        val json = JSONObject().apply {
            put("command", "move")
            put("x", x)
            put("y", y)
        }
        SocketManager.send(json.toString())
        toast("Move to ($x, $y) sent")
    }

    private fun requestState(bits: Int) {
        CommandState.requestedSwBits = bits
        refreshJson()
    }

    private fun syncUiWithRobotState() {
        if (!isAdded) return
        val mode = CommandState.currentMode
        
        tvCurrentState.text = "State: $mode"
        if (CommandState.inError) {
            tvCurrentState.setTextColor(Color.RED)
            tvCurrentState.text = "State: $mode (ERROR: ${CommandState.errorReason})"
        } else {
            tvCurrentState.setTextColor(Color.BLACK)
        }

        updateButtonHighlight(R.id.btnStop, mode == "STOP")
        updateButtonHighlight(R.id.btnKey, mode == "KEY")
        updateButtonHighlight(R.id.btnCali, mode == "CAL" || mode == "CALI")
        updateButtonHighlight(R.id.btnAlign, mode == "ALIGN")
        updateButtonHighlight(R.id.btnRun, mode == "RUN")

        updateLogDisplay(AppLogger.getLogs())
        refreshJson()
    }

    private fun updateLogDisplay(logs: List<String>) {
        logContainer.removeAllViews()
        for (log in logs.take(50)) { // Show last 50 for performance
            val tv = TextView(requireContext()).apply {
                text = log
                textSize = 12f
                setPadding(0, 4, 0, 4)
                setTextColor(if (log.contains("Error") || log.contains("failed") || log.contains("failed")) Color.RED else Color.BLACK)
            }
            logContainer.addView(tv)
        }
    }

    private fun updateButtonHighlight(id: Int, isActive: Boolean) {
        view?.findViewById<Button>(id)?.apply {
            if (isActive) {
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setTextColor(Color.WHITE)
            } else {
                setBackgroundColor(Color.parseColor("#E0E0E0"))
                setTextColor(Color.BLACK)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SocketManager.addListener(socketListener)
        AppLogger.setListener { logs ->
            activity?.runOnUiThread { updateLogDisplay(logs) }
        }
        SocketManager.setFeedbackListener { reason ->
            activity?.runOnUiThread {
                when {
                    reason == "MOVE_SUCCESS" -> {
                        AppLogger.log("Move: Command accepted by robot")
                        toast("이동 명령이 승인되었습니다")
                    }
                    reason.startsWith("MOVE_FAILED") -> {
                        val msg = reason.removePrefix("MOVE_FAILED: ")
                        AppLogger.log("Move: Command rejected - $msg")
                        AlertDialog.Builder(requireContext())
                            .setTitle("이동 거절됨")
                            .setMessage(msg.ifEmpty { "로봇이 이동 명령을 거절했습니다." })
                            .setPositiveButton("확인", null)
                            .show()
                    }
                    else -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("명령 거절됨")
                            .setMessage(reason)
                            .setPositiveButton("확인", null)
                            .show()
                    }
                }
            }
        }
        syncUiWithRobotState()
    }

    override fun onPause() {
        super.onPause()
        SocketManager.removeListener(socketListener)
        SocketManager.setFeedbackListener(null)
        AppLogger.setListener(null)
    }

    private fun hold(button: Button, active: Int) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { CommandState.keyBits = active; refreshJson(); v.performClick(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { CommandState.keyBits = 0b0000; refreshJson(); true }
                else -> true
            }
        }
    }

    private fun confirmPoweroff() {
        AlertDialog.Builder(requireContext())
            .setTitle("라즈베리파이 종료")
            .setMessage("정말 종료할까요?")
            .setPositiveButton("종료") { _, _ ->
                SocketManager.send("{\"command\": \"poweroff\"}")
                toast("종료 명령 전송됨")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun refreshJson() { tvLastJson.text = "JSON: ${CommandState.makeJson()}" }
    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()
}
