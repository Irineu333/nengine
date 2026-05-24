package com.neoutils.engine.scene

import com.neoutils.engine.input.Input
import com.neoutils.engine.render.Renderer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class Scene : Node() {

    /** Set by the runtime (`GameLoop`) at the start of each tick. */
    @Transient
    @Volatile var input: Input? = null
        internal set

    @Transient
    var width: Float = 0f
        private set

    @Transient
    var height: Float = 0f
        private set

    /**
     * `true` while the scene is inside an `onProcess`, `onPhysicsProcess`,
     * `onCollide` or `onDraw` traversal (or another physics phase). Read by
     * `Node.addChild` / `Node.removeChild` to decide between immediate
     * mutation and enqueuing onto the pending queues.
     */
    @Transient
    internal var isMutationDeferred: Boolean = false
        private set

    /**
     * `true` only during render traversal. `addChild`/`removeChild` called
     * while this is set are logged and dropped (decision D5 in design.md):
     * scene-graph mutation during render has no use case and would cost more
     * complexity than it saves to support.
     */
    @Transient
    internal var isRendering: Boolean = false
        private set

    /** Called by the runtime when the rendering surface size changes. */
    fun resize(width: Float, height: Float) {
        if (width == this.width && height == this.height) return
        this.width = width
        this.height = height
        onResize(width, height)
    }

    open fun onResize(width: Float, height: Float) {}

    fun start() {
        if (!isLive) attachToLiveTree(this)
    }

    fun stop() {
        if (isLive) detachFromLiveTree()
    }

    fun process(dt: Float) {
        if (!isLive) return
        runTraversal(rendering = false) { traverseProcess(this, dt) }
    }

    fun physicsProcess(dt: Float) {
        if (!isLive) return
        runTraversal(rendering = false) { traversePhysicsProcess(this, dt) }
    }

    fun render(renderer: Renderer) {
        if (!isLive) return
        runTraversal(rendering = true) { traverseDraw(this, renderer) }
    }

    /**
     * Drains pending child mutations enqueued during the previous traversal.
     * Drained in post-order (children first), removals before additions, so
     * lifecycle is coherent across the whole subtree before the next phase
     * begins.
     */
    fun applyPending() {
        drainPending()
    }

    internal fun beginPhysicsPhase() {
        isMutationDeferred = true
    }

    internal fun endPhysicsPhase() {
        isMutationDeferred = false
    }

    private inline fun runTraversal(rendering: Boolean, block: () -> Unit) {
        isMutationDeferred = true
        isRendering = rendering
        try {
            block()
        } finally {
            isRendering = false
            isMutationDeferred = false
        }
    }

    private fun traverseProcess(node: Node, dt: Float) {
        node.onProcess(dt)
        for (child in node.children) traverseProcess(child, dt)
    }

    private fun traversePhysicsProcess(node: Node, dt: Float) {
        node.onPhysicsProcess(dt)
        for (child in node.children) traversePhysicsProcess(child, dt)
    }

    private fun traverseDraw(node: Node, renderer: Renderer) {
        node.onDraw(renderer)
        for (child in node.children) traverseDraw(child, renderer)
    }
}
