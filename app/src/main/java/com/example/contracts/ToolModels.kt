package com.example.contracts

import com.squareup.moshi.JsonClass

enum class CapabilityStatus {
    IMPLEMENTED,
    PLACEHOLDER,
    NOT_CONFIGURED,
    DISABLED
}

enum class PermissionLevel {
    PUBLIC,
    SAFE,
    CONFIRMATION_REQUIRED,
    RESTRICTED
}

enum class SideEffectType {
    NONE,
    READ,
    WRITE,
    EXTERNAL
}

enum class ToolSource {
    BUILTIN,
    MCP,
    HTTP
}

@JsonClass(generateAdapter = true)
data class ToolParameter(
    val name: String,
    val type: String, // "string", "number", "boolean", "object", "array"
    val description: String,
    val required: Boolean = true,
    val default: String? = null
)

/**
 * Common executable interface for all AI Agent tools.
 * Each tool exposes a deterministic [manifest] describing capabilities, parameters,
 * security clearance, and side-effects, along with an asynchronous [execute] invocation method.
 */
interface Tool {
    val manifest: ToolManifest
    suspend fun execute(args: Map<String, Any?>): ToolResult
}

@JsonClass(generateAdapter = true)
data class ToolManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String,
    val category: String,
    val inputParameters: List<ToolParameter> = emptyList(),
    val permissionLevel: PermissionLevel = PermissionLevel.PUBLIC,
    val sideEffects: SideEffectType = SideEffectType.NONE,
    val idempotent: Boolean = true,
    val timeoutMs: Long = 10_000L,
    val rateLimitPerMinute: Int = 60,
    val status: CapabilityStatus = CapabilityStatus.IMPLEMENTED,
    val enabled: Boolean = true,
    val source: ToolSource = ToolSource.BUILTIN
)

@JsonClass(generateAdapter = true)
data class ToolResult(
    val toolId: String,
    val success: Boolean,
    val data: String, // Serialized result or error message
    val executionTimeMs: Long,
    val status: CapabilityStatus = CapabilityStatus.IMPLEMENTED
) {
    companion object {
        fun notImplemented(toolId: String, message: String = "Tool is a PLACEHOLDER and not implemented"): ToolResult {
            return ToolResult(
                toolId = toolId,
                success = false,
                data = message,
                executionTimeMs = 0L,
                status = CapabilityStatus.PLACEHOLDER
            )
        }

        fun notConfigured(toolId: String, message: String = "Tool is NOT_CONFIGURED"): ToolResult {
            return ToolResult(
                toolId = toolId,
                success = false,
                data = message,
                executionTimeMs = 0L,
                status = CapabilityStatus.NOT_CONFIGURED
            )
        }
    }
}
