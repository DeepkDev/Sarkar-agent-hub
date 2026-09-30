package com.example

import com.example.core.IllegalStateTransitionException
import com.example.core.TaskOrchestrator
import com.example.core.TaskTransitionRules
import com.example.model.TaskEvent
import com.example.model.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskOrchestratorTest {

    private lateinit var orchestrator: TaskOrchestrator

    @Before
    fun setup() {
        orchestrator = TaskOrchestrator()
    }

    @Test
    fun testValidLifecycleProgression() {
        val taskId = "task-valid-lifecycle"

        // 1. Initial pending
        var state = orchestrator.transition(taskId, TaskStatus.PENDING)
        assertEquals(TaskStatus.PENDING, state.status)

        // 2. PENDING -> PLANNING
        state = orchestrator.transition(taskId, TaskStatus.PLANNING)
        assertEquals(TaskStatus.PLANNING, state.status)

        // 3. PLANNING -> EXECUTING
        state = orchestrator.transition(taskId, TaskStatus.EXECUTING)
        assertEquals(TaskStatus.EXECUTING, state.status)

        // 4. EXECUTING -> VERIFYING
        state = orchestrator.transition(taskId, TaskStatus.VERIFYING)
        assertEquals(TaskStatus.VERIFYING, state.status)

        // 5. VERIFYING -> COMPLETED
        state = orchestrator.transition(taskId, TaskStatus.COMPLETED)
        assertEquals(TaskStatus.COMPLETED, state.status)
        assertTrue(state.status.isTerminal)
    }

    @Test
    fun testPermissionWaitAndResumeCycle() {
        val taskId = "task-permission-cycle"

        orchestrator.transition(taskId, TaskStatus.PLANNING)

        // PLANNING -> WAITING_FOR_PERMISSION
        val waitingState = orchestrator.transition(taskId, TaskStatus.WAITING_FOR_PERMISSION)
        assertEquals(TaskStatus.WAITING_FOR_PERMISSION, waitingState.status)

        // WAITING_FOR_PERMISSION -> EXECUTING
        val executingState = orchestrator.transition(taskId, TaskStatus.EXECUTING)
        assertEquals(TaskStatus.EXECUTING, executingState.status)
    }

    @Test
    fun testPauseAndResumeCycle() {
        val taskId = "task-pause-cycle"

        orchestrator.transition(taskId, TaskStatus.EXECUTING)

        // EXECUTING -> PAUSED
        val paused = orchestrator.transition(taskId, TaskStatus.PAUSED)
        assertEquals(TaskStatus.PAUSED, paused.status)

        // PAUSED -> EXECUTING
        val resumed = orchestrator.transition(taskId, TaskStatus.EXECUTING)
        assertEquals(TaskStatus.EXECUTING, resumed.status)
    }

    @Test
    fun testIllegalTransitionPendingToCompletedThrows() {
        val taskId = "task-illegal-pending-completed"
        orchestrator.transition(taskId, TaskStatus.PENDING)

        assertThrows(IllegalStateTransitionException::class.java) {
            orchestrator.transition(taskId, TaskStatus.COMPLETED)
        }
    }

    @Test
    fun testTerminalStatesPreventFurtherTransitions() {
        val taskId = "task-terminal"

        orchestrator.transition(taskId, TaskStatus.PLANNING)
        orchestrator.transition(taskId, TaskStatus.EXECUTING)
        orchestrator.transition(taskId, TaskStatus.COMPLETED)

        // Completed -> Executing is illegal
        assertThrows(IllegalStateTransitionException::class.java) {
            orchestrator.transition(taskId, TaskStatus.EXECUTING)
        }

        // Completed -> Planning is illegal
        assertThrows(IllegalStateTransitionException::class.java) {
            orchestrator.transition(taskId, TaskStatus.PLANNING)
        }
    }

    @Test
    fun testFailedTerminalStateCannotTransition() {
        val taskId = "task-failed-terminal"

        orchestrator.transition(taskId, TaskStatus.PLANNING)
        orchestrator.transition(taskId, TaskStatus.FAILED, reason = "Network failure")

        assertThrows(IllegalStateTransitionException::class.java) {
            orchestrator.transition(taskId, TaskStatus.EXECUTING)
        }
    }

    @Test
    fun testApplyTaskEventsDrivesStateTransitions() = runTest {
        val taskId = "task-event-driven"

        // 1. TaskCreated -> PENDING
        val state1 = orchestrator.applyEvent(
            TaskEvent.TaskCreated(
                taskId = taskId,
                request = "Analyze logs"
            )
        )
        assertEquals(TaskStatus.PENDING, state1.status)
        assertEquals("Analyze logs", state1.request)

        // 2. PlanCreated -> PLANNING
        val state2 = orchestrator.applyEvent(
            TaskEvent.PlanCreated(
                taskId = taskId,
                planId = "plan-1",
                explanation = "Plan with 2 steps",
                stepDescriptions = listOf("Step 1", "Step 2")
            )
        )
        assertEquals(TaskStatus.PLANNING, state2.status)
        assertEquals(2, state2.totalSteps)

        // 3. StepStarted -> EXECUTING
        val state3 = orchestrator.applyEvent(
            TaskEvent.StepStarted(
                taskId = taskId,
                stepId = "step-1",
                description = "Parse log lines"
            )
        )
        assertEquals(TaskStatus.EXECUTING, state3.status)
        assertEquals("step-1", state3.currentStep)

        // 4. StepCompleted -> remains EXECUTING with incremented step count
        val state4 = orchestrator.applyEvent(
            TaskEvent.StepCompleted(
                taskId = taskId,
                stepId = "step-1",
                output = "Parsed 500 lines"
            )
        )
        assertEquals(TaskStatus.EXECUTING, state4.status)
        assertEquals(1, state4.completedSteps)

        // 5. PermissionRequested -> WAITING_FOR_PERMISSION
        val state5 = orchestrator.applyEvent(
            TaskEvent.PermissionRequested(
                taskId = taskId,
                toolId = "write_report",
                toolName = "Report Writer",
                clearanceLevel = "NORMAL",
                sideEffects = "FILE_WRITE",
                reason = "Write output report"
            )
        )
        assertEquals(TaskStatus.WAITING_FOR_PERMISSION, state5.status)

        // 6. PermissionGranted -> EXECUTING
        val state6 = orchestrator.applyEvent(
            TaskEvent.PermissionGranted(
                taskId = taskId,
                toolId = "write_report"
            )
        )
        assertEquals(TaskStatus.EXECUTING, state6.status)

        // 7. TaskCompleted -> COMPLETED
        val state7 = orchestrator.applyEvent(
            TaskEvent.TaskCompleted(
                taskId = taskId,
                finalSummary = "Report written successfully"
            )
        )
        assertEquals(TaskStatus.COMPLETED, state7.status)
        assertTrue(state7.status.isTerminal)
    }

    @Test
    fun testTryTransitionReturnsFailureOnIllegal() {
        val taskId = "task-try-transition"
        orchestrator.transition(taskId, TaskStatus.PENDING)

        val result = orchestrator.tryTransition(taskId, TaskStatus.COMPLETED)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateTransitionException)
    }

    @Test
    fun testTaskTransitionRulesDirectInvariants() {
        assertTrue(TaskTransitionRules.isValidTransition(TaskStatus.PENDING, TaskStatus.PLANNING))
        assertTrue(TaskTransitionRules.isValidTransition(TaskStatus.PLANNING, TaskStatus.EXECUTING))
        assertTrue(TaskTransitionRules.isValidTransition(TaskStatus.EXECUTING, TaskStatus.VERIFYING))
        assertTrue(TaskTransitionRules.isValidTransition(TaskStatus.VERIFYING, TaskStatus.COMPLETED))

        assertFalse(TaskTransitionRules.isValidTransition(TaskStatus.COMPLETED, TaskStatus.EXECUTING))
        assertFalse(TaskTransitionRules.isValidTransition(TaskStatus.FAILED, TaskStatus.PLANNING))
        assertFalse(TaskTransitionRules.isValidTransition(TaskStatus.CANCELLED, TaskStatus.EXECUTING))
        assertFalse(TaskTransitionRules.isValidTransition(TaskStatus.EXPIRED, TaskStatus.PENDING))
    }
}
