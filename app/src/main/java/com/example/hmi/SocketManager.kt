package com.example.hmi

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.hmi.model.*
import com.google.gson.Gson
import okhttp3.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Advanced Singleton manager for WebSocket communication.
 * Features: Structured Data (Gson), Retry Mechanism, Latency Monitoring, Hybrid Ack.
 */
object SocketManager {
    private const val TAG = "SocketManager"
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    
    @Volatile private var localIp: String? = null
    @Volatile private var localPort: Int? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .eventListener(object : okhttp3.EventListener() {
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
    private var isStarted = false

    // Monitoring
    private var lastPingMs = -1L
    private var onPingUpdateListener: ((Long) -> Unit)? = null

    // State tracking for logs
    private var lastLoggedSwBits = -1
    private var lastLoggedKeyBits = -1

    // Retry Mechanism
    private val pendingRequests = mutableMapOf<String, PendingRequest>()
    private const val MAX_RETRIES = 3
    private const val RETRY_TIMEOUT_MS = 1000L

    data class PendingRequest(
        val msgId: String,
        val json: String,
        val command: String,
        var retryCount: Int = 0,
        val runnable: Runnable
    )

    // Listeners
    private var feedbackListener: ((String) -> Unit)? = null
    private var mapDataListener: ((MapData) -> Unit)? = null
    private var robotStatusListener: ((RobotStatus) -> Unit)? = null

    fun setFeedbackListener(l: ((String) -> Unit)?) { feedbackListener = l }
    fun setOnPingUpdateListener(l: ((Long) -> Unit)?) { onPingUpdateListener = l }
    fun setMapDataListener(l: ((MapData) -> Unit)?) { mapDataListener = l }
    fun setRobotStatusListener(l: ((RobotStatus) -> Unit)?) { robotStatusListener = l }

    fun updateHost(newHost: String, newPort: Int) {
        if (Config.HOST == newHost && Config.PORT == newPort && isStarted && webSocket != null) return
        AppLogger.log("Socket: Updating host to $newHost:$newPort")
        Config.HOST = newHost
        Config.PORT = newPort
        if (isStarted) { disconnectInternal(); connect() }
    }

    fun start() { if (!isStarted) { isStarted = true; connect() } }
    fun stop() { isStarted = false; webSocket?.close(1000, "App closed"); webSocket = null }
    private fun disconnectInternal() { webSocket?.close(1000, "Changing host"); webSocket = null }

    private fun connect() {
        val request = Request.Builder().url(Config.WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val localInfo = if (localIp != null) "$localIp:$localPort" else "unknown"
                AppLogger.log("Socket: Connected (Local: $localInfo -> Remote: ${Config.HOST}:${Config.PORT})")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handler.post {
                    processRobotMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.log("Socket: Closing ($reason)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.log("Socket: Connection failed")
                handler.postDelayed({ if (isStarted) connect() }, Config.RECONNECT_DELAY_MS)
            }
        })
    }

    private fun processRobotMessage(text: String) {
        try {
            val json = org.json.JSONObject(text)

            // Msg ID check for Retry removal
            if (json.has("msg_id")) {
                val msgId = json.getString("msg_id")
                pendingRequests.remove(msgId)?.let {
                    handler.removeCallbacks(it.runnable)
                }
            }

            // 1. Robot Status (Streaming)
            if (json.has("mode") && json.has("in_error")) {
                val status = gson.fromJson(text, RobotStatus::class.java)
                
                // Ping tracking
                status.timestamp?.let { sentTime ->
                    lastPingMs = System.currentTimeMillis() - sentTime
                    onPingUpdateListener?.invoke(lastPingMs)
                }

                // Global History Update
                status.tagX?.let { x -> status.tagY?.let { y ->
                    CommandState.addHistory(MapView.Pt(x.toFloat(), y.toFloat()))
                }}

                if (CommandState.currentMode != status.mode || CommandState.inError != status.inError) {
                    AppLogger.rx("Status Update: Mode=${status.mode}, Error=${status.inError}")
                }
                
                CommandState.currentMode = status.mode
                CommandState.inError = status.inError
                CommandState.errorReason = status.errorReason ?: ""
                
                robotStatusListener?.invoke(status)
            }

            // 2. Control Ack
            if (json.has("requested_mode") && json.has("accepted")) {
                val ack = gson.fromJson(text, ControlAck::class.java)
                AppLogger.rx("Mode Ack: ${ack.currentMode} (Accepted: ${ack.accepted})")
                if (!ack.accepted) feedbackListener?.invoke(ack.reason ?: "Rejected")
            }

            // 3. Map Data (Reliable - Requires App Ack)
            if (json.optString("type") == "map_data") {
                val mapData = gson.fromJson(text, MapData::class.java)
                AppLogger.rx("Map Data Received [ID: ${mapData.msgId}]")
                
                // Send Ack to robot
                mapData.msgId?.let { send(AppAck(msgId = it)) }
                
                mapDataListener?.invoke(mapData)
            }

            // 4. Move Ack
            if (json.optString("type") == "move_ack") {
                val ack = gson.fromJson(text, MoveAck::class.java)
                AppLogger.rx("Move Ack: Accepted=${ack.accepted}")
                feedbackListener?.invoke(if (ack.accepted) "MOVE_SUCCESS" else "MOVE_FAILED: ${ack.reason}")
            }

        } catch (e: Exception) { /* Parsing other data */ }
    }

    /**
     * Sends a structured request to the robot.
     */
    fun send(request: RobotRequest) {
        val json = gson.toJson(request)
        
        when (request) {
            is ControlRequest -> {
                if (request.swBits != lastLoggedSwBits || request.keyBits != lastLoggedKeyBits) {
                    AppLogger.tx("Update: Mode=${CommandState.bitsToModeName(request.swBits)}, Key=${keyToDesc(request.keyBits)}")
                    lastLoggedSwBits = request.swBits
                    lastLoggedKeyBits = request.keyBits
                }
                sendRaw(json)
            }
            is MoveRequest -> {
                AppLogger.tx("Move Request: (${request.x}, ${request.y}) [ID: ${request.msgId}]")
                enqueueRetry(request.msgId, json, "move")
            }
            is PoweroffRequest -> {
                AppLogger.tx("Poweroff Request [ID: ${request.msgId}]")
                enqueueRetry(request.msgId, json, "poweroff")
            }
            is GeneratePathRequest -> {
                AppLogger.tx("GeneratePath Request [ID: ${request.msgId}]")
                enqueueRetry(request.msgId, json, "generate_path")
            }
            is AppAck -> {
                // No retry for Ack itself
                sendRaw(json)
            }
        }
    }

    private fun sendRaw(json: String) {
        webSocket?.send(json) ?: Log.e(TAG, "Socket not connected")
    }

    private fun enqueueRetry(msgId: String, json: String, cmdName: String) {
        val runnable = object : Runnable {
            override fun run() {
                val req = pendingRequests[msgId] ?: return
                if (req.retryCount < MAX_RETRIES) {
                    req.retryCount++
                    AppLogger.tx("Retry ($cmdName) ${req.retryCount}/$MAX_RETRIES")
                    sendRaw(json)
                    handler.postDelayed(this, RETRY_TIMEOUT_MS)
                } else {
                    pendingRequests.remove(msgId)
                    AppLogger.log("Error: No response for $cmdName")
                    feedbackListener?.invoke("${cmdName.uppercase()}_RETRY_EXHAUSTED")
                }
            }
        }
        
        pendingRequests[msgId] = PendingRequest(msgId, json, cmdName, 0, runnable)
        sendRaw(json)
        handler.postDelayed(runnable, RETRY_TIMEOUT_MS)
    }

    fun generateId() = UUID.randomUUID().toString().substring(0, 8)

    private fun keyToDesc(key: Int) = when(key) {
        0b1000 -> "FRONT"; 0b0100 -> "BACK"; 0b0010 -> "LEFT"; 0b0001 -> "RIGHT"; else -> "STOP"
    }
}
