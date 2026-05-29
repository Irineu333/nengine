package com.neoutils.engine.dx

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

fun interface LogSink {
    fun emit(timestampMillis: Long, level: LogLevel, tag: String, message: String)
}

object ConsoleLogSink : LogSink {
    override fun emit(timestampMillis: Long, level: LogLevel, tag: String, message: String) {
        val seconds = timestampMillis / 1000
        val millis = timestampMillis % 1000
        val stream = if (level == LogLevel.Error || level == LogLevel.Warn) System.err else System.out
        stream.println("[$seconds.$millis] [$level] [$tag] $message")
    }
}

class LogConfig {

    @Volatile var globalLevel: LogLevel = LogLevel.Info
    private val tagLevels: MutableMap<String, LogLevel> = ConcurrentHashMap()

    fun setTagLevel(tag: String, level: LogLevel) {
        tagLevels[tag] = level
    }

    fun clearTagLevel(tag: String) {
        tagLevels.remove(tag)
    }

    fun effectiveLevel(tag: String): LogLevel = tagLevels[tag] ?: globalLevel
}

object Log {

    /** Process-wide log configuration. Read by [log] before emitting. */
    val config: LogConfig = LogConfig()

    /**
     * Registered delivery targets. Copy-on-write so the hot [log] path can
     * iterate lock-free from any thread while the rare [addSink]/[removeSink]
     * (UI toggles, test setup) mutate concurrently. Seeded with
     * [ConsoleLogSink] so console output survives unless explicitly removed.
     */
    private val sinks = CopyOnWriteArrayList<LogSink>(arrayOf(ConsoleLogSink))

    /** Registers [sink]; idempotent — the same instance is never added twice. */
    fun addSink(sink: LogSink) {
        sinks.addIfAbsent(sink)
    }

    /** Unregisters [sink]; a no-op when it is not currently registered. */
    fun removeSink(sink: LogSink) {
        sinks.remove(sink)
    }

    fun d(tag: String, message: String) = log(LogLevel.Debug, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.Info, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.Warn, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.Error, tag, message)

    private fun log(level: LogLevel, tag: String, message: String) {
        if (level.ordinal < config.effectiveLevel(tag).ordinal) return
        val timestamp = System.currentTimeMillis()
        for (sink in sinks) sink.emit(timestamp, level, tag, message)
    }
}
