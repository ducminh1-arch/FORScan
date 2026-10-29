package com.carscan.core

import com.carscan.core.transport.*
import com.carscan.core.viewmodel.ConnectionType
import com.carscan.core.viewmodel.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class BluetoothTransportTest {

    @Test
    fun testBtClassicTransportInitialState() {
        val transport = BtClassicTransport(
            macAddress = "00:11:22:33:44:55",
            adapterProvider = { null }
        )
        assertEquals(ConnectionState.Disconnected, transport.state.value)
    }

    @Test
    fun testBtClassicTransportSendWhenDisconnectedThrowsNotConnected() = runBlocking {
        val transport = BtClassicTransport(
            macAddress = "00:11:22:33:44:55",
            adapterProvider = { null }
        )
        try {
            transport.send("ATZ")
            fail("Expected NotConnectedException")
        } catch (e: Exception) {
            assertTrue(e is NotConnectedException)
        }
    }

    @Test
    fun testBtClassicTransportConnectWithNullAdapterFails() = runBlocking {
        val transport = BtClassicTransport(
            macAddress = "00:11:22:33:44:55",
            adapterProvider = { null }
        )
        try {
            transport.connect()
            fail("Expected TransportException")
        } catch (e: Exception) {
            assertTrue(e is TransportException)
            assertTrue(transport.state.value is ConnectionState.Error)
        }
    }

    @Test
    fun testBleTransportInitialState() {
        val transport = BleTransport(
            macAddress = "00:11:22:33:44:55",
            adapterProvider = { null }
        )
        assertEquals(ConnectionState.Disconnected, transport.state.value)
    }

    @Test
    fun testBleTransportSendWhenDisconnectedThrowsNotConnected() = runBlocking {
        val transport = BleTransport(
            macAddress = "00:11:22:33:44:55",
            adapterProvider = { null }
        )
        try {
            transport.send("ATZ")
            fail("Expected NotConnectedException")
        } catch (e: Exception) {
            assertTrue(e is NotConnectedException)
        }
    }

    @Test
    fun testSessionManagerBluetoothWithoutDeviceFailsGracefully() = runBlocking {
        val session = SessionManager()
        session.isDemoMode.value = false
        session.connectionType.value = ConnectionType.BLUETOOTH_CLASSIC
        session.selectedBluetoothAddress.value = null

        session.connect()?.join()

        assertTrue(session.connectionState.value is ConnectionState.Error)
        assertNotNull(session.errorMessage.value)
        assertTrue(session.errorMessage.value!!.contains("No Bluetooth device selected"))
    }

    @Test
    fun testSessionManagerBleWithoutDeviceFailsGracefully() = runBlocking {
        val session = SessionManager()
        session.isDemoMode.value = false
        session.connectionType.value = ConnectionType.BLE
        session.selectedBluetoothAddress.value = null

        session.connect()?.join()

        assertTrue(session.connectionState.value is ConnectionState.Error)
        assertNotNull(session.errorMessage.value)
        assertTrue(session.errorMessage.value!!.contains("No BLE device selected"))
    }

    @Test
    fun testBtClassicTransportDataFlowWithFakeSocket() = runBlocking {
        val mockAdapter = Mockito.mock(android.bluetooth.BluetoothAdapter::class.java)
        val mockDevice = Mockito.mock(android.bluetooth.BluetoothDevice::class.java)
        val mockSocket = Mockito.mock(android.bluetooth.BluetoothSocket::class.java)

        Mockito.`when`(mockAdapter.isEnabled).thenReturn(true)
        Mockito.`when`(mockAdapter.getRemoteDevice("00:11:22:33:44:55")).thenReturn(mockDevice)

        val toAdapterIn = java.io.PipedInputStream()
        val toAdapterOut = java.io.PipedOutputStream(toAdapterIn)

        val fromAdapterIn = java.io.PipedInputStream()
        val fromAdapterOut = java.io.PipedOutputStream(fromAdapterIn)

        Mockito.`when`(mockSocket.inputStream).thenReturn(fromAdapterIn)
        Mockito.`when`(mockSocket.outputStream).thenReturn(toAdapterOut)

        val adapterThread = Thread {
            try {
                val reader = toAdapterIn.bufferedReader(Charsets.UTF_8)
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == "ATZ") {
                        fromAdapterOut.write("ELM327 v2.2\r\n>".toByteArray(Charsets.UTF_8))
                        fromAdapterOut.flush()
                    }
                }
            } catch (_: Exception) {}
        }
        adapterThread.isDaemon = true
        adapterThread.start()

        val transport = BtClassicTransport(
            macAddress = "00:11:22:33:44:55",
            adapterProvider = { mockAdapter },
            socketFactory = { _, _ -> mockSocket }
        )

        transport.connect()
        assertEquals(ConnectionState.Connected, transport.state.value)

        val response = transport.send("ATZ", timeoutMs = 1500L)
        assertTrue(response.contains("ELM327"))
        assertTrue(response.endsWith(">"))

        transport.disconnect()
        toAdapterOut.close()
        fromAdapterOut.close()
        assertEquals(ConnectionState.Disconnected, transport.state.value)
    }

    @Test
    fun testBtClassicTransportTimeoutWithFakeSocket() = runBlocking {
        val mockAdapter = Mockito.mock(android.bluetooth.BluetoothAdapter::class.java)
        val mockDevice = Mockito.mock(android.bluetooth.BluetoothDevice::class.java)
        val mockSocket = Mockito.mock(android.bluetooth.BluetoothSocket::class.java)

        Mockito.`when`(mockAdapter.isEnabled).thenReturn(true)
        Mockito.`when`(mockAdapter.getRemoteDevice("00:11:22:33:44:55")).thenReturn(mockDevice)

        val outputStream = java.io.ByteArrayOutputStream()
        val inputStream = java.io.PipedInputStream() // never writes anything

        Mockito.`when`(mockSocket.inputStream).thenReturn(inputStream)
        Mockito.`when`(mockSocket.outputStream).thenReturn(outputStream)

        val transport = BtClassicTransport(
            macAddress = "00:11:22:33:44:55",
            adapterProvider = { mockAdapter },
            socketFactory = { _, _ -> mockSocket }
        )

        transport.connect()
        assertEquals(ConnectionState.Connected, transport.state.value)

        val start = System.currentTimeMillis()
        var caughtTimeout = false
        try {
            transport.send("ATRV", timeoutMs = 200L)
            fail("Expected TimeoutException from silent socket")
        } catch (e: Exception) {
            assertTrue("Expected TimeoutException but got ${e.javaClass.name}", e is TimeoutException)
            caughtTimeout = true
        }

        val elapsed = System.currentTimeMillis() - start
        assertTrue("Must catch TimeoutException", caughtTimeout)
        assertTrue("Timeout must occur around 200ms (was $elapsed ms)", elapsed >= 180)

        transport.disconnect()
    }

    @Test
    fun testBtClassicTransportStaleDataFlushedOnNextCommand() = runBlocking {
        val mockAdapter = Mockito.mock(android.bluetooth.BluetoothAdapter::class.java)
        val mockDevice = Mockito.mock(android.bluetooth.BluetoothDevice::class.java)
        val mockSocket = Mockito.mock(android.bluetooth.BluetoothSocket::class.java)

        Mockito.`when`(mockAdapter.isEnabled).thenReturn(true)
        Mockito.`when`(mockAdapter.getRemoteDevice("00:11:22:33:44:55")).thenReturn(mockDevice)

        val toAdapterIn = java.io.PipedInputStream()
        val toAdapterOut = java.io.PipedOutputStream(toAdapterIn)

        val fromAdapterIn = java.io.PipedInputStream()
        val fromAdapterOut = java.io.PipedOutputStream(fromAdapterIn)

        Mockito.`when`(mockSocket.inputStream).thenReturn(fromAdapterIn)
        Mockito.`when`(mockSocket.outputStream).thenReturn(toAdapterOut)

        val adapterThread = Thread {
            try {
                val reader = toAdapterIn.bufferedReader(Charsets.UTF_8)
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == "CMD_A") {
                        // Intentionally delay responding to CMD_A so client times out (timeout is 150ms)
                        Thread.sleep(350)
                        fromAdapterOut.write("RESP_A_LATE\r\n>".toByteArray(Charsets.UTF_8))
                        fromAdapterOut.flush()
                    } else if (line == "CMD_B") {
                        fromAdapterOut.write("RESP_B_FRESH\r\n>".toByteArray(Charsets.UTF_8))
                        fromAdapterOut.flush()
                    }
                }
            } catch (_: Exception) {}
        }
        adapterThread.isDaemon = true
        adapterThread.start()

        val transport = BtClassicTransport(
            macAddress = "00:11:22:33:44:55",
            adapterProvider = { mockAdapter },
            socketFactory = { _, _ -> mockSocket }
        )

        transport.connect()
        assertEquals(ConnectionState.Connected, transport.state.value)

        // Send Command A with short timeout (150ms) -> times out waiting for adapter response
        var timedOutA = false
        try {
            transport.send("CMD_A", timeoutMs = 150L)
            fail("Command A should have timed out")
        } catch (e: TimeoutException) {
            timedOutA = true
        }
        assertTrue("Command A must time out", timedOutA)

        // Wait for adapter thread to send RESP_A_LATE into fromAdapterIn pipe
        kotlinx.coroutines.delay(300)

        // Send Command B: transport must flush stale RESP_A_LATE before reading CMD_B response
        val respB = transport.send("CMD_B", timeoutMs = 1500L)

        transport.disconnect()
        toAdapterOut.close()
        fromAdapterOut.close()

        assertTrue("Response B must contain fresh response", respB.contains("RESP_B_FRESH"))
        assertFalse("Response B must NOT contain stale response from Command A", respB.contains("RESP_A_LATE"))
    }
}
