package com.example.model

/**
 * Sealed class representing all discrete states in the agent task lifecycle:
 * PENDING, PLANNING, WAITING_FOR_PERMISSION, EXECUTING, VERIFYING,
 * COMPLETED, FAILED, CANCELLED, PAUSED, and EXPIRED.
 */
sealed class TaskStatus(val name: String) {

    /** Initial state when a task is submitted and queued. */
    data object PENDING : TaskStatus("PENDING")

    /** The agent is synthesizing a multi-step execution plan with tool grounding. */
    data object PLANNING : TaskStatus("PLANNING")

    /** Execution is suspended awaiting explicit human approval for sensitive/write operations. */
    data object WAITING_FOR_PERMISSION : TaskStatus("WAITING_FOR_PERMISSION")

    /** Actively executing planned steps and invoking selected tools. */
    data object EXECUTING : TaskStatus("EXECUTING")

    /** Validating step outputs, schema assertions, and domain constraints. */
    data object VERIFYING : TaskStatus("VERIFYING")

    /** Task has successfully achieved its goal and produced final verified output. */
    data object COMPLETED : TaskStatus("COMPLETED")

    /** Task encountered an unrecoverable error, policy violation, or budget exhaustion. */
    data object FAILED : TaskStatus("FAILED")

    /** Explicitly stopped by user intervention. */
    data object CANCELLED : TaskStatus("CANCELLED")

    /** Temporarily halted with state snapshot preserved. */
    data object PAUSED : TaskStatus("PAUSED")

    /** Timed out due to TTL expiry, deadline exceeded, or permission timeout. */
    data object EXPIRED : TaskStatus("EXPIRED")

    /** Whether the status is a terminal state in the task lifecycle. */
    val isTerminal: Boolean
        get() = this is COMPLETED || this is FAILED || this is CANCELLED || this is EXPIRED

    /** Whether the task is actively progressing through execution. */
    val isRunning: Boolean
        get() = this is PLANNING || this is EXECUTING || this is VERIFYING

    override fun toString(): String = name

    companion object {
        // CamelCase aliases for Kotlin idiomatic conventions
        val Pending: TaskStatus get() = PENDING
        val Planning: TaskStatus get() = PLANNING
        val WaitingForPermission: TaskStatus get() = WAITING_FOR_PERMISSION
        val Executing: TaskStatus get() = EXECUTING
        val Verifying: TaskStatus get() = VERIFYING
        val Completed: TaskStatus get() = COMPLETED
        val Failed: TaskStatus get() = FAILED
        val Cancelled: TaskStatus get() = CANCELLED
        val Paused: TaskStatus get() = PAUSED
        val Expired: TaskStatus get() = EXPIRED

        /** All valid TaskStatus instances. */
        val all: List<TaskStatus>
            get() = listOf(
                PENDING,
                PLANNING,
                WAITING_FOR_PERMISSION,
                EXECUTING,
                VERIFYING,
                COMPLETED,
                FAILED,
                CANCELLED,
                PAUSED,
                EXPIRED
            )

        /**
         * Resolves a [TaskStatus] from its uppercase string identifier.
         */
        fun fromString(value: String): TaskStatus {
            return when (value.uppercase().trim()) {
                "PENDING" -> PENDING
                "PLANNING" -> PLANNING
                "WAITING_FOR_PERMISSION" -> WAITING_FOR_PERMISSION
                "EXECUTING" -> EXECUTING
                "VERIFYING" -> VERIFYING
                "COMPLETED" -> COMPLETED
                "FAILED" -> FAILED
                "CANCELLED" -> CANCELLED
                "PAUSED" -> PAUSED
                "EXPIRED" -> EXPIRED
                else -> PENDING
            }
        }
    }
}

/**
 * Comprehensive snapshot of an agent task state.
 */
data class TaskState(
    val taskId: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val request: String = "",
    val currentStep: String? = null,
    val totalSteps: Int = 0,
    val completedSteps: Int = 0,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Extension functions to convert between contract TaskStatus and model TaskStatus.
 */
fun com.example.contracts.TaskStatus.toModelStatus(): TaskStatus = TaskStatus.fromString(this.name)
fun TaskStatus.toContractStatus(): com.example.contracts.TaskStatus = com.example.contracts.TaskStatus.valueOf(this.name)
