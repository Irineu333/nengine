package com.neoutils.engine.bundle.script

internal class ClasspathBundleSource(private val bundleRoot: String) : BundleSource {

    override fun read(path: String): String {
        val fullPath = if (bundleRoot.isEmpty()) path else "$bundleRoot/$path"
        val resource = javaClass.classLoader.getResource(fullPath)
            ?: throw IllegalArgumentException("Script not found on classpath: $fullPath")
        return resource.readText()
    }

    override fun exists(path: String): Boolean {
        val fullPath = if (bundleRoot.isEmpty()) path else "$bundleRoot/$path"
        return javaClass.classLoader.getResource(fullPath) != null
    }
}
