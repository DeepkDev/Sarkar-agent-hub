package com.example.contracts

import com.squareup.moshi.JsonClass
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class EventType {
    TASK_CREATED,
    PLAN_CREATED,
    STEP_STARTED,
    TOOL_REQUESTED,
    PERMISSION_REQUESTED,
    PERMISSION_GRANTED,
    PERMISSION_DENIED,
    PERMISSION_EXPIRED,
    TOOL_EXECUTED,
    VERIFICATION_PASSED,
    VERIFICATION_FAILED,
    RETRY_SCHEDULED,
    REPLAN_TRIGGERED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_CANCELLED,
    TASK_PAUSED,
    TASK_RESUMED
}

/**
 * Moshi-compatible metadata structure for tamper-evident auditing.
 * Every event includes a taskId, timestamp, traceId, eventId, and the hash of the previous event.
 */
@JsonClass(generateAdapter = true)
data class EventMetadata(
    val eventId: String = UUID.randomUUID().toString(),
    val taskId: String,
    val traceId: String = "trace-${taskId.takeLast(6)}",
    val timestamp: Long = System.currentTimeMillis(),
    val prevHash: String = "GENESIS",
    val eventHash: String = ""
) {
    companion object {
        fun createWithHash(
            taskId: String,
            traceId: String = "trace-${taskId.takeLast(6)}",
            type: EventType,
            payloadJson: String,
            prevHash: String
        ): EventMetadata {
            val eventId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val contentToHash = "$eventId|$taskId|$traceId|${type.name}|$timestamp|$payloadJson|$prevHash"
            val hash = calculateSha256(contentToHash)
            return EventMetadata(
                eventId = eventId,
                taskId = taskId,
                traceId = traceId,
                timestamp = timestamp,
                prevHash = prevHash,
                eventHash = hash
            )
        }

        fun calculateSha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Common TaskEvent container used for persistent storage, streaming, and audit hashing.
 * Embeds Moshi-compatible EventMetadata containing taskId, timestamp, traceId, and previous event hash.
 */
@JsonClass(generateAdapter = true)
data class TaskEvent(
    val metadata: EventMetadata,
    val type: EventType,
    val payloadJson: String = "{}"
) {
    val eventId: String get() = metadata.eventId
    val taskId: String get() = metadata.taskId
    val traceId: String get() = metadata.traceId
    val timestamp: Long get() = metadata.timestamp
    val prevHash: String get() = metadata.prevHash
    val eventHash: String get() = metadata.eventHash

    constructor(
        eventId: String = UUID.randomUUID().toString(),
        taskId: String,
        type: EventType,
        timestamp: Long = System.currentTimeMillis(),
        payloadJson: String = "{}",
        prevHash: String = "GENESIS",
        eventHash: String = "",
        traceId: String = "trace-${taskId.takeLast(6)}"
    ) : this(
        metadata = EventMetadata(
            eventId = eventId,
            taskId = taskId,
            traceId = traceId,
            timestamp = timestamp,
            prevHash = prevHash,
            eventHash = eventHash
        ),
        type = type,
        payloadJson = payloadJson
    )

    fun copy(
        eventId: String = metadata.eventId,
        taskId: String = metadata.taskId,
        type: EventType = this.type,
        timestamp: Long = metadata.timestamp,
        payloadJson: String = this.payloadJson,
        prevHash: String = metadata.prevHash,
        eventHash: String = metadata.eventHash,
        traceId: String = metadata.traceId
    ): TaskEvent = TaskEvent(
        metadata = metadata.copy(
            eventId = eventId,
            taskId = taskId,
            traceId = traceId,
            timestamp = timestamp,
            prevHash = prevHash,
            eventHash = eventHash
        ),
        type = type,
        payloadJson = payloadJson
    )

    companion object {
        fun createWithHash(
            taskId: String,
            type: EventType,
            payloadJson: String,
            prevHash: String,
            traceId: String = "trace-${taskId.takeLast(6)}"
        ): TaskEvent {
            val meta = EventMetadata.createWithHash(
                taskId = taskId,
                traceId = traceId,
                type = type,
                payloadJson = payloadJson,
                prevHash = prevHash
            )
            return TaskEvent(
                metadata = meta,
                type = type,
                payloadJson = payloadJson
            )
        }

        fun calculateSha256(input: String): String = EventMetadata.calculateSha256(input)
    }
}

/**
 * Strongly-typed Kotlin sealed class structure representing all task state machine lifecycle events.
 * Serves as the foundation for the event-sourced execution system, type-safe pattern matching,
 * state fold/projection logic, and replay debugging.
 */
sealed class AgentTaskEvent {
    abstract val eventId: String
    abstract val taskId: String
    abstract val timestamp: Long
    abstract val prevHash: String
    abstract val eventHash: String
    abstract val eventType: EventType
    open val traceId: String get() = "trace-${taskId.takeLast(6)}"

    val metadata: EventMetadata
        get() = EventMetadata(
            eventId = eventId,
            taskId = taskId,
            traceId = traceId,
            timestamp = timestamp,
            prevHash = prevHash,
            eventHash = eventHash
        )

    abstract fun toPayloadJson(): String

    fun toTaskEvent(): TaskEvent = TaskEvent(
        metadata = metadata,
        type = eventType,
        payloadJson = toPayloadJson()
    )

    data class TaskCreated(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val request: String,
        val origin: OriginSource = OriginSource.WEB,
        val dryRun: Boolean = false,
        override val prevHash: String = "GENESIS",
        override val eventHash: String = computeHash(eventId, taskId, EventType.TASK_CREATED, timestamp, prevHash, request, origin.name, dryRun.toString())
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.TASK_CREATED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("request", request)
            put("origin", origin.name)
            put("dryRun", dryRun)
        }.toString()
    }

    data class PlanCreated(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val planId: String,
        val explanation: String,
        val steps: List<PlanStep>,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.PLAN_CREATED, timestamp, prevHash, planId, explanation)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.PLAN_CREATED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("planId", planId)
            put("explanation", explanation)
            val arr = JSONArray()
            steps.forEach { step ->
                arr.put(JSONObject().apply {
                    put("id", step.id)
                    put("description", step.description)
                    put("expectedTool", step.expectedTool)
                    put("status", step.status.name)
                })
            }
            put("steps", arr)
        }.toString()
    }

    data class StepStarted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val stepId: String,
        val description: String,
        val expectedTool: String,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.STEP_STARTED, timestamp, prevHash, stepId, description)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.STEP_STARTED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("stepId", stepId)
            put("description", description)
            put("expectedTool", expectedTool)
        }.toString()
    }

    data class ToolRequested(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val stepId: String,
        val toolId: String,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.TOOL_REQUESTED, timestamp, prevHash, stepId, toolId)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.TOOL_REQUESTED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("stepId", stepId)
            put("expectedTool", toolId)
        }.toString()
    }

    data class PermissionRequested(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val permissionId: String,
        val toolId: String,
        val argumentsJson: String,
        val sideEffectsSummary: String,
        val expiresAt: Long,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.PERMISSION_REQUESTED, timestamp, prevHash, permissionId, toolId)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.PERMISSION_REQUESTED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("permissionId", permissionId)
            put("toolId", toolId)
            put("arguments", argumentsJson)
            put("reason", sideEffectsSummary)
            put("expiresAt", expiresAt)
        }.toString()
    }

    data class PermissionGranted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val permissionId: String,
        val toolId: String,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.PERMISSION_GRANTED, timestamp, prevHash, permissionId, toolId)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.PERMISSION_GRANTED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("permissionId", permissionId)
            put("toolId", toolId)
        }.toString()
    }

    data class PermissionDenied(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val permissionId: String,
        val toolId: String,
        val reason: String,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.PERMISSION_DENIED, timestamp, prevHash, permissionId, toolId, reason)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.PERMISSION_DENIED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("permissionId", permissionId)
            put("toolId", toolId)
            put("reason", reason)
        }.toString()
    }

    data class PermissionExpired(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val permissionId: String,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.PERMISSION_EXPIRED, timestamp, prevHash, permissionId)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.PERMISSION_EXPIRED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("permissionId", permissionId)
        }.toString()
    }

    data class ToolExecuted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val executionId: String,
        val toolId: String,
        val argumentsJson: String,
        val outputJson: String,
        val durationMs: Long,
        val successful: Boolean,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.TOOL_EXECUTED, timestamp, prevHash, executionId, toolId, durationMs.toString(), successful.toString())
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.TOOL_EXECUTED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("executionId", executionId)
            put("toolId", toolId)
            put("argumentsJson", argumentsJson)
            put("outputJson", outputJson)
            put("durationMs", durationMs)
            put("successful", successful)
        }.toString()
    }

    data class VerificationPassed(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val stepId: String,
        val reason: String,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.VERIFICATION_PASSED, timestamp, prevHash, stepId, reason)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.VERIFICATION_PASSED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("stepId", stepId)
            put("reason", reason)
        }.toString()
    }

    data class VerificationFailed(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val stepId: String,
        val reason: String,
        val suggestedFeedback: String? = null,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.VERIFICATION_FAILED, timestamp, prevHash, stepId, reason)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.VERIFICATION_FAILED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("stepId", stepId)
            put("reason", reason)
            suggestedFeedback?.let { put("suggestedFeedback", it) }
        }.toString()
    }

    data class RetryScheduled(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val stepId: String,
        val reason: String,
        val retryAttempt: Int = 1,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.RETRY_SCHEDULED, timestamp, prevHash, stepId, retryAttempt.toString())
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.RETRY_SCHEDULED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("stepId", stepId)
            put("reason", reason)
            put("retryAttempt", retryAttempt)
        }.toString()
    }

    data class ReplanTriggered(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val replanCount: Int,
        val reason: String,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.REPLAN_TRIGGERED, timestamp, prevHash, replanCount.toString(), reason)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.REPLAN_TRIGGERED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("replanCount", replanCount)
            put("reason", reason)
        }.toString()
    }

    data class TaskCompleted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val spokenSummary: String,
        val fullText: String,
        val language: String = "en",
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.TASK_COMPLETED, timestamp, prevHash, spokenSummary, language)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.TASK_COMPLETED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("spokenSummary", spokenSummary)
            put("fullText", fullText)
            put("language", language)
        }.toString()
    }

    data class TaskFailed(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val error: String,
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.TASK_FAILED, timestamp, prevHash, error)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.TASK_FAILED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("error", error)
        }.toString()
    }

    data class TaskCancelled(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val reason: String = "Cancelled by user or system",
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.TASK_CANCELLED, timestamp, prevHash, reason)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.TASK_CANCELLED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("reason", reason)
        }.toString()
    }

    data class TaskPaused(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val reason: String = "Paused by operator",
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.TASK_PAUSED, timestamp, prevHash, reason)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.TASK_PAUSED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("reason", reason)
        }.toString()
    }

    data class TaskResumed(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val prevHash: String,
        override val eventHash: String = computeHash(eventId, taskId, EventType.TASK_RESUMED, timestamp, prevHash)
    ) : AgentTaskEvent() {
        override val eventType: EventType = EventType.TASK_RESUMED
        override fun toPayloadJson(): String = JSONObject().apply {
            put("status", "RESUMED")
        }.toString()
    }

    companion object {
        fun computeHash(
            eventId: String,
            taskId: String,
            type: EventType,
            timestamp: Long,
            prevHash: String,
            vararg extraPayload: String
        ): String {
            val payload = extraPayload.joinToString("|")
            val contentToHash = "$eventId|$taskId|${type.name}|$timestamp|$payload|$prevHash"
            return TaskEvent.calculateSha256(contentToHash)
        }

        fun fromTaskEvent(event: TaskEvent): AgentTaskEvent {
            return when (event.type) {
                EventType.TASK_CREATED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    TaskCreated(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        request = json.optString("request", ""),
                        origin = try { OriginSource.valueOf(json.optString("origin", "WEB")) } catch (_: Exception) { OriginSource.WEB },
                        dryRun = json.optBoolean("dryRun", false),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.PLAN_CREATED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    val stepsArray = json.optJSONArray("steps")
                    val stepsList = mutableListOf<PlanStep>()
                    if (stepsArray != null) {
                        for (i in 0 until stepsArray.length()) {
                            val s = stepsArray.getJSONObject(i)
                            stepsList.add(
                                PlanStep(
                                    id = s.optString("id", "step-$i"),
                                    description = s.optString("description", ""),
                                    expectedTool = s.optString("expectedTool", ""),
                                    status = try { StepStatus.valueOf(s.optString("status", "PENDING")) } catch (_: Exception) { StepStatus.PENDING }
                                )
                            )
                        }
                    }
                    PlanCreated(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        planId = json.optString("planId", "plan"),
                        explanation = json.optString("explanation", ""),
                        steps = stepsList,
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.STEP_STARTED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    StepStarted(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        stepId = json.optString("stepId", ""),
                        description = json.optString("description", ""),
                        expectedTool = json.optString("expectedTool", ""),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.TOOL_REQUESTED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    ToolRequested(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        stepId = json.optString("stepId", ""),
                        toolId = json.optString("expectedTool", ""),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.PERMISSION_REQUESTED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    PermissionRequested(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        permissionId = json.optString("permissionId", ""),
                        toolId = json.optString("toolId", ""),
                        argumentsJson = json.optString("arguments", "{}"),
                        sideEffectsSummary = json.optString("reason", ""),
                        expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + 600_000L),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.PERMISSION_GRANTED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    PermissionGranted(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        permissionId = json.optString("permissionId", ""),
                        toolId = json.optString("toolId", ""),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.PERMISSION_DENIED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    PermissionDenied(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        permissionId = json.optString("permissionId", ""),
                        toolId = json.optString("toolId", ""),
                        reason = json.optString("reason", ""),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.PERMISSION_EXPIRED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    PermissionExpired(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        permissionId = json.optString("permissionId", ""),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.TOOL_EXECUTED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    ToolExecuted(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        executionId = json.optString("executionId", "exec"),
                        toolId = json.optString("toolId", ""),
                        argumentsJson = json.optString("argumentsJson", "{}"),
                        outputJson = json.optString("outputJson", ""),
                        durationMs = json.optLong("durationMs", 0L),
                        successful = json.optBoolean("successful", true),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.VERIFICATION_PASSED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    VerificationPassed(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        stepId = json.optString("stepId", ""),
                        reason = json.optString("reason", ""),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.VERIFICATION_FAILED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    VerificationFailed(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        stepId = json.optString("stepId", ""),
                        reason = json.optString("reason", ""),
                        suggestedFeedback = if (json.has("suggestedFeedback")) json.getString("suggestedFeedback") else null,
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.RETRY_SCHEDULED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    RetryScheduled(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        stepId = json.optString("stepId", ""),
                        reason = json.optString("reason", ""),
                        retryAttempt = json.optInt("retryAttempt", 1),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.REPLAN_TRIGGERED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    ReplanTriggered(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        replanCount = json.optInt("replanCount", 1),
                        reason = json.optString("reason", ""),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.TASK_COMPLETED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    TaskCompleted(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        spokenSummary = json.optString("spokenSummary", ""),
                        fullText = json.optString("fullText", ""),
                        language = json.optString("language", "en"),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.TASK_FAILED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    TaskFailed(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        error = json.optString("error", "Task execution failed"),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.TASK_CANCELLED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    TaskCancelled(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        reason = json.optString("reason", "Cancelled"),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.TASK_PAUSED -> {
                    val json = try { JSONObject(event.payloadJson) } catch (_: Exception) { JSONObject() }
                    TaskPaused(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        reason = json.optString("reason", "Paused"),
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }

                EventType.TASK_RESUMED -> {
                    TaskResumed(
                        eventId = event.eventId,
                        taskId = event.taskId,
                        timestamp = event.timestamp,
                        prevHash = event.prevHash,
                        eventHash = event.eventHash
                    )
                }
            }
        }
    }
}
