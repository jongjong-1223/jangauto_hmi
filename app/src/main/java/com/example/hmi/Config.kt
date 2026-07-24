package com.example.hmi

/** Central place for the Raspberry Pi endpoints. */
object Config {
    const val HOST = "192.168.4.1"

    // WebSocket (Real-time)
    const val WS_URL = "ws://$HOST:8887"

    const val TX_PERIOD_MS = 500L      // command send loop (Control)
}
