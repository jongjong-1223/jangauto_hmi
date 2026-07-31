package com.example.hmi

/**
 * Global shared state for the application.
 * Manages requested modes, robot status, and movement history.
 */
object CommandState {
    const val BIT_STOP  = 0b10000
    const val BIT_KEY   = 0b01000
    const val BIT_CAL   = 0b00100
    const val BIT_ALIGN = 0b00010
    const val BIT_RUN   = 0b00001

    @Volatile var requestedSwBits = BIT_STOP
    @Volatile var currentState = "STOP"
    @Volatile var lastRequestTime = 0L
    @Volatile var keyBits = 0b0000
    @Volatile var speedBits = 0b010
    @Volatile var isVideoOn = false
    @Volatile var isSafeMode = false
    @Volatile var isCalibrationComplete = false
    @Volatile var isPathSelected = false

    // Path Parameters Cache
    @Volatile var robotRadius = 1.1
    @Volatile var ridgeSpacing = 0.8
    @Volatile var headlandLen = 2.0
    @Volatile var ridgeYaw = 0.0
    @Volatile var lastSafetyDistances = mutableMapOf<Int, Double>()

    @Volatile var inError = false
    @Volatile var errorReason = ""

    @Volatile var lastMapData: com.example.hmi.model.MapData? = null
    @Volatile var lastGeneratedPaths: List<com.example.hmi.model.CoveragePath>? = null
    @Volatile var lastResultMsgId: String? = null

    // Shared Movement History
    private val history = mutableListOf<MapView.Pt>()
    private const val MAX_HISTORY = 100

    // Velocity History for Graph (last 90 seconds)
    data class VelocityPoint(val timeMs: Long, val linearVel: Float, val angularVel: Float)
    private val velocityHistory = mutableListOf<VelocityPoint>()
    private const val MAX_GRAPH_TIME_MS = 90_000L

    fun addHistory(pt: MapView.Pt) {
        synchronized(history) {
            history.add(pt)
            if (history.size > MAX_HISTORY) history.removeAt(0)
        }
    }

    fun addVelocityData(linear: Float, angular: Float) {
        val now = System.currentTimeMillis()
        synchronized(velocityHistory) {
            velocityHistory.add(VelocityPoint(now, linear, angular))
            // Remove points older than 90 seconds
            velocityHistory.removeAll { now - it.timeMs > MAX_GRAPH_TIME_MS }
        }
    }

    fun getHistory(): List<MapView.Pt> = synchronized(history) { history.toList() }
    fun clearHistory() = synchronized(history) { history.clear() }

    fun getVelocityHistory(): List<VelocityPoint> = synchronized(velocityHistory) { velocityHistory.toList() }
    fun clearVelocityHistory() = synchronized(velocityHistory) { velocityHistory.clear() }

    /**
     * Resets all shared states to default values.
     */
    fun reset() {
        requestedSwBits = BIT_STOP
        currentState = "STOP"
        lastRequestTime = 0L
        keyBits = 0b0000
        speedBits = 0b010
        isVideoOn = false
        isSafeMode = false
        isCalibrationComplete = false
        isPathSelected = false
        
        // Reset Path Params to Defaults
        robotRadius = 1.1
        ridgeSpacing = 0.8
        headlandLen = 2.0
        ridgeYaw = 0.0
        lastSafetyDistances.clear()

        inError = false
        errorReason = ""
        lastMapData = null
        lastGeneratedPaths = null
        lastResultMsgId = null
        clearHistory()
        clearVelocityHistory()
        AppLogger.clear()
    }

    fun bitsToStateName(bits: Int): String = when (bits) {
        BIT_STOP -> "STOP"; BIT_KEY -> "KEY"; BIT_CAL -> "CAL"; BIT_ALIGN -> "ALIGN"; BIT_RUN -> "RUN"
        else -> "UNKNOWN"
    }

    fun nameToBits(name: String): Int = when (name.uppercase()) {
        "STOP" -> BIT_STOP; "KEY" -> BIT_KEY; "CAL", "CALI" -> BIT_CAL; "ALIGN" -> BIT_ALIGN; "RUN" -> BIT_RUN
        else -> BIT_STOP
    }

    /**
     * Enforces transition rules defined in APP_PROTOCOL_HANDSHAKE.md:
     * - STOP, KEY, CAL can transition between each other freely.
     * - ALIGN can only be requested from STOP, KEY, or CAL.
     * - RUN can only be requested from ALIGN.
     * - Moving "down" (e.g., RUN -> ALIGN or RUN/ALIGN -> STOP/KEY/CAL) is always allowed.
     */
    fun isTransitionAllowed(toBits: Int): Boolean {
        val from = currentState
        return when (toBits) {
            BIT_STOP, BIT_KEY, BIT_CAL -> true // Always allowed (flexible group or down-transition)
            BIT_ALIGN -> from == "STOP" || from == "KEY" || from == "CAL" || from == "CALI" || from == "ALIGN"
            BIT_RUN -> from == "ALIGN" || from == "RUN"
            else -> false
        }
    }
}
