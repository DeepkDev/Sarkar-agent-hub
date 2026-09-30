package com.example.core

import com.example.contracts.*
import com.example.providers.AIProvider
import com.example.tools.ToolExecutor
import com.example.tools.ToolRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class Orchestrator(
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    val eventStream: EventStream = DefaultEventStream(),
    val policyEngine: PolicyEngine = PolicyEngine()
) {
    val stateManager: AgentStateManager = AgentStateManager(policyEngine)
    private val activeTasks = ConcurrentHashMap<String, AgentTask>()
    private val taskEvents = ConcurrentHashMap<String, MutableList<TaskEvent>>()
    private val taskJobs = ConcurrentHashMap<String, Job>()
    private val pendingPermissions = ConcurrentHashMap<String, PendingPermission>()
    private val permissionContinuations = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()

    var policyConfig: PolicyConfig = PolicyConfig()

    fun getTask(taskId: String): AgentTask? = activeTasks[taskId]
    fun getEvents(taskId: String): List<TaskEvent> = taskEvents[taskId]?.toList() ?: emptyList()
    fun getPendingPermissions(): List<PendingPermission> = pendingPermissions.values.filter { !it.isExpired && it.status == "PENDING" }

    suspend fun submitTask(
        request: String,
        provider: AIProvider,
        origin: OriginSource = OriginSource.WEB,
        dryRun: Boolean = false,
        budgetOverride: BudgetConfig? = null,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    ): AgentTask {
        val taskId = "task-${System.currentTimeMillis().toString().takeLast(6)}"
        val initialBudget = budgetOverride ?: BudgetConfig()
        val task = AgentTask(
            taskId = taskId,
            status = TaskStatus.PENDING,
            request = request,
            origin = origin,
            dryRun = dryRun,
            budgetConfig = initialBudget
        )

        activeTasks[taskId] = task
        taskEvents[taskId] = mutableListOf()
        stateManager.startTask(taskId)

        val job = scope.launch {
            runOrchestrationLoop(taskId, provider)
        }
        taskJobs[taskId] = job

        return task
    }

    private suspend fun runOrchestrationLoop(taskId: String, provider: AIProvider) {
        val startWallClock = System.currentTimeMillis()
        var task = activeTasks[taskId] ?: return

        try {
            // 1. PENDING -> PLANNING
            recordEvent(taskId, EventType.TASK_CREATED, JSONObject().apply {
                put("request", task.request)
                put("origin", task.origin.name)
                put("dryRun", task.dryRun)
            })

            TaskStateMachine.validateTransition(task.status, TaskStatus.PLANNING)
            task = updateTaskState(taskId) { it.copy(status = TaskStatus.PLANNING) }

            // 2. PLANNER ROLE
            val plan = provider.plan(
                PlanRequest(
                    userGoal = task.request,
                    availableTools = toolRegistry.tools.value,
                    origin = task.origin
                )
            )

            recordEvent(taskId, EventType.PLAN_CREATED, JSONObject().apply {
                put("planId", plan.planId)
                put("explanation", plan.explanation)
                val arr = JSONArray()
                plan.steps.forEach { step ->
                    arr.put(JSONObject().apply {
                        put("id", step.id)
                        put("description", step.description)
                        put("expectedTool", step.expectedTool)
                        put("status", step.status.name)
                    })
                }
                put("steps", arr)
            })

            task = updateTaskState(taskId) { it.copy(plan = plan) }

            if (task.dryRun) {
                // Dry run mode: generate plan, check policies, and finish safely
                recordEvent(taskId, EventType.TASK_COMPLETED, JSONObject().apply {
                    put("spokenSummary", "Dry run completed safely without side effects.")
                    put("fullText", "### Dry Run Complete\nGenerated plan with ${plan.steps.size} steps. No tools were executed.")
                    put("language", "en")
                })
                updateTaskState(taskId) {
                    it.copy(
                        status = TaskStatus.COMPLETED,
                        finalResponse = FinalResponse(
                            spokenSummary = "Dry run completed safely without side effects.",
                            fullText = "### Dry Run Complete\nGenerated plan with ${plan.steps.size} steps. Execution bypassed in preview mode."
                        )
                    )
                }
                return
            }

            var currentPlan = plan
            var stepIndex = 0
            var replanCount = 0

            while (stepIndex < currentPlan.steps.size) {
                val step = currentPlan.steps[stepIndex]

                // Check Hard Budget Limits
                val currentUsage = task.budgetUsage
                val elapsed = System.currentTimeMillis() - startWallClock
                if (currentUsage.stepsUsed >= task.budgetConfig.maxSteps ||
                    currentUsage.toolCallsUsed >= task.budgetConfig.maxToolCalls ||
                    elapsed > task.budgetConfig.maxWallClockMs
                ) {
                    val budgetReason = "Budget exhausted (Steps: ${currentUsage.stepsUsed}/${task.budgetConfig.maxSteps}, Calls: ${currentUsage.toolCallsUsed}/${task.budgetConfig.maxToolCalls}, Elapsed: ${elapsed}ms/${task.budgetConfig.maxWallClockMs}ms)"
                    failTask(taskId, budgetReason)
                    return
                }

                // STEP_STARTED
                stateManager.updateRunningStep(step.id, step.expectedTool)
                recordEvent(taskId, EventType.STEP_STARTED, JSONObject().apply {
                    put("stepId", step.id)
                    put("description", step.description)
                })

                task = updateTaskState(taskId) { t ->
                    val updatedSteps = t.plan?.steps?.map { s ->
                        if (s.id == step.id) s.copy(status = StepStatus.RUNNING) else s
                    } ?: emptyList()
                    t.copy(
                        currentStepId = step.id,
                        status = TaskStatus.EXECUTING,
                        plan = t.plan?.copy(steps = updatedSteps),
                        budgetUsage = t.budgetUsage.copy(
                            stepsUsed = t.budgetUsage.stepsUsed + 1,
                            wallClockMsUsed = elapsed
                        )
                    )
                }

                // 3. EXECUTOR ROLE: Choose tool & parameters
                recordEvent(taskId, EventType.TOOL_REQUESTED, JSONObject().apply {
                    put("stepId", step.id)
                    put("expectedTool", step.expectedTool)
                })

                val decision = provider.decideTool(
                    ToolDecisionRequest(
                        step = step,
                        previousResults = task.toolCalls,
                        availableTools = toolRegistry.tools.value
                    )
                )

                val toolManifest = toolRegistry.getManifest(decision.toolId)
                    ?: throw IllegalStateException("Tool '${decision.toolId}' is not registered.")

                val argsJson = JSONObject(decision.arguments).toString()

                // 4. POLICY ENGINE EVALUATION & STATE TRANSITION
                val policyTransition = stateManager.evaluateAndTransition(toolManifest, argsJson, task, policyConfig)
                val policyDecision = policyTransition.decision

                when (policyDecision.type) {
                    PolicyDecisionType.DENY -> {
                        val denyMsg = "Action blocked by Policy Engine: ${policyDecision.reason}"
                        recordEvent(taskId, EventType.PERMISSION_DENIED, JSONObject().apply {
                            put("toolId", decision.toolId)
                            put("reason", denyMsg)
                        })
                        failTask(taskId, denyMsg)
                        return
                    }

                    PolicyDecisionType.REQUIRE_CONFIRMATION -> {
                        // Suspend task and wait for explicit operator approval
                        val permId = policyDecision.permissionId ?: "perm-${System.currentTimeMillis()}"
                        val pending = PendingPermission(
                            permissionId = permId,
                            taskId = taskId,
                            stepId = step.id,
                            toolId = decision.toolId,
                            argumentsJson = argsJson,
                            sideEffectsSummary = policyDecision.reason,
                            expiresAt = policyDecision.expiresAt ?: (System.currentTimeMillis() + 600_000L)
                        )
                        pendingPermissions[permId] = pending

                        recordEvent(taskId, EventType.PERMISSION_REQUESTED, JSONObject().apply {
                            put("permissionId", permId)
                            put("toolId", decision.toolId)
                            put("arguments", argsJson)
                            put("reason", policyDecision.reason)
                            put("expiresAt", pending.expiresAt)
                        })

                        task = updateTaskState(taskId) {
                            it.copy(
                                status = TaskStatus.WAITING_FOR_PERMISSION,
                                permissionEvents = it.permissionEvents + PermissionEventRecord(
                                    permissionId = permId,
                                    toolId = decision.toolId,
                                    argumentsJson = argsJson,
                                    sideEffectsSummary = policyDecision.reason,
                                    decision = "PENDING"
                                )
                            )
                        }

                        // Await operator decision with timeout
                        val approved = suspendCancellableCoroutine<Boolean> { cont ->
                            permissionContinuations[permId] = cont
                        }

                        if (!approved) {
                            val denyReason = "Operator rejected permission for ${decision.toolId}"
                            recordEvent(taskId, EventType.PERMISSION_DENIED, JSONObject().apply {
                                put("permissionId", permId)
                                put("reason", denyReason)
                            })
                            failTask(taskId, denyReason)
                            return
                        } else {
                            recordEvent(taskId, EventType.PERMISSION_GRANTED, JSONObject().apply {
                                put("permissionId", permId)
                                put("toolId", decision.toolId)
                            })
                            task = updateTaskState(taskId) { it.copy(status = TaskStatus.EXECUTING) }
                        }
                    }

                    PolicyDecisionType.ALLOW -> {
                        // Immediate execution
                    }
                }

                // 5. TOOL EXECUTOR
                val toolExecStart = System.currentTimeMillis()
                val toolResult = toolExecutor.execute(
                    toolId = decision.toolId,
                    arguments = decision.arguments,
                    task = task,
                    timeoutMs = task.budgetConfig.perToolTimeoutMs,
                    policyConfig = policyConfig
                )
                val toolExecDuration = System.currentTimeMillis() - toolExecStart

                val execRecord = ToolExecutionRecord(
                    toolId = decision.toolId,
                    argumentsJson = argsJson,
                    outputJson = toolResult.data,
                    durationMs = toolExecDuration,
                    successful = toolResult.success
                )

                recordEvent(taskId, EventType.TOOL_EXECUTED, JSONObject().apply {
                    put("executionId", execRecord.executionId)
                    put("toolId", decision.toolId)
                    put("argumentsJson", argsJson)
                    put("outputJson", toolResult.data)
                    put("durationMs", toolExecDuration)
                    put("successful", toolResult.success)
                })

                task = updateTaskState(taskId) { t ->
                    val updatedSteps = t.plan?.steps?.map { s ->
                        if (s.id == step.id) {
                            s.copy(
                                status = if (toolResult.success) StepStatus.COMPLETED else StepStatus.FAILED,
                                toolOutput = toolResult.data
                            )
                        } else s
                    } ?: emptyList()
                    t.copy(
                        status = TaskStatus.VERIFYING,
                        toolCalls = t.toolCalls + execRecord,
                        plan = t.plan?.copy(steps = updatedSteps),
                        budgetUsage = t.budgetUsage.copy(
                            toolCallsUsed = t.budgetUsage.toolCallsUsed + 1,
                            wallClockMsUsed = System.currentTimeMillis() - startWallClock
                        )
                    )
                }

                // 6. VERIFIER ROLE
                val verification = provider.verify(
                    VerifyRequest(
                        step = step,
                        toolResult = toolResult
                    )
                )

                when (verification.outcome) {
                    VerificationOutcome.PASS -> {
                        recordEvent(taskId, EventType.VERIFICATION_PASSED, JSONObject().apply {
                            put("stepId", step.id)
                            put("reason", verification.reason)
                        })
                        stepIndex++
                    }

                    VerificationOutcome.RETRY -> {
                        if (task.budgetUsage.retriesUsed < task.budgetConfig.maxRetriesPerStep) {
                            recordEvent(taskId, EventType.RETRY_SCHEDULED, JSONObject().apply {
                                put("stepId", step.id)
                                put("reason", verification.reason)
                            })
                            updateTaskState(taskId) {
                                it.copy(budgetUsage = it.budgetUsage.copy(retriesUsed = it.budgetUsage.retriesUsed + 1))
                            }
                            // Do not increment stepIndex; re-run current step
                        } else {
                            failTask(taskId, "Exceeded maximum retries (${task.budgetConfig.maxRetriesPerStep}) for step: ${step.description}")
                            return
                        }
                    }

                    VerificationOutcome.REPLAN -> {
                        if (replanCount < task.budgetConfig.maxReplans) {
                            replanCount++
                            recordEvent(taskId, EventType.REPLAN_TRIGGERED, JSONObject().apply {
                                put("replanCount", replanCount)
                                put("reason", verification.reason)
                            })
                            val replanned = provider.plan(
                                PlanRequest(
                                    userGoal = "${task.request} (Note: Previous step failed: ${verification.reason})",
                                    availableTools = toolRegistry.tools.value,
                                    origin = task.origin
                                )
                            )
                            currentPlan = replanned
                            stepIndex = 0
                            task = updateTaskState(taskId) {
                                it.copy(
                                    plan = replanned,
                                    budgetUsage = it.budgetUsage.copy(replansUsed = it.budgetUsage.replansUsed + 1)
                                )
                            }
                        } else {
                            failTask(taskId, "Exceeded maximum replans (${task.budgetConfig.maxReplans})")
                            return
                        }
                    }

                    VerificationOutcome.FAIL -> {
                        recordEvent(taskId, EventType.VERIFICATION_FAILED, JSONObject().apply {
                            put("stepId", step.id)
                            put("reason", verification.reason)
                        })
                        failTask(taskId, "Step failed verification: ${verification.reason}")
                        return
                    }
                }
            }

            // 7. SUMMARIZER ROLE
            val finalResp = provider.summarize(
                SummarizeRequest(
                    userGoal = task.request,
                    plan = task.plan ?: currentPlan,
                    toolResults = task.toolCalls,
                    language = if (task.origin == OriginSource.SARKAR) "hi-IN" else "en"
                )
            )

            recordEvent(taskId, EventType.TASK_COMPLETED, JSONObject().apply {
                put("spokenSummary", finalResp.spokenSummary)
                put("fullText", finalResp.fullText)
                put("language", finalResp.language)
            })

            updateTaskState(taskId) {
                it.copy(
                    status = TaskStatus.COMPLETED,
                    finalResponse = finalResp,
                    updatedAt = System.currentTimeMillis()
                )
            }
            stateManager.completeTask("Task $taskId completed successfully")

        } catch (e: CancellationException) {
            recordEvent(taskId, EventType.TASK_CANCELLED, JSONObject().apply {
                put("reason", "Task cancelled by user or timeout")
            })
            updateTaskState(taskId) { it.copy(status = TaskStatus.CANCELLED) }
            stateManager.reset()
        } catch (e: Exception) {
            failTask(taskId, e.message ?: "Unexpected error during execution")
        } finally {
            taskJobs.remove(taskId)
        }
    }

    fun decidePermission(permissionId: String, approve: Boolean) {
        stateManager.handleConfirmation(permissionId, approve)
        val cont = permissionContinuations.remove(permissionId)
        val perm = pendingPermissions[permissionId]
        if (perm != null) {
            pendingPermissions[permissionId] = perm.copy(status = if (approve) "APPROVED" else "DENIED")
        }
        cont?.resumeWith(Result.success(approve))
    }

    fun cancelTask(taskId: String) {
        val job = taskJobs[taskId]
        job?.cancel()
        stateManager.reset()
        updateTaskState(taskId) { it.copy(status = TaskStatus.CANCELLED) }
    }

    private fun failTask(taskId: String, reason: String) {
        stateManager.fail(reason, taskId = taskId)
        recordEvent(taskId, EventType.TASK_FAILED, JSONObject().apply {
            put("error", reason)
        })
        updateTaskState(taskId) {
            it.copy(
                status = TaskStatus.FAILED,
                errors = it.errors + reason,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun recordEvent(taskId: String, type: EventType, payload: JSONObject) {
        val events = taskEvents.getOrPut(taskId) { mutableListOf() }
        val prevHash = events.lastOrNull()?.eventHash ?: "GENESIS"
        val newEvent = TaskEvent.createWithHash(
            taskId = taskId,
            type = type,
            payloadJson = payload.toString(),
            prevHash = prevHash
        )
        events.add(newEvent)
        eventStream.emit(newEvent)
    }

    private fun updateTaskState(taskId: String, transform: (AgentTask) -> AgentTask): AgentTask {
        val current = activeTasks[taskId] ?: throw IllegalArgumentException("Task $taskId not found")
        val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
        activeTasks[taskId] = updated
        return updated
    }
}
