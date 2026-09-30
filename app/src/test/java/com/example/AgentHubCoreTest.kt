package com.example

import com.example.contracts.*
import com.example.core.EventSourcing
import com.example.core.Orchestrator
import com.example.core.PolicyEngine
import com.example.core.TaskStateMachine
import com.example.knowledge.KnowledgeManager
import com.example.providers.MockProvider
import com.example.tools.CalculatorTool
import com.example.tools.SafeMathParser
import com.example.tools.ToolExecutor
import com.example.tools.ToolRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class AgentHubCoreTest {

    @Test
    fun testStateMachineValidTransitions() {
        assertTrue(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.PLANNING))
        assertTrue(TaskStateMachine.canTransition(TaskStatus.PLANNING, TaskStatus.EXECUTING))
        assertTrue(TaskStateMachine.canTransition(TaskStatus.EXECUTING, TaskStatus.VERIFYING))
        assertTrue(TaskStateMachine.canTransition(TaskStatus.VERIFYING, TaskStatus.COMPLETED))
        assertTrue(TaskStateMachine.canTransition(TaskStatus.EXECUTING, TaskStatus.WAITING_FOR_PERMISSION))
    }

    @Test
    fun testStateMachineIllegalTransitionsThrow() {
        assertFalse(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.COMPLETED))
        assertFalse(TaskStateMachine.canTransition(TaskStatus.COMPLETED, TaskStatus.EXECUTING))

        assertThrows(IllegalStateException::class.java) {
            TaskStateMachine.validateTransition(TaskStatus.COMPLETED, TaskStatus.PLANNING)
        }
    }

    @Test
    fun testPolicyEngineClearanceLevels() {
        val task = AgentTask(request = "Test clearance")

        val safeTool = ToolManifest(
            id = "test_safe",
            name = "Safe Tool",
            description = "Safe",
            category = "Test",
            inputParameters = emptyList(),
            permissionLevel = PermissionLevel.SAFE,
            sideEffects = SideEffectType.NONE,
            status = CapabilityStatus.IMPLEMENTED
        )
        val safeDecision = PolicyEngine.evaluate(safeTool, "{}", task)
        assertEquals(PolicyDecisionType.ALLOW, safeDecision.type)

        val confirmTool = ToolManifest(
            id = "test_confirm",
            name = "Confirm Tool",
            description = "Requires confirmation",
            category = "Test",
            inputParameters = emptyList(),
            permissionLevel = PermissionLevel.CONFIRMATION_REQUIRED,
            sideEffects = SideEffectType.WRITE,
            status = CapabilityStatus.IMPLEMENTED
        )
        val confirmDecision = PolicyEngine.evaluate(confirmTool, "{}", task)
        assertEquals(PolicyDecisionType.REQUIRE_CONFIRMATION, confirmDecision.type)
        assertNotNull(confirmDecision.permissionId)

        val restrictedTool = ToolManifest(
            id = "test_restricted",
            name = "Restricted Tool",
            description = "Restricted",
            category = "Test",
            inputParameters = emptyList(),
            permissionLevel = PermissionLevel.RESTRICTED,
            sideEffects = SideEffectType.WRITE,
            status = CapabilityStatus.IMPLEMENTED
        )
        val restrictedDecision = PolicyEngine.evaluate(restrictedTool, "{}", task)
        assertEquals(PolicyDecisionType.DENY, restrictedDecision.type)
    }

    @Test
    fun testPolicyEngineGlobalKillSwitch() {
        val task = AgentTask(request = "Kill switch test")
        val safeTool = ToolManifest(
            id = "test_safe",
            name = "Safe Tool",
            description = "Safe",
            category = "Test",
            inputParameters = emptyList(),
            permissionLevel = PermissionLevel.SAFE,
            sideEffects = SideEffectType.NONE,
            status = CapabilityStatus.IMPLEMENTED
        )
        val killConfig = PolicyConfig(killSwitchEngaged = true)
        val decision = PolicyEngine.evaluate(safeTool, "{}", task, killConfig)
        assertEquals(PolicyDecisionType.DENY, decision.type)
        assertTrue(decision.reason.contains("Kill Switch"))
    }

    @Test
    fun testSafeMathParserEvaluationAndSecurity() {
        assertEquals(60.0, SafeMathParser.evaluate("(15 * 4)"), 0.001)
        assertEquals(100.0, SafeMathParser.evaluate("(15 * 4) + 120 / 3"), 0.001)
        assertEquals(8.0, SafeMathParser.evaluate("2 ^ 3"), 0.001)

        // Injection attempts must throw clean IllegalArgumentException
        assertThrows(IllegalArgumentException::class.java) {
            SafeMathParser.evaluate("import('os')")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SafeMathParser.evaluate("eval(alert(1))")
        }
    }

    @Test
    fun testEventSourcingHashChainingAndIntegrity() {
        val event1 = TaskEvent.createWithHash("task-1", EventType.TASK_CREATED, "{}", "GENESIS")
        val event2 = TaskEvent.createWithHash("task-1", EventType.PLAN_CREATED, "{}", event1.eventHash)
        val event3 = TaskEvent.createWithHash("task-1", EventType.STEP_STARTED, "{}", event2.eventHash)

        val (valid, _) = EventSourcing.verifyChainIntegrity(listOf(event1, event2, event3))
        assertTrue(valid)

        // Tamper test: Corrupted event hash
        val tamperedEvent2 = event2.copy(prevHash = "CORRUPTED_HASH")
        val (tamperedValid, _) = EventSourcing.verifyChainIntegrity(listOf(event1, tamperedEvent2, event3))
        assertFalse(tamperedValid)
    }

    @Test
    fun testSealedClassAgentTaskEvents() {
        val created = AgentTaskEvent.TaskCreated(
            taskId = "task-sealed-1",
            request = "Test sealed class",
            origin = OriginSource.SARKAR
        )
        assertEquals(EventType.TASK_CREATED, created.eventType)
        assertEquals("GENESIS", created.prevHash)
        assertTrue(created.eventHash.isNotBlank())

        val planCreated = AgentTaskEvent.PlanCreated(
            taskId = "task-sealed-1",
            planId = "p-1",
            explanation = "Sample plan",
            steps = listOf(PlanStep(id = "s-1", description = "Run step", expectedTool = "calculator")),
            prevHash = created.eventHash
        )
        assertEquals(EventType.PLAN_CREATED, planCreated.eventType)

        val stepStarted = AgentTaskEvent.StepStarted(
            taskId = "task-sealed-1",
            stepId = "s-1",
            description = "Run step",
            expectedTool = "calculator",
            prevHash = planCreated.eventHash
        )

        val permRequested = AgentTaskEvent.PermissionRequested(
            taskId = "task-sealed-1",
            permissionId = "perm-1",
            toolId = "send_email",
            argumentsJson = "{\"to\":\"admin@sarkar.hub\"}",
            sideEffectsSummary = "Outbound dispatch",
            expiresAt = System.currentTimeMillis() + 60000L,
            prevHash = stepStarted.eventHash
        )
        assertEquals(EventType.PERMISSION_REQUESTED, permRequested.eventType)

        val toolExecuted = AgentTaskEvent.ToolExecuted(
            taskId = "task-sealed-1",
            executionId = "exec-1",
            toolId = "calculator",
            argumentsJson = "{\"expression\":\"1+1\"}",
            outputJson = "{\"result\":2}",
            durationMs = 15L,
            successful = true,
            prevHash = permRequested.eventHash
        )
        assertEquals(EventType.TOOL_EXECUTED, toolExecuted.eventType)

        // Test folding typed events onto state
        var state = AgentTask(taskId = "task-sealed-1", request = "Init")
        state = EventSourcing.applyTypedEvent(state, created)
        assertEquals(TaskStatus.PLANNING, state.status)
        state = EventSourcing.applyTypedEvent(state, planCreated)
        assertEquals(TaskStatus.EXECUTING, state.status)
        assertEquals(1, state.plan?.steps?.size)
        state = EventSourcing.applyTypedEvent(state, stepStarted)
        assertEquals("s-1", state.currentStepId)
        state = EventSourcing.applyTypedEvent(state, permRequested)
        assertEquals(TaskStatus.WAITING_FOR_PERMISSION, state.status)
        assertEquals(1, state.permissionEvents.size)
        state = EventSourcing.applyTypedEvent(state, toolExecuted)
        assertEquals(TaskStatus.VERIFYING, state.status)
        assertEquals(1, state.toolCalls.size)
    }

    @Test
    fun testEventMetadataMoshiCompatibilityAndTamperEvidence() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(EventMetadata::class.java)

        val metadata = EventMetadata(
            eventId = "evt-100",
            taskId = "task-alpha",
            traceId = "trace-alpha-99",
            timestamp = 1727680000000L,
            prevHash = "GENESIS",
            eventHash = "hash123abc"
        )

        // Serialize
        val json = adapter.toJson(metadata)
        assertTrue(json.contains("\"taskId\":\"task-alpha\""))
        assertTrue(json.contains("\"traceId\":\"trace-alpha-99\""))
        assertTrue(json.contains("\"timestamp\":1727680000000"))
        assertTrue(json.contains("\"prevHash\":\"GENESIS\""))
        assertTrue(json.contains("\"eventId\":\"evt-100\""))

        // Deserialize
        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals("task-alpha", deserialized?.taskId)
        assertEquals("trace-alpha-99", deserialized?.traceId)
        assertEquals(1727680000000L, deserialized?.timestamp)
        assertEquals("GENESIS", deserialized?.prevHash)
        assertEquals("hash123abc", deserialized?.eventHash)

        // TaskEvent Moshi serialization
        val taskEventAdapter = moshi.adapter(TaskEvent::class.java)
        val event = TaskEvent(
            metadata = metadata,
            type = EventType.STEP_STARTED,
            payloadJson = "{\"stepId\":\"step-1\"}"
        )
        val taskEventJson = taskEventAdapter.toJson(event)
        assertTrue(taskEventJson.contains("\"metadata\""))
        assertTrue(taskEventJson.contains("\"type\":\"STEP_STARTED\""))

        val parsedTaskEvent = taskEventAdapter.fromJson(taskEventJson)
        assertNotNull(parsedTaskEvent)
        assertEquals("task-alpha", parsedTaskEvent?.taskId)
        assertEquals("trace-alpha-99", parsedTaskEvent?.traceId)
        assertEquals("GENESIS", parsedTaskEvent?.prevHash)
        assertEquals(EventType.STEP_STARTED, parsedTaskEvent?.type)
    }

    @Test
    fun testOrchestratorEndToEndFullTaskRun() = runTest {
        val knowledgeManager = KnowledgeManager()
        val toolRegistry = ToolRegistry(knowledgeManager)
        val toolExecutor = ToolExecutor(toolRegistry)
        val orchestrator = Orchestrator(toolRegistry, toolExecutor)
        val mockProvider = MockProvider()

        val task = orchestrator.submitTask(
            request = "Calculate total server costs: 45 * 12",
            provider = mockProvider,
            origin = OriginSource.WEB,
            scope = this
        )

        testScheduler.advanceUntilIdle()

        val finalTask = orchestrator.getTask(task.taskId)
        assertNotNull(finalTask)
        assertEquals(TaskStatus.COMPLETED, finalTask?.status)
        assertNotNull(finalTask?.finalResponse)
        assertTrue(finalTask!!.finalResponse!!.spokenSummary.isNotBlank())
        assertTrue(finalTask.toolCalls.isNotEmpty())
        assertEquals("calculator", finalTask.toolCalls.first().toolId)
        assertTrue(finalTask.toolCalls.first().successful)
    }
}
