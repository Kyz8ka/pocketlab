package com.pocketlab.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class LanDevice(
    val ip: String,
    val hostname: String?,
    val openPorts: List<Int>,
)

object LanScanner {

    private val PORTS = listOf(
        22, 53, 80, 443, 445, 554, 631,
        8008, 8009, 8080, 8443, 9100, 32400, 62078,
    )

    fun localIpv4Address(): String? =
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress

    suspend fun scan(subnetBase: String): List<LanDevice> = coroutineScope {
        val semaphore = Semaphore(48)
        (1..254).map { host ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val ip = "$subnetBase.$host"
                    val ports = openPorts(ip)
                    if (ports.isEmpty()) {
                        null
                    } else {
                        LanDevice(ip, reverseName(ip), ports)
                    }
                }
            }
        }.awaitAll()
            .filterNotNull()
            .sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    private fun openPorts(ip: String): List<Int> =
        PORTS.filter { port -> isOpen(ip, port) }

    private fun isOpen(ip: String, port: Int, timeoutMs: Int = 180): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }

    private suspend fun reverseName(ip: String): String? =
        withTimeoutOrNull(400) {
            withContext(Dispatchers.IO) {
                runCatching { InetAddress.getByName(ip).canonicalHostName }
                    .getOrNull()
                    ?.takeIf { it != ip }
            }
        }
}
