package com.example.hmi

import org.json.JSONObject

/**
 * Shared, mutable command state.
 * ControlFragment writes to requestedSwBits; MainActivity's 500 ms loop reads it.
 * currentMode and error states are updated by SocketManager based on robot feedback.
 */
object CommandState {
    // Bitmask constants for outgoing requests
    const val BIT_STOP  = 0b10000
    const val BIT_KEY   = 0b01000
    const val BIT_CAL   = 0b00100
    const val BIT_ALIGN = 0b00010
    const val BIT_RUN   = 0b00001

    // State requested by the user
    @Volatile var requestedSwBits = BIT_STOP
    
    // Actual state confirmed by the robot (String representation from protocol)
    @Volatile var currentMode = "STOP"
    
    @Volatile var keyBits = 0b0000     // Front, Back, Left, Right
    @Volatile var speedBits = 0b010    // Slow, Medium, Fast
    @Volatile var isVideoOn = false
    @Volatile var isSafeMode = false

    // Robot health/error status
    @Volatile var inError = false
    @Volatile var errorReason = ""

    /**
     * Converts a bitmask to its string mode name for UI/logic.
     */
    fun bitsToModeName(bits: Int): String = when (bits) {
        BIT_STOP  -> "STOP"
        BIT_KEY   -> "KEY"
        BIT_CAL   -> "CAL"
        BIT_ALIGN -> "ALIGN"
        BIT_RUN   -> "RUN"
        else      -> "UNKNOWN"
    }

    /**
     * Converts a mode name from the robot to our bitmask.
     */
    fun modeNameToBits(name: String): Int = when (name.uppercase()) {
        "STOP"  -> BIT_STOP
        "KEY"   -> BIT_KEY
        "CAL", "CALI" -> BIT_CAL
        "ALIGN" -> BIT_ALIGN
        "RUN"   -> BIT_RUN
        else    -> BIT_STOP
    }

    fun makeJson(): String {
        val json = JSONObject()
        json.put("sw_bits", requestedSwBits)
        json.put("key_bits", keyBits)
        json.put("speed_bits", speedBits)
        json.put("video_bit", if (isVideoOn) 1 else 0)
        json.put("safe_bit", if (isSafeMode) 1 else 0)
        return json.toString()
    }
}
