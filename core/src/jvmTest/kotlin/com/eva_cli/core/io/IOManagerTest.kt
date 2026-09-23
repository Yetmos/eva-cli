package com.eva_cli.core.io

import com.eva_cli.core.runtime.Runtime
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IOManagerTest {
    @Test
    fun managesProviderLifecycleIdempotently() {
        val input = RecordingInputProvider()
        val output = RecordingOutputProvider()
        val manager = IOManager(input, output)

        assertFalse(manager.isStarted)
        manager.start()
        manager.start()

        assertTrue(manager.isStarted)
        assertEquals(1, input.startCount)
        assertEquals(1, output.startCount)

        manager.stop()
        manager.stop()

        assertFalse(manager.isStarted)
        assertEquals(1, input.stopCount)
        assertEquals(1, output.stopCount)
    }

    @Test
    fun delegatesInputAndOutputEvents() {
        val inputEvent = IOEvent(IOType.TEXT, IOPayload.Text("hello"))
        val input = RecordingInputProvider(inputEvent)
        val output = RecordingOutputProvider()
        val manager = IOManager(input, output)

        assertEquals(inputEvent, runSuspending { manager.receive() })

        val outputEvent = IOEvent(IOType.TEXT, IOPayload.Text("world"))
        runSuspending { manager.emit(outputEvent) }

        assertEquals(listOf(outputEvent), output.events)
    }

    @Test
    fun cleansUpProvidersWhenStartupFails() {
        val input = RecordingInputProvider()
        val output = RecordingOutputProvider(startFailure = IllegalStateException("output failed"))
        val manager = IOManager(input, output)

        assertFailsWith<IllegalStateException> { manager.start() }

        assertFalse(manager.isStarted)
        assertEquals(1, input.stopCount)
        assertEquals(1, output.stopCount)
    }

    @Test
    fun runtimeOwnsManagerLifecycle() {
        val input = RecordingInputProvider()
        val output = RecordingOutputProvider()
        val manager = IOManager(input, output)
        val runtime = Runtime(manager)

        runtime.start()
        assertTrue(runtime.isStarted)
        assertTrue(manager.isStarted)

        runtime.stop()
        assertFalse(runtime.isStarted)
        assertFalse(manager.isStarted)
    }

    private fun <T> runSuspending(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return checkNotNull(outcome).getOrThrow()
    }

    private class RecordingInputProvider(
        private val nextEvent: InputEvent? = null,
    ) : InputProvider {
        var startCount = 0
        var stopCount = 0

        override fun start() {
            startCount += 1
        }

        override suspend fun receive(): InputEvent? = nextEvent

        override fun stop() {
            stopCount += 1
        }
    }

    private class RecordingOutputProvider(
        private val startFailure: Throwable? = null,
    ) : OutputProvider {
        var startCount = 0
        var stopCount = 0
        val events = mutableListOf<OutputEvent>()

        override fun start() {
            startCount += 1
            startFailure?.let { throw it }
        }

        override suspend fun emit(event: OutputEvent) {
            events += event
        }

        override fun stop() {
            stopCount += 1
        }
    }
}
