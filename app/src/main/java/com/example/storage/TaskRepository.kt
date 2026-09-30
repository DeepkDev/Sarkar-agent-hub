package com.example.storage

import com.example.contracts.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository interface for managing AgentTask persistence, retrieving task history,
 * observing real-time status updates, and tracking execution states in SQLite via Room.
 */
interface TaskRepository {
    /**
     * Observes all tasks ordered by creation timestamp descending.
     */
    fun observeAllTasks(): Flow<List<AgentTask>>

    /**
     * Observes task history with an optional limit (default 100).
     */
    fun observeTaskHistory(limit: Int = 100): Flow<List<AgentTask>>

    /**
     * Observes a single task reactively by ID.
     */
    fun observeTask(taskId: String): Flow<AgentTask?>

    /**
     * Observes the current execution status of a task by ID.
     */
    fun observeTaskStatus(taskId: String): Flow<TaskStatus?>

    /**
     * Observes all tasks with active, non-terminal execution states.
     */
    fun observeActiveTasks(): Flow<List<AgentTask>>

    /**
     * Observes tasks filtered by status.
     */
    fun observeTasksByStatus(status: TaskStatus): Flow<List<AgentTask>>

    /**
     * Asynchronously fetches a task by ID.
     */
    suspend fun getTask(taskId: String): AgentTask?

    /**
     * Asynchronously gets the current status of a task.
     */
    suspend fun getTaskStatus(taskId: String): TaskStatus?

    /**
     * Fetches recent task history snapshot.
     */
    suspend fun getTaskHistory(limit: Int = 100): List<AgentTask>

    /**
     * Fetches all currently active tasks.
     */
    suspend fun getActiveTasks(): List<AgentTask>

    /**
     * Fetches tasks matching a specific status.
     */
    suspend fun getTasksByStatus(status: TaskStatus): List<AgentTask>

    /**
     * Persists or updates the task state.
     */
    suspend fun saveTask(task: AgentTask)

    /**
     * Batch saves or updates multiple tasks.
     */
    suspend fun saveTasks(tasks: List<AgentTask>)

    /**
     * Updates the status of an existing task.
     */
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus)

    /**
     * Updates execution budget constraints on a task.
     */
    suspend fun updateTaskBudget(taskId: String, budget: TaskBudget)

    /**
     * Observes tasks constrained within a maximum step limit.
     */
    fun observeTasksByMaxSteps(maxSteps: Int): Flow<List<AgentTask>>

    /**
     * Deletes a task by ID.
     */
    suspend fun deleteTask(taskId: String)

    /**
     * Clears all persisted task history.
     */
    suspend fun clearHistory()

    /**
     * Retrieves the total count of persisted tasks.
     */
    suspend fun getTaskCount(): Int

    /**
     * Retrieves the count of tasks in a specific status.
     */
    suspend fun getTaskCountByStatus(status: TaskStatus): Int
}

/**
 * Room-backed implementation of [TaskRepository] that persists and restores [AgentTask]
 * states to and from SQLite using [TaskDao] and [TaskEntity].
 */
class RoomTaskRepository(
    private val taskDao: TaskDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TaskRepository {

    override fun observeAllTasks(): Flow<List<AgentTask>> {
        return taskDao.getAllTasks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTaskHistory(limit: Int): Flow<List<AgentTask>> {
        return taskDao.getTaskHistory(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTask(taskId: String): Flow<AgentTask?> {
        return taskDao.observeTaskById(taskId).map { it?.toDomain() }
    }

    override fun observeTaskStatus(taskId: String): Flow<TaskStatus?> {
        return taskDao.observeTaskStatus(taskId).map { statusStr ->
            statusStr?.let { parseStatus(it) }
        }
    }

    override fun observeActiveTasks(): Flow<List<AgentTask>> {
        return taskDao.getActiveTasks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTasksByStatus(status: TaskStatus): Flow<List<AgentTask>> {
        return taskDao.getTasksByStatus(status.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTask(taskId: String): AgentTask? = withContext(ioDispatcher) {
        taskDao.getTaskById(taskId)?.toDomain()
    }

    override suspend fun getTaskStatus(taskId: String): TaskStatus? = withContext(ioDispatcher) {
        taskDao.getTaskStatus(taskId)?.let { parseStatus(it) }
    }

    override suspend fun getTaskHistory(limit: Int): List<AgentTask> = withContext(ioDispatcher) {
        taskDao.getTaskHistoryList(limit).map { it.toDomain() }
    }

    override suspend fun getActiveTasks(): List<AgentTask> = withContext(ioDispatcher) {
        taskDao.getActiveTasksList().map { it.toDomain() }
    }

    override suspend fun getTasksByStatus(status: TaskStatus): List<AgentTask> = withContext(ioDispatcher) {
        taskDao.getTasksByStatusList(status.name).map { it.toDomain() }
    }

    override suspend fun saveTask(task: AgentTask): Unit = withContext(ioDispatcher) {
        val entity = task.toEntity()
        taskDao.insertTask(entity)
    }

    override suspend fun saveTasks(tasks: List<AgentTask>): Unit = withContext(ioDispatcher) {
        if (tasks.isEmpty()) return@withContext
        val entities = tasks.map { it.toEntity() }
        taskDao.insertTasks(entities)
    }

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus): Unit = withContext(ioDispatcher) {
        taskDao.updateTaskStatus(taskId, status.name, System.currentTimeMillis())
    }

    override suspend fun updateTaskBudget(taskId: String, budget: TaskBudget): Unit = withContext(ioDispatcher) {
        taskDao.updateTaskBudget(
            id = taskId,
            maxSteps = budget.maxSteps,
            maxToolCalls = budget.maxToolCalls,
            maxWallClockMs = budget.maxWallClockMs,
            updatedAt = System.currentTimeMillis()
        )
    }

    override fun observeTasksByMaxSteps(maxSteps: Int): Flow<List<AgentTask>> {
        return taskDao.getTasksByMaxSteps(maxSteps).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun deleteTask(taskId: String): Unit = withContext(ioDispatcher) {
        taskDao.deleteTask(taskId)
    }

    override suspend fun clearHistory(): Unit = withContext(ioDispatcher) {
        taskDao.deleteAllTasks()
    }

    override suspend fun getTaskCount(): Int = withContext(ioDispatcher) {
        taskDao.getTaskCount()
    }

    override suspend fun getTaskCountByStatus(status: TaskStatus): Int = withContext(ioDispatcher) {
        taskDao.getTaskCountByStatus(status.name)
    }

    // ==========================================
    // Entity / Domain Conversion Helpers
    // ==========================================

    companion object {
        fun parseStatus(value: String): TaskStatus {
            return try {
                TaskStatus.valueOf(value)
            } catch (_: Exception) {
                TaskStatus.PENDING
            }
        }

        fun parseOrigin(value: String): OriginSource {
            return try {
                OriginSource.valueOf(value)
            } catch (_: Exception) {
                OriginSource.WEB
            }
        }

        fun TaskEntity.toDomain(): AgentTask {
            val planObj = planJson?.let { parsePlan(it) }
            val finalRespObj = finalResponseJson?.let { parseFinalResponse(it) }
            val toolCallsList = parseToolCalls(toolCallsJson)
            val permissionEventsList = permissionEventsJson?.let { parsePermissionEvents(it) } ?: emptyList()
            val errorsList = parseErrors(errorsJson)
            val budgetUsageObj = parseBudgetUsage(budgetUsageJson)
            val budgetConfigObj = budget.toBudgetConfig()

            return AgentTask(
                taskId = taskId,
                status = parseStatus(status),
                createdAt = createdAt,
                updatedAt = updatedAt,
                request = request,
                plan = planObj,
                currentStepId = currentStepId,
                toolCalls = toolCallsList,
                permissionEvents = permissionEventsList,
                errors = errorsList,
                budgetConfig = budgetConfigObj,
                budgetUsage = budgetUsageObj,
                finalResponse = finalRespObj,
                traceId = traceId,
                origin = parseOrigin(origin),
                dryRun = dryRun,
                budget = budget
            )
        }

        fun AgentTask.toEntity(): TaskEntity {
            val planStr = plan?.let { serializePlan(it) }
            val finalRespStr = finalResponse?.let { serializeFinalResponse(it) }
            val toolCallsStr = serializeToolCalls(toolCalls)
            val permissionEventsStr = if (permissionEvents.isNotEmpty()) serializePermissionEvents(permissionEvents) else null
            val errorsStr = if (errors.isNotEmpty()) JSONArray(errors).toString() else ""
            val budgetUsageStr = serializeBudgetUsage(budgetUsage)
            val budgetConfigStr = serializeBudgetConfig(budget.toBudgetConfig())

            return TaskEntity(
                taskId = taskId,
                status = status.name,
                request = request,
                origin = origin.name,
                dryRun = dryRun,
                planJson = planStr,
                toolCallsJson = toolCallsStr,
                finalResponseJson = finalRespStr,
                errorsJson = errorsStr,
                budgetUsageJson = budgetUsageStr,
                traceId = traceId,
                createdAt = createdAt,
                updatedAt = updatedAt,
                currentStepId = currentStepId,
                permissionEventsJson = permissionEventsStr,
                budgetConfigJson = budgetConfigStr,
                budget = budget
            )
        }

        // --- Serialization Helpers ---

        private fun serializePlan(plan: Plan): String {
            return JSONObject().apply {
                put("planId", plan.planId)
                put("explanation", plan.explanation)
                val stepsArr = JSONArray()
                plan.steps.forEach { step ->
                    stepsArr.put(JSONObject().apply {
                        put("id", step.id)
                        put("description", step.description)
                        put("expectedTool", step.expectedTool)
                        put("status", step.status.name)
                        step.toolInput?.let { put("toolInput", it) }
                        step.toolOutput?.let { put("toolOutput", it) }
                        step.failureReason?.let { put("failureReason", it) }
                        if (step.dependencies.isNotEmpty()) {
                            put("dependencies", JSONArray(step.dependencies))
                        }
                    })
                }
                put("steps", stepsArr)
            }.toString()
        }

        private fun parsePlan(jsonStr: String): Plan? {
            return try {
                val obj = JSONObject(jsonStr)
                val planId = obj.optString("planId", "plan-1")
                val explanation = obj.optString("explanation", "")
                val stepsList = mutableListOf<PlanStep>()
                val stepsArr = obj.optJSONArray("steps")
                if (stepsArr != null) {
                    for (i in 0 until stepsArr.length()) {
                        val s = stepsArr.getJSONObject(i)
                        val stepStatus = try {
                            StepStatus.valueOf(s.optString("status", "PENDING"))
                        } catch (_: Exception) {
                            StepStatus.PENDING
                        }
                        val depsList = mutableListOf<String>()
                        val depsArr = s.optJSONArray("dependencies")
                        if (depsArr != null) {
                            for (j in 0 until depsArr.length()) {
                                depsList.add(depsArr.getString(j))
                            }
                        }
                        stepsList.add(
                            PlanStep(
                                id = s.optString("id", "step-$i"),
                                description = s.optString("description", ""),
                                expectedTool = s.optString("expectedTool", ""),
                                dependencies = depsList,
                                status = stepStatus,
                                toolInput = if (s.has("toolInput")) s.getString("toolInput") else null,
                                toolOutput = if (s.has("toolOutput")) s.getString("toolOutput") else null,
                                failureReason = if (s.has("failureReason")) s.getString("failureReason") else null
                            )
                        )
                    }
                }
                Plan(planId = planId, explanation = explanation, steps = stepsList)
            } catch (_: Exception) {
                null
            }
        }

        private fun serializeFinalResponse(response: FinalResponse): String {
            return JSONObject().apply {
                put("spokenSummary", response.spokenSummary)
                put("fullText", response.fullText)
                put("language", response.language)
            }.toString()
        }

        private fun parseFinalResponse(jsonStr: String): FinalResponse? {
            return try {
                val obj = JSONObject(jsonStr)
                FinalResponse(
                    spokenSummary = obj.optString("spokenSummary", ""),
                    fullText = obj.optString("fullText", ""),
                    language = obj.optString("language", "en")
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun serializeToolCalls(calls: List<ToolExecutionRecord>): String {
            val arr = JSONArray()
            calls.forEach { call ->
                arr.put(JSONObject().apply {
                    put("executionId", call.executionId)
                    put("toolId", call.toolId)
                    put("argumentsJson", call.argumentsJson)
                    put("outputJson", call.outputJson)
                    put("durationMs", call.durationMs)
                    put("successful", call.successful)
                    put("timestamp", call.timestamp)
                })
            }
            return arr.toString()
        }

        private fun parseToolCalls(jsonStr: String): List<ToolExecutionRecord> {
            if (!jsonStr.startsWith("[")) return emptyList()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<ToolExecutionRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        ToolExecutionRecord(
                            executionId = o.optString("executionId", "exec-$i"),
                            toolId = o.optString("toolId", ""),
                            argumentsJson = o.optString("argumentsJson", "{}"),
                            outputJson = o.optString("outputJson", ""),
                            durationMs = o.optLong("durationMs", 0L),
                            successful = o.optBoolean("successful", true),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun serializePermissionEvents(events: List<PermissionEventRecord>): String {
            val arr = JSONArray()
            events.forEach { event ->
                arr.put(JSONObject().apply {
                    put("permissionId", event.permissionId)
                    put("toolId", event.toolId)
                    put("argumentsJson", event.argumentsJson)
                    put("sideEffectsSummary", event.sideEffectsSummary)
                    put("decision", event.decision)
                    put("timestamp", event.timestamp)
                })
            }
            return arr.toString()
        }

        private fun parsePermissionEvents(jsonStr: String): List<PermissionEventRecord> {
            if (!jsonStr.startsWith("[")) return emptyList()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<PermissionEventRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        PermissionEventRecord(
                            permissionId = o.optString("permissionId", ""),
                            toolId = o.optString("toolId", ""),
                            argumentsJson = o.optString("argumentsJson", "{}"),
                            sideEffectsSummary = o.optString("sideEffectsSummary", ""),
                            decision = o.optString("decision", "PENDING"),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun serializeBudgetUsage(usage: BudgetUsage): String {
            return JSONObject().apply {
                put("stepsUsed", usage.stepsUsed)
                put("toolCallsUsed", usage.toolCallsUsed)
                put("retriesUsed", usage.retriesUsed)
                put("replansUsed", usage.replansUsed)
                put("tokensUsed", usage.tokensUsed)
                put("wallClockMsUsed", usage.wallClockMsUsed)
            }.toString()
        }

        private fun parseBudgetUsage(jsonStr: String): BudgetUsage {
            if (jsonStr.startsWith("{")) {
                return try {
                    val obj = JSONObject(jsonStr)
                    BudgetUsage(
                        stepsUsed = obj.optInt("stepsUsed", 0),
                        toolCallsUsed = obj.optInt("toolCallsUsed", 0),
                        retriesUsed = obj.optInt("retriesUsed", 0),
                        replansUsed = obj.optInt("replansUsed", 0),
                        tokensUsed = obj.optInt("tokensUsed", 0),
                        wallClockMsUsed = obj.optLong("wallClockMsUsed", 0L)
                    )
                } catch (_: Exception) {
                    BudgetUsage()
                }
            } else if (jsonStr.contains("/")) {
                // Fallback format e.g. "3/10"
                val parts = jsonStr.split("/")
                val steps = parts[0].toIntOrNull() ?: 0
                return BudgetUsage(stepsUsed = steps)
            }
            return BudgetUsage()
        }

        private fun serializeBudgetConfig(config: BudgetConfig): String {
            return JSONObject().apply {
                put("maxSteps", config.maxSteps)
                put("maxToolCalls", config.maxToolCalls)
                put("maxRetriesPerStep", config.maxRetriesPerStep)
                put("maxReplans", config.maxReplans)
                put("maxTokens", config.maxTokens)
                put("maxWallClockMs", config.maxWallClockMs)
                put("perToolTimeoutMs", config.perToolTimeoutMs)
            }.toString()
        }

        private fun parseBudgetConfig(jsonStr: String): BudgetConfig {
            return try {
                val obj = JSONObject(jsonStr)
                BudgetConfig(
                    maxSteps = obj.optInt("maxSteps", 10),
                    maxToolCalls = obj.optInt("maxToolCalls", 15),
                    maxRetriesPerStep = obj.optInt("maxRetriesPerStep", 2),
                    maxReplans = obj.optInt("maxReplans", 2),
                    maxTokens = obj.optInt("maxTokens", 8000),
                    maxWallClockMs = obj.optLong("maxWallClockMs", 60_000L),
                    perToolTimeoutMs = obj.optLong("perToolTimeoutMs", 10_000L)
                )
            } catch (_: Exception) {
                BudgetConfig()
            }
        }

        private fun parseErrors(errorsStr: String): List<String> {
            if (errorsStr.isBlank()) return emptyList()
            if (errorsStr.startsWith("[")) {
                return try {
                    val arr = JSONArray(errorsStr)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    list
                } catch (_: Exception) {
                    emptyList()
                }
            }
            return errorsStr.split(";").filter { it.isNotBlank() }
        }
    }
}
