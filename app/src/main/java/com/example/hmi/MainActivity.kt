package com.example.hmi

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val txRunnable = object : Runnable {
        override fun run() {
            sendCurrentCommand()
            handler.postDelayed(this, Config.TX_PERIOD_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SocketManager.start()

        if (savedInstanceState == null) {
            showFragment(ControlFragment())
        }

        findViewById<BottomNavigationView>(R.id.bottomNav)
            .setOnItemSelectedListener { item ->
                val f: Fragment = when (item.itemId) {
                    R.id.nav_control -> ControlFragment()
                    R.id.nav_gpath -> GPathFragment()
                    R.id.nav_topic -> TopicFragment()
                    R.id.nav_node -> NodeFragment()
                    else -> ControlFragment()
                }
                showFragment(f)
                true
            }

        // Command send loop runs at the Activity level so it keeps going on every tab.
        handler.postDelayed(txRunnable, Config.TX_PERIOD_MS)
    }

    private fun showFragment(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, f)
            .commit()
    }

    private fun sendCurrentCommand() {
        SocketManager.send(CommandState.makeJson())
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.stop()
        handler.removeCallbacks(txRunnable)
    }
}
