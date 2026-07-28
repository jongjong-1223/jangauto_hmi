package com.example.hmi

import java.text.SimpleDateFormat
import java.util.*

/**
 * Singleton logger to collect app-wide events and display them in the UI.
 */
object AppLogger {
    private val logs = mutableListOf<String>()
    private var listener: ((List<String>) -> Unit)? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** Add a log message with a timestamp. */
    fun log(msg: String) {
        val timestamp = timeFormat.format(Date())
        val formattedMsg = "[$timestamp] $msg"
        
        synchronized(logs) {
            logs.add(0, formattedMsg) // Newest first
            if (logs.size > 500) logs.removeAt(logs.size - 1) // Keep last 500
        }
        
        listener?.invoke(getLogs())
    }

    fun tx(msg: String) = log("[TX] -> $msg")
    fun rx(msg: String) = log("[RX] <- $msg")

    /** Set a listener to be notified when logs are updated. */
    fun setListener(l: ((List<String>) -> Unit)?) {
        listener = l
        l?.invoke(getLogs())
    }

    /** Get a copy of the current logs. */
    fun getLogs(): List<String> = synchronized(logs) { logs.toList() }

    /** Clear all logs. */
    fun clear() {
        synchronized(logs) { logs.clear() }
        listener?.invoke(emptyList())
    }
}
