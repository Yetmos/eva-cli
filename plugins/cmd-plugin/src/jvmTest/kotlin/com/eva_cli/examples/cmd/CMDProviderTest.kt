package com.eva_cli.examples.cmd

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CMDProviderTest {
    @Test
    fun submitsInputAndWritesTextOutputThroughHostBindings() = runBlocking {
        val provider = CMDProvider()
        val inputs = mutableListOf<String>()
        val outputs = mutableListOf<String>()
        provider.bindInput { inputs += it }
        provider.bindOutput { outputs += it }
        provider.start()

        provider.submit("echo hello")
        provider.write("Echo: hello")

        assertEquals(listOf("echo hello"), inputs)
        assertEquals(listOf("Echo: hello"), outputs)
        provider.stop()
    }

    @Test
    fun ignoresInputAndOutputAfterStop() = runBlocking {
        val provider = CMDProvider()
        val outputs = mutableListOf<String>()
        provider.bindOutput { outputs += it }
        provider.start()
        provider.stop()

        provider.submit("echo hello")
        provider.write("Echo: hello")

        assertTrue(outputs.isEmpty())
    }
}
