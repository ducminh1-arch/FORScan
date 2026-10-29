package com.carscan.core.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class BleConfig(
    val serviceUuid: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB"),
    val writeUuid: UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB"),
    val notifyUuid: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB"),
    val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
)

class BleTransport(
    val macAddress: String,
    val config: BleConfig = BleConfig(),
    private val context: Context? = null,
    private val adapterProvider: () -> BluetoothAdapter? = { BluetoothAdapter.getDefaultAdapter() }
) : Transport {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = 23

    private val sendMutex = Mutex()
    private val responseBuffer = StringBuilder()
    private var connectionDeferred: CompletableDeferred<Unit>? = null
    private var writeDeferred: CompletableDeferred<Unit>? = null
    private var notifySetupDeferred: CompletableDeferred<Unit>? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = ConnectionState.Connecting
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.value = ConnectionState.Disconnected
                connectionDeferred?.completeExceptionally(ConnectionLostException())
                disconnect()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Search for service
                var targetService = g.getService(config.serviceUuid)
                var wChar = targetService?.getCharacteristic(config.writeUuid)
                var nChar = targetService?.getCharacteristic(config.notifyUuid)

                // Fallback to HM-10 / CC2541 or other common ELM327 BLE clones
                if (wChar == null || nChar == null) {
                    val hm10Service = g.getService(UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"))
                    val hm10Char = hm10Service?.getCharacteristic(UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"))
                    if (hm10Char != null) {
                        targetService = hm10Service
                        wChar = hm10Char
                        nChar = hm10Char
                    }
                }

                // Generic auto-discovery if still not found
                if (wChar == null || nChar == null) {
                    for (srv in g.services) {
                        for (ch in srv.characteristics) {
                            val props = ch.properties
                            if ((props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0 && wChar == null) {
                                wChar = ch
                            }
                            if ((props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0 && nChar == null) {
                                nChar = ch
                            }
                        }
                    }
                }

                writeCharacteristic = wChar
                notifyCharacteristic = nChar

                if (wChar != null && nChar != null) {
                    // Request higher MTU
                    g.requestMtu(512)
                } else {
                    connectionDeferred?.completeExceptionally(
                        TransportException("Failed to find ELM327 compatible BLE characteristics on device $macAddress")
                    )
                }
            } else {
                connectionDeferred?.completeExceptionally(TransportException("GATT service discovery failed with status: $status"))
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
            }
            // Enable notifications on the notify characteristic
            enableNotification(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _state.value = ConnectionState.Connected
                connectionDeferred?.complete(Unit)
                notifySetupDeferred?.complete(Unit)
            } else {
                connectionDeferred?.completeExceptionally(TransportException("Failed to enable BLE notifications (status: $status)"))
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeDeferred?.complete(Unit)
            } else {
                writeDeferred?.completeExceptionally(TransportException("BLE characteristic write failed with status: $status"))
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleIncomingBytes(characteristic.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }
    }

    private fun handleIncomingBytes(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        val str = String(bytes, Charsets.UTF_8)
        synchronized(responseBuffer) {
            responseBuffer.append(str)
        }
    }

    private fun enableNotification(g: BluetoothGatt) {
        val nChar = notifyCharacteristic ?: return
        g.setCharacteristicNotification(nChar, true)

        val descriptor = nChar.getDescriptor(config.cccdUuid)
        if (descriptor != null) {
            val value = if ((nChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        } else {
            // Some devices don't require CCCD write or have no descriptor
            _state.value = ConnectionState.Connected
            connectionDeferred?.complete(Unit)
        }
    }

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
                throw TransportException("Invalid BLE MAC address: $macAddress", e)
            }

            val deferred = CompletableDeferred<Unit>()
            connectionDeferred = deferred

            val appContext = context ?: try {
                val at = Class.forName("android.app.ActivityThread")
                at.getMethod("currentApplication").invoke(null) as? Context
            } catch (_: Exception) {
                null
            } ?: throw TransportException("Application context is required for BLE GATT connection.")

            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(appContext, false, gattCallback)
            }

            // Wait for connection and notification setup with 10s timeout
            withTimeout(10000L) {
                deferred.await()
            }
        } catch (e: Exception) {
            disconnect()
            _state.value = ConnectionState.Error(e.message ?: "Failed to connect to BLE device $macAddress")
            throw TransportException("BLE connection failed: ${e.message}", e)
        }
    }

    override fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}

        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        connectionDeferred = null
        writeDeferred = null
        _state.value = ConnectionState.Disconnected
    }

    override suspend fun send(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        sendMutex.withLock {
            if (_state.value !is ConnectionState.Connected) {
                throw NotConnectedException()
            }
            val g = gatt ?: throw NotConnectedException()
            val wChar = writeCharacteristic ?: throw NotConnectedException()

            synchronized(responseBuffer) {
                responseBuffer.setLength(0)
            }

            val payload = if (command.endsWith("\r")) command else "$command\r"
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)

            // Chunk bytes by MTU - 3
            val maxChunkSize = maxOf(20, negotiatedMtu - 3)
            var offset = 0

            while (offset < payloadBytes.size) {
                val chunkSize = minOf(maxChunkSize, payloadBytes.size - offset)
                val chunk = ByteArray(chunkSize)
                System.arraycopy(payloadBytes, offset, chunk, 0, chunkSize)

                val deferred = CompletableDeferred<Unit>()
                writeDeferred = deferred

                val writeType = if ((wChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(wChar, chunk, writeType)
                } else {
                    @Suppress("DEPRECATION")
                    wChar.value = chunk
                    @Suppress("DEPRECATION")
                    wChar.writeType = writeType
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(wChar)
                }

                if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                    withTimeout(2000L) {
                        deferred.await()
                    }
                } else {
                    delay(10)
                }

                offset += chunkSize
            }

            // Read until prompt character '>' with timeout
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                synchronized(responseBuffer) {
                    if (responseBuffer.contains(">")) {
                        return@withContext responseBuffer.toString()
                    }
                }

                if (System.currentTimeMillis() >= deadline) {
                    throw TimeoutException("BLE command timed out waiting for '>' prompt after ${timeoutMs}ms.")
                }

                delay(10)
            }

            @Suppress("UNREACHABLE_CODE")
            ""
        }
    }
}
