package com.carscan.core.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BtClassicTransport(
    val macAddress: String,
    val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
    private val adapterProvider: () -> BluetoothAdapter? = { BluetoothAdapter.getDefaultAdapter() },
    private val socketFactory: ((BluetoothDevice, UUID) -> BluetoothSocket)? = null
) : Transport {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (_state.value is ConnectionState.Connected) return@withContext
        _state.value = ConnectionState.Connecting

        try {
            val btAdapter = adapterProvider()
                ?: throw TransportException("Bluetooth is not supported on this device.")
            if (!btAdapter.isEnabled) {
                throw TransportException("Bluetooth is disabled. Please enable Bluetooth.")
            }

            val device: BluetoothDevice = try {
                btAdapter.getRemoteDevice(macAddress)
            } catch (e: Exception) {
                throw TransportException("Invalid Bluetooth MAC address: $macAddress", e)
            }

            // Discovery must be cancelled before connecting for optimal bandwidth/latency
            try {
                btAdapter.cancelDiscovery()
            } catch (_: SecurityException) {}

            val sock = socketFactory?.invoke(device, sppUuid) ?: try {
                device.createRfcommSocketToServiceRecord(sppUuid)
            } catch (e: Exception) {
                // Fallback reflection method for stubborn ELM327 clone adapters
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            }

            sock.connect()

            socket = sock
            inputStream = sock.inputStream
            outputStream = sock.outputStream
            _state.value = ConnectionState.Connected
        } catch (e: Exception) {
            disconnect()
            _state.value = ConnectionState.Error(e.message ?: "Failed to connect to Bluetooth device $macAddress")
            throw TransportException("Bluetooth Classic connection failed: ${e.message}", e)
        }
    }

    override fun disconnect() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
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

        while (!sb.contains(">")) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                throw TimeoutException("Bluetooth Classic command timed out waiting for '>' prompt after ${timeoutMs}ms.")
            }

            val available = inp.available()
            if (available > 0) {
                val bytesToRead = minOf(available, buffer.size)
                val bytesRead = inp.read(buffer, 0, bytesToRead)
                if (bytesRead == -1) {
                    _state.value = ConnectionState.Disconnected
                    throw ConnectionLostException()
                }
                sb.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
            } else {
                delay(5)
            }
        }

        sb.toString()
    }
}
