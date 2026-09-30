package com.example.core

import java.util.UUID

/**
 * High-level state enumeration of the Agent lifecycle.
 */
enum class AgentStateType {
    IDLE,
    RUNNING,
    PAUSED,
    ERROR
}

/** Alias for AgentStateType */
typealias AgentStateEnum = AgentStateType

/**
 * Specific reasons explaining why an agent transitioned into a PAUSED state.
 */
enum class PauseReason {
    /** Waiting for operator clearance on a CONFIRMATION_REQUIRED tool as evaluated by PolicyEngine. */
    POLICY_CONFIRMATION,

    /** Manually paused by operator or UI user. */
    MANUAL,

    /** Paused due to approaching budget thresholds. */
    BUDGET_EXHAUSTION,

    /** Temporarily suspended due to API rate-limit delays or backoff. */
    RATE_LIMIT
}

/**
 * Details of a pending policy clearance required to resume a PAUSED agent.
 */
data class AgentPendingConfirmation(
    val permissionId: String,
    val taskId: String,
    val toolId: String,
    val toolName: String = toolId,
    val argumentsJson: String = "{}",
    val reason: String = "",
    val sideEffects: String? = null,
    val expiresAt: Long = System.currentTimeMillis() + (10 * 60 * 1000L)
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}

/**
 * Sealed class hierarchy modeling full Agent states with context, payload metadata,
 * and deterministic transitions based on Policy Engine evaluations.
 */
sealed class AgentState(val type: AgentStateType) {

    /**
     * Agent is idle and waiting to receive tasks.
     */
    data class Idle(
        val message: String = "Ready for tasks",
        val lastCompletedTaskId: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : AgentState(AgentStateType.IDLE)

    /**
     * Agent is actively planning, executing steps, or evaluating tools.
     */
    data class Running(
        val taskId: String,
        val stepId: String? = null,
        val currentTool: String? = null,
        val startedAt: Long = System.currentTimeMillis()
    ) : AgentState(AgentStateType.RUNNING)

    /**
     * Agent execution is suspended, either waiting on policy confirmation
     * or manually halted by an operator.
     */
    data class Paused(
        val reason: PauseReason = PauseReason.POLICY_CONFIRMATION,
        val taskId: String,
        val pendingConfirmation: AgentPendingConfirmation? = null,
        val pausedAt: Long = System.currentTimeMillis()
    ) : AgentState(AgentStateType.PAUSED)

    /**
     * Agent encountered an unrecoverable failure, policy block (DENY),
     * emergency kill-switch activation, or budget exhaustion.
     */
    data class Error(
        val message: String,
        val code: String = "AGENT_ERROR",
        val taskId: String? = null,
        val policyViolation: Boolean = false,
        val recoverable: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    ) : AgentState(AgentStateType.ERROR)
}

/**
 * Historical record documenting an Agent state transition for auditing and observability.
 */
data class StateTransitionRecord(
    val transitionId: String = "trans-${UUID.randomUUID().toString().take(8)}",
    val fromState: AgentState,
    val toState: AgentState,
    val trigger: String,
    val timestamp: Long = System.currentTimeMillis()
)
