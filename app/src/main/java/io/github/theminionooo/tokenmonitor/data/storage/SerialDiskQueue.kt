package io.github.theminionooo.tokenmonitor.data.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Runs disk operations in submission order and keeps shutdown behind the final operation. */
internal class SerialDiskQueue(private val scope: CoroutineScope) {
    private var tail: Job? = null
    private var closeJob: Job? = null
    private var closed = false

    @Synchronized
    fun enqueue(operation: suspend () -> Unit): Job {
        check(!closed) { "Disk queue is closed." }
        val previous = tail
        return scope.launch {
            previous?.join()
            operation()
        }.also { tail = it }
    }

    @Synchronized
    fun closeWhenIdle(): Job? {
        if (closed) return closeJob ?: tail
        closed = true
        val pending = tail
        if (pending == null) {
            scope.cancel()
            return null
        }
        val completion = Job()
        closeJob = completion
        pending.invokeOnCompletion {
            scope.cancel()
            completion.complete()
        }
        return completion
    }
}
