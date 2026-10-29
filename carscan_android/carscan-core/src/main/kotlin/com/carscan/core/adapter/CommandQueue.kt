package com.carscan.core.adapter

import com.carscan.core.transport.CommandFailedException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CommandQueue {
    private val mutex = Mutex()
    private var isCancelled = false

    suspend fun <T> execute(block: suspend () -> T): T {
        mutex.withLock {
            if (isCancelled) {
                throw CommandFailedException("Command queue was cancelled.")
            }
            return block()
        }
    }

    fun cancelAll() {
        isCancelled = true
    }

    fun reset() {
        isCancelled = false
    }
}
