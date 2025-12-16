package cc.unitmesh.xuiper.prompt

/**
 * JVM implementation of ResourceLoader using classloader.
 */
actual object ResourceLoader {
    actual fun loadResource(path: String): String {
        return ResourceLoader::class.java.classLoader
            ?.getResourceAsStream(path)
            ?.bufferedReader()
            ?.readText()
            ?: throw IllegalStateException("Cannot load resource: $path")
    }
}

