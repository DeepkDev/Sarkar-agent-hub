package com.example.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.contracts.EventMetadata
import com.example.contracts.EventType
import com.example.contracts.TaskEvent as ContractTaskEvent
import com.example.model.TaskEvent as ModelTaskEvent
import java.util.UUID

/**
 * Room entity defining the persistent database table schema for event-sourced [TaskEvent] streams.
 *
 * Mapped fields for event-sourced persistence:
 * - [taskId]: Unique identifier of the associated task
 * - [eventType]: Discriminator string of the event (e.g. TASK_CREATED, STEP_STARTED, TOOL_EXECUTED)
 * - [payload]: Serialized JSON content of the event
 * - [timestamp]: Monotonic millisecond timestamp of event occurrence
 * - [traceId]: Distributed tracing identifier for correlation across distributed components
 * - [prevHash]: Cryptographic SHA-256 hash of the immediately preceding event (or GENESIS)
 * - [eventHash]: SHA-256 digest of this event linked to [prevHash] for tamper-evident auditing
 */
@Entity(
    tableName = "task_events",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["taskId", "timestamp"]),
        Index(value = ["traceId"])
    ]
)
data class TaskEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "eventId")
    val eventId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "taskId")
    val taskId: String,

    @ColumnInfo(name = "eventType")
    val eventType: String,

    @ColumnInfo(name = "payload")
    val payload: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "traceId")
    val traceId: String = "",

    @ColumnInfo(name = "prevHash")
    val prevHash: String,

    @ColumnInfo(name = "eventHash")
    val eventHash: String = ""
) {

    /**
     * Converts this entity to a contract domain [ContractTaskEvent].
     */
    fun toContractTaskEvent(): ContractTaskEvent {
        val parsedType = try {
            EventType.valueOf(eventType)
        } catch (_: Exception) {
            EventType.TASK_CREATED
        }

        return ContractTaskEvent(
            metadata = EventMetadata(
                eventId = eventId,
                taskId = taskId,
                traceId = if (traceId.isNotBlank()) traceId else "trace-${taskId.takeLast(6)}",
                timestamp = timestamp,
                prevHash = prevHash,
                eventHash = eventHash
            ),
            type = parsedType,
            payloadJson = payload
        )
    }

    /**
     * Converts this entity to the Moshi-deserialized polymorphic [ModelTaskEvent].
     */
    fun toModelTaskEvent(): ModelTaskEvent? {
        return ModelTaskEvent.fromJson(payload)
    }

    /**
     * Converts to existing [EventEntity] for compatibility.
     */
    fun toEventEntity(): EventEntity {
        return EventEntity(
            eventId = eventId,
            taskId = taskId,
            type = eventType,
            timestamp = timestamp,
            payloadJson = payload,
            prevHash = prevHash,
            eventHash = eventHash,
            traceId = traceId
        )
    }

    companion object {
        /**
         * Creates a [TaskEventEntity] from a [ContractTaskEvent].
         */
        fun fromContract(event: ContractTaskEvent): TaskEventEntity {
            return TaskEventEntity(
                eventId = event.eventId,
                taskId = event.taskId,
                eventType = event.type.name,
                payload = event.payloadJson,
                timestamp = event.timestamp,
                traceId = event.traceId,
                prevHash = event.prevHash,
                eventHash = event.eventHash
            )
        }

        /**
         * Creates a [TaskEventEntity] from a polymorphic [ModelTaskEvent].
         */
        fun fromModel(event: ModelTaskEvent): TaskEventEntity {
            return TaskEventEntity(
                eventId = event.eventId,
                taskId = event.taskId,
                eventType = event.type,
                payload = ModelTaskEvent.toJson(event),
                timestamp = event.timestamp,
                traceId = event.traceId,
                prevHash = event.prevHash,
                eventHash = event.eventHash
            )
        }

        /**
         * Creates a [TaskEventEntity] from an [EventEntity].
         */
        fun fromEntity(entity: EventEntity): TaskEventEntity {
            return TaskEventEntity(
                eventId = entity.eventId,
                taskId = entity.taskId,
                eventType = entity.type,
                payload = entity.payloadJson,
                timestamp = entity.timestamp,
                traceId = entity.traceId,
                prevHash = entity.prevHash,
                eventHash = entity.eventHash
            )
        }
    }
}
