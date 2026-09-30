package com.example.core

import com.example.model.TaskEvent
import com.example.model.TaskState
import com.example.model.TaskStatus
import com.example.storage.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Exception thrown when an invalid task state transition is attempted.
 */
class IllegalStateTransitionException(
    val taskId: String,
    val from: TaskStatus,
    val to: TaskStatus,
    message: String = "Illegal state transition for task '$taskId' from $from to $to"
) : IllegalStateException(message)

/**
 * Valid state transition rules for agent task lifecycle.
 */
object TaskTransitionRules {
    private val VALID_TRANSITIONS: Map<TaskStatus, Set<TaskStatus>> by lazy {
        mapOf(
            TaskStatus.PENDING to setOf(
                TaskStatus.PLANNING,
                TaskStatus.EXECUTING,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.EXPIRED
            ),
            TaskStatus.PLANNING to setOf(
                TaskStatus.WAITING_FOR_PERMISSION,
                TaskStatus.EXECUTING,
                TaskStatus.PAUSED,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.EXPIRED
            ),
            TaskStatus.WAITING_FOR_PERMISSION to setOf(
                TaskStatus.EXECUTING,
                TaskStatus.PLANNING,
                TaskStatus.PAUSED,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.EXPIRED
            ),
            TaskStatus.EXECUTING to setOf(
                TaskStatus.WAITING_FOR_PERMISSION,
                TaskStatus.VERIFYING,
                TaskStatus.COMPLETED,
                TaskStatus.PAUSED,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.EXPIRED
            ),
            TaskStatus.VERIFYING to setOf(
                TaskStatus.COMPLETED,
                TaskStatus.EXECUTING,
                TaskStatus.PLANNING,
                TaskStatus.PAUSED,
                TaskStatus.FAILED,
                TaskStatus.CANCELLED,
                TaskStatus.EXPIRED
            ),
            TaskStatus.PAUSED to setOf(
                TaskStatus.PLANNING,
                TaskStatus.WAITING_FOR_PERMISSION,
                TaskStatus.EXECUTING,
                TaskStatus.VERIFYING,
                TaskStatus.CANCELLED,
                TaskStatus.EXPIRED
            ),
            // Terminal states cannot transition into any new state
            TaskStatus.COMPLETED to emptySet(),
            TaskStatus.FAILED to emptySet(),
            TaskStatus.CANCELLED to emptySet(),
            TaskStatus.EXPIRED to emptySet()
        )
    }

    /**
     * Determines whether transitioning from [from] to [to] is permitted.
     */
    fun isValidTransition(from: TaskStatus, to: TaskStatus): Boolean {
        if (from == to) return true // Idempotent same-state transitions are allowed
        return VALID_TRANSITIONS[from]?.contains(to) == true
    }

    /**
     * Returns the set of valid next states accessible from [current].
     */
    fun getValidNextStates(current: TaskStatus): Set<TaskStatus> {
        return VALID_TRANSITIONS[current] ?: emptySet()
    }
}

/**
 * Orchestrates task lifecycle state transitions, consuming [TaskEvent]s and updating [TaskState]
 * while strictly enforcing state machine invariant rules and preventing illegal transitions.
 */
class TaskOrchestrator(
    private val eventRepository: EventRepository? = null
) {
    private val mutex = Mutex()
    private val tasks = ConcurrentHashMap<String, TaskState>()
    private val prePausedStates = ConcurrentHashMap<String, TaskStatus>()

    private val _tasksState = MutableStateFlow<Map<String, TaskState>>(emptyMap())
    val tasksState: StateFlow<Map<String, TaskState>> = _tasksState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<TaskEvent> = _eventFlow.asSharedFlow()

    /**
     * Retrieves the current [TaskState] for a task, or null if unknown.
     */
    fun getTaskState(taskId: String): TaskState? = tasks[taskId]

    /**
     * Returns an active reactive flow of state updates for the given [taskId].
     */
    fun observeTask(taskId: String): Flow<TaskState> {
        return tasksState.map { it[taskId] }
            .filterNotNull()
            .distinctUntilChanged()
    }

    /**
     * Checks if a transition is legal for the given task.
     */
    fun canTransition(taskId: String, targetStatus: TaskStatus): Boolean {
        val current = tasks[taskId]?.status ?: TaskStatus.PENDING
        return TaskTransitionRules.isValidTransition(current, targetStatus)
    }

    /**
     * Attempts to transition the task into [targetStatus], returning a [Result].
     */
    fun tryTransition(taskId: String, targetStatus: TaskStatus, reason: String? = null): Result<TaskState> {
        return runCatching {
            transition(taskId, targetStatus, reason)
        }
    }

    /**
     * Enforces transition from current status to [targetStatus].
     * Throws [IllegalStateTransitionException] if the transition violates lifecycle rules.
     */
    @Synchronized
    fun transition(taskId: String, targetStatus: TaskStatus, reason: String? = null): TaskState {
        val existing = tasks[taskId] ?: TaskState(
            taskId = taskId,
            status = TaskStatus.PENDING,
            updatedAt = System.currentTimeMillis()
        )

        val current = existing.status

        if (current == targetStatus) {
            return existing
        }

        if (!TaskTransitionRules.isValidTransition(current, targetStatus)) {
            throw IllegalStateTransitionException(
                taskId = taskId,
                from = current,
                to = targetStatus,
                message = "Illegal transition for task '$taskId': cannot transition from $current to $targetStatus (valid next states: ${TaskTransitionRules.getValidNextStates(current)})"
            )
        }

        // Keep track of pre-pause status for resumption
        if (targetStatus == TaskStatus.PAUSED) {
            prePausedStates[taskId] = current
        }

        val updated = existing.copy(
            status = targetStatus,
            error = if (targetStatus == TaskStatus.FAILED) reason ?: existing.error else existing.error,
            updatedAt = System.currentTimeMillis()
        )

        tasks[taskId] = updated
        _tasksState.value = HashMap(tasks)
        return updated
    }

    /**
     * Ingests a domain [TaskEvent], applying corresponding state transitions and updating task state.
     * Enforces valid transitions and rejects invalid state progressions.
     */
    suspend fun applyEvent(event: TaskEvent): TaskState = mutex.withLock {
        val taskId = event.taskId
        val currentState = tasks[taskId]

        when (event) {
            is TaskEvent.TaskCreated -> {
                if (currentState != null && currentState.status != TaskStatus.PENDING) {
                    throw IllegalStateTransitionException(
                        taskId = taskId,
                        from = currentState.status,
                        to = TaskStatus.PENDING,
                        message = "Cannot re-create already existing task in state ${currentState.status}"
                    )
                }
                val initialState = TaskState(
                    taskId = taskId,
                    status = TaskStatus.PENDING,
                    request = event.request,
                    updatedAt = event.timestamp
                )
                tasks[taskId] = initialState
                _tasksState.value = HashMap(tasks)
                emitEvent(event)
                initialState
            }

            is TaskEvent.PlanCreated -> {
                // Task moves to PLANNING if it was PENDING, or remains in PLANNING
                val targetStatus = TaskStatus.PLANNING
                val newState = if (currentState == null) {
                    transition(taskId, targetStatus)
                } else if (currentState.status == TaskStatus.PENDING) {
                    transition(taskId, targetStatus)
                } else if (currentState.status == TaskStatus.PLANNING) {
                    currentState
                } else {
                    // Check if transition back to PLANNING (e.g. from VERIFYING or WAITING_FOR_PERMISSION) is allowed
                    transition(taskId, targetStatus)
                }

                val updated = newState.copy(
                    totalSteps = event.stepDescriptions.size,
                    updatedAt = event.timestamp
                )
                tasks[taskId] = updated
                _tasksState.value = HashMap(tasks)
                emitEvent(event)
                updated
            }

            is TaskEvent.StepStarted -> {
                val newState = transition(taskId, TaskStatus.EXECUTING)
                val updated = newState.copy(
                    currentStep = event.stepId,
                    updatedAt = event.timestamp
                )
                tasks[taskId] = updated
                _tasksState.value = HashMap(tasks)
                emitEvent(event)
                updated
            }

            is TaskEvent.StepCompleted -> {
                val current = tasks[taskId] ?: transition(taskId, TaskStatus.EXECUTING)
                val updated = current.copy(
                    completedSteps = current.completedSteps + 1,
                    updatedAt = event.timestamp
                )
                tasks[taskId] = updated
                _tasksState.value = HashMap(tasks)
                emitEvent(event)
                updated
            }

            is TaskEvent.StepFailed -> {
                val updated = transition(taskId, TaskStatus.FAILED, reason = event.error)
                emitEvent(event)
                updated
            }

            is TaskEvent.ToolExecuted -> {
                // Must be executing to run tools
                if (currentState?.status != TaskStatus.EXECUTING) {
                    transition(taskId, TaskStatus.EXECUTING)
                }
                val current = tasks[taskId]!!
                emitEvent(event)
                current
            }

            is TaskEvent.PermissionRequested -> {
                val updated = transition(taskId, TaskStatus.WAITING_FOR_PERMISSION, reason = event.reason)
                emitEvent(event)
                updated
            }

            is TaskEvent.PermissionGranted -> {
                val updated = transition(taskId, TaskStatus.EXECUTING)
                emitEvent(event)
                updated
            }

            is TaskEvent.PermissionDenied -> {
                val updated = transition(taskId, TaskStatus.FAILED, reason = "Permission denied: ${event.reason}")
                emitEvent(event)
                updated
            }

            is TaskEvent.TaskCompleted -> {
                val updated = transition(taskId, TaskStatus.COMPLETED)
                emitEvent(event)
                updated
            }

            is TaskEvent.TaskFailed -> {
                val updated = transition(taskId, TaskStatus.FAILED, reason = event.errorMessage)
                emitEvent(event)
                updated
            }

            is TaskEvent.TaskCancelled -> {
                val updated = transition(taskId, TaskStatus.CANCELLED, reason = event.reason)
                emitEvent(event)
                updated
            }

            is TaskEvent.TaskPaused -> {
                val updated = transition(taskId, TaskStatus.PAUSED)
                emitEvent(event)
                updated
            }

            is TaskEvent.TaskResumed -> {
                val resumedTo = prePausedStates.remove(taskId) ?: TaskStatus.EXECUTING
                val updated = transition(taskId, resumedTo)
                emitEvent(event)
                updated
            }
        }
    }

    private suspend fun emitEvent(event: TaskEvent) {
        _eventFlow.tryEmit(event)
        eventRepository?.appendEvent(
            com.example.contracts.TaskEvent(
                metadata = com.example.contracts.EventMetadata(
                    eventId = event.eventId,
                    taskId = event.taskId,
                    traceId = event.traceId,
                    timestamp = event.timestamp,
                    prevHash = event.prevHash,
                    eventHash = event.eventHash
                ),
                type = try {
                    com.example.contracts.EventType.valueOf(event.type)
                } catch (_: Exception) {
                    com.example.contracts.EventType.TASK_CREATED
                },
                payloadJson = ModelTaskEventSerializer.toJson(event)
            )
        )
    }

    private object ModelTaskEventSerializer {
        fun toJson(event: TaskEvent): String = TaskEvent.toJson(event)
    }
}
