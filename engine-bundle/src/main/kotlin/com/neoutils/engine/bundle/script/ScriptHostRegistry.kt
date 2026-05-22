package com.neoutils.engine.bundle.script

object ScriptHostRegistry {

    private val hosts: MutableMap<String, ScriptHost> = mutableMapOf()

    fun register(host: ScriptHost) {
        hosts[host.extension] = host
    }

    fun clear() {
        hosts.clear()
    }

    fun hostFor(path: String): ScriptHost? {
        val dotIdx = path.lastIndexOf('.')
        if (dotIdx < 0) return null
        return hosts[path.substring(dotIdx)]
    }

    fun loadAll(paths: Set<String>, bundle: BundleSource): Map<String, Script> =
        paths.associateWith { path ->
            val host = hostFor(path) ?: throw UnsupportedScriptExtensionException(path)
            host.load(path, bundle)
        }
}

class UnsupportedScriptExtensionException(val path: String) : RuntimeException(
    run {
        val ext = path.substringAfterLast('.', "")
        "No ScriptHost registered for '$path' (extension: ${if (ext.isNotEmpty()) ".$ext" else "<none>"})"
    }
)
