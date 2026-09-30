package com.example.contracts

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class TaskStatus {
    PENDING,
    PLANNING,
    WAITING_FOR_PERMISSION,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED,
    EXPIRED;

    val isTerminal: Boolean
        get() = this in setOf(COMPLETED, FAILED, CANCELLED, EXPIRED)
}

enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}

enum class OriginSource {
    WEB,
    API,
    SARKAR
}

enum class VerificationOutcome {
    PASS,
    RETRY,
    REPLAN,
    FAIL
}

/**
 * Task execution budget configuration managing execution constraints
 * including max steps, max tool calls, max wall-clock duration, retries, and token limits.
 */
@JsonClass(generateAdapter = true)
data class TaskBudget(
    val maxSteps: Int = 10,
    val maxToolCalls: Int = 15,
    val maxWallClockMs: Long = 60_000L,
    val maxRetriesPerStep: Int = 2,
    val maxReplans: Int = 2,
    val maxTokens: Int = 8000,
    val perToolTimeoutMs: Long = 10_000L
) {
    /** Checks whether any hard budget limits have been breached. */
    fun isExhausted(stepsUsed: Int, toolCallsUsed: Int, wallClockMsUsed: Long): Boolean {
        return stepsUsed >= maxSteps || toolCallsUsed >= maxToolCalls || wallClockMsUsed >= maxWallClockMs
    }

    fun stepsRemaining(stepsUsed: Int): Int = (maxSteps - stepsUsed).coerceAtLeast(0)
    fun toolCallsRemaining(toolCallsUsed: Int): Int = (maxToolCalls - toolCallsUsed).coerceAtLeast(0)
    fun wallClockMsRemaining(wallClockMsUsed: Long): Long = (maxWallClockMs - wallClockMsUsed).coerceAtLeast(0L)

    fun toBudgetConfig(): BudgetConfig = BudgetConfig(
        maxSteps = maxSteps,
        maxToolCalls = maxToolCalls,
        maxRetriesPerStep = maxRetriesPerStep,
        maxReplans = maxReplans,
        maxTokens = maxTokens,
        maxWallClockMs = maxWallClockMs,
        perToolTimeoutMs = perToolTimeoutMs
    )

    companion object {
        val DEFAULT = TaskBudget()
        val QUICK = TaskBudget(maxSteps = 3, maxToolCalls = 5, maxWallClockMs = 20_000L)
        val EXTENDED = TaskBudget(maxSteps = 25, maxToolCalls = 40, maxWallClockMs = 180_000L, maxTokens = 20_000)

        fun fromBudgetConfig(config: BudgetConfig): TaskBudget = TaskBudget(
            maxSteps = config.maxSteps,
            maxToolCalls = config.maxToolCalls,
            maxWallClockMs = config.maxWallClockMs,
            maxRetriesPerStep = config.maxRetriesPerStep,
            maxReplans = config.maxReplans,
            maxTokens = config.maxTokens,
            perToolTimeoutMs = config.perToolTimeoutMs
        )
    }
}

@JsonClass(generateAdapter = true)
data class BudgetConfig(
    val maxSteps: Int = 10,
    val maxToolCalls: Int = 15,
    val maxRetriesPerStep: Int = 2,
    val maxReplans: Int = 2,
    val maxTokens: Int = 8000,
    val maxWallClockMs: Long = 60_000L,
    val perToolTimeoutMs: Long = 10_000L
) {
    fun toTaskBudget(): TaskBudget = TaskBudget.fromBudgetConfig(this)
}

@JsonClass(generateAdapter = true)
data class BudgetUsage(
    val stepsUsed: Int = 0,
    val toolCallsUsed: Int = 0,
    val retriesUsed: Int = 0,
    val replansUsed: Int = 0,
    val tokensUsed: Int = 0,
    val wallClockMsUsed: Long = 0L
)

@JsonClass(generateAdapter = true)
data class PlanStep(
    val id: String = UUID.randomUUID().toString().take(8),
    val description: String,
    val expectedTool: String,
    val dependencies: List<String> = emptyList(),
    val status: StepStatus = StepStatus.PENDING,
    val toolInput: String? = null,
    val toolOutput: String? = null,
    val failureReason: String? = null
)

@JsonClass(generateAdapter = true)
data class Plan(
    val planId: String = UUID.randomUUID().toString().take(8),
    val explanation: String = "",
    val steps: List<PlanStep> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ToolDecision(
    val toolId: String,
    val arguments: Map<String, Any?>,
    val rationale: String
)

@JsonClass(generateAdapter = true)
data class VerificationResult(
    val outcome: VerificationOutcome,
    val reason: String,
    val suggestedFeedback: String? = null
)

@JsonClass(generateAdapter = true)
data class FinalResponse(
    val spokenSummary: String,
    val fullText: String,
    val language: String = "en"
)

@JsonClass(generateAdapter = true)
data class ToolExecutionRecord(
    val executionId: String = UUID.randomUUID().toString().take(8),
    val toolId: String,
    val argumentsJson: String,
    val outputJson: String,
    val durationMs: Long,
    val successful: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class PermissionEventRecord(
    val permissionId: String,
    val toolId: String,
    val argumentsJson: String,
    val sideEffectsSummary: String,
    val decision: String, // "PENDING", "APPROVED", "DENIED", "EXPIRED"
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class AgentTask(
    val taskId: String = UUID.randomUUID().toString(),
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val request: String,
    val plan: Plan? = null,
    val currentStepId: String? = null,
    val toolCalls: List<ToolExecutionRecord> = emptyList(),
    val permissionEvents: List<PermissionEventRecord> = emptyList(),
    val errors: List<String> = emptyList(),
    val budgetConfig: BudgetConfig = BudgetConfig(),
    val budgetUsage: BudgetUsage = BudgetUsage(),
    val finalResponse: FinalResponse? = null,
    val traceId: String = "trace-${UUID.randomUUID().toString().take(12)}",
    val origin: OriginSource = OriginSource.WEB,
    val dryRun: Boolean = false,
    val budget: TaskBudget = TaskBudget.fromBudgetConfig(budgetConfig)
)
