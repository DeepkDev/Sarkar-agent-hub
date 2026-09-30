package com.example.core

import com.example.contracts.TaskStatus

object TaskStateMachine {
    private val validTransitions: Map<TaskStatus, Set<TaskStatus>> = mapOf(
        TaskStatus.PENDING to setOf(TaskStatus.PLANNING, TaskStatus.CANCELLED),
        TaskStatus.PLANNING to setOf(
            TaskStatus.EXECUTING,
            TaskStatus.WAITING_FOR_PERMISSION,
            TaskStatus.FAILED,
            TaskStatus.CANCELLED,
            TaskStatus.COMPLETED // e.g. dry-run finishes at planning
        ),
        TaskStatus.WAITING_FOR_PERMISSION to setOf(
            TaskStatus.EXECUTING,
            TaskStatus.FAILED,
            TaskStatus.CANCELLED,
            TaskStatus.EXPIRED,
            TaskStatus.PAUSED
        ),
        TaskStatus.EXECUTING to setOf(
            TaskStatus.VERIFYING,
            TaskStatus.WAITING_FOR_PERMISSION,
            TaskStatus.FAILED,
            TaskStatus.CANCELLED,
            TaskStatus.PAUSED
        ),
        TaskStatus.VERIFYING to setOf(
            TaskStatus.EXECUTING, // when retrying
            TaskStatus.PLANNING,  // when replanning
            TaskStatus.COMPLETED,
            TaskStatus.FAILED,
            TaskStatus.CANCELLED
        ),
        TaskStatus.PAUSED to setOf(
            TaskStatus.EXECUTING,
            TaskStatus.WAITING_FOR_PERMISSION,
            TaskStatus.CANCELLED
        ),
        // Terminal states have no valid transitions
        TaskStatus.COMPLETED to emptySet(),
        TaskStatus.FAILED to emptySet(),
        TaskStatus.CANCELLED to emptySet(),
        TaskStatus.EXPIRED to emptySet()
    )

    fun canTransition(from: TaskStatus, to: TaskStatus): Boolean {
        if (from == to) return true
        return validTransitions[from]?.contains(to) == true
    }

    fun validateTransition(from: TaskStatus, to: TaskStatus) {
        if (!canTransition(from, to)) {
            throw IllegalStateException(
                "Illegal task state transition: Cannot transition from ${from.name} to ${to.name}"
            )
        }
    }
}
