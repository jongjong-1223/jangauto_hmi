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
 * Features: Structured Data (Gson), Retry Mechanism, Multi-Listener, Hybrid Ack.
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
    private var isConnecting = false

    private val reconnectRunnable = Runnable { connect() }

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
    private val mapDataListeners = mutableListOf<(MapData) -> Unit>()
    private val robotStatusListeners = mutableListOf<(RobotStatus) -> Unit>()
    private val coveragePathListeners = mutableListOf<(CoveragePathResult) -> Unit>()

    fun setFeedbackListener(l: ((String) -> Unit)?) { feedbackListener = l }
    
    fun addMapDataListener(l: (MapData) -> Unit) { synchronized(mapDataListeners) { mapDataListeners.add(l) } }
    fun removeMapDataListener(l: (MapData) -> Unit) { synchronized(mapDataListeners) { mapDataListeners.remove(l) } }
    
    fun addRobotStatusListener(l: (RobotStatus) -> Unit) { synchronized(robotStatusListeners) { robotStatusListeners.add(l) } }
    fun removeRobotStatusListener(l: (RobotStatus) -> Unit) { synchronized(robotStatusListeners) { robotStatusListeners.remove(l) } }

    fun addCoveragePathListener(l: (CoveragePathResult) -> Unit) { synchronized(coveragePathListeners) { coveragePathListeners.add(l) } }
    fun removeCoveragePathListener(l: (CoveragePathResult) -> Unit) { synchronized(coveragePathListeners) { coveragePathListeners.remove(l) } }

    fun updateHost(newHost: String, newPort: Int) {
        if (Config.HOST == newHost && Config.PORT == newPort && (webSocket != null || isConnecting)) return
        AppLogger.log("Socket: Updating host to $newHost:$newPort")
        Config.HOST = newHost
        Config.PORT = newPort
        if (isStarted) {
            disconnectInternal()
            handler.removeCallbacks(reconnectRunnable)
            handler.postDelayed(reconnectRunnable, 500) // Small delay to let socket cleanup
        }
    }

    fun start() { 
        if (!isStarted) { 
            isStarted = true
            connect() 
        } 
    }
    
    fun stop() {
        isStarted = false
        handler.removeCallbacks(reconnectRunnable)
        disconnectInternal()
        
        // Clear all pending retries
        pendingRequests.values.forEach { handler.removeCallbacks(it.runnable) }
        pendingRequests.clear()
        
        // Reset logging state
        lastLoggedSwBits = -1
        lastLoggedKeyBits = -1

        synchronized(mapDataListeners) { mapDataListeners.clear() }
        synchronized(robotStatusListeners) { robotStatusListeners.clear() }
    }

    private fun disconnectInternal() { 
        webSocket?.let { 
            it.close(1000, "User logout/Host change")
            AppLogger.log("Socket: Closing current connection")
        }
        webSocket = null
        isConnecting = false
    }

    private fun connect() {
        if (!isStarted || webSocket != null || isConnecting) return
        
        isConnecting = true
        val url = Config.WS_URL
        AppLogger.log("Socket: Connecting to $url")
        
        val request = Request.Builder().url(url).build()
        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnecting = false
                webSocket = ws
                val localInfo = if (localIp != null) "$localIp:$localPort" else "unknown"
                AppLogger.log("Socket: Connected (Local: $localInfo -> Remote: ${Config.HOST}:${Config.PORT})")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handler.post { processRobotMessage(text) }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                AppLogger.log("Socket: Closing ($reason)")
                ws.close(1000, null)
                if (ws === webSocket) {
                    webSocket = null
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                if (ws === webSocket) webSocket = null
                
                // Only log and retry if this failure belongs to the current expected connection
                val errorMsg = t.message ?: "Unknown error"
                if (!errorMsg.contains("Socket closed") && isStarted) {
                    AppLogger.log("Socket: Connection failed - $errorMsg")
                    handler.removeCallbacks(reconnectRunnable)
                    handler.postDelayed(reconnectRunnable, Config.RECONNECT_DELAY_MS)
                }
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
            if (json.has("current_state") && json.has("in_error")) {
                val status = gson.fromJson(text, RobotStatus::class.java)
                
                // Global History Update
                status.tagX?.let { x -> status.tagY?.let { y ->
                    CommandState.addHistory(MapView.Pt(x.toFloat(), y.toFloat()))
                }}

                if (CommandState.currentState != status.state || CommandState.inError != status.inError) {
                    AppLogger.rx("Current State from Robot: ${status.state}, Error=${status.inError}")
                    // Clear velocity history if transitioning to CAL
                    if (status.state.uppercase() == "CAL" || status.state.uppercase() == "CALI") {
                        CommandState.clearVelocityHistory()
                    }
                }

                // Detect Calibration Completion transition
                val wasCalComplete = CommandState.isCalibrationComplete
                val isNowCalComplete = status.calibrationComplete ?: false
                if (!wasCalComplete && isNowCalComplete) {
                    AppLogger.log("Calibration Completed! Resetting graph data.")
                    CommandState.clearVelocityHistory()
                }
                
                CommandState.currentState = status.state
                CommandState.inError = status.inError
                CommandState.errorReason = status.errorReason ?: ""
                CommandState.isCalibrationComplete = isNowCalComplete
                CommandState.isPathSelected = status.pathSelected ?: false
                
                // Add to Velocity History
                status.tagVel?.let { v -> status.tagYawRate?.let { w ->
                    CommandState.addVelocityData(v.toFloat(), w.toFloat())
                }}
                
                synchronized(robotStatusListeners) {
                    robotStatusListeners.forEach { it.invoke(status) }
                }
            }

            // 3. Map Data (Reliable - Requires App Ack)
            if (json.optString("type") == "map_data") {
                val mapData = gson.fromJson(text, MapData::class.java)
                CommandState.lastMapData = mapData

                // Send Ack to robot
                mapData.msgId?.let { send(AppAck(msgId = it)) }
                
                synchronized(mapDataListeners) {
                    mapDataListeners.forEach { it.invoke(mapData) }
                }
            }

            // 4. Coverage Path Result (Reliable - Requires App Ack or Selection)
            if (json.optString("type") == "coverage_path_result") {
                val result = gson.fromJson(text, CoveragePathResult::class.java)
                AppLogger.rx("Coverage Path Result Received [ID: ${result.msgId}]")
                
                CommandState.lastGeneratedPaths = result.paths
                CommandState.lastResultMsgId = result.msgId
                
                // Send Ack to robot
                send(AppAck(msgId = result.msgId))
                
                synchronized(coveragePathListeners) {
                    coveragePathListeners.forEach { it.invoke(result) }
                }
            }

            // 5. Move Ack
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
                    AppLogger.tx("Request State Change: ${CommandState.bitsToStateName(request.swBits)}, Key=${keyToDesc(request.keyBits)}")
                    lastLoggedSwBits = request.swBits
                    lastLoggedKeyBits = request.keyBits
                }
                sendRaw(json)
            }
            is MoveRequest -> {
                AppLogger.tx("Move Request: (${request.x}, ${request.y}) [ID: ${request.msgId}]")
                enqueueRetry(request.msgId, json, "move")
            }
            is GenerateCoveragePathRequest -> {
                AppLogger.tx("Generate Coverage Path [ID: ${request.msgId}]")
                enqueueRetry(request.msgId, json, "gen_cov_path")
            }
            is SelectCoveragePathRequest -> {
                AppLogger.tx("Select Path Index: ${request.pathIndex} [ID: ${request.msgId}]")
                enqueueRetry(request.msgId, json, "select_path")
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
        Log.d(TAG, ">>> Sending: $json")
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
