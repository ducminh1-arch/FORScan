package com.carscan.core.transport

import kotlinx.coroutines.flow.StateFlow

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

open class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
class NotConnectedException : TransportException("Adapter transport is not connected.")
class TimeoutException(msg: String = "Command timed out waiting for adapter response.") : TransportException(msg)
class CommandFailedException(msg: String) : TransportException("Command failed: $msg")
class ConnectionLostException : TransportException("Transport connection was lost.")
class NoDataException : TransportException("NO DATA: No response received from target ECU.")
class CanErrorException : TransportException("CAN ERROR: Bus framing or hardware error.")
class BusBusyException : TransportException("BUS BUSY: CAN bus is busy.")
class UnableToConnectException : TransportException("UNABLE TO CONNECT: Failed to connect to CAN bus.")
class AdapterStoppedException : TransportException("STOPPED: Adapter interrupted execution.")
class BufferFullException : TransportException("BUFFER FULL: Adapter RX buffer overrun.")
class BusSwitchFailedException(msg: String = "Bus switch failed") : TransportException(msg)


interface Transport {
    val state: StateFlow<ConnectionState>
    suspend fun connect()
    fun disconnect()
    suspend fun send(command: String, timeoutMs: Long = 3000L): String
}
