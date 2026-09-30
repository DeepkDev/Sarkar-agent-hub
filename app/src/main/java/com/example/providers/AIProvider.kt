package com.example.providers

import com.example.contracts.*

/**
 * Pluggable provider interface for AI / LLM engines powering the agent system.
 * Defines standard operations: generate, plan, decideTool, and verify to support
 * a modular, multi-model execution architecture.
 */
interface AIProvider {
    /** Unique alphanumeric identifier for this provider implementation (e.g. "gemini_provider", "mock_provider") */
    val id: String

    /** Human-readable display label for the UI */
    val displayName: String

    /** Declarative feature matrix and token limit capabilities */
    fun capabilities(): ProviderCapabilities

    /** Checks connectivity, API key validity, or service availability */
    suspend fun health(): ProviderHealth

    /**
     * Generates a freeform completion or structured text response from a prompt.
     *
     * @param req Structured generation parameters including prompt, system prompt, temperature, and tokens.
     * @return Generated text and execution metadata.
     */
    suspend fun generate(req: GenerateRequest): GenerateResponse

    /**
     * Convenience text generation overload.
     *
     * @param prompt User prompt text.
     * @return Generated string.
     */
    suspend fun generate(prompt: String): String = generate(GenerateRequest(prompt = prompt)).text

    /**
     * Decomposes a user goal into an actionable multi-step execution [Plan].
     *
     * @param req Request containing user goal, available tool manifests, and execution origin.
     * @return Ordered plan containing steps and expected tools.
     */
    suspend fun plan(req: PlanRequest): Plan

    /**
     * Analyzes current step context and tool schema to construct tool execution arguments.
     *
     * @param req Current step, previous execution outputs, and tool schemas.
     * @return Chosen tool and argument mapping.
     */
    suspend fun decideTool(req: ToolDecisionRequest): ToolDecision

    /**
     * Validates whether a tool execution output satisfies the step expectations.
     *
     * @param req Step description, expected output, and actual tool result.
     * @return Verification outcome (PASS, RETRY, REPLAN, FAIL) with rationale.
     */
    suspend fun verify(req: VerifyRequest): VerificationResult

    /**
     * Generates a concise spoken summary and comprehensive narrative of task execution.
     *
     * @param req User goal, final plan, and execution logs.
     * @return Spoken summary and detailed textual response.
     */
    suspend fun summarize(req: SummarizeRequest): FinalResponse

    /**
     * Normalizes underlying provider/network exceptions into typed [AgentError].
     */
    fun normalizeError(e: Throwable): AgentError
}
