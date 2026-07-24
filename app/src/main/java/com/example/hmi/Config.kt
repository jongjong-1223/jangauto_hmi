package com.example.hmi

/** Central place for the Raspberry Pi endpoints. */
object Config {
    /** The resolved IP address of the robot. Updated via NSD. */
    @Volatile
    var HOST: String = "192.168.4.1" // Default fallback

    /** The service type for mDNS discovery. */
    const val SERVICE_TYPE = "_robot._tcp."

    // WebSocket (Real-time)
    /** Computes the URL dynamically based on the current HOST. */
    val WS_URL: String
        get() = "ws://$HOST:8887"

    const val TX_PERIOD_MS = 500L      // command send loop (Control)
}
