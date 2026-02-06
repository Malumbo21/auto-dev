package cc.unitmesh.devins.ui.compose.agent.acp

// JS platform: ACP is handled in TypeScript CLI via AcpClientConnection.ts
// The Kotlin/JS Compose UI does not support ACP connections directly.
actual fun createAcpConnection(): AcpConnection? = null

actual fun isAcpSupported(): Boolean = false
