package com.idroid.app.network

import android.content.Context
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TcpClient - Resilient TCP connection engine for Idroid companion.
 * Features automated multi-host failover: if primary Wi-Fi IP is blocked by router AP Isolation,
 * it automatically tries USB reverse tunnel (127.0.0.1) and gateway candidates without stranding the user.
 */
class TcpClient(
    private val host: String,
    private val port: Int = 53100,
    private val fallbackHosts: List<String> = emptyList(),
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit,
    private val onError: ((String) -> Unit)? = null,
    private val context: Context? = null
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        /**
         * Reads the Linux kernel ARP cache to find all active IP addresses on current interfaces (e.g. Hotspot clients).
         */
        fun getArpPeers(): List<String> {
            val peers = mutableListOf<String>()
            try {
                val lines = java.io.File("/proc/net/arp").readLines()
                for (line in lines) {
                    val tokens = line.trim().split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val flags = tokens[2]
                        if (flags != "0x0" && ip.contains(".") && !ip.startsWith("127.")) {
                            peers.add(ip)
                        }
                    }
                }
            } catch (_: Exception) {}
            return peers
        }

        /**
         * Builds an ordered list of candidate hosts to try connecting to.
         * Automatically injects:
         * 1. Primary host (e.g. last known IP or user entry)
         * 2. Any connected peers from the kernel ARP table (Hotspot peer, e.g. 10.x or 192.168.43.x)
         * 3. 127.0.0.1 (USB Reverse Tunnel) so USB fallback works seamlessly
         */
        fun buildCandidateHosts(primary: String, fallbacks: List<String> = emptyList(), includeArp: Boolean = true): List<String> {
            val list = mutableListOf<String>()
            val primaryTrimmed = primary.trim()
            if (primaryTrimmed.isNotBlank()) {
                list.add(primaryTrimmed)
            }
            for (alt in fallbacks) {
                val trimmed = alt.trim()
                if (trimmed.isNotBlank() && !list.contains(trimmed)) {
                    list.add(trimmed)
                }
            }
            if (includeArp) {
                val arpPeers = getArpPeers()
                for (peer in arpPeers) {
                    if (!list.contains(peer)) {
                        list.add(peer)
                    }
                }
            }
            // Always include USB loopback reverse tunnel fallback
            if (!list.contains("127.0.0.1")) {
                list.add("127.0.0.1")
            }
            return list
        }

        /**
         * Classifies socket exceptions into actionable, non-confusing diagnostic messages.
         */
        fun classifyConnectionError(errMsg: String, host: String, port: Int, context: Context? = null, phoneIpOverride: String? = null): String {
            val report = NetworkDiagnostics.diagnose(context, host, port, null, errMsg, false, phoneIpOverride)
            return "${report.headline}: ${report.explanation} ${report.actionPrompt}"
        }
    }

    fun connect() {
        if (isRunning.getAndSet(true)) return

        scope.launch {
            var retryDelayMs = 1000L
            val targetHosts = buildCandidateHosts(host, fallbackHosts)

            while (isRunning.get()) {
                var connectedSuccessfully = false
                var lastErr: Exception? = null
                var primaryErr: Exception? = null

                for (targetHost in targetHosts) {
                    if (!isRunning.get()) break
                    try {
                        android.util.Log.i("IdroidTcp", "Connecting to $targetHost:$port...")
                        val newSocket = Socket()
                        // 2000ms timeout per host for fast failover if a host is blocked by AP isolation
                        newSocket.connect(InetSocketAddress(targetHost, port), 2000)
                        newSocket.tcpNoDelay = true
                        newSocket.soTimeout = 30000 // 30s read timeout

                        socket = newSocket
                        writer = PrintWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8), true)
                        reader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))

                        retryDelayMs = 1000L
                        android.util.Log.i("IdroidTcp", "Connected to $targetHost:$port successfully!")
                        connectedSuccessfully = true
                        onConnectionStateChanged(true)
                        startHeartbeat()

                        // Read loop with bounded line buffer to prevent OOM
                        while (isRunning.get() && !newSocket.isClosed) {
                            val r = reader ?: break
                            val line = readLineBounded(r) ?: break
                            if (line.isNotBlank()) {
                                android.util.Log.d("IdroidTcp", "Received: ${line.take(200)}")
                                onMessageReceived(line)
                            }
                        }
                        break // Done with read loop
                    } catch (e: Throwable) {
                        if (e is OutOfMemoryError) {
                            android.util.Log.e("IdroidTcp", "Caught OutOfMemoryError in TCP read loop, recovering safely: ${e.message}")
                            System.gc()
                        }
                        val ex = if (e is Exception) e else Exception(e.message)
                        if (targetHost == host) {
                            primaryErr = ex
                        }
                        lastErr = ex
                        cleanupSocket()
                    }
                }

                if (!connectedSuccessfully) {
                    onConnectionStateChanged(false)
                    // If primary Wi-Fi host encountered an error and loopback 127.0.0.1 failed secondarily with ECONNREFUSED on phone,
                    // prioritize the primary error so we don't display a bogus "Connection refused" to the user!
                    val effectiveErr = if (primaryErr != null && host != "127.0.0.1" && lastErr?.message?.contains("127.0.0.1") == true) {
                        primaryErr
                    } else {
                        lastErr
                    }
                    val rawMsg = effectiveErr?.message ?: "Connection error"
                    android.util.Log.e("IdroidTcp", "Socket exception connecting to candidates $targetHosts: $rawMsg")
                    val friendlyError = classifyConnectionError(rawMsg, host, port, context)
                    onError?.invoke(friendlyError)
                } else {
                    cleanupSocket()
                    onConnectionStateChanged(false)
                }

                if (isRunning.get()) {
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(15000L) // Exponential backoff up to 15s
                }
            }
        }
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isRunning.get() && socket?.isConnected == true) {
                delay(15000)
                send("{\"type\":\"ping\",\"message\":\"ping\"}")
            }
        }
    }

    fun send(jsonMessage: String) {
        scope.launch {
            try {
                if (!jsonMessage.contains("\"mouse_move\"")) {
                    android.util.Log.d("IdroidTcp", "Sending: $jsonMessage")
                }
                writer?.println(jsonMessage)
                writer?.flush()
            } catch (e: Exception) {
                android.util.Log.e("IdroidTcp", "Error sending message: ${e.message}")
            }
        }
    }

    /**
     * Reads a line with an explicit memory ceiling (default 4MB) to safeguard the Android VM
     * against OutOfMemoryError crashes from oversized messages or missing newlines.
     */
    private fun readLineBounded(reader: BufferedReader, maxChars: Int = 4 * 1024 * 1024): String? {
        val sb = StringBuilder(2048)
        var dropped = false
        while (true) {
            val ch = reader.read()
            if (ch == -1) {
                return if (sb.isEmpty() || dropped) null else sb.toString()
            }
            if (ch == '\n'.code) {
                return if (dropped) null else sb.toString()
            }
            if (ch != '\r'.code) {
                if (!dropped) {
                    if (sb.length < maxChars) {
                        sb.append(ch.toChar())
                    } else {
                        android.util.Log.w("IdroidTcp", "TCP line exceeded safe limit of $maxChars chars; dropping remainder to prevent OOM")
                        dropped = true
                        sb.setLength(0)
                    }
                }
            }
        }
    }

    private fun cleanupSocket() {
        try {
            reader?.close()
            writer?.close()
            socket?.close()
        } catch (_: Exception) {}
        reader = null
        writer = null
        socket = null
    }

    fun isConnected(): Boolean {
        return isRunning.get() && socket?.isConnected == true && socket?.isClosed == false
    }

    fun disconnect() {
        isRunning.set(false)
        scope.cancel()
        cleanupSocket()
    }
}
