package com.example.hmi

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.hmi.model.*
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var drawerLayout: DrawerLayout
    
    // Shared UI elements in top bar (updated via listeners)
    private var tvPing: TextView? = null
    
    // Dialog state for real-time updates
    private var mapFullInDialog: MapView? = null
    private var logContainerInDialog: LinearLayout? = null

    private val txRunnable = object : Runnable {
        override fun run() {
            sendCurrentCommand()
            handler.postDelayed(this, Config.TX_PERIOD_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navigationView)

        // Handle WindowInsets (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        // Initialize NSD
        nsdHelperInstance = nsdHelperInstance ?: NsdHelper(this)
        nsdHelperInstance?.startDiscovery { discoveredIp, discoveredPort ->
            runOnUiThread { SocketManager.updateHost(discoveredIp, discoveredPort) }
        }

        SocketManager.start()

        if (savedInstanceState == null) {
            showFragment(ControlFragment())
            navView.setCheckedItem(R.id.nav_control)
        }

        navView.setNavigationItemSelectedListener { item ->
            val f: Fragment = when (item.itemId) {
                R.id.nav_control -> ControlFragment()
                R.id.nav_gpath -> GPathFragment()
                R.id.nav_graph -> GraphFragment()
                else -> ControlFragment()
            }
            showFragment(f)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Global Listeners for shared UI
        SocketManager.setOnPingUpdateListener { ping ->
            runOnUiThread { tvPing?.text = "Ping: ${ping}ms" }
        }
        
        SocketManager.setRobotStatusListener { status ->
            runOnUiThread { mapFullInDialog?.setRobotState(
                tag = status.tagX?.let { x -> status.tagY?.let { y -> MapView.Pt(x.toFloat(), y.toFloat()) } },
                ori = status.tagOri?.toFloat() ?: 0f,
                vel = status.tagVel?.toFloat() ?: 0f,
                history = CommandState.getHistory(),
                hasTag = true
            )}
        }
        
        SocketManager.setMapDataListener { data ->
            runOnUiThread {
                val anchors = data.anchors?.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } ?: emptyList()
                val walls = data.walls?.map { wall -> wall.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } } ?: emptyList()
                mapFullInDialog?.setMapData(anchors, walls, emptyList())
            }
        }

        AppLogger.setListener { logs ->
            runOnUiThread { updateLogDisplay(logs) }
        }

        SocketManager.setFeedbackListener { reason ->
            runOnUiThread {
                when {
                    reason == "MOVE_SUCCESS" -> Toast.makeText(this, "이동 명령 승인됨", Toast.LENGTH_SHORT).show()
                    reason.startsWith("MOVE_FAILED") -> showSimpleDialog("이동 거절", reason.removePrefix("MOVE_FAILED: "))
                    reason.endsWith("RETRY_EXHAUSTED") -> showSimpleDialog("응답 없음", "로봇이 응답하지 않습니다.")
                    else -> showSimpleDialog("알림", reason)
                }
            }
        }

        handler.postDelayed(txRunnable, Config.TX_PERIOD_MS)
    }

    private fun showSimpleDialog(title: String, msg: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun showFragment(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, f).commit()
    }

    private fun sendCurrentCommand() {
        SocketManager.send(ControlRequest(
            swBits = CommandState.requestedSwBits,
            keyBits = CommandState.keyBits,
            speedBits = CommandState.speedBits,
            videoBit = if (CommandState.isVideoOn) 1 else 0,
            safeBit = if (CommandState.isSafeMode) 1 else 0
        ))
    }

    // Global Top Bar Setup for Fragments
    fun setupTopBar(root: View, title: String) {
        root.findViewById<TextView>(R.id.tvHeader)?.text = title
        tvPing = root.findViewById(R.id.tvPing)
        
        root.findViewById<View>(R.id.btnMenu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        root.findViewById<View>(R.id.btnFullMap).setOnClickListener { showFullMapDialog() }
        root.findViewById<View>(R.id.btnLogs).setOnClickListener { showLogsDialog() }
        root.findViewById<View>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
    }

    fun showFullMapDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_full_map, null)
        mapFullInDialog = view.findViewById(R.id.mapViewFull)
        mapFullInDialog?.isZoomMode = false
        
        AlertDialog.Builder(this).setView(view).setOnDismissListener { mapFullInDialog = null }.show()
    }

    fun showLogsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_logs, null)
        logContainerInDialog = view.findViewById(R.id.logContainer)
        updateLogDisplay(AppLogger.getLogs())
        
        AlertDialog.Builder(this).setView(view).setOnDismissListener { logContainerInDialog = null }.show()
    }

    private fun updateLogDisplay(logs: List<String>) {
        val container = logContainerInDialog ?: return
        container.removeAllViews()
        for (log in logs.take(50)) {
            val tv = TextView(this).apply {
                text = log; textSize = 12f; setPadding(0, 4, 0, 4)
                setTextColor(if (log.contains("Error") || log.contains("failed")) Color.RED else Color.BLACK)
            }
            container.addView(tv)
        }
    }

    fun showSettingsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_control_settings, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        
        view.findViewById<Button>(R.id.btnPoweroff).setOnClickListener {
            confirmPoweroff(); dialog.dismiss()
        }
        view.findViewById<SwitchCompat>(R.id.swVideo).apply {
            isChecked = CommandState.isVideoOn
            setOnCheckedChangeListener { _, c -> CommandState.isVideoOn = c }
        }
        view.findViewById<SwitchCompat>(R.id.swSafe).apply {
            isChecked = CommandState.isSafeMode
            setOnCheckedChangeListener { _, c -> CommandState.isSafeMode = c }
        }
        view.findViewById<Button>(R.id.btnSlow).setOnClickListener { CommandState.speedBits = 0b100 }
        view.findViewById<Button>(R.id.btnMedium).setOnClickListener { CommandState.speedBits = 0b010 }
        view.findViewById<Button>(R.id.btnFast).setOnClickListener { CommandState.speedBits = 0b001 }
        
        dialog.show()
    }

    private fun confirmPoweroff() {
        AlertDialog.Builder(this).setTitle("Poweroff").setMessage("Shutdown robot?")
            .setPositiveButton("Yes") { _, _ -> SocketManager.send(PoweroffRequest(msgId = SocketManager.generateId())) }
            .setNegativeButton("No", null).show()
    }

    fun openDrawer() = drawerLayout.openDrawer(GravityCompat.START)

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(txRunnable)
    }

    companion object {
        private var nsdHelperInstance: NsdHelper? = null
    }
}
