package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.contracts.*
import com.example.storage.AgentHubDatabase
import com.example.storage.RoomTaskRepository
import com.example.storage.TaskDao
import com.example.storage.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryTest {

    private lateinit var database: AgentHubDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var repository: TaskRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AgentHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = database.taskDao()
        repository = RoomTaskRepository(taskDao, testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testPersistAndRetrieveTaskState() = runTest(testDispatcher) {
        val task = AgentTask(
            taskId = "task-alpha-1",
            status = TaskStatus.EXECUTING,
            request = "Analyze network metrics and send summary",
            origin = OriginSource.API,
            dryRun = false,
            currentStepId = "step-2",
            plan = Plan(
                planId = "plan-123",
                explanation = "Execute 2 steps",
                steps = listOf(
                    PlanStep(id = "step-1", description = "Calculate sums", expectedTool = "calculator", status = StepStatus.COMPLETED),
                    PlanStep(id = "step-2", description = "Send alert", expectedTool = "send_email", status = StepStatus.RUNNING)
                )
            ),
            toolCalls = listOf(
                ToolExecutionRecord(
                    executionId = "call-1",
                    toolId = "calculator",
                    argumentsJson = "{\"expression\":\"100 + 50\"}",
                    outputJson = "150",
                    durationMs = 25L,
                    successful = true
                )
            ),
            permissionEvents = listOf(
                PermissionEventRecord(
                    permissionId = "perm-1",
                    toolId = "send_email",
                    argumentsJson = "{\"to\":\"admin@example.com\"}",
                    sideEffectsSummary = "External communication",
                    decision = "APPROVED"
                )
            ),
            errors = listOf("Minor warning on connection retry"),
            budgetConfig = BudgetConfig(maxSteps = 8, maxToolCalls = 12),
            budgetUsage = BudgetUsage(stepsUsed = 1, toolCallsUsed = 1, tokensUsed = 420),
            finalResponse = null,
            traceId = "trace-alpha-99"
        )

        // Save task
        repository.saveTask(task)

        // Retrieve task state from SQLite
        val retrieved = repository.getTask("task-alpha-1")
        assertNotNull(retrieved)
        retrieved!!

        assertEquals("task-alpha-1", retrieved.taskId)
        assertEquals(TaskStatus.EXECUTING, retrieved.status)
        assertEquals("Analyze network metrics and send summary", retrieved.request)
        assertEquals(OriginSource.API, retrieved.origin)
        assertEquals("step-2", retrieved.currentStepId)
        assertEquals("trace-alpha-99", retrieved.traceId)

        // Verify nested Plan preservation
        assertNotNull(retrieved.plan)
        assertEquals("plan-123", retrieved.plan!!.planId)
        assertEquals(2, retrieved.plan!!.steps.size)
        assertEquals("calculator", retrieved.plan!!.steps[0].expectedTool)
        assertEquals(StepStatus.COMPLETED, retrieved.plan!!.steps[0].status)
        assertEquals(StepStatus.RUNNING, retrieved.plan!!.steps[1].status)

        // Verify ToolExecutionRecord preservation
        assertEquals(1, retrieved.toolCalls.size)
        assertEquals("calculator", retrieved.toolCalls[0].toolId)
        assertEquals("150", retrieved.toolCalls[0].outputJson)
        assertTrue(retrieved.toolCalls[0].successful)

        // Verify PermissionEventRecord preservation
        assertEquals(1, retrieved.permissionEvents.size)
        assertEquals("perm-1", retrieved.permissionEvents[0].permissionId)
        assertEquals("APPROVED", retrieved.permissionEvents[0].decision)

        // Verify BudgetUsage and Config
        assertEquals(8, retrieved.budgetConfig.maxSteps)
        assertEquals(1, retrieved.budgetUsage.stepsUsed)
        assertEquals(420, retrieved.budgetUsage.tokensUsed)
    }

    @Test
    fun testRetrieveTaskStatus() = runTest(testDispatcher) {
        val task = AgentTask(
            taskId = "task-beta-1",
            status = TaskStatus.PLANNING,
            request = "Draft report"
        )
        repository.saveTask(task)

        val status = repository.getTaskStatus("task-beta-1")
        assertEquals(TaskStatus.PLANNING, status)

        // Update status in SQLite
        repository.updateTaskStatus("task-beta-1", TaskStatus.COMPLETED)
        val updatedStatus = repository.getTaskStatus("task-beta-1")
        assertEquals(TaskStatus.COMPLETED, updatedStatus)
    }

    @Test
    fun testObserveTaskStatusReactive() = runTest(testDispatcher) {
        val task = AgentTask(
            taskId = "task-gamma-1",
            status = TaskStatus.PENDING,
            request = "Execute batch job"
        )
        repository.saveTask(task)

        val initialStatus = repository.observeTaskStatus("task-gamma-1").first()
        assertEquals(TaskStatus.PENDING, initialStatus)

        repository.updateTaskStatus("task-gamma-1", TaskStatus.EXECUTING)
        val updatedStatus = repository.observeTaskStatus("task-gamma-1").first()
        assertEquals(TaskStatus.EXECUTING, updatedStatus)
    }

    @Test
    fun testRetrieveTaskHistoryAndFiltering() = runTest(testDispatcher) {
        val task1 = AgentTask(taskId = "t-1", status = TaskStatus.COMPLETED, request = "Task 1", createdAt = 1000L)
        val task2 = AgentTask(taskId = "t-2", status = TaskStatus.FAILED, request = "Task 2", createdAt = 2000L)
        val task3 = AgentTask(taskId = "t-3", status = TaskStatus.EXECUTING, request = "Task 3", createdAt = 3000L)

        repository.saveTasks(listOf(task1, task2, task3))

        // Query history
        val history = repository.getTaskHistory(limit = 10)
        assertEquals(3, history.size)
        // Ordered by createdAt DESC
        assertEquals("t-3", history[0].taskId)
        assertEquals("t-2", history[1].taskId)
        assertEquals("t-1", history[2].taskId)

        // Query by status
        val completed = repository.getTasksByStatus(TaskStatus.COMPLETED)
        assertEquals(1, completed.size)
        assertEquals("t-1", completed[0].taskId)

        val failed = repository.getTasksByStatus(TaskStatus.FAILED)
        assertEquals(1, failed.size)
        assertEquals("t-2", failed[0].taskId)

        // Query active (non-terminal) tasks
        val active = repository.getActiveTasks()
        assertEquals(1, active.size)
        assertEquals("t-3", active[0].taskId)
        assertEquals(TaskStatus.EXECUTING, active[0].status)

        // Counts
        assertEquals(3, repository.getTaskCount())
        assertEquals(1, repository.getTaskCountByStatus(TaskStatus.EXECUTING))
        assertEquals(1, repository.getTaskCountByStatus(TaskStatus.COMPLETED))
        assertEquals(0, repository.getTaskCountByStatus(TaskStatus.CANCELLED))
    }

    @Test
    fun testDeleteTaskAndClearHistory() = runTest(testDispatcher) {
        val taskA = AgentTask(taskId = "del-1", status = TaskStatus.COMPLETED, request = "Job A")
        val taskB = AgentTask(taskId = "del-2", status = TaskStatus.COMPLETED, request = "Job B")
        repository.saveTasks(listOf(taskA, taskB))

        assertEquals(2, repository.getTaskCount())

        // Delete single task
        repository.deleteTask("del-1")
        assertNull(repository.getTask("del-1"))
        assertNotNull(repository.getTask("del-2"))
        assertEquals(1, repository.getTaskCount())

        // Clear all history
        repository.clearHistory()
        assertEquals(0, repository.getTaskCount())
        assertTrue(repository.getTaskHistory().isEmpty())
    }

    @Test
    fun testFinalResponseSerialization() = runTest(testDispatcher) {
        val task = AgentTask(
            taskId = "final-resp-task",
            status = TaskStatus.COMPLETED,
            request = "Summarize meeting notes",
            finalResponse = FinalResponse(
                spokenSummary = "All items resolved",
                fullText = "Here is the comprehensive breakdown of decisions made.",
                language = "en-US"
            )
        )
        repository.saveTask(task)

        val retrieved = repository.getTask("final-resp-task")
        assertNotNull(retrieved)
        assertNotNull(retrieved!!.finalResponse)
        assertEquals("All items resolved", retrieved.finalResponse!!.spokenSummary)
        assertEquals("Here is the comprehensive breakdown of decisions made.", retrieved.finalResponse!!.fullText)
        assertEquals("en-US", retrieved.finalResponse!!.language)
    }

    @Test
    fun testTaskBudgetConstraintsAndExhaustion() {
        val budget = TaskBudget(
            maxSteps = 5,
            maxToolCalls = 8,
            maxWallClockMs = 30_000L
        )

        assertFalse(budget.isExhausted(stepsUsed = 4, toolCallsUsed = 7, wallClockMsUsed = 25_000L))
        assertTrue(budget.isExhausted(stepsUsed = 5, toolCallsUsed = 2, wallClockMsUsed = 10_000L))
        assertTrue(budget.isExhausted(stepsUsed = 2, toolCallsUsed = 8, wallClockMsUsed = 10_000L))
        assertTrue(budget.isExhausted(stepsUsed = 2, toolCallsUsed = 2, wallClockMsUsed = 30_000L))

        assertEquals(1, budget.stepsRemaining(stepsUsed = 4))
        assertEquals(3, budget.toolCallsRemaining(toolCallsUsed = 5))
        assertEquals(10_000L, budget.wallClockMsRemaining(wallClockMsUsed = 20_000L))
    }

    @Test
    fun testTaskBudgetEmbeddedInTaskRoomEntity() = runTest(testDispatcher) {
        val customBudget = TaskBudget(
            maxSteps = 4,
            maxToolCalls = 7,
            maxWallClockMs = 45_000L,
            maxRetriesPerStep = 3,
            maxReplans = 1,
            maxTokens = 4096
        )

        val task = AgentTask(
            taskId = "budget-task-1",
            status = TaskStatus.PENDING,
            request = "Constrained execution test",
            budget = customBudget
        )

        repository.saveTask(task)

        // Directly query raw TaskEntity/Task from SQLite via taskDao
        val entity = taskDao.getTaskById("budget-task-1")
        assertNotNull(entity)
        assertEquals(4, entity!!.budget.maxSteps)
        assertEquals(7, entity.budget.maxToolCalls)
        assertEquals(45_000L, entity.budget.maxWallClockMs)
        assertEquals(3, entity.budget.maxRetriesPerStep)
        assertEquals(1, entity.budget.maxReplans)
        assertEquals(4096, entity.budget.maxTokens)

        // Verify retrieval via repository returns populated TaskBudget
        val retrieved = repository.getTask("budget-task-1")
        assertNotNull(retrieved)
        assertEquals(4, retrieved!!.budget.maxSteps)
        assertEquals(7, retrieved.budget.maxToolCalls)
        assertEquals(45_000L, retrieved.budget.maxWallClockMs)
    }

    @Test
    fun testQueryAndFilterTasksByBudgetConstraints() = runTest(testDispatcher) {
        val smallTask = AgentTask(
            taskId = "small-task",
            status = TaskStatus.PENDING,
            request = "Quick task",
            budget = TaskBudget(maxSteps = 3, maxWallClockMs = 15_000L)
        )
        val largeTask = AgentTask(
            taskId = "large-task",
            status = TaskStatus.PENDING,
            request = "Heavy task",
            budget = TaskBudget(maxSteps = 20, maxWallClockMs = 120_000L)
        )

        repository.saveTasks(listOf(smallTask, largeTask))

        val smallStepsTasks = repository.observeTasksByMaxSteps(maxSteps = 5).first()
        assertEquals(1, smallStepsTasks.size)
        assertEquals("small-task", smallStepsTasks[0].taskId)

        val allStepsTasks = repository.observeTasksByMaxSteps(maxSteps = 25).first()
        assertEquals(2, allStepsTasks.size)
    }

    @Test
    fun testUpdateTaskBudgetInSQLite() = runTest(testDispatcher) {
        val task = AgentTask(
            taskId = "dynamic-budget-task",
            status = TaskStatus.EXECUTING,
            request = "Adaptive budget task",
            budget = TaskBudget(maxSteps = 5, maxToolCalls = 5, maxWallClockMs = 30_000L)
        )
        repository.saveTask(task)

        // Adaptively extend budget
        val extendedBudget = TaskBudget(maxSteps = 15, maxToolCalls = 25, maxWallClockMs = 90_000L)
        repository.updateTaskBudget("dynamic-budget-task", extendedBudget)

        val updatedEntity = taskDao.getTaskById("dynamic-budget-task")
        assertNotNull(updatedEntity)
        assertEquals(15, updatedEntity!!.budget.maxSteps)
        assertEquals(25, updatedEntity.budget.maxToolCalls)
        assertEquals(90_000L, updatedEntity.budget.maxWallClockMs)

        val updatedDomain = repository.getTask("dynamic-budget-task")
        assertNotNull(updatedDomain)
        assertEquals(15, updatedDomain!!.budget.maxSteps)
        assertEquals(25, updatedDomain.budget.maxToolCalls)
        assertEquals(90_000L, updatedDomain.budget.maxWallClockMs)
    }
}
