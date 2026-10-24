package com.idroid.app.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

object TurboFileTransfer {

    data class ProgressUpdate(
        val fileIndex: Int,
        val totalFiles: Int,
        val fileName: String,
        val bytesSent: Long,
        val totalBytes: Long,
        val speedMbPerSec: Double
    )

    suspend fun sendFiles(
        context: Context,
        hostIp: String,
        port: Int = 53108,
        uris: List<Uri>,
        onProgress: (ProgressUpdate) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.success(0)
        
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.sendBufferSize = 512 * 1024
            socket.connect(InetSocketAddress(hostIp, port), 6000)

            val out = DataOutputStream(socket.getOutputStream().buffered(256 * 1024))
            val `in` = socket.getInputStream().bufferedReader()

            // 1. Magic
            out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 'D'.code.toByte()))

            // 2. Count
            out.writeInt(uris.size)

            val buffer = ByteArray(256 * 1024)
            var totalTransferred = 0L
            val startTime = System.currentTimeMillis()

            for ((index, uri) in uris.withIndex()) {
                val (name, size) = resolveFileMetadata(context, uri)
                val nameBytes = name.toByteArray(Charsets.UTF_8)

                out.writeShort(nameBytes.size)
                out.write(nameBytes)
                out.writeLong(size)

                val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open input stream for $uri")

                inputStream.use { stream ->
                    var fileBytesSent = 0L
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        fileBytesSent += read
                        totalTransferred += read

                        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                        val speed = if (elapsedSec > 0.05) {
                            (totalTransferred / (1024.0 * 1024.0)) / elapsedSec
                        } else 0.0

                        onProgress(
                            ProgressUpdate(
                                fileIndex = index + 1,
                                totalFiles = uris.size,
                                fileName = name,
                                bytesSent = fileBytesSent,
                                totalBytes = size,
                                speedMbPerSec = speed
                            )
                        )
                    }
                }
            }

            out.flush()

            // Wait for Mac confirmation OK
            val resp = `in`.readLine()
            if (resp != null && resp.startsWith("OK")) {
                Result.success(uris.size)
            } else {
                Result.failure(Exception("Transfer rejected or response: $resp"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    fun resolveFileMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "File_${System.currentTimeMillis()}"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}
        return name to size
    }
}
