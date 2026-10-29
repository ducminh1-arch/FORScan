package com.carscan.core

import com.carscan.core.transport.ConnectionState
import com.carscan.core.transport.TimeoutException
import com.carscan.core.transport.WiFiTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class WiFiTransportTest {

    @Test
    fun testWiFiTransportSendTimeoutWithSilentServer() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val clientAccepted = CompletableDeferred<Unit>()
        val serverStop = AtomicBoolean(false)

        val serverThread = Thread {
            try {
                val client = serverSocket.accept()
                clientAccepted.complete(Unit)
                // Server never replies, keeping connection alive
                while (!serverStop.get()) {
                    Thread.sleep(50)
                }
                client.close()
            } catch (_: Exception) {}
        }
        serverThread.isDaemon = true
        serverThread.start()

        val transport = WiFiTransport(host = "127.0.0.1", port = port)
        transport.connect()
        clientAccepted.await()

        val timeoutMs = 500L
        val start = System.currentTimeMillis()
        var caughtException: Exception? = null

        try {
            transport.send("ATZ", timeoutMs = timeoutMs)
            fail("Expected TimeoutException")
        } catch (e: Exception) {
            caughtException = e
        } finally {
            val elapsed = System.currentTimeMillis() - start
            serverStop.set(true)
            transport.disconnect()
            serverSocket.close()

            assertNotNull("Exception must be caught", caughtException)
            assertTrue(
                "Expected com.carscan.core.transport.TimeoutException but got ${caughtException?.javaClass?.name}: ${caughtException?.message}",
                caughtException is TimeoutException
            )

            // Verify elapsed time is within timeoutMs plus small margin (500ms .. 900ms)
            assertTrue(
                "Elapsed time ($elapsed ms) must be around $timeoutMs ms (expected: 480 .. 950 ms)",
                elapsed in 480..950
            )
        }
    }

    @Test
    fun testWiFiTransportSendNormalResponse() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val clientAccepted = CompletableDeferred<Unit>()

        val serverThread = Thread {
            try {
                val client = serverSocket.accept()
                clientAccepted.complete(Unit)
                val inp = client.getInputStream()
                val out = client.getOutputStream()
                val buf = ByteArray(64)
                inp.read(buf) // read command
                out.write("ELM327 v1.5\r\n>".toByteArray(Charsets.UTF_8))
                out.flush()
                Thread.sleep(100)
                client.close()
            } catch (_: Exception) {}
        }
        serverThread.isDaemon = true
        serverThread.start()

        val transport = WiFiTransport(host = "127.0.0.1", port = port)
        transport.connect()
        clientAccepted.await()

        val resp = transport.send("ATZ", timeoutMs = 2000L)
        transport.disconnect()
        serverSocket.close()

        assertTrue(resp.contains("ELM327"))
        assertTrue(resp.contains(">"))
    }

    @Test
    fun testStaleDataFlushedOnNextCommandAfterTimeout() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val clientAccepted = CompletableDeferred<Unit>()
        val serverStop = AtomicBoolean(false)

        val serverThread = Thread {
            try {
                val client = serverSocket.accept()
                clientAccepted.complete(Unit)
                val inp = client.getInputStream()
                val out = client.getOutputStream()
                val reader = inp.bufferedReader(Charsets.UTF_8)

                // 1. Read command A ("CMD_A")
                val cmdA = reader.readLine()
                // Intentionally delay responding to A so client times out (timeout is 200ms)
                Thread.sleep(400)
                out.write("RESP_A_STALE_DATA\r\n>".toByteArray(Charsets.UTF_8))
                out.flush()

                // 2. Read command B ("CMD_B")
                val cmdB = reader.readLine()
                out.write("RESP_B_FRESH\r\n>".toByteArray(Charsets.UTF_8))
                out.flush()

                while (!serverStop.get()) {
                    Thread.sleep(50)
                }
                client.close()
            } catch (_: Exception) {}
        }
        serverThread.isDaemon = true
        serverThread.start()

        val transport = WiFiTransport(host = "127.0.0.1", port = port)
        transport.connect()
        clientAccepted.await()

        // Send Command A with short timeout (200ms), which times out
        var timedOutA = false
        try {
            transport.send("CMD_A", timeoutMs = 200L)
            fail("Command A was expected to time out")
        } catch (e: TimeoutException) {
            timedOutA = true
        }
        assertTrue("Command A must time out", timedOutA)

        // Wait a short moment so server sends delayed RESP_A into TCP receive buffer
        kotlinx.coroutines.delay(350)

        // Send Command B; transport must flush stale RESP_A before sending CMD_B
        val respB = transport.send("CMD_B", timeoutMs = 2000L)

        serverStop.set(true)
        transport.disconnect()
        serverSocket.close()

        // Verify that response B contains fresh data and NO stale data from Command A
        assertTrue("Response B must contain fresh response", respB.contains("RESP_B_FRESH"))
        assertFalse("Response B must NOT contain stale data from Command A", respB.contains("RESP_A_STALE_DATA"))
    }
}
