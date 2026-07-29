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

    @Volatile var inError = false
    @Volatile var errorReason = ""

    @Volatile var lastMapData: com.example.hmi.model.MapData? = null

    // Shared Movement History
    private val history = mutableListOf<MapView.Pt>()
    private const val MAX_HISTORY = 100

    fun addHistory(pt: MapView.Pt) {
        synchronized(history) {
            history.add(pt)
            if (history.size > MAX_HISTORY) history.removeAt(0)
        }
    }

    fun getHistory(): List<MapView.Pt> = synchronized(history) { history.toList() }
    fun clearHistory() = synchronized(history) { history.clear() }

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
        inError = false
        errorReason = ""
        lastMapData = null
        clearHistory()
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
