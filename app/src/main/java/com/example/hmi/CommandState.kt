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
    @Volatile var currentMode = "STOP"
    @Volatile var keyBits = 0b0000
    @Volatile var speedBits = 0b010
    @Volatile var isVideoOn = false
    @Volatile var isSafeMode = false

    @Volatile var inError = false
    @Volatile var errorReason = ""

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

    fun bitsToModeName(bits: Int): String = when (bits) {
        BIT_STOP -> "STOP"; BIT_KEY -> "KEY"; BIT_CAL -> "CAL"; BIT_ALIGN -> "ALIGN"; BIT_RUN -> "RUN"
        else -> "UNKNOWN"
    }
}
