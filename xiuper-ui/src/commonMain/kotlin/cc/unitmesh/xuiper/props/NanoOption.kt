package cc.unitmesh.xuiper.props

/**
 * Generic option data structure for UI components.
 * 
 * Used by:
 * - Select, Radio, RadioGroup (selection options)
 * - DataTable (column definitions with meta)
 * - Any component that needs key-value pairs
 * 
 * @property value The unique identifier/value of the option
 * @property label The display text for the option
 */
data class NanoOption(
    val value: String,
    val label: String
) {
    companion object {
        /**
         * Create an option where value and label are the same.
         */
        fun simple(value: String): NanoOption = NanoOption(value, value)
    }
}

/**
 * Extended option with additional metadata.
 * 
 * Used for components that need extra properties beyond value/label,
 * such as DataTable columns (sortable, format, width, etc.)
 * 
 * @property value The unique identifier/key of the option
 * @property label The display text for the option
 * @property meta Additional properties as key-value pairs
 */
data class NanoOptionWithMeta(
    val value: String,
    val label: String,
    val meta: Map<String, Any?> = emptyMap()
) {
    /**
     * Get a meta value by key with type casting.
     */
    inline fun <reified T> getMeta(key: String): T? = meta[key] as? T
    
    /**
     * Get a meta value by key with default.
     */
    inline fun <reified T> getMeta(key: String, default: T): T = (meta[key] as? T) ?: default
    
    /**
     * Convert to simple NanoOption (drops meta).
     */
    fun toNanoOption(): NanoOption = NanoOption(value, label)
    
    companion object {
        /**
         * Create from NanoOption with empty meta.
         */
        fun from(option: NanoOption): NanoOptionWithMeta = 
            NanoOptionWithMeta(option.value, option.label)
        
        /**
         * Create a simple option where value and label are the same.
         */
        fun simple(value: String): NanoOptionWithMeta = 
            NanoOptionWithMeta(value, value)
    }
}

