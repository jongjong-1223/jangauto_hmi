package com.example.hmi

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Singleton manager for WebSocket communication.
 * Handles connection, automatic reconnection, and data distribution.
 */
object SocketManager {
    private const val TAG = "SocketManager"
    
    private var localIp: String? = null
    private var localPort: Int? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket needs no timeout
        .eventListener(object : EventListener() {
            override fun connectionAcquired(call: Call, connection: Connection) {
                try {
                    val socket = connection.socket()
                    localIp = socket.localAddress.hostAddress
                    localPort = socket.localPort
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get local socket info", e)
                }
            }
        })
        .build()

    private var webSocket: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isStarted = false

    // Listeners for incoming data
    private val listeners = mutableSetOf<(String) -> Unit>()

    fun addListener(l: (String) -> Unit) { listeners.add(l) }
    fun removeListener(l: (String) -> Unit) { listeners.remove(l) }

    /** Update the target host and port, and reconnect if necessary. */
    fun updateHost(newHost: String, newPort: Int) {
        if (Config.HOST == newHost && Config.PORT == newPort && isStarted && webSocket != null) return
        
        Log.i(TAG, "Updating host to $newHost:$newPort, restarting connection...")
        AppLogger.log("Socket: Updating host to $newHost:$newPort")
        Config.HOST = newHost
        Config.PORT = newPort
        
        if (isStarted) {
            disconnectInternal()
            connect()
        }
    }

    /** Start the connection process. */
    fun start() {
        if (isStarted) return
        isStarted = true
        connect()
    }

    private fun disconnectInternal() {
        webSocket?.close(1000, "Changing host")
        webSocket = null
    }

    private fun connect() {
        val isDefault = Config.HOST == "192.168.4.1" && Config.PORT == 8887
        if (isDefault) {
            Log.d(TAG, "Connecting using default settings...")
            AppLogger.log("Socket: Connecting...")
        } else {
            Log.d(TAG, "Connecting to ${Config.WS_URL}...")
            AppLogger.log("Socket: Connecting to ${Config.WS_URL}")
        }
        
        val request = Request.Builder().url(Config.WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket Connected")
                val localInfo = if (localIp != null) "$localIp:$localPort" else "unknown"
                val remoteInfo = "${Config.HOST}:${Config.PORT}"
                AppLogger.log("Socket: Connected (Local: $localInfo -> Remote: $remoteInfo)")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handler.post {
                    listeners.forEach { it(text) }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                Log.w(TAG, "WebSocket Closing: $reason")
                AppLogger.log("Socket: Closing ($reason)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Error: ${t.message}")
                AppLogger.log("Socket: Connection failed")
                // Auto reconnect after delay from config
                handler.postDelayed({ if (isStarted) connect() }, Config.RECONNECT_DELAY_MS)
            }
        })
    }

    /** Send a JSON string to the robot. */
    fun send(json: String) {
        webSocket?.send(json) ?: Log.e(TAG, "Cannot send, socket not connected")
    }

    /** Stop and close the connection. */
    fun stop() {
        isStarted = false
        webSocket?.close(1000, "App closed")
        webSocket = null
    }
}
