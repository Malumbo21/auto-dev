/**
 * ACP (Agent Client Protocol) module for the JS CLI.
 *
 * Provides both:
 * - AcpAgentServer: Exposes our CodingAgent as an ACP agent (other editors connect to us)
 * - AcpClientConnection: Connects to external ACP agents (we connect to them)
 */

export { startAcpAgentServer } from './AcpAgentServer.js';
export { AcpClientConnection, type AcpClientCallbacks } from './AcpClientConnection.js';
