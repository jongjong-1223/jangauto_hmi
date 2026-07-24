package com.example.hmi

import org.json.JSONObject

/**
 * Shared, mutable command state.
 * ControlFragment writes to it; MainActivity's 500 ms loop reads it and POSTs to /to_rasp.
 * Kept as a singleton so the send loop keeps running no matter which tab is visible.
 */
object CommandState {
    @Volatile var swBits = 0b10000     // Stop, Key, Cali, Align, Run
    @Volatile var keyBits = 0b0000     // Front, Back, Left, Right
    @Volatile var speedBits = 0b010    // Slow, Medium, Fast
    @Volatile var isVideoOn = false
    @Volatile var isSafeMode = false

    fun makeJson(): String {
        val json = JSONObject()
        json.put("sw_bits", swBits)
        json.put("key_bits", keyBits)
        json.put("speed_bits", speedBits)
        json.put("video_bit", if (isVideoOn) 1 else 0)
        json.put("safe_bit", if (isSafeMode) 1 else 0)
        return json.toString()
    }
}
