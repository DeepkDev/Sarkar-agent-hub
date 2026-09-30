package com.example

import com.example.contracts.*
import com.example.core.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class AgentStateManagerTest {

    private lateinit var policyEngine: PolicyEngine
    private lateinit var stateManager: AgentStateManager

    @Before
    fun setup() {
        policyEngine = PolicyEngine()
        stateManager = AgentStateManager(policyEngine)
    }

    @Test
    fun testInitialStateIsIdle() {
        assertEquals(AgentStateType.IDLE, stateManager.currentStateType)
        assertTrue(stateManager.isIdle)
        assertFalse(stateManager.isRunning)
        assertFalse(stateManager.isPaused)
        assertFalse(stateManager.isError)

        val idleState = stateManager.currentState as AgentState.Idle
        assertEquals("Ready for tasks", idleState.message)
    }

    @Test
    fun testStartTaskTransitionsToRunning() {
        val started = stateManager.startTask("task-100")
        assertTrue(started)
        assertEquals(AgentStateType.RUNNING, stateManager.currentStateType)
        assertTrue(stateManager.isRunning)

        val running = stateManager.currentState as AgentState.Running
        assertEquals("task-100", running.taskId)
        assertNull(running.stepId)
        assertNull(running.currentTool)

        // Updating step
        stateManager.updateRunningStep("step-1", "calculator")
        val updated = stateManager.currentState as AgentState.Running
        assertEquals("step-1", updated.stepId)
        assertEquals("calculator", updated.currentTool)
    }

    @Test
    fun testPolicyAllowMaintainsRunningState() {
        stateManager.startTask("task-101")

        val safeToolRequest = ToolRequest(
            toolId = "calculator",
            toolName = "Calculator",
            permissionLevel = PermissionLevel.PUBLIC,
            sideEffects = SideEffectType.NONE
        )
        val context = TaskContext(taskId = "task-101", origin = OriginSource.WEB)

        val result = stateManager.evaluateAndTransition(safeToolRequest, context)
        assertTrue(result.allowed)
        assertFalse(result.needsConfirmation)
        assertEquals(EvaluationResult.ALLOW, result.evaluationResult)
        assertEquals(AgentStateType.RUNNING, stateManager.currentStateType)

        val running = stateManager.currentState as AgentState.Running
        assertEquals("calculator", running.currentTool)
    }

    @Test
    fun testPolicyConfirmationRequiredTransitionsToPaused() {
        stateManager.startTask("task-102")

        val confirmToolRequest = ToolRequest(
            toolId = "send_email",
            toolName = "Send Email",
            permissionLevel = PermissionLevel.CONFIRMATION_REQUIRED,
            sideEffects = SideEffectType.EXTERNAL
        )
        val context = TaskContext(taskId = "task-102", origin = OriginSource.WEB)

        val result = stateManager.evaluateAndTransition(confirmToolRequest, context)
        assertFalse(result.allowed)
        assertTrue(result.needsConfirmation)
        assertEquals(EvaluationResult.REQUIRE_CONFIRMATION, result.evaluationResult)
        assertEquals(AgentStateType.PAUSED, stateManager.currentStateType)
        assertTrue(stateManager.isPaused)

        val paused = stateManager.currentState as AgentState.Paused
        assertEquals(PauseReason.POLICY_CONFIRMATION, paused.reason)
        assertEquals("task-102", paused.taskId)
        assertNotNull(paused.pendingConfirmation)

        val confirmation = paused.pendingConfirmation!!
        assertEquals("send_email", confirmation.toolId)
        assertFalse(confirmation.isExpired)

        // Cannot resume without operator decision
        assertFalse(stateManager.resume())
        assertTrue(stateManager.isPaused)

        // Operator APPROVAL resumes execution
        val resumedState = stateManager.handleConfirmation(confirmation.permissionId, approved = true)
        assertEquals(AgentStateType.RUNNING, resumedState.type)
        assertTrue(stateManager.isRunning)
    }

    @Test
    fun testPolicyConfirmationRejectionTransitionsToError() {
        stateManager.startTask("task-103")

        val confirmToolRequest = ToolRequest(
            toolId = "delete_file",
            toolName = "Delete File",
            permissionLevel = PermissionLevel.CONFIRMATION_REQUIRED,
            sideEffects = SideEffectType.WRITE
        )
        val context = TaskContext(taskId = "task-103", origin = OriginSource.WEB)

        val result = stateManager.evaluateAndTransition(confirmToolRequest, context)
        val paused = stateManager.currentState as AgentState.Paused
        val permId = paused.pendingConfirmation!!.permissionId

        // Operator REJECTION transitions to Error
        val errorState = stateManager.handleConfirmation(permId, approved = false, operatorComment = "Unauthorized delete")
        assertEquals(AgentStateType.ERROR, errorState.type)
        assertTrue(stateManager.isError)

        val error = stateManager.currentState as AgentState.Error
        assertEquals("OPERATOR_DENIED", error.code)
        assertTrue(error.policyViolation)
        assertTrue(error.message.contains("Unauthorized delete"))
    }

    @Test
    fun testPolicyRestrictedToolTransitionsToError() {
        stateManager.startTask("task-104")

        val restrictedRequest = ToolRequest(
            toolId = "root_shell",
            toolName = "Root Shell",
            permissionLevel = PermissionLevel.RESTRICTED,
            sideEffects = SideEffectType.EXTERNAL
        )
        val context = TaskContext(taskId = "task-104", origin = OriginSource.WEB)

        val result = stateManager.evaluateAndTransition(restrictedRequest, context)
        assertFalse(result.allowed)
        assertFalse(result.needsConfirmation)
        assertEquals(EvaluationResult.DENY, result.evaluationResult)
        assertEquals(AgentStateType.ERROR, stateManager.currentStateType)
        assertTrue(stateManager.isError)

        val error = stateManager.currentState as AgentState.Error
        assertEquals("POLICY_DENIED", error.code)
        assertTrue(error.policyViolation)
    }

    @Test
    fun testEmergencyKillSwitchHaltsAgent() {
        stateManager.startTask("task-105")
        assertTrue(stateManager.isRunning)

        // Engage kill switch
        val error = stateManager.engageEmergencyKillSwitch("Threat detected: Global lockdown")
        assertEquals(AgentStateType.ERROR, error.type)
        assertEquals("KILL_SWITCH_ENGAGED", (error as AgentState.Error).code)
        assertFalse(error.recoverable)
        assertTrue(stateManager.isError)

        // Subsequent requests are DENIED by policy engine
        val request = ToolRequest(
            toolId = "calculator",
            toolName = "Calculator",
            permissionLevel = PermissionLevel.PUBLIC
        )
        val context = TaskContext(taskId = "task-105")
        val result = stateManager.evaluateAndTransition(request, context)
        assertFalse(result.allowed)
        assertEquals(EvaluationResult.DENY, result.evaluationResult)

        // Disengage kill switch resets agent to idle
        val idle = stateManager.disengageEmergencyKillSwitch()
        assertEquals(AgentStateType.IDLE, idle.type)
        assertTrue(stateManager.isIdle)
    }

    @Test
    fun testManualPauseAndResume() {
        stateManager.startTask("task-106")
        assertTrue(stateManager.isRunning)

        // Manual pause
        assertTrue(stateManager.pause(PauseReason.MANUAL))
        assertEquals(AgentStateType.PAUSED, stateManager.currentStateType)
        val paused = stateManager.currentState as AgentState.Paused
        assertEquals(PauseReason.MANUAL, paused.reason)
        assertNull(paused.pendingConfirmation)

        // Manual resume
        assertTrue(stateManager.resume())
        assertEquals(AgentStateType.RUNNING, stateManager.currentStateType)
    }

    @Test
    fun testTaskCompletionTransitionsToIdle() {
        stateManager.startTask("task-107")
        assertTrue(stateManager.isRunning)

        stateManager.completeTask("All steps verified")
        assertEquals(AgentStateType.IDLE, stateManager.currentStateType)
        val idle = stateManager.currentState as AgentState.Idle
        assertEquals("task-107", idle.lastCompletedTaskId)
    }

    @Test
    fun testStateTransitionHistoryAudit() {
        stateManager.startTask("task-history")
        stateManager.pause(PauseReason.MANUAL)
        stateManager.resume()
        stateManager.completeTask("Finished")

        val history = stateManager.transitionHistory
        assertTrue(history.size >= 4)

        assertEquals(AgentStateType.IDLE, history[0].fromState.type)
        assertEquals(AgentStateType.RUNNING, history[0].toState.type)

        assertEquals(AgentStateType.RUNNING, history[1].fromState.type)
        assertEquals(AgentStateType.PAUSED, history[1].toState.type)

        assertEquals(AgentStateType.PAUSED, history[2].fromState.type)
        assertEquals(AgentStateType.RUNNING, history[2].toState.type)

        assertEquals(AgentStateType.RUNNING, history[3].fromState.type)
        assertEquals(AgentStateType.IDLE, history[3].toState.type)
    }
}
