package com.example.hmi

/** Central place for the Raspberry Pi endpoints. */
object Config {
    /** The resolved IP address of the robot. Updated via NSD. */
    @Volatile
    var HOST: String = "192.168.4.1" // Default fallback

    /** The resolved Port of the robot. Updated via NSD. */
    @Volatile
    var PORT: Int = 8887 // Default fallback

    /** The service type for mDNS discovery. */
    const val SERVICE_TYPE = "_robot._tcp."

    /** Delay before attempting to reconnect WebSocket. */
    const val RECONNECT_DELAY_MS = 3000L

    // WebSocket (Real-time)
    /** Computes the URL dynamically based on the current HOST and PORT. */
    val WS_URL: String
        get() = "ws://$HOST:$PORT"

    const val TX_PERIOD_MS = 500L      // command send loop (Control)
}
