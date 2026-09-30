package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.contracts.*
import com.example.core.*
import com.example.storage.AgentHubDatabase
import com.example.storage.EventDao
import com.example.storage.RoomEventRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
class TaskReplayServiceTest {

    private lateinit var database: AgentHubDatabase
    private lateinit var eventDao: EventDao
    private lateinit var eventRepository: RoomEventRepository
    private lateinit var replayService: TaskReplayService
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AgentHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventDao = database.eventDao()
        eventRepository = RoomEventRepository(eventDao, testDispatcher)
        replayService = DefaultTaskReplayService(eventRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testReplayFullHappyPathExecution() = runTest(testDispatcher) {
        val taskId = "task-replay-happy"

        // Build authentic sequence of events
        val events = listOf(
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_CREATED,
                payloadJson = JSONObject().put("request", "Calculate metrics").put("origin", "WEB").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.PLAN_CREATED,
                payloadJson = JSONObject().apply {
                    put("explanation", "Single step")
                    put("steps", org.json.JSONArray().put(JSONObject().apply {
                        put("id", "step-1")
                        put("description", "Sum metrics")
                        put("expectedTool", "calculator")
                        put("status", "PENDING")
                    }))
                }.toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.STEP_STARTED,
                payloadJson = JSONObject().put("stepId", "step-1").put("expectedTool", "calculator").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.TOOL_REQUESTED,
                payloadJson = JSONObject().put("toolId", "calculator").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.TOOL_EXECUTED,
                payloadJson = JSONObject().apply {
                    put("executionId", "exec-1")
                    put("toolId", "calculator")
                    put("outputJson", "42")
                    put("successful", true)
                    put("durationMs", 15L)
                }.toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.VERIFICATION_PASSED,
                payloadJson = JSONObject().put("stepId", "step-1").put("reason", "Accurate sum").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_COMPLETED,
                payloadJson = JSONObject().apply {
                    put("spokenSummary", "Computation finished: 42")
                    put("fullText", "All metrics calculated.")
                }.toString()
            )
        )

        // Persist through EventRepository with cryptographic SHA-256 chaining
        eventRepository.appendEvents(events)

        // Replay and derive task state
        val result = replayService.replayAndDeriveTaskState(taskId)

        assertEquals(taskId, result.taskId)
        assertEquals(7, result.eventsReplayed)
        assertTrue(result.isCryptographicChainIntact)
        assertTrue(result.areStateTransitionsValid)
        assertNull(result.firstViolation)

        // Derived domain task verification
        assertEquals(TaskStatus.COMPLETED, result.task.status)
        assertEquals("Calculate metrics", result.task.request)
        assertEquals(1, result.task.toolCalls.size)
        assertEquals("42", result.task.toolCalls[0].outputJson)
        assertNotNull(result.task.finalResponse)
        assertEquals("Computation finished: 42", result.task.finalResponse!!.spokenSummary)

        // Derived sealed AgentState verification (Task completed -> IDLE)
        assertEquals(AgentStateType.IDLE, result.agentState.type)
        assertTrue(result.agentState is AgentState.Idle)
        assertEquals(taskId, (result.agentState as AgentState.Idle).lastCompletedTaskId)

        // Detailed transition audit check
        assertEquals(7, result.transitionHistory.size)
        assertEquals(AgentStateType.IDLE, result.transitionHistory[0].fromState.type)
        assertEquals(AgentStateType.RUNNING, result.transitionHistory[0].toState.type)
        assertEquals(AgentStateType.RUNNING, result.transitionHistory.last().fromState.type)
        assertEquals(AgentStateType.IDLE, result.transitionHistory.last().toState.type)
    }

    @Test
    fun testReplayPolicyConfirmationAndApprovalCycle() = runTest(testDispatcher) {
        val taskId = "task-policy-cycle"

        val events = listOf(
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_CREATED,
                payloadJson = JSONObject().put("request", "Send status alert").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.STEP_STARTED,
                payloadJson = JSONObject().put("stepId", "step-1").put("expectedTool", "send_email").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.PERMISSION_REQUESTED,
                payloadJson = JSONObject().apply {
                    put("permissionId", "perm-777")
                    put("toolId", "send_email")
                    put("reason", "External dispatch clearance needed")
                    put("expiresAt", System.currentTimeMillis() + 600_000L)
                }.toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.PERMISSION_GRANTED,
                payloadJson = JSONObject().put("permissionId", "perm-777").put("toolId", "send_email").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_COMPLETED,
                payloadJson = JSONObject().put("spokenSummary", "Alert sent").toString()
            )
        )

        eventRepository.appendEvents(events)
        val result = replayService.replayAndDeriveTaskState(taskId)

        assertTrue(result.isCryptographicChainIntact)
        assertTrue(result.areStateTransitionsValid)

        // Verify transition through PAUSED state at event index 2
        val pausedTransition = result.transitionHistory[2]
        assertEquals(EventType.PERMISSION_REQUESTED, pausedTransition.event.type)
        assertEquals(AgentStateType.RUNNING, pausedTransition.fromState.type)
        assertEquals(AgentStateType.PAUSED, pausedTransition.toState.type)
        assertTrue(pausedTransition.toState is AgentState.Paused)
        val paused = pausedTransition.toState as AgentState.Paused
        assertEquals("perm-777", paused.pendingConfirmation?.permissionId)

        // Verify transition from PAUSED back to RUNNING at event index 3
        val resumedTransition = result.transitionHistory[3]
        assertEquals(EventType.PERMISSION_GRANTED, resumedTransition.event.type)
        assertEquals(AgentStateType.PAUSED, resumedTransition.fromState.type)
        assertEquals(AgentStateType.RUNNING, resumedTransition.toState.type)
    }

    @Test
    fun testReplayDetectsIllegalStateTransitions() = runTest(testDispatcher) {
        val taskId = "task-illegal-transitions"

        // Bad event sequence: STEP_STARTED occurred while agent was IDLE (missing TASK_CREATED)
        val badEvents = listOf(
            TaskEvent(
                taskId = taskId,
                type = EventType.STEP_STARTED,
                payloadJson = JSONObject().put("stepId", "step-unknown").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_COMPLETED,
                payloadJson = JSONObject().put("spokenSummary", "Premature completion").toString()
            )
        )

        eventRepository.appendEvents(badEvents)
        val result = replayService.replayAndDeriveTaskState(taskId)

        assertFalse("Transitions should be flagged as invalid", result.areStateTransitionsValid)
        assertNotNull(result.firstViolation)
        assertEquals(0, result.firstViolation!!.eventIndex)
        assertEquals(EventType.STEP_STARTED, result.firstViolation!!.event.type)
        assertTrue(result.firstViolation!!.violationMessage.contains("Illegal transition"))
    }

    @Test
    fun testReplayDetectsCompletedWhilePausedViolation() = runTest(testDispatcher) {
        val taskId = "task-paused-violation"

        // Sequence: TASK_CREATED -> PERMISSION_REQUESTED (PAUSED) -> TASK_COMPLETED (Illegal: Cannot complete while paused!)
        val invalidEvents = listOf(
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_CREATED,
                payloadJson = JSONObject().put("request", "Dangerous task").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.PERMISSION_REQUESTED,
                payloadJson = JSONObject().put("permissionId", "perm-1").put("toolId", "format_drive").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_COMPLETED,
                payloadJson = JSONObject().put("spokenSummary", "Done").toString()
            )
        )

        eventRepository.appendEvents(invalidEvents)
        val result = replayService.replayAndDeriveTaskState(taskId)

        assertFalse(result.areStateTransitionsValid)
        assertNotNull(result.firstViolation)
        assertEquals(2, result.firstViolation!!.eventIndex)
        assertEquals(EventType.TASK_COMPLETED, result.firstViolation!!.event.type)
        assertTrue(result.firstViolation!!.violationMessage.contains("while agent was PAUSED"))
    }

    @Test
    fun testPointInTimeReplay() = runTest(testDispatcher) {
        val taskId = "task-point-in-time"

        val events = listOf(
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_CREATED,
                payloadJson = JSONObject().put("request", "Time travel test").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.PERMISSION_REQUESTED,
                payloadJson = JSONObject().put("permissionId", "perm-mid").put("toolId", "delete_db").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.PERMISSION_GRANTED,
                payloadJson = JSONObject().put("permissionId", "perm-mid").put("toolId", "delete_db").toString()
            ),
            TaskEvent(
                taskId = taskId,
                type = EventType.TASK_COMPLETED,
                payloadJson = JSONObject().put("spokenSummary", "Done").toString()
            )
        )

        eventRepository.appendEvents(events)

        // Replay up to index 1 (PERMISSION_REQUESTED)
        val midResult = replayService.replayToPointInTime(taskId, maxEventIndex = 1)
        assertEquals(2, midResult.eventsReplayed)
        assertEquals(AgentStateType.PAUSED, midResult.agentState.type)
        assertTrue(midResult.agentState is AgentState.Paused)
        assertEquals("perm-mid", (midResult.agentState as AgentState.Paused).pendingConfirmation?.permissionId)
        assertTrue(midResult.areStateTransitionsValid)

        // Full replay
        val fullResult = replayService.replayAndDeriveTaskState(taskId)
        assertEquals(4, fullResult.eventsReplayed)
        assertEquals(AgentStateType.IDLE, fullResult.agentState.type)
    }

    @Test
    fun testObserveDerivedTaskStateFlow() = runTest(testDispatcher) {
        val taskId = "task-flow-observe"

        val event1 = TaskEvent(
            taskId = taskId,
            type = EventType.TASK_CREATED,
            payloadJson = JSONObject().put("request", "Streaming event test").toString()
        )
        eventRepository.appendEvent(event1)

        val flowResult = replayService.observeDerivedTaskState(taskId).first()
        assertEquals(1, flowResult.eventsReplayed)
        assertEquals(AgentStateType.RUNNING, flowResult.agentState.type)
        assertTrue(flowResult.areStateTransitionsValid)
    }
}
