package com.example

import com.example.model.TaskEvent
import org.junit.Assert.*
import org.junit.Test

class TaskEventsMoshiTest {

    @Test
    fun testTaskCreatedSerialization() {
        val event = TaskEvent.TaskCreated(
            taskId = "task-alpha-1",
            request = "Run database migration",
            origin = "CLI",
            dryRun = true,
            prevHash = "GENESIS",
            eventHash = "hash123",
            traceId = "trace-alpha"
        )

        val json = TaskEvent.toJson(event)
        assertTrue(json.contains("\"type\":\"TASK_CREATED\""))
        assertTrue(json.contains("\"request\":\"Run database migration\""))
        assertTrue(json.contains("\"dryRun\":true"))

        val parsed = TaskEvent.fromJson(json) as? TaskEvent.TaskCreated
        assertNotNull(parsed)
        assertEquals("task-alpha-1", parsed?.taskId)
        assertEquals("Run database migration", parsed?.request)
        assertEquals("CLI", parsed?.origin)
        assertTrue(parsed?.dryRun == true)
        assertEquals("GENESIS", parsed?.prevHash)
        assertEquals("hash123", parsed?.eventHash)
        assertEquals("trace-alpha", parsed?.traceId)
    }

    @Test
    fun testStepStartedAndStepCompletedSerialization() {
        val stepStarted = TaskEvent.StepStarted(
            taskId = "task-beta",
            stepId = "step-01",
            description = "Fetch data from API",
            prevHash = "prev-hash-0",
            eventHash = "hash-step-start"
        )
        val jsonStarted = TaskEvent.toJson(stepStarted)
        val parsedStarted = TaskEvent.fromJson(jsonStarted) as? TaskEvent.StepStarted
        assertNotNull(parsedStarted)
        assertEquals("step-01", parsedStarted?.stepId)
        assertEquals("Fetch data from API", parsedStarted?.description)

        val stepCompleted = TaskEvent.StepCompleted(
            taskId = "task-beta",
            stepId = "step-01",
            output = "Fetched 42 records",
            prevHash = "hash-step-start",
            eventHash = "hash-step-complete"
        )
        val jsonCompleted = TaskEvent.toJson(stepCompleted)
        val parsedCompleted = TaskEvent.fromJson(jsonCompleted) as? TaskEvent.StepCompleted
        assertNotNull(parsedCompleted)
        assertEquals("Fetched 42 records", parsedCompleted?.output)
    }

    @Test
    fun testToolExecutedAndPermissionEventsSerialization() {
        val permRequested = TaskEvent.PermissionRequested(
            taskId = "task-gamma",
            toolId = "file_write",
            toolName = "File Writer",
            clearanceLevel = "DANGEROUS",
            sideEffects = "FILESYSTEM_WRITE",
            reason = "Save backup report",
            prevHash = "hash-prev",
            eventHash = "hash-perm-req"
        )
        val permJson = TaskEvent.toJson(permRequested)
        val parsedPerm = TaskEvent.fromJson(permJson) as? TaskEvent.PermissionRequested
        assertNotNull(parsedPerm)
        assertEquals("file_write", parsedPerm?.toolId)
        assertEquals("DANGEROUS", parsedPerm?.clearanceLevel)

        val toolExecuted = TaskEvent.ToolExecuted(
            taskId = "task-gamma",
            toolId = "file_write",
            inputArguments = "{\"path\":\"/data/log.txt\"}",
            output = "Wrote 1024 bytes",
            successful = true,
            prevHash = "hash-perm-req",
            eventHash = "hash-tool-exec"
        )
        val toolJson = TaskEvent.toJson(toolExecuted)
        val parsedTool = TaskEvent.fromJson(toolJson) as? TaskEvent.ToolExecuted
        assertNotNull(parsedTool)
        assertTrue(parsedTool?.successful == true)
        assertEquals("Wrote 1024 bytes", parsedTool?.output)
    }

    @Test
    fun testTerminalEventsSerialization() {
        val completed = TaskEvent.TaskCompleted(
            taskId = "task-term",
            finalSummary = "Operation successfully completed."
        )
        val completedJson = TaskEvent.toJson(completed)
        val parsedCompleted = TaskEvent.fromJson(completedJson) as? TaskEvent.TaskCompleted
        assertNotNull(parsedCompleted)
        assertEquals("Operation successfully completed.", parsedCompleted?.finalSummary)

        val failed = TaskEvent.TaskFailed(
            taskId = "task-term",
            errorMessage = "Out of memory",
            errorCode = "OOM_ERROR"
        )
        val failedJson = TaskEvent.toJson(failed)
        val parsedFailed = TaskEvent.fromJson(failedJson) as? TaskEvent.TaskFailed
        assertNotNull(parsedFailed)
        assertEquals("Out of memory", parsedFailed?.errorMessage)
        assertEquals("OOM_ERROR", parsedFailed?.errorCode)
    }
}
