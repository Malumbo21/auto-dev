# XiuperFS Database Migration Design

## Overview

XiuperFS DB backend requires a migration strategy to handle schema evolution across versions, ensuring data integrity and backward/forward compatibility where feasible.

## Design Principles

1. **Explicit Versioning**: Use SQLite `PRAGMA user_version` to track current schema version
2. **Additive Migrations**: Prefer adding columns/tables over modifying existing structures
3. **Single-Direction Upgrades**: Focus on forward migrations (v1→v2→v3); downgrades are opt-in
4. **Fail-Safe Initialization**: If user_version = 0, create latest schema directly; else apply incremental migrations
5. **No ORM Magic**: Keep migrations explicit and SQL-based for cross-platform transparency

## Schema Version Management

### Version Numbering

- **Version 1 (Current)**: Initial schema with `FsNode` table (path, isDir, content, mtimeEpochMillis)
- **Future Versions**: Incremental integer (2, 3, ...) per breaking or additive change

### Version Storage

```sql
-- Read current version
PRAGMA user_version;

-- Set version after migration
PRAGMA user_version = <new_version>;
```

## Migration Architecture

### File Structure

```
xiuper-fs/src/commonMain/kotlin/cc/unitmesh/xiuper/fs/db/
├── DbFsBackend.kt              # Main backend (unchanged API)
├── DatabaseDriverFactory.kt     # Expect/actual driver factories (existing)
├── migrations/
│   ├── Migration.kt            # Migration interface + registry
│   ├── Migration_1_to_2.kt     # Example: add xattr support
│   └── Migration_2_to_3.kt     # Future migrations
└── XiuperFsDatabase.sq          # (generated; we work with FsNode.sq)
```

### Migration Interface

```kotlin
package cc.unitmesh.xiuper.fs.db.migrations

import app.cash.sqldelight.db.SqlDriver

/**
 * Represents a single migration step from [fromVersion] to [toVersion].
 */
interface Migration {
    val fromVersion: Int
    val toVersion: Int
    val description: String
    
    /**
     * Apply the migration to [driver].
     * Throws if migration fails; caller rolls back or aborts.
     */
    fun migrate(driver: SqlDriver)
}

/**
 * Registry of all migrations, ordered by fromVersion.
 */
object MigrationRegistry {
    val all: List<Migration> = listOf(
        // Migration_1_to_2(),
        // Migration_2_to_3(),
    )
    
    /**
     * Returns the chain of migrations needed to go from [current] to [target].
     * Throws if no path exists or if current > target (downgrade unsupported by default).
     */
    fun path(current: Int, target: Int): List<Migration> {
        if (current > target) {
            throw UnsupportedOperationException("Downgrade from $current to $target not supported")
        }
        if (current == target) return emptyList()
        
        val chain = mutableListOf<Migration>()
        var v = current
        while (v < target) {
            val next = all.firstOrNull { it.fromVersion == v }
                ?: throw IllegalStateException("No migration from version $v")
            if (next.toVersion > target) {
                throw IllegalStateException("Migration overshoots target: ${next.toVersion} > $target")
            }
            chain.add(next)
            v = next.toVersion
        }
        return chain
    }
}
```

### Example Migration (v1 → v2: Add Extended Attributes)

```kotlin
package cc.unitmesh.xiuper.fs.db.migrations

import app.cash.sqldelight.db.SqlDriver

/**
 * Migration 1→2: Add xattr (extended attributes) table for POSIX xattr emulation.
 */
class Migration_1_to_2 : Migration {
    override val fromVersion = 1
    override val toVersion = 2
    override val description = "Add extended attributes table"
    
    override fun migrate(driver: SqlDriver) {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS FsXattr (
                    path TEXT NOT NULL,
                    name TEXT NOT NULL,
                    value BLOB NOT NULL,
                    PRIMARY KEY (path, name)
                )
            """.trimIndent(),
            parameters = 0,
            binders = null
        )
    }
}
```

## Migration Process

### On Database Initialization

```kotlin
fun createDatabase(driverFactory: DatabaseDriverFactory): XiuperFsDatabase {
    val driver = driverFactory.createDriver()
    
    val currentVersion = getUserVersion(driver)
    val targetVersion = XiuperFsDatabase.Schema.version.toInt()
    
    when {
        currentVersion == 0 -> {
            // Fresh DB: create latest schema directly
            XiuperFsDatabase.Schema.create(driver)
            setUserVersion(driver, targetVersion)
        }
        currentVersion < targetVersion -> {
            // Existing DB: apply migrations
            val migrations = MigrationRegistry.path(currentVersion, targetVersion)
            for (migration in migrations) {
                try {
                    migration.migrate(driver)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Migration failed: ${migration.description} ($currentVersion → ${migration.toVersion})",
                        e
                    )
                }
            }
            setUserVersion(driver, targetVersion)
        }
        currentVersion > targetVersion -> {
            // Future DB opened by older code: error or warn
            throw IllegalStateException(
                "Database version $currentVersion is newer than supported $targetVersion. " +
                "Please upgrade the application."
            )
        }
        else -> {
            // Already up-to-date
        }
    }
    
    return XiuperFsDatabase(driver)
}

private fun getUserVersion(driver: SqlDriver): Int {
    return driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            if (cursor.next().value) {
                QueryResult.Value(cursor.getLong(0)?.toInt() ?: 0)
            } else {
                QueryResult.Value(0)
            }
        },
        parameters = 0,
        binders = null
    ).value
}

private fun setUserVersion(driver: SqlDriver, version: Int) {
    driver.execute(
        identifier = null,
        sql = "PRAGMA user_version = $version",
        parameters = 0,
        binders = null
    )
}
```

## Testing Strategy

1. **Upgrade Path Tests** (JVM-only initially):
   - Create DB at v1, apply migration, verify v2 schema + data integrity
   - Multi-step: v1→v2→v3
2. **Idempotency Tests**:
   - Apply migration twice; second run should be no-op or safe
3. **Rollback Tests** (optional):
   - If downgrade support is added, test v2→v1
4. **Data Preservation**:
   - Insert data at v1, migrate to v2, verify data intact + new features work

## Package Structure Impact

### Before (current)
```
cc.unitmesh.xiuper.fs.db/
├── DbFsBackend.kt
├── DatabaseDriverFactory.kt
└── FsNode.sq (sqldelight source)
```

### After (with migrations)
```
cc.unitmesh.xiuper.fs.db/
├── DbFsBackend.kt
├── DatabaseDriverFactory.kt
├── migrations/
│   ├── Migration.kt
│   ├── Migration_1_to_2.kt
│   └── (future migrations)
└── FsNode.sq
```

This keeps migrations as a subpackage under `db`, making it clear they're internal to the DB backend and not exposed to the public `xiuper-fs` API.

## Rollout Plan

1. **Phase 1** (Current PR): Ship v1 schema as-is; document migration design
2. **Phase 2**: Implement `Migration` interface + registry + `createDatabase` upgrade logic
3. **Phase 3**: Add first real migration (e.g., xattr or file metadata columns)
4. **Phase 4**: Write conformance/migration tests

## Open Questions

1. **Downgrade Support**: Do we need it? (Recommendation: No, unless enterprise requirement)
2. **Migration Naming**: File-per-migration vs single registry file?
3. **SQL vs Kotlin DSL**: Keep raw SQL for transparency or use a builder?

---

**Status**: Design approved, ready for implementation in next iteration.
