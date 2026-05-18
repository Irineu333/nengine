package com.neoutils.engine.scene

import com.neoutils.engine.dx.Debug
import com.neoutils.engine.input.Input
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

open class Scene : Node() {

    /** Set by the runtime (`GameLoop`) at the start of each tick. */
    @Volatile var input: Input? = null
        internal set

    var width: Float = 0f
        private set
    var height: Float = 0f
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

    fun update(dt: Float) {
        if (!isLive) return
        traverseUpdate(this, dt)
    }

    fun render(renderer: Renderer) {
        if (!isLive) return
        traverseRender(this, renderer)
        if (Debug.colliderVisualization) drawColliderBounds(this, renderer)
    }

    private fun traverseUpdate(node: Node, dt: Float) {
        node.onUpdate(dt)
        for (child in node.children) traverseUpdate(child, dt)
    }

    private fun traverseRender(node: Node, renderer: Renderer) {
        node.onRender(renderer)
        for (child in node.children) traverseRender(child, renderer)
    }

    private fun drawColliderBounds(node: Node, renderer: Renderer) {
        if (node is Collider) renderer.drawRect(node.bounds(), DEBUG_COLLIDER_COLOR, filled = false)
        for (child in node.children) drawColliderBounds(child, renderer)
    }

    companion object {
        private val DEBUG_COLLIDER_COLOR: Color = Color(0f, 1f, 0f, 0.8f)
    }
}
