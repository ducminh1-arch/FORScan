package com.carscan.core.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class WiFiTransport(
    val host: String = "192.168.0.10",
    val port: Int = 35000
) : Transport {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Connecting
        try {
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 5000)
            s.soTimeout = 1000
            socket = s
            inputStream = s.getInputStream()
            outputStream = s.getOutputStream()
            _state.value = ConnectionState.Connected
        } catch (e: Exception) {
            _state.value = ConnectionState.Error(e.message ?: "Failed to connect to $host:$port")
            throw TransportException("Failed to connect to $host:$port", e)
        }
    }

    override fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        _state.value = ConnectionState.Disconnected
    }

    override suspend fun send(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        if (_state.value !is ConnectionState.Connected) {
            throw NotConnectedException()
        }
        val s = socket ?: throw NotConnectedException()
        val out = outputStream ?: throw NotConnectedException()
        val inp = inputStream ?: throw NotConnectedException()

        val payload = if (command.endsWith("\r")) command else "$command\r"

        // Flush any stale/residual incoming data before sending new command
        val flushBuf = ByteArray(1024)
        while (inp.available() > 0) {
            val bytesRead = inp.read(flushBuf, 0, minOf(flushBuf.size, inp.available()))
            if (bytesRead <= 0) break
        }

        out.write(payload.toByteArray(Charsets.UTF_8))
        out.flush()

        val deadline = System.currentTimeMillis() + timeoutMs
        val sb = StringBuilder()
        val buffer = ByteArray(1024)

        try {
            while (!sb.contains(">")) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    throw TimeoutException("Command timed out waiting for adapter response after ${timeoutMs}ms.")
                }

                // Set socket read timeout in small slices (up to 100ms or remaining time)
                // to promptly respond to deadline without blocking native thread forever
                s.soTimeout = remaining.coerceIn(1L, 100L).toInt()

                try {
                    val bytesRead = inp.read(buffer)
                    if (bytesRead == -1) {
                        _state.value = ConnectionState.Disconnected
                        throw ConnectionLostException()
                    }
                    sb.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                } catch (e: SocketTimeoutException) {
                    if (System.currentTimeMillis() >= deadline) {
                        throw TimeoutException("Command timed out waiting for adapter response after ${timeoutMs}ms.")
                    }
                }
            }
            sb.toString()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw TimeoutException("Command timed out waiting for adapter response after ${timeoutMs}ms.")
        }
    }
}
