package cc.unitmesh.devins.ui.compose.agent.acp

import cc.unitmesh.config.AcpAgentConfig

// JS platform: ACP is handled in TypeScript CLI via AcpClientConnection.ts
// The Kotlin/JS Compose UI does not support ACP connections directly.
actual fun createAcpConnection(): AcpConnection? = null

actual fun createConnectionForAgent(config: AcpAgentConfig): AcpConnection? = null

actual fun isAcpSupported(): Boolean = false
