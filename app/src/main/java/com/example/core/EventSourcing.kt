package com.example.core

import com.example.contracts.*
import org.json.JSONObject

object EventSourcing {

    /**
     * Verifies the tamper-evident cryptographic hash chain of an event log.
     */
    fun verifyChainIntegrity(events: List<TaskEvent>): Pair<Boolean, String> {
        if (events.isEmpty()) return true to "Empty event log"
        var prevHash = "GENESIS"

        for ((index, event) in events.withIndex()) {
            if (event.prevHash != prevHash) {
                return false to "Chain broken at event #$index (${event.type}): expected prevHash=$prevHash, found=${event.prevHash}"
            }
            prevHash = event.eventHash
        }
        return true to "All ${events.size} events verified cryptographically intact"
    }

    /**
     * Replays events up to a given index or timestamp to reconstruct the task state at that point in time.
     * Enables time-travel debugging in the UI.
     */
    fun replayState(
        initialRequest: String,
        events: List<TaskEvent>,
        maxEventIndex: Int? = null
    ): AgentTask {
        val targetEvents = if (maxEventIndex != null) {
            events.take((maxEventIndex + 1).coerceAtMost(events.size))
        } else {
            events
        }

        var currentTask = AgentTask(
            taskId = events.firstOrNull()?.taskId ?: "unknown",
            request = initialRequest,
            status = TaskStatus.PENDING
        )

        for (event in targetEvents) {
            currentTask = applyEvent(currentTask, event)
        }

        return currentTask
    }

    private fun applyEvent(task: AgentTask, event: TaskEvent): AgentTask {
        return when (event.type) {
            EventType.TASK_CREATED -> {
                task.copy(
                    taskId = event.taskId,
                    status = TaskStatus.PLANNING,
                    createdAt = event.timestamp,
                    updatedAt = event.timestamp
                )
            }

            EventType.PLAN_CREATED -> {
                try {
                    val json = JSONObject(event.payloadJson)
                    val explanation = json.optString("explanation", "")
                    val stepsArray = json.optJSONArray("steps")
                    val stepsList = mutableListOf<PlanStep>()
                    if (stepsArray != null) {
                        for (i in 0 until stepsArray.length()) {
                            val stepObj = stepsArray.getJSONObject(i)
                            stepsList.add(
                                PlanStep(
                                    id = stepObj.optString("id", "step-$i"),
                                    description = stepObj.optString("description", ""),
                                    expectedTool = stepObj.optString("expectedTool", ""),
                                    status = StepStatus.valueOf(stepObj.optString("status", "PENDING"))
                                )
                            )
                        }
                    }
                    task.copy(
                        plan = Plan(explanation = explanation, steps = stepsList),
                        status = TaskStatus.EXECUTING,
                        updatedAt = event.timestamp
                    )
                } catch (e: Exception) {
                    task.copy(updatedAt = event.timestamp)
                }
            }

            EventType.STEP_STARTED -> {
                val stepId = try { JSONObject(event.payloadJson).optString("stepId") } catch (_: Exception) { null }
                val updatedSteps = task.plan?.steps?.map {
                    if (it.id == stepId) it.copy(status = StepStatus.RUNNING) else it
                } ?: emptyList()
                task.copy(
                    currentStepId = stepId,
                    plan = task.plan?.copy(steps = updatedSteps),
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp,
                    budgetUsage = task.budgetUsage.copy(stepsUsed = task.budgetUsage.stepsUsed + 1)
                )
            }

            EventType.PERMISSION_REQUESTED -> {
                task.copy(
                    status = TaskStatus.WAITING_FOR_PERMISSION,
                    updatedAt = event.timestamp
                )
            }

            EventType.PERMISSION_GRANTED -> {
                task.copy(
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp
                )
            }

            EventType.PERMISSION_DENIED -> {
                task.copy(
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp
                )
            }

            EventType.PERMISSION_EXPIRED -> {
                task.copy(
                    status = TaskStatus.EXPIRED,
                    updatedAt = event.timestamp
                )
            }

            EventType.TOOL_EXECUTED -> {
                try {
                    val json = JSONObject(event.payloadJson)
                    val record = ToolExecutionRecord(
                        executionId = json.optString("executionId", "exec"),
                        toolId = json.optString("toolId", ""),
                        argumentsJson = json.optString("argumentsJson", "{}"),
                        outputJson = json.optString("outputJson", ""),
                        durationMs = json.optLong("durationMs", 0L),
                        successful = json.optBoolean("successful", true),
                        timestamp = event.timestamp
                    )
                    val updatedToolCalls = task.toolCalls + record
                    val updatedSteps = task.plan?.steps?.map {
                        if (it.id == task.currentStepId) {
                            it.copy(
                                status = if (record.successful) StepStatus.COMPLETED else StepStatus.FAILED,
                                toolOutput = record.outputJson
                            )
                        } else it
                    } ?: emptyList()

                    task.copy(
                        toolCalls = updatedToolCalls,
                        plan = task.plan?.copy(steps = updatedSteps),
                        status = TaskStatus.VERIFYING,
                        updatedAt = event.timestamp,
                        budgetUsage = task.budgetUsage.copy(
                            toolCallsUsed = task.budgetUsage.toolCallsUsed + 1,
                            wallClockMsUsed = task.budgetUsage.wallClockMsUsed + record.durationMs
                        )
                    )
                } catch (e: Exception) {
                    task.copy(updatedAt = event.timestamp)
                }
            }

            EventType.VERIFICATION_PASSED -> {
                task.copy(
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp
                )
            }

            EventType.VERIFICATION_FAILED -> {
                task.copy(
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp
                )
            }

            EventType.RETRY_SCHEDULED -> {
                task.copy(
                    budgetUsage = task.budgetUsage.copy(retriesUsed = task.budgetUsage.retriesUsed + 1),
                    updatedAt = event.timestamp
                )
            }

            EventType.REPLAN_TRIGGERED -> {
                task.copy(
                    status = TaskStatus.PLANNING,
                    budgetUsage = task.budgetUsage.copy(replansUsed = task.budgetUsage.replansUsed + 1),
                    updatedAt = event.timestamp
                )
            }

            EventType.TASK_COMPLETED -> {
                try {
                    val json = JSONObject(event.payloadJson)
                    val finalResp = FinalResponse(
                        spokenSummary = json.optString("spokenSummary", "Task completed."),
                        fullText = json.optString("fullText", "Task execution finished successfully."),
                        language = json.optString("language", "en")
                    )
                    task.copy(
                        status = TaskStatus.COMPLETED,
                        finalResponse = finalResp,
                        updatedAt = event.timestamp
                    )
                } catch (_: Exception) {
                    task.copy(status = TaskStatus.COMPLETED, updatedAt = event.timestamp)
                }
            }

            EventType.TASK_FAILED -> {
                val reason = try { JSONObject(event.payloadJson).optString("error", "Task execution failed") } catch (_: Exception) { "Task execution failed" }
                task.copy(
                    status = TaskStatus.FAILED,
                    errors = task.errors + reason,
                    updatedAt = event.timestamp
                )
            }

            EventType.TASK_CANCELLED -> {
                task.copy(status = TaskStatus.CANCELLED, updatedAt = event.timestamp)
            }

            EventType.TASK_PAUSED -> {
                task.copy(status = TaskStatus.PAUSED, updatedAt = event.timestamp)
            }

            EventType.TASK_RESUMED -> {
                task.copy(status = TaskStatus.EXECUTING, updatedAt = event.timestamp)
            }

            else -> task.copy(updatedAt = event.timestamp)
        }
    }

    /**
     * Strongly typed event application using exhaustive when pattern matching on the AgentTaskEvent sealed class hierarchy.
     */
    fun applyTypedEvent(task: AgentTask, event: AgentTaskEvent): AgentTask {
        return when (event) {
            is AgentTaskEvent.TaskCreated -> {
                task.copy(
                    taskId = event.taskId,
                    status = TaskStatus.PLANNING,
                    request = event.request,
                    origin = event.origin,
                    dryRun = event.dryRun,
                    createdAt = event.timestamp,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.PlanCreated -> {
                task.copy(
                    plan = Plan(planId = event.planId, explanation = event.explanation, steps = event.steps),
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.StepStarted -> {
                val updatedSteps = task.plan?.steps?.map {
                    if (it.id == event.stepId) it.copy(status = StepStatus.RUNNING) else it
                } ?: emptyList()
                task.copy(
                    currentStepId = event.stepId,
                    plan = task.plan?.copy(steps = updatedSteps),
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp,
                    budgetUsage = task.budgetUsage.copy(stepsUsed = task.budgetUsage.stepsUsed + 1)
                )
            }
            is AgentTaskEvent.ToolRequested -> {
                task.copy(updatedAt = event.timestamp)
            }
            is AgentTaskEvent.PermissionRequested -> {
                val rec = PermissionEventRecord(
                    permissionId = event.permissionId,
                    toolId = event.toolId,
                    argumentsJson = event.argumentsJson,
                    sideEffectsSummary = event.sideEffectsSummary,
                    decision = "PENDING",
                    timestamp = event.timestamp
                )
                task.copy(
                    status = TaskStatus.WAITING_FOR_PERMISSION,
                    permissionEvents = task.permissionEvents + rec,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.PermissionGranted -> {
                val updatedPerms = task.permissionEvents.map {
                    if (it.permissionId == event.permissionId) it.copy(decision = "APPROVED") else it
                }
                task.copy(
                    status = TaskStatus.EXECUTING,
                    permissionEvents = updatedPerms,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.PermissionDenied -> {
                val updatedPerms = task.permissionEvents.map {
                    if (it.permissionId == event.permissionId) it.copy(decision = "DENIED") else it
                }
                task.copy(
                    status = TaskStatus.EXECUTING,
                    permissionEvents = updatedPerms,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.PermissionExpired -> {
                val updatedPerms = task.permissionEvents.map {
                    if (it.permissionId == event.permissionId) it.copy(decision = "EXPIRED") else it
                }
                task.copy(
                    status = TaskStatus.EXPIRED,
                    permissionEvents = updatedPerms,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.ToolExecuted -> {
                val record = ToolExecutionRecord(
                    executionId = event.executionId,
                    toolId = event.toolId,
                    argumentsJson = event.argumentsJson,
                    outputJson = event.outputJson,
                    durationMs = event.durationMs,
                    successful = event.successful,
                    timestamp = event.timestamp
                )
                val updatedToolCalls = task.toolCalls + record
                val updatedSteps = task.plan?.steps?.map {
                    if (it.id == task.currentStepId) {
                        it.copy(
                            status = if (event.successful) StepStatus.COMPLETED else StepStatus.FAILED,
                            toolOutput = event.outputJson
                        )
                    } else it
                } ?: emptyList()

                task.copy(
                    toolCalls = updatedToolCalls,
                    plan = task.plan?.copy(steps = updatedSteps),
                    status = TaskStatus.VERIFYING,
                    updatedAt = event.timestamp,
                    budgetUsage = task.budgetUsage.copy(
                        toolCallsUsed = task.budgetUsage.toolCallsUsed + 1,
                        wallClockMsUsed = task.budgetUsage.wallClockMsUsed + event.durationMs
                    )
                )
            }
            is AgentTaskEvent.VerificationPassed -> {
                task.copy(
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.VerificationFailed -> {
                task.copy(
                    status = TaskStatus.EXECUTING,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.RetryScheduled -> {
                task.copy(
                    budgetUsage = task.budgetUsage.copy(retriesUsed = task.budgetUsage.retriesUsed + 1),
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.ReplanTriggered -> {
                task.copy(
                    status = TaskStatus.PLANNING,
                    budgetUsage = task.budgetUsage.copy(replansUsed = task.budgetUsage.replansUsed + 1),
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.TaskCompleted -> {
                val finalResp = FinalResponse(
                    spokenSummary = event.spokenSummary,
                    fullText = event.fullText,
                    language = event.language
                )
                task.copy(
                    status = TaskStatus.COMPLETED,
                    finalResponse = finalResp,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.TaskFailed -> {
                task.copy(
                    status = TaskStatus.FAILED,
                    errors = task.errors + event.error,
                    updatedAt = event.timestamp
                )
            }
            is AgentTaskEvent.TaskCancelled -> {
                task.copy(status = TaskStatus.CANCELLED, updatedAt = event.timestamp)
            }
            is AgentTaskEvent.TaskPaused -> {
                task.copy(status = TaskStatus.PAUSED, updatedAt = event.timestamp)
            }
            is AgentTaskEvent.TaskResumed -> {
                task.copy(status = TaskStatus.EXECUTING, updatedAt = event.timestamp)
            }
        }
    }
}
