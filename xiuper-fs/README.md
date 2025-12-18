# xiuper-fs: Agent-VFS-style Virtual File System (KMP)

Kotlin Multiplatform virtual filesystem abstraction inspired by Agent-VFS, providing POSIX-like file operations over heterogeneous backends (HTTP APIs, databases, MCP resources).

## Features

### Core VFS Infrastructure ✅
- **XiuperFileSystem**: Async POSIX-inspired API (`stat`, `list`, `read`, `write`, `delete`, `mkdir`, `commit`)
- **FsBackend SPI**: Pluggable backend interface for custom implementations
- **XiuperVfs Router**: Mount point resolution with longest-prefix matching
- **Mount Configuration**: Per-mount read-only enforcement and policy hooks

### Backends ✅
1. **REST-FS** (`RestFsBackend`)
   - HTTP-based virtual filesystem with schema-driven path mapping
   - AuthProvider support (Bearer token, custom headers)
   - Capability-aware (declares `supportsMkdir=false`, `supportsDelete=false`)

2. **DB-FS** (`DbFsBackend`)
   - SQLDelight-backed database filesystem
   - Migration framework with PRAGMA user_version tracking
   - Extended attributes support (v2 schema with FsXattr table)
   - Platform-specific drivers:
     - **JVM**: JdbcSqliteDriver (real SQLite)
     - **Android**: AndroidSqliteDriver
     - **iOS**: NativeSqliteDriver
     - **WASM**: WebWorkerDriver + sql.js (browser)
     - **JS/Node**: Explicit unsupported (NoopSqlDriver throws exception)

3. **InMemory** (`InMemoryFsBackend`)
   - In-memory testing backend

### Migration Framework ✅
- `Migration` interface for database schema upgrades
- `MigrationRegistry` with automatic path discovery
- Version tracking via PRAGMA user_version
- Current schema: v2 (FsNode + FsXattr with composite PK and CASCADE DELETE)
- Comprehensive upgrade tests (8/8 passing)

### Security & Policy ✅
- **MountPolicy**: Access control interface
  - `MountPolicy.AllowAll`: Default permissive policy
  - `MountPolicy.ReadOnly`: Deny all write operations
  - Extensible for custom policies (path filters, approval workflows)
- **Audit System**:
  - `FsAuditEvent`: Track operation, path, backend, status, latency
  - `FsAuditCollector`: Pluggable collectors (NoOp, Console)
  - Automatic audit logging in XiuperVfs

### Compose Integration ✅
- **FsRepository**: Reactive filesystem adapter for Compose UI
- StateFlow-based observers:
  - `observeDir(path)`: List<FsEntry>
  - `observeFile(path)`: ByteArray?
  - `observeText(path)`: String
- Write operations with automatic refresh:
  - `writeFile(path, content)`
  - `writeText(path, text)`
  - `deleteFile(path)` - refreshes parent directory
  - `createDir(path)` - refreshes parent directory
- Cache management (`clearCache()`)

### Testing ✅
- **Capability-aware conformance tests**: POSIX subset validation
- **Migration tests**: Infrastructure + upgrade paths (v1→v2)
- **REST conformance**: Capability declaration validation
- **Platform coverage**: JVM, common (shared across targets)

## Platform Support

| Platform | SQLDelight Driver | Status |
|----------|-------------------|--------|
| JVM Desktop | JdbcSqliteDriver | ✅ Production |
| Android | AndroidSqliteDriver | ✅ Production |
| iOS (Darwin) | NativeSqliteDriver | ✅ Production |
| WASM (wasmJs) | WebWorkerDriver + sql.js | ✅ Production |
| JS/Node | NoopSqlDriver | ❌ Unsupported (explicit) |

## Quick Start

### Basic VFS Setup

```kotlin
import cc.unitmesh.xiuper.fs.*
import cc.unitmesh.xiuper.fs.db.*
import cc.unitmesh.xiuper.fs.http.*
import cc.unitmesh.xiuper.fs.policy.*

// Create backends
val dbBackend = DbFsBackend(
    database = createDatabase(DatabaseDriverFactory().createDriver()),
    clock = Clock.System
)

val restBackend = RestFsBackend(
    service = RestServiceConfig(
        baseUrl = "https://api.github.com",
        auth = AuthProvider.BearerToken { System.getenv("GITHUB_TOKEN") }
    )
)

// Mount into VFS
val vfs = XiuperVfs(
    mounts = listOf(
        Mount(
            mountPoint = FsPath("/db"),
            backend = dbBackend,
            policy = MountPolicy.AllowAll
        ),
        Mount(
            mountPoint = FsPath("/github"),
            backend = restBackend,
            readOnly = true,
            policy = MountPolicy.ReadOnly
        )
    ),
    auditCollector = FsAuditCollector.Console
)

// Use VFS
val entries = vfs.list(FsPath("/db"))
val content = vfs.read(FsPath("/github/repos/owner/repo"))
vfs.write(FsPath("/db/notes.txt"), "Hello".encodeToByteArray())
```

### Compose Integration

```kotlin
import cc.unitmesh.xiuper.fs.compose.FsRepository
import kotlinx.coroutines.CoroutineScope

@Composable
fun FileExplorer(scope: CoroutineScope, vfs: XiuperFileSystem) {
    val repo = remember { FsRepository(vfs, scope) }
    val entries by repo.observeDir("/db").collectAsState()
    
    LazyColumn {
        items(entries) { entry ->
            Text(entry.name)
        }
    }
}
```

## Architecture

```
┌─────────────────────────────────────────┐
│           Compose UI Layer              │
│  (FsRepository, StateFlow observers)    │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────┴──────────────────────┐
│        XiuperVfs (Router + Policy)      │
│  - Mount resolution                     │
│  - Read-only enforcement                │
│  - Policy checks (MountPolicy)          │
│  - Audit logging (FsAuditCollector)     │
└──────────────────┬──────────────────────┘
                   │
      ┌────────────┴────────────┐
      │                         │
┌─────┴──────┐          ┌──────┴──────┐
│ DbFsBackend│          │RestFsBackend│
│ (SQLDelight│          │(HTTP+Schema)│
│ + Migration│          │   + Auth   │
│  Framework)│          │  + Caps    │
└────────────┘          └─────────────┘
```

## Testing

```bash
# Run all tests
./gradlew :xiuper-fs:jvmTest

# Run migration tests only
./gradlew :xiuper-fs:jvmTest --tests "*Migration*Test*"

# Run conformance tests
./gradlew :xiuper-fs:jvmTest --tests "*Conformance*"
```

## Future Roadmap

### Phase 2: REST-FS Advanced Features 🚧
- Field projection (`/resources/{id}/fields/{name}`)
- Magic files (`new` for create operations)
- Control files (`query` + `results/` for searches)
- Pagination as infinite directories

### Phase 3: MCP Backend 🔮
- MCP resources integration
- MCP tools as executable files
- Multi-platform transport (stdio, HTTP)

### Phase 4: Advanced Policies 📋
- Path allowlist/denylist (`PathFilterPolicy`)
- Delete approval workflows (`DeleteApprovalPolicy`)
- Rate limiting hooks

## Related Documentation

- Design Document: `docs/filesystem/xiuper-fs-design.md` (gitignored)
- Migration Design: `xiuper-fs/xiuper-fs-db-migration-design.md`
- Issue #519: Agent-VFS proposal

## License

See main project LICENSE.
