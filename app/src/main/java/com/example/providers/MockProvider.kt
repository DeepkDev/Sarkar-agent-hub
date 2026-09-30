package com.example.providers

import com.example.contracts.*
import kotlinx.coroutines.delay
import java.util.UUID

enum class MockSimulationMode {
    STANDARD,
    TIMEOUT_SIM,
    MALFORMED_OUTPUT_SIM,
    TOOL_FAILURE_SIM,
    REPLAN_PASS_SIM
}

class MockProvider(
    var simulationMode: MockSimulationMode = MockSimulationMode.STANDARD
) : AIProvider {
    override val id: String = "mock_provider"
    override val displayName: String = "Mock AI Provider (Deterministic)"

    private var replanAttemptCount = 0

    override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        structuredOutput = true,
        toolCalling = true,
        streaming = false,
        maxContextTokens = 16_000,
        vision = false
    )

    override suspend fun health(): ProviderHealth = ProviderHealth.IMPLEMENTED

    override suspend fun generate(req: GenerateRequest): GenerateResponse {
        if (simulationMode == MockSimulationMode.TIMEOUT_SIM) {
            delay(15_000L)
        }
        if (simulationMode == MockSimulationMode.MALFORMED_OUTPUT_SIM) {
            throw IllegalArgumentException("Malformed output simulation")
        }

        val text = when {
            req.prompt.contains("hello", ignoreCase = true) -> "Hello! I am your AI assistant."
            req.prompt.contains("summary", ignoreCase = true) -> "Summary of request completed successfully."
            else -> "Generated mock response for: ${req.prompt.take(60)}"
        }

        return GenerateResponse(
            text = text,
            tokensUsed = text.length / 4 + 5,
            finishReason = "STOP",
            model = id
        )
    }

    override suspend fun plan(req: PlanRequest): Plan {
        if (simulationMode == MockSimulationMode.TIMEOUT_SIM) {
            delay(15_000L) // Exceeds timeout
        }

        if (simulationMode == MockSimulationMode.MALFORMED_OUTPUT_SIM) {
            throw IllegalArgumentException("Malformed JSON payload: unexpected token '<' at position 0")
        }

        val prompt = req.userGoal.lowercase()
        val steps = mutableListOf<PlanStep>()

        when {
            prompt.contains("calc") || prompt.contains("math") || prompt.contains("+") || prompt.contains("*") || prompt.contains("/") -> {
                steps.add(
                    PlanStep(
                        id = "step-1",
                        description = "Parse arithmetic expression and calculate result",
                        expectedTool = "calculator"
                    )
                )
            }

            prompt.contains("time") || prompt.contains("date") || prompt.contains("today") -> {
                steps.add(
                    PlanStep(
                        id = "step-1",
                        description = "Retrieve current timezone-aware timestamp",
                        expectedTool = "datetime"
                    )
                )
            }

            prompt.contains("email") || prompt.contains("send") -> {
                steps.add(
                    PlanStep(
                        id = "step-1",
                        description = "Dispatch approval-gated email message",
                        expectedTool = "send_email"
                    )
                )
            }

            prompt.contains("delete") || prompt.contains("purge") -> {
                steps.add(
                    PlanStep(
                        id = "step-1",
                        description = "Request restricted data purge",
                        expectedTool = "delete_data"
                    )
                )
            }

            prompt.contains("file") || prompt.contains("write") -> {
                steps.add(
                    PlanStep(
                        id = "step-1",
                        description = "Access filesystem resource",
                        expectedTool = "file_operation"
                    )
                )
            }

            prompt.contains("search") || prompt.contains("web") -> {
                steps.add(
                    PlanStep(
                        id = "step-1",
                        description = "Query external web search engine",
                        expectedTool = "web_search"
                    )
                )
            }

            prompt.contains("knowledge") || prompt.contains("doc") || prompt.contains("policy") || prompt.contains("voice") || prompt.contains("sarkar") -> {
                steps.add(
                    PlanStep(
                        id = "step-1",
                        description = "Search local knowledge base for verified information",
                        expectedTool = "knowledge_search"
                    )
                )
            }

            else -> {
                // Default two-step plan: inspect time, then calculate benchmark
                steps.add(
                    PlanStep(
                        id = "step-1",
                        description = "Fetch current system timestamp",
                        expectedTool = "datetime"
                    )
                )
                steps.add(
                    PlanStep(
                        id = "step-2",
                        description = "Compute verification benchmark formula",
                        expectedTool = "calculator",
                        dependencies = listOf("step-1")
                    )
                )
            }
        }

        return Plan(
            planId = "plan-${UUID.randomUUID().toString().take(6)}",
            explanation = "Deterministic execution plan generated by Mock AI Provider for '${req.userGoal}'.",
            steps = steps
        )
    }

    override suspend fun decideTool(req: ToolDecisionRequest): ToolDecision {
        val toolId = req.step.expectedTool
        val args = mutableMapOf<String, Any?>()

        when (toolId) {
            "calculator" -> {
                args["expression"] = "(45 * 12) + (100 / 4)"
            }
            "datetime" -> {
                args["timezone"] = "UTC"
            }
            "knowledge_search" -> {
                args["query"] = "architecture state machine"
            }
            "text_processing" -> {
                args["text"] = "SARKAR Agent Hub is ready."
                args["operation"] = "stats"
            }
            "json_processing" -> {
                args["jsonString"] = "{\"status\": \"active\", \"version\": 2}"
                args["operation"] = "format"
            }
            "send_email" -> {
                args["to"] = "operations@sarkar.hub"
                args["subject"] = "Agent Hub Orchestration Approval Test"
                args["body"] = "This action requires explicit supervisor clearance before dispatch."
            }
            "delete_data" -> {
                args["recordId"] = "rec-demo-9999"
            }
            "file_operation" -> {
                args["path"] = "/sandbox/test.txt"
                args["action"] = "write"
            }
            "web_search" -> {
                args["query"] = "AI agent orchestration architectures"
            }
            else -> {
                args["input"] = "default_param"
            }
        }

        return ToolDecision(
            toolId = toolId,
            arguments = args,
            rationale = "Selected tool '$toolId' based on plan step '${req.step.description}'"
        )
    }

    override suspend fun verify(req: VerifyRequest): VerificationResult {
        if (simulationMode == MockSimulationMode.REPLAN_PASS_SIM && replanAttemptCount == 0) {
            replanAttemptCount++
            return VerificationResult(
                outcome = VerificationOutcome.REPLAN,
                reason = "Simulated initial verification mismatch: output requires replanning.",
                suggestedFeedback = "Adjust calculation parameters and re-verify."
            )
        }

        if (simulationMode == MockSimulationMode.TOOL_FAILURE_SIM) {
            return VerificationResult(
                outcome = VerificationOutcome.FAIL,
                reason = "Simulated permanent verification failure."
            )
        }

        return if (req.toolResult.success) {
            VerificationResult(
                outcome = VerificationOutcome.PASS,
                reason = "Tool output verified successfully against step criteria."
            )
        } else {
            VerificationResult(
                outcome = VerificationOutcome.FAIL,
                reason = "Tool returned error: ${req.toolResult.data}"
            )
        }
    }

    override suspend fun summarize(req: SummarizeRequest): FinalResponse {
        val successCount = req.toolResults.count { it.successful }
        val spoken = "Task completed successfully. Executed $successCount step actions with full verification."
        val full = buildString {
            append("### SARKAR Agent Hub — Task Execution Report\n\n")
            append("**Goal:** ${req.userGoal}\n\n")
            append("**Plan ID:** ${req.plan.planId}\n")
            append("**Steps Executed:** ${req.plan.steps.size}\n\n")
            append("#### Step Verification Results:\n")
            req.toolResults.forEachIndexed { idx, res ->
                append("${idx + 1}. **${res.toolId}** (Time: ${res.durationMs}ms) — ${if (res.successful) "PASS" else "FAIL"}\n")
            }
            append("\n*Generated by MockProvider (MOCK) — zero external API keys required.*")
        }

        return FinalResponse(
            spokenSummary = spoken,
            fullText = full,
            language = req.language
        )
    }

    override fun normalizeError(e: Throwable): AgentError {
        return AgentError(
            code = ErrorCode.UNKNOWN_ERROR,
            message = e.message ?: "Mock provider encountered an unexpected condition"
        )
    }
}
