package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.test.fail

private class Sample : Node()

class NodeRegistryTest {

    @BeforeTest fun setUp() { NodeRegistry.clear() }
    @AfterTest fun tearDown() { NodeRegistry.clear() }

    @Test
    fun `register and create round-trip`() {
        NodeRegistry.register(Sample::class) { Sample() }
        val typeName = Sample::class.qualifiedName!!
        val first = NodeRegistry.create(typeName)
        val second = NodeRegistry.create(typeName)
        assertTrue(first is Sample)
        assertNotSame(first, second)
    }

    @Test
    fun `unknown type throws with name in message`() {
        try {
            NodeRegistry.create("com.example.Mystery")
            fail("expected UnknownNodeTypeException")
        } catch (e: UnknownNodeTypeException) {
            assertEquals("com.example.Mystery", e.typeName)
            assertTrue(e.message!!.contains("com.example.Mystery"))
        }
    }

    @Test
    fun `registerEngineTypes registers built-ins`() {
        NodeRegistry.registerEngineTypes()
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Scene"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Node2D"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Shape"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Text"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.physics.BoxCollider"))
    }
}
