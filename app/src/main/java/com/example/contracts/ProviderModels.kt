package com.example.contracts

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProviderCapabilities(
    val structuredOutput: Boolean = true,
    val toolCalling: Boolean = true,
    val streaming: Boolean = false,
    val maxContextTokens: Int = 32_000,
    val vision: Boolean = false
)

enum class ProviderHealth {
    IMPLEMENTED,
    NOT_CONFIGURED,
    DEGRADED
}

enum class ErrorCode {
    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    MALFORMED_OUTPUT,
    BUDGET_EXCEEDED,
    POLICY_DENIED,
    PERMISSION_EXPIRED,
    TOOL_FAILED,
    STATE_TRANSITION_INVALID,
    UNKNOWN_ERROR
}

@JsonClass(generateAdapter = true)
data class AgentError(
    val code: ErrorCode,
    val message: String,
    val details: String? = null,
    val retryable: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GenerateRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val stopSequences: List<String> = emptyList(),
    val context: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GenerateResponse(
    val text: String,
    val tokensUsed: Int = 0,
    val finishReason: String = "STOP",
    val model: String? = null
)

@JsonClass(generateAdapter = true)
data class PlanRequest(
    val userGoal: String,
    val availableTools: List<ToolManifest>,
    val origin: OriginSource = OriginSource.WEB
)

@JsonClass(generateAdapter = true)
data class ToolDecisionRequest(
    val step: PlanStep,
    val previousResults: List<ToolExecutionRecord>,
    val availableTools: List<ToolManifest>
)

@JsonClass(generateAdapter = true)
data class VerifyRequest(
    val step: PlanStep,
    val toolResult: ToolResult
)

@JsonClass(generateAdapter = true)
data class SummarizeRequest(
    val userGoal: String,
    val plan: Plan,
    val toolResults: List<ToolExecutionRecord>,
    val language: String = "en"
)
