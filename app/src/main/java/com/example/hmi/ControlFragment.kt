package com.example.hmi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import android.widget.Toast

class ControlFragment : Fragment() {

    private lateinit var tvCurrentState: TextView
    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvLastJson: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_control, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvCurrentState = view.findViewById(R.id.tvCurrentState)
        tvCurrentSpeed = view.findViewById(R.id.tvCurrentSpeed)
        tvLastJson = view.findViewById(R.id.tvLastJson)

        // Drive mode
        view.findViewById<Button>(R.id.btnStop).setOnClickListener { CommandState.swBits = 0b10000; setState("STOP") }
        view.findViewById<Button>(R.id.btnKey).setOnClickListener { CommandState.swBits = 0b01000; setState("KEY") }
        view.findViewById<Button>(R.id.btnCali).setOnClickListener { CommandState.swBits = 0b00100; setState("CALI") }
        view.findViewById<Button>(R.id.btnAlign).setOnClickListener { CommandState.swBits = 0b00010; setState("ALIGN") }
        view.findViewById<Button>(R.id.btnRun).setOnClickListener { CommandState.swBits = 0b00001; setState("RUN") }

        // Speed
        view.findViewById<Button>(R.id.btnSlow).setOnClickListener { CommandState.speedBits = 0b100; setSpeed("SLOW") }
        view.findViewById<Button>(R.id.btnMedium).setOnClickListener { CommandState.speedBits = 0b010; setSpeed("MEDIUM") }
        view.findViewById<Button>(R.id.btnFast).setOnClickListener { CommandState.speedBits = 0b001; setSpeed("FAST") }

        // Options
        view.findViewById<SwitchCompat>(R.id.swVideo).setOnCheckedChangeListener { _, c ->
            CommandState.isVideoOn = c; refreshJson()
        }
        view.findViewById<SwitchCompat>(R.id.swSafe).setOnCheckedChangeListener { _, c ->
            CommandState.isSafeMode = c; refreshJson()
        }

        // Manual hold buttons
        hold(view.findViewById(R.id.btnFront), 0b1000)
        hold(view.findViewById(R.id.btnBack), 0b0100)
        hold(view.findViewById(R.id.btnLeft), 0b0010)
        hold(view.findViewById(R.id.btnRight), 0b0001)

        // POWEROFF
        view.findViewById<Button>(R.id.btnPoweroff).setOnClickListener { confirmPoweroff() }

        refreshJson()
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
            .setMessage("정말 종료(sudo poweroff)할까요?")
            .setPositiveButton("종료") { _, _ ->
                SocketManager.send("{\"command\": \"poweroff\"}")
                toast("종료 명령 전송됨")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun setState(s: String) { tvCurrentState.text = "State: $s"; refreshJson() }
    private fun setSpeed(s: String) { tvCurrentSpeed.text = "Speed: $s"; refreshJson() }
    private fun refreshJson() { tvLastJson.text = "JSON: ${CommandState.makeJson()}" }
    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()
}
