package com.example.hmi

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Helper class for Network Service Discovery (mDNS).
 * Handles finding the robot on the local network.
 */
class NsdHelper(context: Context) {
    private val tag = "NsdHelper"

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var onRobotFound: ((String) -> Unit)? = null

    /**
     * Start discovering services of type [Config.SERVICE_TYPE].
     * @param callback Called with the resolved IP address of the first robot found.
     */
    fun startDiscovery(callback: (String) -> Unit) {
        onRobotFound = callback

        // 1. Acquire MulticastLock to ensure we can receive mDNS packets
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("NsdHelperLock").apply {
                setReferenceCounted(true)
                acquire()
            }
        }

        // 2. Define the Discovery Listener
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "Service discovery started")
                AppLogger.log("NSD: Discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}")
                AppLogger.log("NSD: Found service ${service.serviceName}")
                if (service.serviceType == Config.SERVICE_TYPE) {
                    Log.d(tag, "Compatible service found, resolving...")
                    AppLogger.log("NSD: Resolving compatible service...")
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(tag, "Service lost: ${service.serviceName}")
                AppLogger.log("NSD: Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(regType: String) {
                Log.i(tag, "Discovery stopped: $regType")
                AppLogger.log("NSD: Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery failed: Error code: $errorCode")
                AppLogger.log("NSD: Start discovery failed (code $errorCode)")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Stop discovery failed: Error code: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        // 3. Start the actual discovery
        nsdManager.discoverServices(Config.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun resolveService(service: NsdServiceInfo) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Resolve failed: $errorCode")
                AppLogger.log("NSD: Resolve failed (code $errorCode)")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i(tag, "Resolve Succeeded. IP: ${serviceInfo.host.hostAddress}")
                AppLogger.log("NSD: Resolved IP: ${serviceInfo.host.hostAddress}")
                val hostIp = serviceInfo.host.hostAddress
                if (hostIp != null) {
                    onRobotFound?.invoke(hostIp)
                    // We found one, so we can stop discovery to save battery
                    stopDiscovery()
                }
            }
        })
    }

    /** Stop discovery and release resources. */
    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(tag, "Error stopping discovery", e)
            }
            discoveryListener = null
        }

        multicastLock?.let {
            if (it.isHeld) it.release()
            multicastLock = null
        }
        
        onRobotFound = null
    }
}
