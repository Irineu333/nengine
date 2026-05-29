package com.neoutils.engine.debug

import com.neoutils.engine.dx.LogLevel

/** Immutable snapshot of a single log line tailed by [LogOverlayWidget]. */
data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)
