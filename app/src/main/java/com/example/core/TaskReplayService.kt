package com.example.core

import com.example.contracts.*
import com.example.storage.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Detailed audit of an individual event-driven state transition evaluated
 * against the [AgentState] sealed class rules during replay.
 */
data class ReplayTransitionRecord(
    val eventIndex: Int,
    val event: TaskEvent,
    val fromState: AgentState,
    val toState: AgentState,
    val isValid: Boolean,
    val ruleExplanation: String
)

/**
 * Encapsulates a violation of the sealed class state transition rules during replay.
 */
data class StateTransitionViolation(
    val eventIndex: Int,
    val event: TaskEvent,
    val fromState: AgentState,
    val violationMessage: String
)

/**
 * Result of replaying and deriving task state from an [EventRepository] event sequence.
 */
data class ReplayTaskStateResult(
    val taskId: String,
    val task: AgentTask,
    val agentState: AgentState,
    val eventsReplayed: Int,
    val isCryptographicChainIntact: Boolean,
    val areStateTransitionsValid: Boolean,
    val transitionHistory: List<ReplayTransitionRecord>,
    val firstViolation: StateTransitionViolation? = null
)

/**
 * Service layer interface for replaying [TaskEvent]s persisted in [EventRepository],
 * deriving the active [AgentTask] and sealed [AgentState], and enforcing strict
 * state transition validation rules against the [AgentState] sealed class hierarchy.
 */
interface TaskReplayService {
    /**
     * Replays all cryptographically verified events for [taskId] from [EventRepository],
     * derives the current domain [AgentTask] and sealed [AgentState], and verifies
     * every transition against the [AgentState] rules.
     */
    suspend fun replayAndDeriveTaskState(taskId: String): ReplayTaskStateResult

    /**
     * Replays events up to a given index or cutoff (time-travel debugging),
     * deriving intermediate task state and validating transitions.
     */
    suspend fun replayToPointInTime(taskId: String, maxEventIndex: Int): ReplayTaskStateResult

    /**
     * Reactive flow observing real-time event additions from [EventRepository]
     * and continuously emitting updated derived states and verified transitions.
     */
    fun observeDerivedTaskState(taskId: String): Flow<ReplayTaskStateResult>

    /**
     * Pure function validating an sequence of [TaskEvent]s against
     * the sealed class transition rules, returning transitions and validity.
     */
    fun validateAndTraceTransitions(
        events: List<TaskEvent>,
        initialState: AgentState = AgentState.Idle()
    ): Pair<Boolean, List<ReplayTransitionRecord>>
}

/**
 * Default implementation of [TaskReplayService] querying [EventRepository]
 * and evaluating transitions against the [AgentState] sealed class state machine.
 */
class DefaultTaskReplayService(
    private val eventRepository: EventRepository
) : TaskReplayService {

    override suspend fun replayAndDeriveTaskState(taskId: String): ReplayTaskStateResult {
        val verifiedSequence = eventRepository.getVerifiedEventsForTask(taskId)
        val events = verifiedSequence.events
        val chainIntact = verifiedSequence.verification.isValid

        return processReplay(
            taskId = taskId,
            events = events,
            chainIntact = chainIntact,
            maxEventIndex = null
        )
    }

    override suspend fun replayToPointInTime(taskId: String, maxEventIndex: Int): ReplayTaskStateResult {
        val verifiedSequence = eventRepository.getVerifiedEventsForTask(taskId)
        val events = verifiedSequence.events
        val chainIntact = verifiedSequence.verification.isValid

        return processReplay(
            taskId = taskId,
            events = events,
            chainIntact = chainIntact,
            maxEventIndex = maxEventIndex
        )
    }

    override fun observeDerivedTaskState(taskId: String): Flow<ReplayTaskStateResult> {
        return eventRepository.observeEventsForTask(taskId).map { events ->
            val (chainIntact, _) = EventSourcing.verifyChainIntegrity(events)
            processReplay(
                taskId = taskId,
                events = events,
                chainIntact = chainIntact,
                maxEventIndex = null
            )
        }
    }

    override fun validateAndTraceTransitions(
        events: List<TaskEvent>,
        initialState: AgentState
    ): Pair<Boolean, List<ReplayTransitionRecord>> {
        var currentState: AgentState = initialState
        val records = mutableListOf<ReplayTransitionRecord>()
        var allValid = true

        for ((index, event) in events.withIndex()) {
            val (nextState, isValid, explanation) = evaluateTransition(currentState, event)
            if (!isValid) {
                allValid = false
            }

            records.add(
                ReplayTransitionRecord(
                    eventIndex = index,
                    event = event,
                    fromState = currentState,
                    toState = nextState,
                    isValid = isValid,
                    ruleExplanation = explanation
                )
            )

            currentState = nextState
        }

        return allValid to records
    }

    private fun processReplay(
        taskId: String,
        events: List<TaskEvent>,
        chainIntact: Boolean,
        maxEventIndex: Int?
    ): ReplayTaskStateResult {
        val targetEvents = if (maxEventIndex != null) {
            events.take((maxEventIndex + 1).coerceAtMost(events.size))
        } else {
            events
        }

        val request = targetEvents.firstOrNull { it.type == EventType.TASK_CREATED }?.let {
            try {
                JSONObject(it.payloadJson).optString("request", "")
            } catch (_: Exception) { "" }
        } ?: ""

        val reconstructedTask = EventSourcing.replayState(
            initialRequest = request,
            events = targetEvents
        )

        val (transitionsValid, transitions) = validateAndTraceTransitions(targetEvents)

        val firstViolation = transitions.firstOrNull { !it.isValid }?.let {
            StateTransitionViolation(
                eventIndex = it.eventIndex,
                event = it.event,
                fromState = it.fromState,
                violationMessage = it.ruleExplanation
            )
        }

        val finalAgentState = transitions.lastOrNull()?.toState ?: AgentState.Idle()

        return ReplayTaskStateResult(
            taskId = taskId,
            task = reconstructedTask,
            agentState = finalAgentState,
            eventsReplayed = targetEvents.size,
            isCryptographicChainIntact = chainIntact,
            areStateTransitionsValid = transitionsValid,
            transitionHistory = transitions,
            firstViolation = firstViolation
        )
    }

    /**
     * Evaluates a single event against the current [AgentState] and determines:
     * - The next [AgentState]
     * - Whether the transition conforms to the defined sealed class rules
     * - A descriptive explanation of the rule applied
     */
    private fun evaluateTransition(
        currentState: AgentState,
        event: TaskEvent
    ): Triple<AgentState, Boolean, String> {
        val payload = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }

        return when (event.type) {
            EventType.TASK_CREATED -> {
                when (currentState) {
                    is AgentState.Idle -> {
                        val next = AgentState.Running(taskId = event.taskId, startedAt = event.timestamp)
                        Triple(next, true, "Valid transition: IDLE -> RUNNING (Task created: ${event.taskId})")
                    }
                    is AgentState.Error -> {
                        val next = AgentState.Running(taskId = event.taskId, startedAt = event.timestamp)
                        Triple(next, true, "Valid recovery transition: ERROR -> RUNNING (New task initiated: ${event.taskId})")
                    }
                    is AgentState.Running -> {
                        Triple(
                            currentState,
                            false,
                            "Illegal transition: Already in RUNNING state for taskId='${currentState.taskId}'. Cannot re-initialize task."
                        )
                    }
                    is AgentState.Paused -> {
                        Triple(
                            currentState,
                            false,
                            "Illegal transition: Cannot initiate new task while in PAUSED state."
                        )
                    }
                }
            }

            EventType.PLAN_CREATED -> {
                when (currentState) {
                    is AgentState.Running -> {
                        Triple(currentState, true, "Valid state maintained: RUNNING (Plan generated)")
                    }
                    else -> {
                        val next = AgentState.Running(taskId = event.taskId)
                        Triple(next, false, "Illegal transition: PLAN_CREATED received while in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.STEP_STARTED -> {
                val stepId = if (payload.has("stepId")) payload.optString("stepId") else null
                val expectedTool = if (payload.has("expectedTool")) payload.optString("expectedTool") else null
                when (currentState) {
                    is AgentState.Running -> {
                        val next = currentState.copy(stepId = stepId, currentTool = expectedTool)
                        Triple(next, true, "Valid state maintained: RUNNING (Step started: $stepId)")
                    }
                    else -> {
                        val next = AgentState.Running(taskId = event.taskId, stepId = stepId, currentTool = expectedTool)
                        Triple(next, false, "Illegal transition: STEP_STARTED occurred while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.TOOL_REQUESTED -> {
                val toolId = if (payload.has("toolId")) payload.optString("toolId") else null
                when (currentState) {
                    is AgentState.Running -> {
                        val next = currentState.copy(currentTool = toolId)
                        Triple(next, true, "Valid state maintained: RUNNING (Tool requested: $toolId)")
                    }
                    else -> {
                        val next = AgentState.Running(taskId = event.taskId, currentTool = toolId)
                        Triple(next, false, "Illegal transition: TOOL_REQUESTED occurred while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.PERMISSION_REQUESTED -> {
                val permId = payload.optString("permissionId", "perm-unknown")
                val toolId = payload.optString("toolId", "")
                val reason = payload.optString("reason", "Operator clearance required")
                val expiresAt = payload.optLong("expiresAt", event.timestamp + 600_000L)

                val confirmation = AgentPendingConfirmation(
                    permissionId = permId,
                    taskId = event.taskId,
                    toolId = toolId,
                    reason = reason,
                    expiresAt = expiresAt
                )

                when (currentState) {
                    is AgentState.Running -> {
                        val next = AgentState.Paused(
                            reason = PauseReason.POLICY_CONFIRMATION,
                            taskId = event.taskId,
                            pendingConfirmation = confirmation,
                            pausedAt = event.timestamp
                        )
                        Triple(next, true, "Valid transition: RUNNING -> PAUSED (Awaiting policy confirmation for $toolId)")
                    }
                    else -> {
                        val next = AgentState.Paused(
                            reason = PauseReason.POLICY_CONFIRMATION,
                            taskId = event.taskId,
                            pendingConfirmation = confirmation,
                            pausedAt = event.timestamp
                        )
                        Triple(next, false, "Illegal transition: PERMISSION_REQUESTED occurred while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.PERMISSION_GRANTED -> {
                val toolId = if (payload.has("toolId")) payload.optString("toolId") else null
                when (currentState) {
                    is AgentState.Paused -> {
                        val next = AgentState.Running(
                            taskId = event.taskId,
                            currentTool = toolId ?: currentState.pendingConfirmation?.toolId,
                            startedAt = event.timestamp
                        )
                        Triple(next, true, "Valid transition: PAUSED -> RUNNING (Operator cleared permission for $toolId)")
                    }
                    else -> {
                        val next = AgentState.Running(taskId = event.taskId, currentTool = toolId)
                        Triple(next, false, "Illegal transition: PERMISSION_GRANTED received while in ${currentState::class.simpleName} state (not PAUSED).")
                    }
                }
            }

            EventType.PERMISSION_DENIED -> {
                val reason = payload.optString("reason", "Permission denied by operator or policy")
                val next = AgentState.Error(
                    message = reason,
                    code = "PERMISSION_DENIED",
                    taskId = event.taskId,
                    policyViolation = true,
                    recoverable = true,
                    timestamp = event.timestamp
                )

                when (currentState) {
                    is AgentState.Paused -> {
                        Triple(next, true, "Valid transition: PAUSED -> ERROR (Operator denied clearance)")
                    }
                    is AgentState.Running -> {
                        Triple(next, true, "Valid transition: RUNNING -> ERROR (Policy Engine blocked execution)")
                    }
                    else -> {
                        Triple(next, false, "Illegal transition: PERMISSION_DENIED received while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.PERMISSION_EXPIRED -> {
                val next = AgentState.Error(
                    message = "Policy authorization expired without operator response",
                    code = "PERMISSION_EXPIRED",
                    taskId = event.taskId,
                    policyViolation = false,
                    recoverable = true,
                    timestamp = event.timestamp
                )

                when (currentState) {
                    is AgentState.Paused -> {
                        Triple(next, true, "Valid transition: PAUSED -> ERROR (Permission approval expired)")
                    }
                    else -> {
                        Triple(next, false, "Illegal transition: PERMISSION_EXPIRED received while in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.TOOL_EXECUTED -> {
                when (currentState) {
                    is AgentState.Running -> {
                        Triple(currentState, true, "Valid state maintained: RUNNING (Tool execution recorded)")
                    }
                    else -> {
                        val next = AgentState.Running(taskId = event.taskId)
                        Triple(next, false, "Illegal transition: TOOL_EXECUTED occurred while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.VERIFICATION_PASSED,
            EventType.VERIFICATION_FAILED,
            EventType.RETRY_SCHEDULED,
            EventType.REPLAN_TRIGGERED -> {
                when (currentState) {
                    is AgentState.Running -> {
                        Triple(currentState, true, "Valid state maintained: RUNNING (${event.type.name})")
                    }
                    else -> {
                        val next = AgentState.Running(taskId = event.taskId)
                        Triple(next, false, "Illegal transition: ${event.type.name} received while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.TASK_PAUSED -> {
                when (currentState) {
                    is AgentState.Running -> {
                        val next = AgentState.Paused(
                            reason = PauseReason.MANUAL,
                            taskId = event.taskId,
                            pausedAt = event.timestamp
                        )
                        Triple(next, true, "Valid transition: RUNNING -> PAUSED (Manual pause)")
                    }
                    else -> {
                        val next = AgentState.Paused(
                            reason = PauseReason.MANUAL,
                            taskId = event.taskId,
                            pausedAt = event.timestamp
                        )
                        Triple(next, false, "Illegal transition: TASK_PAUSED received while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.TASK_RESUMED -> {
                when (currentState) {
                    is AgentState.Paused -> {
                        val next = AgentState.Running(
                            taskId = event.taskId,
                            startedAt = event.timestamp
                        )
                        Triple(next, true, "Valid transition: PAUSED -> RUNNING (Resumed execution)")
                    }
                    else -> {
                        val next = AgentState.Running(taskId = event.taskId)
                        Triple(next, false, "Illegal transition: TASK_RESUMED received while agent was in ${currentState::class.simpleName} state (not PAUSED).")
                    }
                }
            }

            EventType.TASK_COMPLETED -> {
                val next = AgentState.Idle(
                    message = "Task completed successfully",
                    lastCompletedTaskId = event.taskId,
                    timestamp = event.timestamp
                )

                when (currentState) {
                    is AgentState.Running -> {
                        Triple(next, true, "Valid transition: RUNNING -> IDLE (Task completed: ${event.taskId})")
                    }
                    is AgentState.Paused -> {
                        Triple(next, false, "Illegal transition: TASK_COMPLETED while agent was PAUSED on unresolved permission.")
                    }
                    else -> {
                        Triple(next, false, "Illegal transition: TASK_COMPLETED received while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.TASK_FAILED -> {
                val errorMsg = payload.optString("error", "Task execution failed")
                val next = AgentState.Error(
                    message = errorMsg,
                    code = "TASK_FAILED",
                    taskId = event.taskId,
                    policyViolation = false,
                    recoverable = true,
                    timestamp = event.timestamp
                )

                when (currentState) {
                    is AgentState.Running, is AgentState.Paused -> {
                        Triple(next, true, "Valid transition: ${currentState::class.simpleName} -> ERROR (Failure: $errorMsg)")
                    }
                    else -> {
                        Triple(next, false, "Illegal transition: TASK_FAILED received while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }

            EventType.TASK_CANCELLED -> {
                val next = AgentState.Idle(
                    message = "Task cancelled",
                    lastCompletedTaskId = event.taskId,
                    timestamp = event.timestamp
                )

                when (currentState) {
                    is AgentState.Running, is AgentState.Paused -> {
                        Triple(next, true, "Valid transition: ${currentState::class.simpleName} -> IDLE (Task cancelled: ${event.taskId})")
                    }
                    else -> {
                        Triple(next, false, "Illegal transition: TASK_CANCELLED received while agent was in ${currentState::class.simpleName} state.")
                    }
                }
            }
        }
    }
}
