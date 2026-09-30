package com.example.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.Type
import java.util.UUID

/**
 * Sealed class hierarchy representing domain events emitted throughout the agent task lifecycle.
 * All subclasses are Moshi-compatible with code-generated adapters and support polymorphic
 * serialization and deserialization via a type discriminator.
 */
sealed class TaskEvent {
    abstract val eventId: String
    abstract val taskId: String
    abstract val timestamp: Long
    abstract val type: String
    abstract val prevHash: String
    abstract val eventHash: String
    abstract val traceId: String

    @JsonClass(generateAdapter = true)
    data class TaskCreated(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "TASK_CREATED",
        val request: String,
        val origin: String = "WEB",
        val dryRun: Boolean = false,
        override val prevHash: String = "GENESIS",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class PlanCreated(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "PLAN_CREATED",
        val planId: String,
        val explanation: String,
        val stepDescriptions: List<String> = emptyList(),
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class StepStarted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "STEP_STARTED",
        val stepId: String,
        val description: String,
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class StepCompleted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "STEP_COMPLETED",
        val stepId: String,
        val output: String,
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class StepFailed(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "STEP_FAILED",
        val stepId: String,
        val error: String,
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class ToolExecuted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "TOOL_EXECUTED",
        val toolId: String,
        val inputArguments: String,
        val output: String,
        val successful: Boolean,
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class PermissionRequested(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "PERMISSION_REQUESTED",
        val toolId: String,
        val toolName: String,
        val clearanceLevel: String,
        val sideEffects: String,
        val reason: String,
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class PermissionGranted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "PERMISSION_GRANTED",
        val toolId: String,
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class PermissionDenied(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "PERMISSION_DENIED",
        val toolId: String,
        val reason: String,
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class TaskCompleted(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "TASK_COMPLETED",
        val finalSummary: String,
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class TaskFailed(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "TASK_FAILED",
        val errorMessage: String,
        val errorCode: String = "GENERAL_ERROR",
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class TaskCancelled(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "TASK_CANCELLED",
        val reason: String = "User cancelled",
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class TaskPaused(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "TASK_PAUSED",
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    @JsonClass(generateAdapter = true)
    data class TaskResumed(
        override val eventId: String = UUID.randomUUID().toString(),
        override val taskId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: String = "TASK_RESUMED",
        override val prevHash: String = "",
        override val eventHash: String = "",
        override val traceId: String = "trace-${taskId.takeLast(6)}"
    ) : TaskEvent()

    /**
     * Moshi polymorphic adapter for [TaskEvent].
     */
    class TaskEventJsonAdapter(private val moshi: Moshi) : JsonAdapter<TaskEvent>() {
        override fun toJson(writer: JsonWriter, value: TaskEvent?) {
            if (value == null) {
                writer.nullValue()
                return
            }
            when (value) {
                is TaskCreated -> moshi.adapter(TaskCreated::class.java).toJson(writer, value)
                is PlanCreated -> moshi.adapter(PlanCreated::class.java).toJson(writer, value)
                is StepStarted -> moshi.adapter(StepStarted::class.java).toJson(writer, value)
                is StepCompleted -> moshi.adapter(StepCompleted::class.java).toJson(writer, value)
                is StepFailed -> moshi.adapter(StepFailed::class.java).toJson(writer, value)
                is ToolExecuted -> moshi.adapter(ToolExecuted::class.java).toJson(writer, value)
                is PermissionRequested -> moshi.adapter(PermissionRequested::class.java).toJson(writer, value)
                is PermissionGranted -> moshi.adapter(PermissionGranted::class.java).toJson(writer, value)
                is PermissionDenied -> moshi.adapter(PermissionDenied::class.java).toJson(writer, value)
                is TaskCompleted -> moshi.adapter(TaskCompleted::class.java).toJson(writer, value)
                is TaskFailed -> moshi.adapter(TaskFailed::class.java).toJson(writer, value)
                is TaskCancelled -> moshi.adapter(TaskCancelled::class.java).toJson(writer, value)
                is TaskPaused -> moshi.adapter(TaskPaused::class.java).toJson(writer, value)
                is TaskResumed -> moshi.adapter(TaskResumed::class.java).toJson(writer, value)
            }
        }

        override fun fromJson(reader: JsonReader): TaskEvent? {
            val peeked = reader.peekJson()
            val map = moshi.adapter(Map::class.java).fromJson(peeked) as? Map<*, *>
            val typeStr = (map?.get("type") as? String)
                ?: (map?.get("eventType") as? String)
                ?: return null

            return when (typeStr.uppercase()) {
                "TASK_CREATED" -> moshi.adapter(TaskCreated::class.java).fromJson(reader)
                "PLAN_CREATED" -> moshi.adapter(PlanCreated::class.java).fromJson(reader)
                "STEP_STARTED" -> moshi.adapter(StepStarted::class.java).fromJson(reader)
                "STEP_COMPLETED" -> moshi.adapter(StepCompleted::class.java).fromJson(reader)
                "STEP_FAILED" -> moshi.adapter(StepFailed::class.java).fromJson(reader)
                "TOOL_EXECUTED" -> moshi.adapter(ToolExecuted::class.java).fromJson(reader)
                "PERMISSION_REQUESTED" -> moshi.adapter(PermissionRequested::class.java).fromJson(reader)
                "PERMISSION_GRANTED" -> moshi.adapter(PermissionGranted::class.java).fromJson(reader)
                "PERMISSION_DENIED" -> moshi.adapter(PermissionDenied::class.java).fromJson(reader)
                "TASK_COMPLETED" -> moshi.adapter(TaskCompleted::class.java).fromJson(reader)
                "TASK_FAILED" -> moshi.adapter(TaskFailed::class.java).fromJson(reader)
                "TASK_CANCELLED" -> moshi.adapter(TaskCancelled::class.java).fromJson(reader)
                "TASK_PAUSED" -> moshi.adapter(TaskPaused::class.java).fromJson(reader)
                "TASK_RESUMED" -> moshi.adapter(TaskResumed::class.java).fromJson(reader)
                else -> null
            }
        }
    }

    class Factory : JsonAdapter.Factory {
        override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
            if (Types.getRawType(type) == TaskEvent::class.java) {
                return TaskEventJsonAdapter(moshi)
            }
            return null
        }
    }

    companion object {
        val defaultMoshi: Moshi by lazy {
            Moshi.Builder()
                .add(Factory())
                .add(KotlinJsonAdapterFactory())
                .build()
        }

        fun toJson(event: TaskEvent): String {
            return defaultMoshi.adapter(TaskEvent::class.java).toJson(event)
        }

        fun fromJson(json: String): TaskEvent? {
            return defaultMoshi.adapter(TaskEvent::class.java).fromJson(json)
        }
    }
}
