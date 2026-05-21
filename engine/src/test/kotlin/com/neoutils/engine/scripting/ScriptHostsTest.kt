package com.neoutils.engine.scripting

import com.neoutils.engine.scene.Node
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ScriptHostsTest {

    private class MockScriptHost : ScriptHost {
        override fun compile(path: String): KClass<out Node> = error("Not needed")
        override fun factoryFor(path: String): () -> Node = error("Not needed")
        override fun pathFor(klass: KClass<out Node>): String? = null
    }

    @AfterTest
    fun tearDown() {
        ScriptHosts.clear()
    }

    @Test
    fun testRegisterCurrentClear() {
        assertNull(ScriptHosts.current())

        val host = MockScriptHost()
        ScriptHosts.register(host)
        assertSame(host, ScriptHosts.current())

        ScriptHosts.clear()
        assertNull(ScriptHosts.current())
    }
}
