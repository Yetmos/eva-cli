package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.DispatchType
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.Result
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EchoDispatcherTest {
    @Test
    fun recordsDispatchBeforeTheProviderHandlesTheInput() = runBlocking {
        val dispatcher = EchoDispatcher()
        val provider = EchoProvider()
        val input = TestEvent(Event.Payload.Text("hello"))
        dispatcher.providers[provider.id] = provider
        provider.emit = {
            assertEquals(
                Action.Dispatch(DispatchType.HANDLE, ComponentRef(provider.id, ComponentRef.Kind.PROVIDER)),
                input.records.single().action,
            )
        }
        provider.start()

        dispatcher.dispatch(input)

        assertEquals(2, input.records.size)
        assertEquals(dispatcher.id, input.records[0].actor.id)
        assertEquals(provider.id, input.records[1].actor.id)
        assertEquals(Action.Handle(Result.DONE), input.records[1].action)
        assertEquals(listOf(input.records[0]), dispatcher.records)
        assertEquals(listOf(input.records[1]), provider.records)
    }

    @Test
    fun preservesTheOriginalFailureWithoutRecordingSuccessfulHandling() = runBlocking {
        val dispatcher = EchoDispatcher()
        val provider = EchoProvider()
        val input = TestEvent(Event.Payload.Text("hello"))
        val failure = IllegalStateException("output failed")
        dispatcher.providers[provider.id] = provider
        provider.emit = { throw failure }
        provider.start()

        assertSame(failure, assertFailsWith<IllegalStateException> { dispatcher.dispatch(input) })

        assertTrue(input.records.single().action is Action.Dispatch)
        assertTrue(provider.records.isEmpty())
    }

    @Test
    fun skipsDispatchWhileDisabled() = runBlocking {
        val dispatcher = EchoDispatcher()
        val input = TestEvent(Event.Payload.Text("hello"))
        dispatcher.enable = false

        dispatcher.dispatch(input)

        assertTrue(input.records.isEmpty())
        assertTrue(dispatcher.records.isEmpty())
    }

    @Test
    fun forwardsRepliesWithoutInventingAnUpstreamTarget() = runBlocking {
        val dispatcher = EchoDispatcher()
        val reply = TestEvent(Event.Payload.Text("Echo: hello"))
        var forwarded: Event? = null
        dispatcher.emit = { forwarded = it }

        dispatcher.receive(reply)

        assertSame(reply, forwarded)
        assertTrue(reply.records.isEmpty())
        assertTrue(dispatcher.records.isEmpty())
    }
}
