package com.neoutils.engine.bundle.python

import org.graalvm.polyglot.Context
import org.junit.Test
import kotlin.test.assertEquals

class GraalPySmokeTest {

    @Test
    fun `python context evaluates 1 plus 1`() {
        Context.newBuilder("python").build().use { ctx ->
            val result = ctx.eval("python", "1 + 1")
            assertEquals(2, result.asInt())
        }
    }
}
