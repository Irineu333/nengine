package com.neoutils.engine.dx

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class CountingSink : LogSink {
    var emits: Int = 0
    val entries: MutableList<Triple<LogLevel, String, String>> = mutableListOf()
    override fun emit(timestampMillis: Long, level: LogLevel, tag: String, message: String) {
        emits++
        entries += Triple(level, tag, message)
    }
}

class LogMultiSinkTest {

    private var previousGlobal: LogLevel = LogLevel.Info

    @BeforeTest
    fun setup() {
        previousGlobal = Log.config.globalLevel
        Log.config.globalLevel = LogLevel.Info
    }

    @AfterTest
    fun teardown() {
        Log.config.globalLevel = previousGlobal
    }

    @Test
    fun `fan-out delivers the same entry to every registered sink`() {
        val a = CountingSink()
        val b = CountingSink()
        Log.addSink(a)
        Log.addSink(b)
        try {
            Log.i("X", "hello")
            assertEquals(listOf(Triple(LogLevel.Info, "X", "hello")), a.entries)
            assertEquals(listOf(Triple(LogLevel.Info, "X", "hello")), b.entries)
        } finally {
            Log.removeSink(a)
            Log.removeSink(b)
        }
    }

    @Test
    fun `ConsoleLogSink is registered by default`() {
        val original = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured))
        try {
            Log.i("Boot", "ready")
        } finally {
            System.setOut(original)
        }
        assertTrue(
            captured.toString().contains("ready"),
            "expected console output to contain the message, got '$captured'",
        )
    }

    @Test
    fun `addSink is idempotent`() {
        val a = CountingSink()
        Log.addSink(a)
        Log.addSink(a)
        try {
            Log.i("X", "once")
            assertEquals(1, a.emits)
        } finally {
            Log.removeSink(a)
        }
    }

    @Test
    fun `removeSink stops delivery`() {
        val a = CountingSink()
        Log.addSink(a)
        Log.removeSink(a)
        Log.i("X", "after removal")
        assertEquals(0, a.emits)
    }

    @Test
    fun `level gate applies before fan-out`() {
        val a = CountingSink()
        Log.addSink(a)
        try {
            Log.config.globalLevel = LogLevel.Info
            Log.d("X", "suppressed")
            assertEquals(0, a.emits, "Debug must be gated out before fan-out")
            Log.i("X", "passes")
            assertEquals(1, a.emits)
            assertEquals(LogLevel.Info, a.entries.single().first)
        } finally {
            Log.removeSink(a)
        }
    }
}
