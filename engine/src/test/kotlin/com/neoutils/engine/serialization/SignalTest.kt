package com.neoutils.engine.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalTest {

    @Test
    fun `connect registers a handler invoked on emit`() {
        val signal = Signal<Int>()
        var received: Int? = null
        signal.connect { received = it }
        signal.emit(42)
        assertEquals(42, received)
    }

    @Test
    fun `disposable returned by connect unregisters the handler`() {
        val signal = Signal<Int>()
        var invoked = false
        val sub = signal.connect { invoked = true }
        sub.dispose()
        signal.emit(5)
        assertFalse(invoked)
    }

    @Test
    fun `disconnect by lambda reference removes the handler`() {
        val signal = Signal<Int>()
        var invoked = false
        val handler: (Int) -> Unit = { invoked = true }
        signal.connect(handler)
        signal.disconnect(handler)
        signal.emit(5)
        assertFalse(invoked)
    }

    @Test
    fun `emit invokes handlers in registration order`() {
        val signal = Signal<Int>()
        val order = mutableListOf<String>()
        signal.connect { order += "h1" }
        signal.connect { order += "h2" }
        signal.connect { order += "h3" }
        signal.emit(0)
        assertEquals(listOf("h1", "h2", "h3"), order)
    }

    @Test
    fun `registration during emit only affects the next emission`() {
        val signal = Signal<Int>()
        val log = mutableListOf<String>()
        val h2: (Int) -> Unit = { log += "h2" }
        signal.connect {
            log += "h1"
            signal.connect(h2)
        }
        signal.emit(1)
        assertEquals(listOf("h1"), log)
        signal.emit(2)
        assertEquals(listOf("h1", "h1", "h2"), log)
    }

    @Test
    fun `disconnect during emit does not affect the current snapshot`() {
        val signal = Signal<Int>()
        val log = mutableListOf<String>()
        lateinit var sub2: Disposable
        signal.connect {
            log += "h1"
            sub2.dispose()
        }
        sub2 = signal.connect { log += "h2" }
        signal.emit(1)
        assertEquals(listOf("h1", "h2"), log)
        signal.emit(2)
        assertTrue(log.last() == "h1")
        assertEquals(listOf("h1", "h2", "h1"), log)
    }
}
