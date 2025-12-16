package cc.unitmesh.xuiper.prompt

/**
 * Platform-specific resource loader for prompt templates.
 * 
 * On JVM: Uses classloader to load resources from JAR/classpath
 * On WasmJs: Returns embedded resource strings or loads from network
 */
expect object ResourceLoader {
    /**
     * Load a resource file as a string.
     * 
     * @param path Resource path (e.g., "prompts/standard.txt")
     * @return Resource content as string
     * @throws IllegalStateException if resource cannot be loaded
     */
    fun loadResource(path: String): String
}

