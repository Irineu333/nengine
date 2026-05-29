package com.neoutils.engine.debug

import com.neoutils.engine.dx.Log
import com.neoutils.engine.dx.LogLevel
import com.neoutils.engine.dx.LogSink
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

/**
 * Screen-space tail of the last [capacity] log entries. Subscribes itself as
 * a [LogSink] while [enabled] and unsubscribes when disabled, so a closed
 * overlay records nothing and an opened one shows a *live tail* of entries
 * emitted from then on — not past history.
 *
 * `Log.*` may run on any thread while `drawDebug` runs on the render thread,
 * so the ring buffer is guarded by [lock]: [emit] writes under it and
 * `drawDebug` copies a snapshot under it before drawing.
 */
class LogOverlayWidget : ScreenDebugWidget(), LogSink {

    override val title: String = "Log"

    /** Display-only floor; orthogonal to `Log.config`. Set freely at runtime. */
    var minLevel: LogLevel = LogLevel.Debug

    private val capacity: Int = 12
    private val buffer: Array<LogEntry?> = arrayOfNulls(capacity)
    private var head: Int = 0
    private var size: Int = 0
    private val lock = Any()

    init { name = "LogOverlayWidget" }

    override var enabled: Boolean = false
        set(value) {
            val flippingOn = value && !field
            val flippingOff = !value && field
            field = value
            when {
                flippingOn -> {
                    synchronized(lock) {
                        head = 0
                        size = 0
                    }
                    Log.addSink(this)
                }
                flippingOff -> Log.removeSink(this)
            }
        }

    override fun emit(timestampMillis: Long, level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(timestampMillis, level, tag, message)
        synchronized(lock) {
            buffer[head] = entry
            head = (head + 1) % capacity
            if (size < capacity) size++
        }
    }

    override fun drawDebug(renderer: Renderer) {
        val snapshot = synchronized(lock) {
            val out = ArrayList<LogEntry>(size)
            var cursor = (head - size + capacity) % capacity
            repeat(size) {
                buffer[cursor]?.let(out::add)
                cursor = (cursor + 1) % capacity
            }
            out
        }
        if (snapshot.isEmpty()) return
        val owningTree = tree ?: return

        val visible = snapshot.filter { it.level.ordinal >= minLevel.ordinal }
        if (visible.isEmpty()) return

        // Anchor at the bottom-left, oldest visible line on top, newest at the
        // base. Re-anchored every frame so it follows tree.resize.
        val bottom = owningTree.size.y - PADDING
        for ((rowFromBottom, entry) in visible.asReversed().withIndex()) {
            val y = bottom - rowFromBottom * LINE_HEIGHT
            renderer.drawText(
                text = "[${entry.tag}] ${entry.message}",
                position = Vec2(PADDING, y),
                size = TEXT_SIZE,
                color = colorFor(entry.level),
            )
        }
    }

    private fun colorFor(level: LogLevel): Color = when (level) {
        LogLevel.Warn -> DEBUG_LOG_WARN_COLOR
        LogLevel.Error -> DEBUG_LOG_ERROR_COLOR
        else -> DEBUG_LOG_NEUTRAL_COLOR
    }

    companion object {
        private const val PADDING: Float = 8f
        private const val LINE_HEIGHT: Float = 16f
        private const val TEXT_SIZE: Float = 12f
    }
}
