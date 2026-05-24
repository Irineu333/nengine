package com.neoutils.engine.serialization

/**
 * Minimal per-node event hub. A `Signal<T>` keeps a list of handlers that
 * are invoked in registration order whenever [emit] is called.
 *
 * `Signal` is runtime state — handlers are plain lambdas and are never
 * serialized. The owning node must expose its signals as `@Transient` (or
 * keep them in a Python-side map populated at attach time, which is how the
 * Python bridge wires them) and re-register handlers from `onEnter`
 * after loading a scene.
 */
class Signal<T> {

    private val handlers = mutableListOf<(T) -> Unit>()

    /**
     * Subscribes [handler] and returns a [Disposable] whose `dispose()` call
     * removes the subscription. Handlers fire in the order they were
     * connected; a handler may register or unregister other handlers from
     * inside its body and the change becomes visible on the *next* [emit].
     */
    fun connect(handler: (T) -> Unit): Disposable {
        handlers += handler
        return Disposable { handlers.remove(handler) }
    }

    /** Convenience overload: removes [handler] if it was previously connected. */
    fun disconnect(handler: (T) -> Unit) {
        handlers.remove(handler)
    }

    /**
     * Iterates over a snapshot of the handler list so that registering or
     * removing a handler during emission stays consistent: a handler added
     * by another handler only fires on the next emission; a handler removed
     * by another handler still runs in the current emission if it was in
     * the snapshot.
     */
    fun emit(value: T) {
        val snapshot = handlers.toList()
        for (handler in snapshot) handler(value)
    }
}

/**
 * Wraps a one-shot dispose action. Returned by [Signal.connect] so callers
 * can hold a handle to their subscription without leaking the lambda
 * identity.
 */
class Disposable internal constructor(private val onDispose: () -> Unit) {
    fun dispose() {
        onDispose()
    }
}
