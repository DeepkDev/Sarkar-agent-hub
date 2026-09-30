package com.example.core

import com.example.contracts.EventType
import com.example.contracts.TaskEvent
import com.example.model.TaskEvent as ModelTaskEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Interceptor for monitoring, persisting, or auditing events flowing through [EventStream].
 */
fun interface EventStreamInterceptor {
    fun onEvent(event: TaskEvent)
}

/**
 * Reactive broadcast Event Stream engine for agent execution telemetry,
 * supporting subscriber tracking, time-window replay, task-scoped streaming,
 * and Server-Sent Events (SSE) serialization.
 */
interface EventStream {
    /**
     * Broadcast hot flow of all emitted task events.
     */
    val events: SharedFlow<TaskEvent>

    /**
     * Publishes a new [TaskEvent] into the stream non-blockingly.
     */
    fun emit(event: TaskEvent): Boolean

    /**
     * Publishes a new [TaskEvent] into the stream suspending if needed.
     */
    suspend fun emitSuspend(event: TaskEvent)

    /**
     * Publishes a domain [ModelTaskEvent] by converting it into a contract event.
     */
    fun emit(modelEvent: ModelTaskEvent): Boolean

    /**
     * Returns a reactive flow of events filtered for a specific [taskId].
     */
    fun streamForTask(taskId: String): Flow<TaskEvent>

    /**
     * Returns a reactive flow of events filtered by [EventType].
     */
    fun streamForType(type: EventType): Flow<TaskEvent>

    /**
     * Returns a reactive flow of events matching an arbitrary [predicate].
     */
    fun streamFiltered(predicate: (TaskEvent) -> Boolean): Flow<TaskEvent>

    /**
     * Returns a reactive flow of domain [ModelTaskEvent]s.
     */
    fun streamModelEvents(): Flow<ModelTaskEvent>

    /**
     * Retrieves the most recently emitted events up to [limit].
     */
    fun getRecentEvents(limit: Int = 100): List<TaskEvent>

    /**
     * Retrieves all cached events for the given [taskId].
     */
    fun getEventsForTask(taskId: String): List<TaskEvent>

    /**
     * Number of active subscribers listening to this event stream.
     */
    val subscriberCount: StateFlow<Int>

    /**
     * Formats an event conforming to the W3C Server-Sent Events (SSE) text protocol.
     */
    fun toSseFormat(event: TaskEvent): String

    /**
     * Registers an [EventStreamInterceptor] for cross-cutting telemetry or logging.
     */
    fun registerInterceptor(interceptor: EventStreamInterceptor)

    /**
     * Unregisters an [EventStreamInterceptor].
     */
    fun removeInterceptor(interceptor: EventStreamInterceptor)

    /**
     * Clears all in-memory event buffers.
     */
    fun clear()

    /**
     * Clears cached events for a specific task.
     */
    fun clearTask(taskId: String)
}

/**
 * Default high-performance thread-safe implementation of [EventStream].
 */
class DefaultEventStream(
    bufferCapacity: Int = 512,
    replayCapacity: Int = 64
) : EventStream {

    private val _events = MutableSharedFlow<TaskEvent>(
        replay = replayCapacity,
        extraBufferCapacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<TaskEvent> = _events.asSharedFlow()

    private val recentEventsBuffer = ArrayDeque<TaskEvent>(bufferCapacity)
    private val taskEventsMap = ConcurrentHashMap<String, MutableList<TaskEvent>>()
    private val interceptors = CopyOnWriteArrayList<EventStreamInterceptor>()

    private val _subscriberCount = MutableStateFlow(0)
    override val subscriberCount: StateFlow<Int> = _subscriberCount.asStateFlow()

    override fun emit(event: TaskEvent): Boolean {
        recordInternal(event)
        val emitted = _events.tryEmit(event)
        notifyInterceptors(event)
        return emitted
    }

    override suspend fun emitSuspend(event: TaskEvent) {
        recordInternal(event)
        _events.emit(event)
        notifyInterceptors(event)
    }

    override fun emit(modelEvent: ModelTaskEvent): Boolean {
        val contractType = try {
            EventType.valueOf(modelEvent.type)
        } catch (_: Exception) {
            EventType.TASK_CREATED
        }
        val contractEvent = TaskEvent(
            metadata = com.example.contracts.EventMetadata(
                eventId = modelEvent.eventId,
                taskId = modelEvent.taskId,
                traceId = modelEvent.traceId,
                timestamp = modelEvent.timestamp,
                prevHash = modelEvent.prevHash,
                eventHash = modelEvent.eventHash
            ),
            type = contractType,
            payloadJson = ModelTaskEvent.toJson(modelEvent)
        )
        return emit(contractEvent)
    }

    private fun recordInternal(event: TaskEvent) {
        synchronized(recentEventsBuffer) {
            if (recentEventsBuffer.size >= 512) {
                recentEventsBuffer.removeFirst()
            }
            recentEventsBuffer.addLast(event)
        }

        taskEventsMap.compute(event.taskId) { _, list ->
            val mutableList = list ?: mutableListOf()
            mutableList.add(event)
            mutableList
        }
    }

    private fun notifyInterceptors(event: TaskEvent) {
        for (interceptor in interceptors) {
            try {
                interceptor.onEvent(event)
            } catch (_: Throwable) {
                // Interceptor failures should never disrupt the core event stream
            }
        }
    }

    override fun streamForTask(taskId: String): Flow<TaskEvent> {
        return events.filter { it.taskId == taskId }
    }

    override fun streamForType(type: EventType): Flow<TaskEvent> {
        return events.filter { it.type == type }
    }

    override fun streamFiltered(predicate: (TaskEvent) -> Boolean): Flow<TaskEvent> {
        return events.filter(predicate)
    }

    override fun streamModelEvents(): Flow<ModelTaskEvent> {
        return events.map { event ->
            ModelTaskEvent.fromJson(event.payloadJson) ?: ModelTaskEvent.TaskCreated(
                eventId = event.eventId,
                taskId = event.taskId,
                timestamp = event.timestamp,
                request = event.payloadJson,
                prevHash = event.prevHash,
                eventHash = event.eventHash,
                traceId = event.traceId
            )
        }
    }

    override fun getRecentEvents(limit: Int): List<TaskEvent> {
        synchronized(recentEventsBuffer) {
            return recentEventsBuffer.takeLast(limit.coerceAtLeast(0))
        }
    }

    override fun getEventsForTask(taskId: String): List<TaskEvent> {
        return taskEventsMap[taskId]?.toList() ?: emptyList()
    }

    override fun toSseFormat(event: TaskEvent): String {
        return SseFormatter.format(event)
    }

    override fun registerInterceptor(interceptor: EventStreamInterceptor) {
        interceptors.add(interceptor)
    }

    override fun removeInterceptor(interceptor: EventStreamInterceptor) {
        interceptors.remove(interceptor)
    }

    override fun clear() {
        synchronized(recentEventsBuffer) {
            recentEventsBuffer.clear()
        }
        taskEventsMap.clear()
    }

    override fun clearTask(taskId: String) {
        taskEventsMap.remove(taskId)
    }
}

/**
 * Standard W3C Server-Sent Events (SSE) serializer for event streaming over HTTP/WebSockets.
 */
object SseFormatter {
    fun format(event: TaskEvent): String {
        val payloadClean = event.payloadJson.trim()
        val dataJson = buildString {
            append("{\"eventId\":\"").append(escape(event.eventId)).append("\",")
            append("\"taskId\":\"").append(escape(event.taskId)).append("\",")
            append("\"type\":\"").append(escape(event.type.name)).append("\",")
            append("\"timestamp\":").append(event.timestamp).append(",")
            append("\"traceId\":\"").append(escape(event.traceId)).append("\",")
            append("\"prevHash\":\"").append(escape(event.prevHash)).append("\",")
            append("\"eventHash\":\"").append(escape(event.eventHash)).append("\",")
            append("\"payload\":")
            if (payloadClean.startsWith("{") || payloadClean.startsWith("[")) {
                append(payloadClean)
            } else {
                append("\"").append(escape(payloadClean)).append("\"")
            }
            append("}")
        }

        return buildString {
            append("id: ").append(event.eventId).append("\n")
            append("event: ").append(event.type.name).append("\n")
            append("data: ").append(dataJson).append("\n\n")
        }
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
