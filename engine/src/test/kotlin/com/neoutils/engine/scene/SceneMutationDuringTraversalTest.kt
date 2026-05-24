package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.physics.PhysicsSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class LifecycleSpy(name: String, val log: MutableList<String>) : Node() {
    init { this.name = name }
    override fun onEnter() { log += "enter:$name" }
    override fun onExit() { log += "exit:$name" }
}

class SceneMutationDuringTraversalTest {

    @Test
    fun `addChild during onProcess does not crash and applies before next phase`() {
        val scene = Scene()
        val log = mutableListOf<String>()
        val spawned = LifecycleSpy("spawn", log)
        val spawner = object : Node() {
            var didSpawn = false
            override fun onProcess(dt: Float) {
                if (!didSpawn) {
                    didSpawn = true
                    scene.addChild(spawned)
                }
            }
        }
        scene.addChild(spawner)
        scene.start()
        log.clear()

        scene.process(0.016f)
        scene.applyPending()

        assertTrue(spawned.isLive, "spawned child should be live after drain")
        assertEquals(listOf("enter:spawn"), log)
        assertTrue(scene.children.contains(spawned))
    }

    @Test
    fun `removeChild during onProcess does not crash and applies before next phase`() {
        val scene = Scene()
        val log = mutableListOf<String>()
        val victim = LifecycleSpy("victim", log)
        scene.addChild(victim)
        val killer = object : Node() {
            override fun onProcess(dt: Float) { scene.removeChild(victim) }
        }
        scene.addChild(killer)
        scene.start()
        log.clear()

        scene.process(0.016f)
        scene.applyPending()

        assertFalse(victim.isLive)
        assertEquals(listOf("exit:victim"), log)
        assertFalse(scene.children.contains(victim))
    }

    @Test
    fun `addChild during onCollide does not crash and applies before next phase`() {
        val scene = Scene()
        val log = mutableListOf<String>()
        val spawned = LifecycleSpy("spawn", log)
        val a = object : BoxCollider() {
            var didSpawn = false
            override fun onCollide(other: Collider) {
                if (!didSpawn) {
                    didSpawn = true
                    scene.addChild(spawned)
                }
            }
        }
        val b = BoxCollider().apply { size = Vec2(10f, 10f) }
        scene.addChild(a)
        scene.addChild(b)
        scene.start()
        log.clear()

        PhysicsSystem().step(scene)
        scene.applyPending()

        assertTrue(spawned.isLive)
        assertEquals(listOf("enter:spawn"), log)
    }

    @Test
    fun `removeChild during onCollide does not crash and applies before next phase`() {
        val scene = Scene()
        val log = mutableListOf<String>()
        val victim = LifecycleSpy("victim", log)
        scene.addChild(victim)
        val a = object : BoxCollider() {
            override fun onCollide(other: Collider) { scene.removeChild(victim) }
        }
        val b = BoxCollider().apply { size = Vec2(10f, 10f) }
        scene.addChild(a)
        scene.addChild(b)
        scene.start()
        log.clear()

        PhysicsSystem().step(scene)
        scene.applyPending()

        assertFalse(victim.isLive)
        assertEquals(listOf("exit:victim"), log)
    }
}
