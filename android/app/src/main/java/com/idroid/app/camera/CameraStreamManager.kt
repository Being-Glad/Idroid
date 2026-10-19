package com.idroid.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.compose.runtime.mutableStateOf
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

object CameraStreamManager {
    private const val TAG = "IdroidCameraStream"
    private const val MAGIC_HEADER = 0x49444341 // "IDCA" in ASCII

    val isStreaming = mutableStateOf(false)
    val liveFps = mutableStateOf(0)
    val liveBitrateMbps = mutableStateOf(0.0f)
    val totalFramesSent = mutableStateOf(0L)
    val statusMessage = mutableStateOf("Standby")

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val isRunning = AtomicBoolean(false)

    // Configurable parameters
    private var streamWidth = 1920
    private var streamHeight = 1080
    private var targetFps = 60
    private var targetBitrate = 6_000_000 // 6 Mbps for pristine 1080p

    fun startStream(
        context: Context,
        targetIp: String,
        targetPort: Int = 53104,
        fps: Int = 60,
        quality: String = "1080p 60fps"
    ) {
        if (isRunning.get()) return

        when {
            quality.contains("720p") -> {
                streamWidth = 1280
                streamHeight = 720
                targetBitrate = 3_500_000
            }
            quality.contains("4K") -> {
                streamWidth = 3840
                streamHeight = 2160
                targetBitrate = 14_000_000
            }
            else -> {
                streamWidth = 1920
                streamHeight = 1080
                targetBitrate = 6_000_000
            }
        }
        targetFps = fps

        startBackgroundThread()
        isRunning.set(true)
        isStreaming.value = true
        statusMessage.value = "Connecting to $targetIp:$targetPort..."

        Thread {
            try {
                // 1. Establish low-latency TCP connection to Mac
                val s = Socket()
                s.tcpNoDelay = true
                s.sendBufferSize = 128 * 1024
                s.connect(InetSocketAddress(targetIp, targetPort), 4000)
                socket = s
                outputStream = s.getOutputStream()
                statusMessage.value = "Starting Hardware Encoder..."

                // 2. Setup Hardware H.264 Encoder
                setupEncoder()

                // 3. Open Camera and Start Capture
                openCamera(context)

                // 4. Start Drain Loop
                drainEncoderLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Stream failed to start: ${e.message}", e)
                statusMessage.value = "Error: ${e.message ?: "Failed to connect"}"
                stopStream()
            }
        }.start()
    }

    private fun setupEncoder() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, streamWidth, streamHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger("latency", 0) // Low-latency real-time mode
            setInteger("priority", 0) // Realtime priority
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
        mediaCodec = codec
        Log.i(TAG, "Hardware H.264 Encoder initialized: ${streamWidth}x${streamHeight} @ ${targetFps}fps")
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(context: Context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList[0]

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createCaptureSession(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraDevice = null
                Log.e(TAG, "Camera error: $error")
            }
        }, backgroundHandler)
    }

    private fun createCaptureSession(camera: CameraDevice) {
        val surface = inputSurface ?: return
        val surfaces = listOf(surface)

        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(surface)
                        set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        // Target high framerate
                        set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(targetFps, targetFps))
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
                    }
                    session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                    statusMessage.value = "Streaming Live (${streamWidth}x${streamHeight} @ ${targetFps}fps)"
                    Log.i(TAG, "Camera capture session repeating at target ${targetFps} FPS")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera repeating request: ${e.message}")
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Camera capture session configuration failed")
            }
        }, backgroundHandler)
    }

    private fun drainEncoderLoop() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val out = outputStream ?: return

        var frameCounter = 0
        var byteCounter = 0L
        var lastMetricsTime = System.currentTimeMillis()

        val headerBuffer = ByteBuffer.allocate(17) // 4 magic + 4 size + 8 timestamp + 1 flags

        while (isRunning.get()) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    val payloadSize = bufferInfo.size
                    val timestampMs = System.currentTimeMillis()
                    val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                    headerBuffer.clear()
                    headerBuffer.putInt(MAGIC_HEADER)
                    headerBuffer.putInt(payloadSize)
                    headerBuffer.putLong(timestampMs)
                    headerBuffer.put(if (isKeyFrame) 1.toByte() else 0.toByte())

                    try {
                        out.write(headerBuffer.array())
                        val bytes = ByteArray(payloadSize)
                        outputBuffer.get(bytes)
                        out.write(bytes)
                        out.flush()

                        frameCounter++
                        byteCounter += payloadSize + 17
                        totalFramesSent.value = totalFramesSent.value + 1

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastMetricsTime
                        if (elapsed >= 1000) {
                            val measuredFps = (frameCounter * 1000.0 / elapsed).toInt()
                            val measuredMbps = (byteCounter * 8.0 / (elapsed * 1000.0)).toFloat()
                            liveFps.value = measuredFps
                            liveBitrateMbps.value = measuredMbps

                            frameCounter = 0
                            byteCounter = 0
                            lastMetricsTime = now
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Socket write failed: ${e.message}")
                        break
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
            }
        }

        stopStream()
    }

    fun stopStream() {
        if (!isRunning.getAndSet(false)) return

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null

        try {
            inputSurface?.release()
        } catch (_: Exception) {}
        inputSurface = null

        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null

        stopBackgroundThread()

        isStreaming.value = false
        liveFps.value = 0
        liveBitrateMbps.value = 0f
        statusMessage.value = "Standby"
        Log.i(TAG, "Camera stream cleanly stopped.")
    }

    private fun startBackgroundThread() {
        val thread = HandlerThread("CameraBackground").apply { start() }
        backgroundThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (_: Exception) {}
        backgroundThread = null
        backgroundHandler = null
    }
}
