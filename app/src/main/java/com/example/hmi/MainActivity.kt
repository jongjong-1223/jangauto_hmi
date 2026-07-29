package com.example.hmi

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    private lateinit var drawerLayout: DrawerLayout
    private var mapFullInDialog: MapView? = null
    private var logContainerInDialog: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommandState.reset() // Ensure fresh start
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navigationView)

        drawerLayout.setPadding(0, 0, 0, 0)

        // Initialize NSD
        nsdHelperInstance = nsdHelperInstance ?: NsdHelper(this)
        nsdHelperInstance?.startDiscovery { discoveredIp, discoveredPort ->
            runOnUiThread { SocketManager.updateHost(discoveredIp, discoveredPort) }
        }

        SocketManager.start()
        startRobotService()

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

        SocketManager.addRobotStatusListener { status ->
            runOnUiThread { mapFullInDialog?.setRobotState(
                tag = status.tagX?.let { x -> status.tagY?.let { y -> MapView.Pt(x.toFloat(), y.toFloat()) } },
                ori = status.tagOri?.toFloat() ?: 0f,
                vel = status.tagVel?.toFloat() ?: 0f,
                yawRate = status.tagYawRate?.toFloat() ?: 0f,
                history = CommandState.getHistory(),
                hasTag = true
            )}
        }
        
        SocketManager.addMapDataListener { data ->
            runOnUiThread {
                val mapPoints = data.map?.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } ?: emptyList()
                val obstacles = data.obstacles?.map { obs -> obs.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } } ?: emptyList()
                mapFullInDialog?.setMapData(mapPoints, obstacles, emptyList())
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
    }

    private fun startRobotService() {
        val serviceIntent = Intent(this, RobotConnectionService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun showSimpleDialog(title: String, msg: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("확인", null).show()
    }

    private fun showFragment(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, f).commit()
    }

    fun setupTopBar(root: View) {
        val topBar = root.findViewById<View>(R.id.topBar)
        val bottomArea = root.findViewById(R.id.bottomControl) 
            ?: root.findViewById<View>(R.id.gpathContainer)
            ?: root.findViewById<View>(R.id.graphContainer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar?.setPadding(0, systemBars.top, 0, 0)
            bottomArea?.let { view ->
                if (view is com.google.android.material.card.MaterialCardView) {
                    val params = view.layoutParams as ViewGroup.MarginLayoutParams
                    val originalMargin = (12 * resources.displayMetrics.density).toInt()
                    params.setMargins(originalMargin, originalMargin, originalMargin, originalMargin + systemBars.bottom)
                    view.layoutParams = params
                } else {
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
                }
            }
            insets
        }
        
        root.findViewById<View>(R.id.btnMenu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        root.findViewById<View>(R.id.btnFullMap).setOnClickListener { showFullMapDialog() }
        root.findViewById<View>(R.id.btnLogs).setOnClickListener { showLogsDialog() }
        root.findViewById<View>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
    }

    fun showFullMapDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_full_map, null)
        mapFullInDialog = view.findViewById(R.id.mapViewFull)
        mapFullInDialog?.isZoomMode = false
        
        // Immediate apply cached data
        CommandState.lastMapData?.let { data ->
            val pts = data.map?.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } ?: emptyList()
            val obs = data.obstacles?.map { o -> o.map { MapView.Pt(it.x.toFloat(), it.y.toFloat()) } } ?: emptyList()
            mapFullInDialog?.setMapData(pts, obs, emptyList())
        }

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
                val isError = (log.contains("Error", ignoreCase = true) && !log.contains("Error=false", ignoreCase = true)) || 
                             log.contains("failed", ignoreCase = true) ||
                             log.contains("timed out", ignoreCase = true) ||
                             log.contains("exhausted", ignoreCase = true)
                setTextColor(if (isError) Color.RED else Color.BLACK)
            }
            container.addView(tv)
        }
    }

    fun showSettingsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_control_settings, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<Button>(R.id.btnPoweroff).setOnClickListener { confirmPoweroff(); dialog.dismiss() }
        view.findViewById<SwitchCompat>(R.id.swVideo).apply { isChecked = CommandState.isVideoOn; setOnCheckedChangeListener { _, c -> CommandState.isVideoOn = c } }
        view.findViewById<SwitchCompat>(R.id.swSafe).apply { isChecked = CommandState.isSafeMode; setOnCheckedChangeListener { _, c -> CommandState.isSafeMode = c } }
        dialog.show()
    }

    private fun confirmPoweroff() {
        AlertDialog.Builder(this).setTitle("Poweroff").setMessage("Shutdown robot?")
            .setPositiveButton("Yes") { _, _ -> SocketManager.send(PoweroffRequest(msgId = SocketManager.generateId())) }
            .setNegativeButton("No", null).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdHelperInstance?.stopDiscovery()
        nsdHelperInstance = null
        
        val serviceIntent = Intent(this, RobotConnectionService::class.java)
        stopService(serviceIntent)
        CommandState.reset()
    }

    companion object {
        private var nsdHelperInstance: NsdHelper? = null
    }
}
