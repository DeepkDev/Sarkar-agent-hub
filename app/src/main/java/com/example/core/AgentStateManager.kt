package com.example.core

import com.example.contracts.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Result of a tool evaluation accompanied by the resulting AgentState transition.
 */
data class PolicyTransitionResult(
    val allowed: Boolean,
    val needsConfirmation: Boolean,
    val newState: AgentState,
    val decision: PolicyDecision,
    val evaluationResult: EvaluationResult
)

/**
 * State manager service orchestrating [AgentState] transitions driven deterministically
 * by [PolicyEngine] clearance rules, operator interventions, and execution milestones.
 */
class AgentStateManager(
    val policyEngine: PolicyEngine = PolicyEngine()
) {
    private val _state = MutableStateFlow<AgentState>(AgentState.Idle())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _stateType = MutableStateFlow(AgentStateType.IDLE)
    val stateType: StateFlow<AgentStateType> = _stateType.asStateFlow()

    private val _history = mutableListOf<StateTransitionRecord>()
    val transitionHistory: List<StateTransitionRecord>
        @Synchronized get() = _history.toList()

    val currentState: AgentState
        get() = _state.value

    val currentStateType: AgentStateType
        get() = _state.value.type

    val isIdle: Boolean get() = _state.value is AgentState.Idle
    val isRunning: Boolean get() = _state.value is AgentState.Running
    val isPaused: Boolean get() = _state.value is AgentState.Paused
    val isError: Boolean get() = _state.value is AgentState.Error

    /**
     * Transitions agent to [AgentState.Running] when initiating a new task.
     */
    @Synchronized
    fun startTask(taskId: String): Boolean {
        val current = _state.value
        if (current is AgentState.Running && current.taskId == taskId) {
            return true
        }

        val newState = AgentState.Running(taskId = taskId, startedAt = System.currentTimeMillis())
        transitionTo(newState, trigger = "Task Started: $taskId")
        return true
    }

    /**
     * Updates current step/tool details during active [AgentState.Running] execution.
     */
    @Synchronized
    fun updateRunningStep(stepId: String?, toolId: String?): Boolean {
        val current = _state.value
        if (current !is AgentState.Running) return false

        val updated = current.copy(stepId = stepId, currentTool = toolId)
        _state.value = updated
        return true
    }

    /**
     * Evaluates a [ToolRequest] via [PolicyEngine] and automatically transitions
     * the agent state based on the evaluation:
     * - [EvaluationResult.ALLOW] -> Updates/maintains [AgentState.Running]
     * - [EvaluationResult.REQUIRE_CONFIRMATION] -> Transitions to [AgentState.Paused]
     * - [EvaluationResult.DENY] -> Transitions to [AgentState.Error]
     */
    @Synchronized
    fun evaluateAndTransition(
        request: ToolRequest,
        context: TaskContext
    ): PolicyTransitionResult {
        // Evaluate policy
        val eval = policyEngine.evaluate(request, context)
        val decision = eval.toPolicyDecision()

        val transitionResult = when (eval.result) {
            EvaluationResult.ALLOW -> {
                val current = _state.value
                val runningState = if (current is AgentState.Running) {
                    current.copy(currentTool = request.toolId)
                } else {
                    AgentState.Running(taskId = context.taskId, currentTool = request.toolId)
                }
                transitionTo(runningState, trigger = "Policy ALLOW: ${request.toolId}")
                PolicyTransitionResult(
                    allowed = true,
                    needsConfirmation = false,
                    newState = runningState,
                    decision = decision,
                    evaluationResult = eval.result
                )
            }

            EvaluationResult.REQUIRE_CONFIRMATION -> {
                val confirmation = AgentPendingConfirmation(
                    permissionId = eval.permissionId ?: "perm-${UUID.randomUUID().toString().take(8)}",
                    taskId = context.taskId,
                    toolId = request.toolId,
                    toolName = request.toolName,
                    argumentsJson = "{}",
                    reason = eval.reason,
                    expiresAt = eval.expiresAt ?: (System.currentTimeMillis() + 600_000L)
                )
                val pausedState = AgentState.Paused(
                    reason = PauseReason.POLICY_CONFIRMATION,
                    taskId = context.taskId,
                    pendingConfirmation = confirmation,
                    pausedAt = System.currentTimeMillis()
                )
                transitionTo(pausedState, trigger = "Policy REQUIRE_CONFIRMATION: ${request.toolId}")
                PolicyTransitionResult(
                    allowed = false,
                    needsConfirmation = true,
                    newState = pausedState,
                    decision = decision,
                    evaluationResult = eval.result
                )
            }

            EvaluationResult.DENY -> {
                val isKillSwitch = policyEngine.config.killSwitchEngaged
                val errorCode = if (isKillSwitch) "KILL_SWITCH_BLOCKED" else "POLICY_DENIED"
                val errorState = AgentState.Error(
                    message = eval.reason,
                    code = errorCode,
                    taskId = context.taskId,
                    policyViolation = true,
                    recoverable = !isKillSwitch,
                    timestamp = System.currentTimeMillis()
                )
                transitionTo(errorState, trigger = "Policy DENY: ${request.toolId} ($errorCode)")
                PolicyTransitionResult(
                    allowed = false,
                    needsConfirmation = false,
                    newState = errorState,
                    decision = decision,
                    evaluationResult = eval.result
                )
            }
        }

        return transitionResult
    }

    /**
     * Backward-compatible evaluation and state transition taking [ToolManifest], arguments, and [AgentTask].
     */
    @Synchronized
    fun evaluateAndTransition(
        tool: ToolManifest,
        argsJson: String,
        task: AgentTask,
        policyConfig: PolicyConfig = policyEngine.config
    ): PolicyTransitionResult {
        val decision = PolicyEngine.evaluate(tool, argsJson, task, policyConfig)
        val evalResult = when (decision.type) {
            PolicyDecisionType.ALLOW -> EvaluationResult.ALLOW
            PolicyDecisionType.REQUIRE_CONFIRMATION -> EvaluationResult.REQUIRE_CONFIRMATION
            PolicyDecisionType.DENY -> EvaluationResult.DENY
        }

        val transitionResult = when (decision.type) {
            PolicyDecisionType.ALLOW -> {
                val current = _state.value
                val runningState = if (current is AgentState.Running) {
                    current.copy(currentTool = tool.id)
                } else {
                    AgentState.Running(taskId = task.taskId, currentTool = tool.id)
                }
                transitionTo(runningState, trigger = "Policy ALLOW: ${tool.id}")
                PolicyTransitionResult(
                    allowed = true,
                    needsConfirmation = false,
                    newState = runningState,
                    decision = decision,
                    evaluationResult = evalResult
                )
            }

            PolicyDecisionType.REQUIRE_CONFIRMATION -> {
                val permId = decision.permissionId ?: "perm-${UUID.randomUUID().toString().take(8)}"
                val confirmation = AgentPendingConfirmation(
                    permissionId = permId,
                    taskId = task.taskId,
                    toolId = tool.id,
                    toolName = tool.name,
                    argumentsJson = argsJson,
                    reason = decision.reason,
                    expiresAt = decision.expiresAt ?: (System.currentTimeMillis() + 600_000L)
                )
                val pausedState = AgentState.Paused(
                    reason = PauseReason.POLICY_CONFIRMATION,
                    taskId = task.taskId,
                    pendingConfirmation = confirmation,
                    pausedAt = System.currentTimeMillis()
                )
                transitionTo(pausedState, trigger = "Policy REQUIRE_CONFIRMATION: ${tool.id}")
                PolicyTransitionResult(
                    allowed = false,
                    needsConfirmation = true,
                    newState = pausedState,
                    decision = decision,
                    evaluationResult = evalResult
                )
            }

            PolicyDecisionType.DENY -> {
                val isKillSwitch = policyConfig.killSwitchEngaged
                val errorState = AgentState.Error(
                    message = decision.reason,
                    code = if (isKillSwitch) "KILL_SWITCH_BLOCKED" else "POLICY_DENIED",
                    taskId = task.taskId,
                    policyViolation = true,
                    recoverable = !isKillSwitch,
                    timestamp = System.currentTimeMillis()
                )
                transitionTo(errorState, trigger = "Policy DENY: ${tool.id}")
                PolicyTransitionResult(
                    allowed = false,
                    needsConfirmation = false,
                    newState = errorState,
                    decision = decision,
                    evaluationResult = evalResult
                )
            }
        }

        return transitionResult
    }

    /**
     * Resolves a pending confirmation when the agent is in [AgentState.Paused].
     * - Approved: Resumes execution in [AgentState.Running]
     * - Rejected: Transitions agent to [AgentState.Error] with OPERATOR_DENIED code
     */
    @Synchronized
    fun handleConfirmation(
        permissionId: String,
        approved: Boolean,
        operatorComment: String? = null
    ): AgentState {
        val current = _state.value
        if (current !is AgentState.Paused) {
            return current
        }

        val pending = current.pendingConfirmation
        if (pending != null && pending.permissionId != permissionId) {
            return current
        }

        val newState = if (approved) {
            AgentState.Running(
                taskId = current.taskId,
                currentTool = pending?.toolId,
                startedAt = System.currentTimeMillis()
            )
        } else {
            val comment = operatorComment?.let { " ($it)" } ?: ""
            AgentState.Error(
                message = "Operator denied authorization for tool '${pending?.toolName ?: permissionId}'$comment",
                code = "OPERATOR_DENIED",
                taskId = current.taskId,
                policyViolation = true,
                recoverable = true,
                timestamp = System.currentTimeMillis()
            )
        }

        val triggerDesc = if (approved) "Operator APPROVED ($permissionId)" else "Operator REJECTED ($permissionId)"
        transitionTo(newState, trigger = triggerDesc)
        return newState
    }

    /**
     * Manually suspends active agent execution.
     */
    @Synchronized
    fun pause(reason: PauseReason = PauseReason.MANUAL): Boolean {
        val current = _state.value
        if (current !is AgentState.Running) return false

        val pausedState = AgentState.Paused(
            reason = reason,
            taskId = current.taskId,
            pausedAt = System.currentTimeMillis()
        )
        transitionTo(pausedState, trigger = "Execution Paused: ${reason.name}")
        return true
    }

    /**
     * Resumes execution from [AgentState.Paused] if no blocking policy confirmations remain.
     */
    @Synchronized
    fun resume(): Boolean {
        val current = _state.value
        if (current !is AgentState.Paused) return false

        if (current.reason == PauseReason.POLICY_CONFIRMATION && current.pendingConfirmation != null) {
            return false // Cannot resume without resolving pending confirmation
        }

        val runningState = AgentState.Running(
            taskId = current.taskId,
            startedAt = System.currentTimeMillis()
        )
        transitionTo(runningState, trigger = "Execution Resumed from ${current.reason.name}")
        return true
    }

    /**
     * Transitions agent to [AgentState.Error] on faults, budget limits, or uncaught exceptions.
     */
    @Synchronized
    fun fail(
        message: String,
        code: String = "AGENT_ERROR",
        taskId: String? = null,
        recoverable: Boolean = true
    ): Boolean {
        val effectiveTaskId = taskId ?: when (val s = _state.value) {
            is AgentState.Running -> s.taskId
            is AgentState.Paused -> s.taskId
            is AgentState.Error -> s.taskId
            is AgentState.Idle -> null
        }

        val errorState = AgentState.Error(
            message = message,
            code = code,
            taskId = effectiveTaskId,
            policyViolation = false,
            recoverable = recoverable,
            timestamp = System.currentTimeMillis()
        )
        transitionTo(errorState, trigger = "Error encountered: $code - $message")
        return true
    }

    /**
     * Concludes task execution and returns the agent to [AgentState.Idle].
     */
    @Synchronized
    fun completeTask(message: String = "Task completed successfully"): Boolean {
        val current = _state.value
        val taskId = when (current) {
            is AgentState.Running -> current.taskId
            is AgentState.Paused -> current.taskId
            else -> null
        }

        val idleState = AgentState.Idle(
            message = message,
            lastCompletedTaskId = taskId,
            timestamp = System.currentTimeMillis()
        )
        transitionTo(idleState, trigger = "Task Completed: ${taskId ?: "N/A"}")
        return true
    }

    /**
     * Resets agent to [AgentState.Idle] clearing any prior errors.
     */
    @Synchronized
    fun reset(): Boolean {
        val idleState = AgentState.Idle(message = "Agent reset to initial idle state")
        transitionTo(idleState, trigger = "Agent Reset")
        return true
    }

    /**
     * Activates the global emergency kill switch in PolicyEngine and halts the agent into [AgentState.Error].
     */
    @Synchronized
    fun engageEmergencyKillSwitch(reason: String = "Global Emergency Kill Switch activated"): AgentState {
        policyEngine.config = policyEngine.config.copy(killSwitchEngaged = true)
        val currentTaskId = when (val s = _state.value) {
            is AgentState.Running -> s.taskId
            is AgentState.Paused -> s.taskId
            is AgentState.Error -> s.taskId
            is AgentState.Idle -> null
        }

        val errorState = AgentState.Error(
            message = reason,
            code = "KILL_SWITCH_ENGAGED",
            taskId = currentTaskId,
            policyViolation = true,
            recoverable = false,
            timestamp = System.currentTimeMillis()
        )
        transitionTo(errorState, trigger = "EMERGENCY KILL SWITCH ENGAGED")
        return errorState
    }

    /**
     * Disengages the emergency kill switch and resets agent state to [AgentState.Idle].
     */
    @Synchronized
    fun disengageEmergencyKillSwitch(): AgentState {
        policyEngine.config = policyEngine.config.copy(killSwitchEngaged = false)
        val idleState = AgentState.Idle(message = "Emergency Kill Switch disengaged, agent ready")
        transitionTo(idleState, trigger = "Emergency Kill Switch Disengaged")
        return idleState
    }

    /**
     * Internal transition helper updating StateFlows and appending to the audit transition history.
     */
    private fun transitionTo(newState: AgentState, trigger: String) {
        val previousState = _state.value
        _state.value = newState
        _stateType.value = newState.type

        val record = StateTransitionRecord(
            fromState = previousState,
            toState = newState,
            trigger = trigger,
            timestamp = System.currentTimeMillis()
        )
        _history.add(record)
    }
}
