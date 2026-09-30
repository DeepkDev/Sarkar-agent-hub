package com.example.providers

import com.example.contracts.*

class LocalProviderStub(
    val endpointUrl: String = "http://localhost:11434/api/generate"
) : AIProvider {
    override val id: String = "local_provider_stub"
    override val displayName: String = "Local LLM (Ollama / llama.cpp)"

    override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        structuredOutput = false,
        toolCalling = false,
        streaming = true,
        maxContextTokens = 8_192,
        vision = false
    )

    override suspend fun health(): ProviderHealth = ProviderHealth.NOT_CONFIGURED

    override suspend fun generate(req: GenerateRequest): GenerateResponse {
        throw UnsupportedOperationException("Local LLM Provider is in PLACEHOLDER state (endpoint $endpointUrl).")
    }

    override suspend fun plan(req: PlanRequest): Plan {
        throw UnsupportedOperationException("Local LLM Provider is in PLACEHOLDER state (endpoint $endpointUrl).")
    }

    override suspend fun decideTool(req: ToolDecisionRequest): ToolDecision {
        throw UnsupportedOperationException("Local LLM Provider is in PLACEHOLDER state.")
    }

    override suspend fun verify(req: VerifyRequest): VerificationResult {
        throw UnsupportedOperationException("Local LLM Provider is in PLACEHOLDER state.")
    }

    override suspend fun summarize(req: SummarizeRequest): FinalResponse {
        throw UnsupportedOperationException("Local LLM Provider is in PLACEHOLDER state.")
    }

    override fun normalizeError(e: Throwable): AgentError {
        return AgentError(
            code = ErrorCode.PROVIDER_UNAVAILABLE,
            message = "Local provider not configured: ${e.message}"
        )
    }
}
