package com.example.storage

import com.example.contracts.EventMetadata
import com.example.contracts.EventType
import com.example.contracts.TaskEvent
import com.example.util.EventHasher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Audit result verifying whether an event sequence's SHA-256 hash chain remains intact and authentic.
 */
data class EventVerificationResult(
    val isValid: Boolean,
    val verifiedCount: Int,
    val brokenAtIndex: Int? = null,
    val failureReason: String? = null
)

/**
 * Encapsulates the retrieved event list alongside its cryptographic verification status.
 */
data class VerifiedEventSequence(
    val events: List<TaskEvent>,
    val verification: EventVerificationResult
)

/**
 * Repository interface for persisting, querying, and verifying tamper-evident TaskEvent sequences.
 */
interface EventRepository {
    /**
     * Appends a new [TaskEvent] to the persistent log for its taskId.
     * Ensures the event is cryptographically linked to the previous event's hash (or GENESIS for the first event).
     */
    suspend fun appendEvent(event: TaskEvent): TaskEvent

    /**
     * Atomically appends a sequence of events, chaining hashes across the entire batch.
     */
    suspend fun appendEvents(events: List<TaskEvent>): List<TaskEvent>

    /**
     * Reactive stream of events for a given taskId ordered chronologically.
     */
    fun observeEventsForTask(taskId: String): Flow<List<TaskEvent>>

    /**
     * Synchronously fetches all events for a given taskId ordered chronologically without verification.
     */
    suspend fun getEventsForTask(taskId: String): List<TaskEvent>

    /**
     * Retrieves all events for a given taskId and verifies the cryptographic SHA-256 hash chain across every event.
     * Detects any database manipulation, corrupted payloads, altered timestamps, or broken links.
     */
    suspend fun getVerifiedEventsForTask(taskId: String): VerifiedEventSequence

    /**
     * Returns the most recent event recorded for a given taskId, or null if none exist.
     */
    suspend fun getLatestEvent(taskId: String): TaskEvent?

    /**
     * Reactive stream of recent events across all tasks.
     */
    fun observeRecentEvents(limit: Int = 100): Flow<List<TaskEvent>>

    /**
     * Purges events for a task.
     */
    suspend fun clearEventsForTask(taskId: String)
}

/**
 * Room-backed implementation of [EventRepository] ensuring persistent storage
 * and cryptographic tamper-evident verification using [EventHasher].
 */
class RoomEventRepository(
    private val eventDao: EventDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : EventRepository {

    override suspend fun appendEvent(event: TaskEvent): TaskEvent = withContext(ioDispatcher) {
        val latest = eventDao.getLatestEventForTask(event.taskId)
        val expectedPrevHash = latest?.eventHash ?: EventHasher.GENESIS_HASH

        // Determine if prevHash needs to be assigned or aligned with the chain
        val effectivePrevHash = if (event.prevHash.isBlank() || event.prevHash == EventHasher.GENESIS_HASH) {
            expectedPrevHash
        } else {
            event.prevHash
        }

        // Calculate deterministic SHA-256 digest linked to effectivePrevHash
        val contentEvent = if (event.prevHash != effectivePrevHash) {
            event.copy(prevHash = effectivePrevHash)
        } else {
            event
        }
        val computedHash = EventHasher.generateHash(contentEvent, effectivePrevHash)
        val finalizedEvent = contentEvent.copy(eventHash = computedHash)

        val entity = toEntity(finalizedEvent)
        eventDao.insertEvent(entity)
        finalizedEvent
    }

    override suspend fun appendEvents(events: List<TaskEvent>): List<TaskEvent> = withContext(ioDispatcher) {
        if (events.isEmpty()) return@withContext emptyList()
        val persisted = mutableListOf<TaskEvent>()

        var currentLatest = eventDao.getLatestEventForTask(events.first().taskId)
        var prevHash = currentLatest?.eventHash ?: EventHasher.GENESIS_HASH

        val entitiesToInsert = mutableListOf<EventEntity>()

        for (event in events) {
            val eventWithPrev = event.copy(prevHash = prevHash)
            val computedHash = EventHasher.generateHash(eventWithPrev, prevHash)
            val finalized = eventWithPrev.copy(eventHash = computedHash)

            entitiesToInsert.add(toEntity(finalized))
            persisted.add(finalized)
            prevHash = computedHash
        }

        eventDao.insertEvents(entitiesToInsert)
        persisted
    }

    override fun observeEventsForTask(taskId: String): Flow<List<TaskEvent>> {
        return eventDao.getEventsForTask(taskId).map { list ->
            list.map { toTaskEvent(it) }
        }
    }

    override suspend fun getEventsForTask(taskId: String): List<TaskEvent> = withContext(ioDispatcher) {
        eventDao.getEventsForTaskList(taskId).map { toTaskEvent(it) }
    }

    override suspend fun getVerifiedEventsForTask(taskId: String): VerifiedEventSequence = withContext(ioDispatcher) {
        val entities = eventDao.getEventsForTaskList(taskId)
        val events = entities.map { toTaskEvent(it) }

        if (events.isEmpty()) {
            return@withContext VerifiedEventSequence(
                events = emptyList(),
                verification = EventVerificationResult(isValid = true, verifiedCount = 0)
            )
        }

        var expectedPrevHash = EventHasher.GENESIS_HASH

        for ((index, event) in events.withIndex()) {
            // 1. Verify link to previous event hash
            if (event.prevHash != expectedPrevHash) {
                return@withContext VerifiedEventSequence(
                    events = events,
                    verification = EventVerificationResult(
                        isValid = false,
                        verifiedCount = index,
                        brokenAtIndex = index,
                        failureReason = "Chain broken at event #$index (${event.type}): expected prevHash=$expectedPrevHash, found=${event.prevHash}"
                    )
                )
            }

            // 2. Verify SHA-256 integrity of the event contents
            if (!EventHasher.verifyEvent(event, expectedPrevHash)) {
                val expectedHash = EventHasher.generateHash(event, expectedPrevHash)
                return@withContext VerifiedEventSequence(
                    events = events,
                    verification = EventVerificationResult(
                        isValid = false,
                        verifiedCount = index,
                        brokenAtIndex = index,
                        failureReason = "Tamper detected at event #$index (${event.type}): eventId=${event.eventId}, expectedHash=$expectedHash, recordedHash=${event.eventHash}"
                    )
                )
            }

            expectedPrevHash = event.eventHash
        }

        VerifiedEventSequence(
            events = events,
            verification = EventVerificationResult(
                isValid = true,
                verifiedCount = events.size,
                failureReason = null
            )
        )
    }

    override suspend fun getLatestEvent(taskId: String): TaskEvent? = withContext(ioDispatcher) {
        eventDao.getLatestEventForTask(taskId)?.let { toTaskEvent(it) }
    }

    override fun observeRecentEvents(limit: Int): Flow<List<TaskEvent>> {
        return eventDao.getRecentEvents().map { list ->
            list.take(limit).map { toTaskEvent(it) }
        }
    }

    override suspend fun clearEventsForTask(taskId: String) = withContext(ioDispatcher) {
        eventDao.deleteEventsForTask(taskId)
    }

    companion object {
        fun toEntity(event: TaskEvent): EventEntity {
            return EventEntity(
                eventId = event.eventId,
                taskId = event.taskId,
                type = event.type.name,
                timestamp = event.timestamp,
                payloadJson = event.payloadJson,
                prevHash = event.prevHash,
                eventHash = event.eventHash,
                traceId = event.traceId
            )
        }

        fun toTaskEvent(entity: EventEntity): TaskEvent {
            val type = try {
                EventType.valueOf(entity.type)
            } catch (_: Exception) {
                EventType.TASK_CREATED
            }
            return TaskEvent(
                metadata = EventMetadata(
                    eventId = entity.eventId,
                    taskId = entity.taskId,
                    traceId = if (entity.traceId.isNotBlank()) entity.traceId else "trace-${entity.taskId.takeLast(6)}",
                    timestamp = entity.timestamp,
                    prevHash = entity.prevHash,
                    eventHash = entity.eventHash
                ),
                type = type,
                payloadJson = entity.payloadJson
            )
        }
    }
}
