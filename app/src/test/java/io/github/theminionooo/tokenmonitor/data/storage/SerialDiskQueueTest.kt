package io.github.theminionooo.tokenmonitor.data.storage

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialDiskQueueTest {
    @Test
    fun `close waits for queued operations in submission order`() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val queue = SerialDiskQueue(scope)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()

        try {
            queue.enqueue {
                firstStarted.countDown()
                releaseFirst.await()
                order += "save"
            }
            queue.enqueue { order += "widget refresh" }
            val finalOperation = checkNotNull(queue.closeWhenIdle())

            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            assertTrue(scope.isActive)
            releaseFirst.countDown()
            runBlocking { finalOperation.join() }

            assertEquals(listOf("save", "widget refresh"), order)
            assertFalse(scope.isActive)
        } finally {
            releaseFirst.countDown()
            dispatcher.close()
            executor.shutdownNow()
        }
    }
}
