package com.idroid.app.network

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TcpClient(
    private val host: String,
    private val port: Int = 53100,
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit,
    private val onError: ((String) -> Unit)? = null
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        if (isRunning.getAndSet(true)) return

        scope.launch {
            var retryDelayMs = 1000L
            val targetHosts = listOf(host)
            while (isRunning.get()) {
                var connectedSuccessfully = false
                var lastErr: Exception? = null

                for (targetHost in targetHosts) {
                    if (!isRunning.get()) break
                    try {
                        android.util.Log.i("IdroidTcp", "Connecting to $targetHost:$port...")
                        val newSocket = Socket()
                        newSocket.connect(InetSocketAddress(targetHost, port), 2000)
                        newSocket.tcpNoDelay = true
                        newSocket.soTimeout = 30000 // 30s timeout

                        socket = newSocket
                        writer = PrintWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8), true)
                        reader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))

                        retryDelayMs = 1000L
                        android.util.Log.i("IdroidTcp", "Connected to $targetHost:$port successfully!")
                        connectedSuccessfully = true
                        onConnectionStateChanged(true)
                        startHeartbeat()

                        // Read loop
                        while (isRunning.get() && !newSocket.isClosed) {
                            val line = reader?.readLine() ?: break
                            if (line.isNotBlank()) {
                                android.util.Log.d("IdroidTcp", "Received: $line")
                                onMessageReceived(line)
                            }
                        }
                        break // Done with read loop
                    } catch (e: Exception) {
                        lastErr = e
                        cleanupSocket()
                    }
                }

                if (!connectedSuccessfully) {
                    onConnectionStateChanged(false)
                    val errMsg = lastErr?.message ?: "Connection error"
                    android.util.Log.e("IdroidTcp", "Socket exception connecting to $host:$port: $errMsg")
                    val friendlyError = if (errMsg.contains("EHOSTUNREACH", ignoreCase = true) || errMsg.contains("No route to host", ignoreCase = true)) {
                        "Router AP Isolation is blocking $host. Connect Mac to phone's Personal Hotspot or disable AP Isolation in router."
                    } else if (errMsg.contains("ECONNREFUSED", ignoreCase = true)) {
                        "Connection refused by $host:$port. Ensure Idroid is open on your Mac."
                    } else if (errMsg.contains("timeout", ignoreCase = true)) {
                        "Connection timed out reaching $host:$port."
                    } else {
                        errMsg
                    }
                    onError?.invoke(friendlyError)
                } else {
                    cleanupSocket()
                    onConnectionStateChanged(false)
                }

                if (isRunning.get()) {
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(30000L) // Exponential backoff up to 30s
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
