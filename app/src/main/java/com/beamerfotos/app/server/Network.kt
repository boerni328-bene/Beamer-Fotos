package com.beamerfotos.app.server

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.NetworkInterface

object Network {
    /** IPv4-Adresse des Beamers im WLAN (oder LAN), z. B. 192.168.1.23; null ohne Netz. */
    fun address(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active = runCatching {
            cm?.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
        if (active != null) return active
        // Rückfall, z. B. wenn der Beamer selbst einen Hotspot aufspannt.
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
