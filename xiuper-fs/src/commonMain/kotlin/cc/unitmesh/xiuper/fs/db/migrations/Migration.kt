package cc.unitmesh.xiuper.fs.db.migrations

import app.cash.sqldelight.db.SqlDriver

/**
 * Represents a single migration step from [fromVersion] to [toVersion].
 * 
 * Each migration should be idempotent where possible (safe to run multiple times).
 */
interface Migration {
    val fromVersion: Int
    val toVersion: Int
    val description: String
    
    /**
     * Apply the migration to [driver].
     * 
     * @throws Exception if migration fails; caller should handle rollback/abort
     */
    fun migrate(driver: SqlDriver)
}

/**
 * Registry of all available migrations, ordered by fromVersion.
 */
object MigrationRegistry {
    val all: List<Migration> = listOf(
        // Future migrations will be registered here, e.g.:
        // Migration_1_to_2(),
        // Migration_2_to_3(),
    )
    
    /**
     * Returns the chain of migrations needed to go from [current] to [target].
     * 
     * @throws IllegalStateException if no valid migration path exists
     * @throws UnsupportedOperationException if downgrade is attempted (current > target)
     */
    fun path(current: Int, target: Int): List<Migration> {
        if (current > target) {
            throw UnsupportedOperationException(
                "Downgrade from version $current to $target is not supported. " +
                "Please use a compatible application version."
            )
        }
        if (current == target) return emptyList()
        
        val chain = mutableListOf<Migration>()
        var version = current
        
        while (version < target) {
            val nextMigration = all.firstOrNull { it.fromVersion == version }
                ?: throw IllegalStateException(
                    "No migration found from version $version. " +
                    "Database may be corrupted or migration registry incomplete."
                )
            
            if (nextMigration.toVersion > target) {
                throw IllegalStateException(
                    "Migration ${nextMigration.description} overshoots target: " +
                    "${nextMigration.toVersion} > $target"
                )
            }
            
            chain.add(nextMigration)
            version = nextMigration.toVersion
        }
        
        return chain
    }
}
