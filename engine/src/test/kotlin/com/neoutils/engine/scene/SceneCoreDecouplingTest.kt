package com.neoutils.engine.scene

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SceneCoreDecouplingTest {

    @Test
    fun `Scene_kt does not import from engine_dx`() {
        val source = locateSceneSource()
        val offenders = source.readLines()
            .map { it.trim() }
            .filter { it.startsWith("import com.neoutils.engine.dx") }
        assertTrue(
            offenders.isEmpty(),
            "Scene.kt must not import from com.neoutils.engine.dx; found:\n${offenders.joinToString("\n")}",
        )
    }

    private fun locateSceneSource(): File {
        val candidates = listOf(
            "engine/src/main/kotlin/com/neoutils/engine/scene/Scene.kt",
            "src/main/kotlin/com/neoutils/engine/scene/Scene.kt",
            "../engine/src/main/kotlin/com/neoutils/engine/scene/Scene.kt",
        )
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) return f
        }
        error("Scene.kt source not found in any of: $candidates (cwd=${File(".").absolutePath})")
    }
}
