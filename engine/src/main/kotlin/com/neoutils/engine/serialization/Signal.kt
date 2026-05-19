package com.neoutils.engine.serialization

/**
 * Minimal per-node event bus. Handlers are plain lambdas — not persisted by
 * the scene file. The owning node must expose the signal as a `@Transient`
 * field and re-register handlers from `onEnter` after a load.
 */
class Signal<T> {

    private val handlers = mutableListOf<(T) -> Unit>()

    operator fun plusAssign(handler: (T) -> Unit) {
        handlers += handler
    }

    operator fun minusAssign(handler: (T) -> Unit) {
        handlers -= handler
    }

    /**
     * Iterates over a snapshot of the handler list so registration or removal
     * during emission stays consistent: a handler added by another handler
     * only receives the next emission; a handler removed by another handler
     * still receives the current one if it was in the snapshot.
     */
    fun emit(value: T) {
        val snapshot = handlers.toList()
        for (handler in snapshot) handler(value)
    }
}
