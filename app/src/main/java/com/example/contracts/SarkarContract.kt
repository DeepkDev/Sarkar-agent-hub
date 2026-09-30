package com.example.contracts

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@JsonClass(generateAdapter = true)
data class SarkarTaskInput(
    val voicePrompt: String,
    val userId: String,
    val deviceContext: Map<String, String> = emptyMap(),
    val language: String = "hi-IN" // Hindi / Hinglish / English default support
)

@JsonClass(generateAdapter = true)
data class TaskRef(
    val taskId: String,
    val traceId: String,
    val statusUrl: String,
    val eventStreamUrl: String
)

@JsonClass(generateAdapter = true)
data class TaskStatusView(
    val taskId: String,
    val status: TaskStatus,
    val currentStep: String?,
    val progressPercent: Int,
    val hasPendingPermission: Boolean
)

@JsonClass(generateAdapter = true)
data class DelegatedToolRequest(
    val delegationId: String,
    val taskId: String,
    val deviceToolName: String, // e.g. "android.camera.capture", "android.contacts.lookup"
    val parameters: Map<String, Any?>
)

@JsonClass(generateAdapter = true)
data class PermissionDecision(
    val permissionId: String,
    val approved: Boolean,
    val decidedBy: String = "user_voice_or_prompt",
    val timestamp: Long = System.currentTimeMillis()
)

interface SarkarConnector {
    val status: CapabilityStatus

    suspend fun submitTask(input: SarkarTaskInput): TaskRef
    suspend fun getTaskStatus(id: String): TaskStatusView
    fun onTaskUpdate(id: String): Flow<TaskEvent>
    suspend fun getFinalResult(id: String): FinalResponse
    suspend fun requestToolExecution(req: DelegatedToolRequest): ToolResult
    suspend fun handlePermissionRequest(req: PendingPermission): PermissionDecision
}

/**
 * NullSarkarConnector:
 * Standalone reference implementation that explicitly reports NOT_CONFIGURED.
 * SARKAR Android Assistant connects later over the public REST/SSE API.
 */
class NullSarkarConnector : SarkarConnector {
    override val status: CapabilityStatus = CapabilityStatus.NOT_CONFIGURED

    override suspend fun submitTask(input: SarkarTaskInput): TaskRef {
        throw UnsupportedOperationException("SarkarConnector is NOT_CONFIGURED. SARKAR connects through public API.")
    }

    override suspend fun getTaskStatus(id: String): TaskStatusView {
        throw UnsupportedOperationException("SarkarConnector is NOT_CONFIGURED.")
    }

    override fun onTaskUpdate(id: String): Flow<TaskEvent> = emptyFlow()

    override suspend fun getFinalResult(id: String): FinalResponse {
        throw UnsupportedOperationException("SarkarConnector is NOT_CONFIGURED.")
    }

    override suspend fun requestToolExecution(req: DelegatedToolRequest): ToolResult {
        return ToolResult.notConfigured(req.deviceToolName, "Delegated device tools require active SARKAR client connection")
    }

    override suspend fun handlePermissionRequest(req: PendingPermission): PermissionDecision {
        return PermissionDecision(req.permissionId, approved = false, decidedBy = "null_connector_reject")
    }
}
