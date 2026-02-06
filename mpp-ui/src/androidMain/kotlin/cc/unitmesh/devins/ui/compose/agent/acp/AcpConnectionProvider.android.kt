package cc.unitmesh.devins.ui.compose.agent.acp

import cc.unitmesh.config.AcpAgentConfig

actual fun createAcpConnection(): AcpConnection? = null

actual fun createConnectionForAgent(config: AcpAgentConfig): AcpConnection? = null

actual fun isAcpSupported(): Boolean = false
