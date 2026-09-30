package com.example

import com.example.model.TaskState
import com.example.model.TaskStatus
import com.example.model.toContractStatus
import com.example.model.toModelStatus
import org.junit.Assert.*
import org.junit.Test

class TaskStateTest {

    @Test
    fun testAllTenStatesAreDefined() {
        val states = TaskStatus.all
        assertEquals(10, states.size)

        assertTrue(states.contains(TaskStatus.PENDING))
        assertTrue(states.contains(TaskStatus.PLANNING))
        assertTrue(states.contains(TaskStatus.WAITING_FOR_PERMISSION))
        assertTrue(states.contains(TaskStatus.EXECUTING))
        assertTrue(states.contains(TaskStatus.VERIFYING))
        assertTrue(states.contains(TaskStatus.COMPLETED))
        assertTrue(states.contains(TaskStatus.FAILED))
        assertTrue(states.contains(TaskStatus.CANCELLED))
        assertTrue(states.contains(TaskStatus.PAUSED))
        assertTrue(states.contains(TaskStatus.EXPIRED))
    }

    @Test
    fun testTerminalAndRunningProperties() {
        // Terminal states
        assertTrue(TaskStatus.COMPLETED.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
        assertTrue(TaskStatus.CANCELLED.isTerminal)
        assertTrue(TaskStatus.EXPIRED.isTerminal)

        // Non-terminal states
        assertFalse(TaskStatus.PENDING.isTerminal)
        assertFalse(TaskStatus.PLANNING.isTerminal)
        assertFalse(TaskStatus.WAITING_FOR_PERMISSION.isTerminal)
        assertFalse(TaskStatus.EXECUTING.isTerminal)
        assertFalse(TaskStatus.VERIFYING.isTerminal)
        assertFalse(TaskStatus.PAUSED.isTerminal)

        // Running states
        assertTrue(TaskStatus.PLANNING.isRunning)
        assertTrue(TaskStatus.EXECUTING.isRunning)
        assertTrue(TaskStatus.VERIFYING.isRunning)
        assertFalse(TaskStatus.PENDING.isRunning)
        assertFalse(TaskStatus.COMPLETED.isRunning)
    }

    @Test
    fun testFromStringParsing() {
        assertEquals(TaskStatus.PENDING, TaskStatus.fromString("pending"))
        assertEquals(TaskStatus.PLANNING, TaskStatus.fromString("PLANNING"))
        assertEquals(TaskStatus.WAITING_FOR_PERMISSION, TaskStatus.fromString("WAITING_FOR_PERMISSION"))
        assertEquals(TaskStatus.EXECUTING, TaskStatus.fromString("executing"))
        assertEquals(TaskStatus.VERIFYING, TaskStatus.fromString("VERIFYING"))
        assertEquals(TaskStatus.COMPLETED, TaskStatus.fromString("completed"))
        assertEquals(TaskStatus.FAILED, TaskStatus.fromString("FAILED"))
        assertEquals(TaskStatus.CANCELLED, TaskStatus.fromString("cancelled"))
        assertEquals(TaskStatus.PAUSED, TaskStatus.fromString("paused"))
        assertEquals(TaskStatus.EXPIRED, TaskStatus.fromString("EXPIRED"))
        assertEquals(TaskStatus.PENDING, TaskStatus.fromString("UNKNOWN_VALUE"))
    }

    @Test
    fun testBidirectionalConversionWithContractEnum() {
        for (enumVal in com.example.contracts.TaskStatus.values()) {
            val modelVal = enumVal.toModelStatus()
            assertEquals(enumVal.name, modelVal.name)
            val convertedBack = modelVal.toContractStatus()
            assertEquals(enumVal, convertedBack)
        }
    }

    @Test
    fun testTaskStateCreation() {
        val state = TaskState(
            taskId = "task-100",
            status = TaskStatus.EXECUTING,
            request = "Run analysis",
            totalSteps = 4,
            completedSteps = 2
        )
        assertEquals("task-100", state.taskId)
        assertEquals(TaskStatus.EXECUTING, state.status)
        assertEquals(4, state.totalSteps)
        assertEquals(2, state.completedSteps)
    }
}
