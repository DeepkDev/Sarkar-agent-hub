package com.example.tools

import com.example.contracts.*

/**
 * Model Context Protocol (MCP) Adapter Interface & Stub.
 * Maps MCP JSON-RPC protocol tools into Agent Hub's standard ToolManifest without changing the Registry or Executor.
 * Status: PLACEHOLDER (as specified in Section 4 & 17).
 */
interface McpClient {
    suspend fun listMcpTools(): List<McpToolDefinition>
    suspend fun callMcpTool(name: String, arguments: Map<String, Any?>): Map<String, Any?>
}

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

class McpToolAdapterStub(
    private val mcpServerUri: String = "mcp://localhost:8080/sse"
) : BaseTool {
    override val manifest = ToolManifest(
        id = "mcp_adapter_stub",
        name = "MCP Protocol Adapter",
        version = "1.0.0",
        description = "Bridge to external Model Context Protocol (MCP) servers. Dynamically discovers and proxies remote tools.",
        category = "Integration",
        inputParameters = listOf(
            ToolParameter("targetTool", "string", "Name of the target MCP tool"),
            ToolParameter("arguments", "object", "Arguments map forwarded to the MCP tool")
        ),
        permissionLevel = PermissionLevel.SAFE,
        sideEffects = SideEffectType.EXTERNAL,
        status = CapabilityStatus.PLACEHOLDER,
        source = ToolSource.MCP
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        return ToolResult.notImplemented(
            "mcp_adapter_stub",
            "MCP Server connection ($mcpServerUri) is in PLACEHOLDER state. External MCP servers can be configured in settings."
        )
    }
}
