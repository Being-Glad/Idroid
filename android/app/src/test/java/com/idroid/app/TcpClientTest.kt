package com.idroid.app

import com.idroid.app.network.DiagnosticType
import com.idroid.app.network.NetworkDiagnostics
import com.idroid.app.network.TcpClient
import org.junit.Assert.*
import org.junit.Test

/**
 * TcpClientTest - Unit tests verifying:
 * 1. Automatic multi-host candidate generation and USB fallback (127.0.0.1).
 * 2. Intelligent, accurate classification of connection errors (Different Wi-Fi, AP Isolation, App Closed).
 * 3. Subnet extraction and mismatch detection.
 */
class TcpClientTest {

    @Test
    fun testBuildCandidateHostsInjectsUsbLoopbackFallback() {
        val candidates = TcpClient.buildCandidateHosts("192.168.1.6", includeArp = false)
        assertEquals(2, candidates.size)
        assertEquals("192.168.1.6", candidates[0])
        assertEquals("127.0.0.1", candidates[1])
    }

    @Test
    fun testBuildCandidateHostsDeduplicatesLoopback() {
        val candidates = TcpClient.buildCandidateHosts("127.0.0.1", includeArp = false)
        assertEquals("Should not duplicate 127.0.0.1 if it is the primary host", 1, candidates.size)
        assertEquals("127.0.0.1", candidates[0])
    }

    @Test
    fun testBuildCandidateHostsPreservesOrderAndFallbacks() {
        val fallbacks = listOf("192.168.43.1", "192.168.1.6")
        val candidates = TcpClient.buildCandidateHosts("192.168.1.6", fallbacks, includeArp = false)
        assertEquals(3, candidates.size)
        assertEquals("192.168.1.6", candidates[0])
        assertEquals("192.168.43.1", candidates[1])
        assertEquals("127.0.0.1", candidates[2])
    }

    @Test
    fun testSubnetExtractionAndComparison() {
        assertEquals("192.168.1", NetworkDiagnostics.extractSubnet("192.168.1.5"))
        assertEquals("10.0.0", NetworkDiagnostics.extractSubnet("10.0.0.12"))
        assertEquals("172.20.10", NetworkDiagnostics.extractSubnet("172.20.10.1"))
        assertEquals("", NetworkDiagnostics.extractSubnet("127.0.0"))

        assertTrue(NetworkDiagnostics.isSameSubnet("192.168.1.5", "192.168.1.2"))
        assertFalse(NetworkDiagnostics.isSameSubnet("192.168.29.14", "192.168.1.2"))
        assertFalse(NetworkDiagnostics.isSameSubnet("10.0.0.5", "192.168.1.2"))
    }

    @Test
    fun testClassifyConnectionErrorIdentifiesApIsolation() {
        val err1 = "java.net.ConnectException: failed to connect to /192.168.1.6 (port 53100) from /192.168.1.12: connect failed: EHOSTUNREACH (No route to host)"
        val classification1 = TcpClient.classifyConnectionError(err1, "192.168.1.6", 53100, phoneIpOverride = "192.168.1.12")
        assertTrue(classification1.contains("Router") || classification1.contains("AP Isolation") || classification1.contains("Wi-Fi"))
        assertTrue(classification1.contains("Personal Hotspot") || classification1.contains("Hotspot"))
    }

    @Test
    fun testClassifyConnectionErrorIdentifiesConnectionRefused() {
        val err = "java.net.ConnectException: failed to connect to /192.168.1.6 (port 53100): connect failed: ECONNREFUSED (Connection refused)"
        val classification = TcpClient.classifyConnectionError(err, "192.168.1.6", 53100, phoneIpOverride = "192.168.1.12")
        assertTrue(classification.contains("Idroid is Closed on Mac") || classification.contains("Connection refused") || classification.contains("closed"))
    }

    @Test
    fun testDiagnoseUsbDisconnected() {
        val report = NetworkDiagnostics.diagnose(
            context = null,
            targetIp = "127.0.0.1",
            targetPort = 53100,
            rawError = "Connection refused",
            isConnected = false
        )
        assertEquals(DiagnosticType.USB_DISCONNECTED, report.type)
        assertTrue(report.headline.contains("USB"))
    }

    @Test
    fun testDiagnoseConnectedState() {
        val report = NetworkDiagnostics.diagnose(
            context = null,
            targetIp = "192.168.1.2",
            targetPort = 53100,
            isConnected = true
        )
        assertEquals(DiagnosticType.CONNECTED, report.type)
        assertEquals("Connected & Active", report.headline)
    }

    @Test
    fun testDiagnoseDifferentWifiNetwork() {
        val report = NetworkDiagnostics.diagnose(
            context = null,
            targetIp = "192.168.1.2",
            targetPort = 53100,
            targetSsid = "Home_5G",
            rawError = "connect timed out",
            isConnected = false,
            phoneIpOverride = "192.168.29.50"
        )
        assertEquals(DiagnosticType.DIFFERENT_WIFI_NETWORK, report.type)
        assertEquals("Different Wi-Fi Network Detected", report.headline)
        assertTrue(report.explanation.contains("Home_5G"))
    }

    @Test
    fun testDiagnoseCellularDataOnly() {
        val report = NetworkDiagnostics.diagnose(
            context = null,
            targetIp = "192.168.1.2",
            targetPort = 53100,
            targetSsid = "Home_5G",
            rawError = "connect timed out",
            isConnected = false,
            phoneIpOverride = "100.80.20.10"
        )
        assertEquals(DiagnosticType.CELLULAR_DATA_ONLY, report.type)
        assertEquals("Phone is on Mobile Data", report.headline)
        assertTrue(report.explanation.contains("Cellular Mobile Data"))
    }
}
