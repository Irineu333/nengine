package com.neoutils.engine.bundle.script

import java.io.File

internal class DirectoryBundleSource(private val bundleDir: File) : BundleSource {

    override fun read(path: String): String {
        val file = File(bundleDir, path)
        if (!file.isFile) {
            throw IllegalArgumentException("Script not found on disk: ${file.absolutePath}")
        }
        return file.readText()
    }

    override fun exists(path: String): Boolean = File(bundleDir, path).isFile
}
