package com.neoutils.engine.scene

import com.neoutils.engine.render.Renderer

abstract class Node {

    var name: String = this::class.simpleName ?: "Node"

    var parent: Node? = null
        private set

    private val _children: MutableList<Node> = mutableListOf()
    val children: List<Node> get() = _children

    var isLive: Boolean = false
        private set

    /**
     * Cached owning `Scene` while this node is live. Populated by
     * `attachToLiveTree` before `onEnter` runs and cleared by
     * `detachFromLiveTree` after `onExit` returns. Lets `rootScene()` run in
     * O(1) instead of walking the parent chain every frame.
     */
    var scene: Scene? = null
        internal set

    fun addChild(child: Node) {
        require(child.parent == null) { "Node '${child.name}' already has a parent" }
        require(child !== this) { "Cannot add a node as its own child" }
        child.parent = this
        _children.add(child)
        if (isLive) {
            val owning = if (this is Scene) this else scene
            if (owning != null) child.attachToLiveTree(owning)
        }
    }

    fun removeChild(child: Node) {
        require(child.parent === this) { "Node '${child.name}' is not a child of '$name'" }
        if (isLive) child.detachFromLiveTree()
        _children.remove(child)
        child.parent = null
    }

    internal fun attachToLiveTree(owningScene: Scene) {
        if (isLive) return
        scene = owningScene
        isLive = true
        onEnter()
        for (child in _children) child.attachToLiveTree(owningScene)
    }

    internal fun detachFromLiveTree() {
        if (!isLive) return
        for (child in _children) child.detachFromLiveTree()
        onExit()
        isLive = false
        scene = null
    }

    /** Returns the owning `Scene` in O(1) when live, or `null` otherwise. */
    fun rootScene(): Scene? = scene

    open fun onEnter() {}
    open fun onUpdate(dt: Float) {}
    open fun onRender(renderer: Renderer) {}
    open fun onExit() {}
}
