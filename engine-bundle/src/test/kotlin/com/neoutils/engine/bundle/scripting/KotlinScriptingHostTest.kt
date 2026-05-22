package com.neoutils.engine.bundle.scripting

import com.neoutils.engine.scene.Node
import java.io.File
import kotlin.test.*

class KotlinScriptingHostTest {

    private lateinit var tempCacheDir: File

    @BeforeTest
    fun setUp() {
        tempCacheDir = File("build/tmp/test-scripting-cache-${System.nanoTime()}")
        tempCacheDir.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        tempCacheDir.deleteRecursively()
    }

    private fun host(engineVersion: String? = null) =
        KotlinScriptingHost(ScriptSource.Classpath(""), tempCacheDir, engineVersion)

    private fun cacheBins(): Int =
        tempCacheDir.listFiles { _, name -> name.endsWith(".bin") }?.size ?: 0

    @Test
    fun testSmoke() {
        val resolved = host().compileAll(setOf("scripts/hello.nengine.kts"))
        val klass = resolved.getValue("scripts/hello.nengine.kts")
        assertEquals("HelloNode", klass.simpleName)
        val instance = klass.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as Node
        assertEquals("HelloNode", instance.name)
    }

    @Test
    fun testMissingScript() {
        assertFailsWith<IllegalArgumentException> {
            host().compileAll(setOf("scripts/does_not_exist.nengine.kts"))
        }
    }

    @Test
    fun testZeroClasses() {
        val ex = assertFailsWith<IllegalStateException> {
            host().compileAll(setOf("scripts/zero_classes.nengine.kts"))
        }
        assertTrue(ex.message!!.contains("contains zero top-level classes extending Node"))
    }

    @Test
    fun testTwoClasses() {
        val ex = assertFailsWith<IllegalStateException> {
            host().compileAll(setOf("scripts/two_classes.nengine.kts"))
        }
        assertTrue(ex.message!!.contains("contains more than one top-level class extending Node"))
    }

    @Test
    fun testNonNode() {
        val ex = assertFailsWith<IllegalStateException> {
            host().compileAll(setOf("scripts/non_node.nengine.kts"))
        }
        assertTrue(ex.message!!.contains("contains zero top-level classes extending Node"))
    }

    @Test
    fun testRoundRobinSucceedsRegardlessOfInputOrder() {
        // DepB extends DepA. Submitting [dep_b, dep_a] is the "wrong" order;
        // round-robin must still resolve both.
        val resolved = host().compileAll(
            linkedSetOf("scripts/dep_b.nengine.kts", "scripts/dep_a.nengine.kts")
        )
        val depBKlass = resolved.getValue("scripts/dep_b.nengine.kts")
        assertEquals("DepB", depBKlass.simpleName)
        val instance = depBKlass.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val runTest = instance.javaClass.getMethod("runTest")
        assertEquals(42, runTest.invoke(instance) as Int)
    }

    @Test
    fun testSyntaxErrorFailsFast() {
        val ex = assertFailsWith<IllegalStateException> {
            host().compileAll(setOf("scripts/syntax_error.nengine.kts"))
        }
        assertTrue(ex.message!!.contains("Compilation failed"))
    }

    @Test
    fun testCyclicDependencyDetected() {
        val ex = assertFailsWith<CyclicScriptDependencyError> {
            host().compileAll(
                setOf("scripts/cycle_a.nengine.kts", "scripts/cycle_b.nengine.kts")
            )
        }
        assertTrue(ex.paths.contains("scripts/cycle_a.nengine.kts"))
        assertTrue(ex.paths.contains("scripts/cycle_b.nengine.kts"))
    }

    @Test
    fun testCacheHitWithSameImportSet() {
        val first = host()
        first.compileAll(setOf("scripts/hello.nengine.kts"))
        assertEquals(1, first.compilationCount)

        val second = host()
        second.compileAll(setOf("scripts/hello.nengine.kts"))
        assertEquals(0, second.compilationCount)
        assertEquals(1, cacheBins())
    }

    @Test
    fun testCacheInvalidatedByImportSetChange() {
        host().compileAll(setOf("scripts/hello.nengine.kts"))
        assertEquals(1, cacheBins())

        // Compiling hello alongside dep_a forces dep_a to compile first (it has
        // no pending refs) which expands wrapperClasses before hello is reached;
        // hello's cache key then differs from the standalone run and a new
        // cache entry is written.
        host().compileAll(
            linkedSetOf("scripts/dep_a.nengine.kts", "scripts/hello.nengine.kts")
        )
        assertEquals(3, cacheBins())
    }

    @Test
    fun testCacheInvalidatedByEngineVersion() {
        host(engineVersion = "v1").compileAll(setOf("scripts/hello.nengine.kts"))
        assertEquals(1, cacheBins())

        host(engineVersion = "v2").compileAll(setOf("scripts/hello.nengine.kts"))
        assertEquals(2, cacheBins())
    }

    @Test
    fun testOrphanBytecodeRemovedAtBootstrap() {
        val classesDir = File(tempCacheDir, "classes")
        classesDir.mkdirs()
        val orphan = File(classesDir, "scripts/Orphan_nengine.class")
        orphan.parentFile.mkdirs()
        orphan.writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
        assertTrue(orphan.exists())

        host().compileAll(setOf("scripts/hello.nengine.kts"))
        assertFalse(orphan.exists())
    }
}
