package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.contracts.EventType
import com.example.contracts.TaskEvent
import com.example.storage.AgentHubDatabase
import com.example.storage.EventDao
import com.example.storage.EventEntity
import com.example.storage.RoomEventRepository
import com.example.util.EventHasher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class EventRepositoryTest {

    private lateinit var database: AgentHubDatabase
    private lateinit var eventDao: EventDao
    private lateinit var repository: RoomEventRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AgentHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventDao = database.eventDao()
        repository = RoomEventRepository(eventDao, testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppendAndVerifyEventSequenceIntact() = runTest(testDispatcher) {
        val taskId = "task-room-seq-1"

        val event1 = TaskEvent.createWithHash(
            taskId = taskId,
            type = EventType.TASK_CREATED,
            payloadJson = "{\"request\":\"Deploy security agent\"}",
            prevHash = EventHasher.GENESIS_HASH
        )

        val persisted1 = repository.appendEvent(event1)
        assertEquals(EventHasher.GENESIS_HASH, persisted1.prevHash)
        assertTrue(persisted1.eventHash.isNotBlank())

        val event2 = TaskEvent.createWithHash(
            taskId = taskId,
            type = EventType.PLAN_CREATED,
            payloadJson = "{\"steps\":[\"step1\",\"step2\"]}",
            prevHash = persisted1.eventHash
        )
        val persisted2 = repository.appendEvent(event2)
        assertEquals(persisted1.eventHash, persisted2.prevHash)

        val event3 = TaskEvent.createWithHash(
            taskId = taskId,
            type = EventType.STEP_STARTED,
            payloadJson = "{\"stepId\":\"step1\"}",
            prevHash = persisted2.eventHash
        )
        val persisted3 = repository.appendEvent(event3)
        assertEquals(persisted2.eventHash, persisted3.prevHash)

        // Retrieval and cryptographic verification
        val result = repository.getVerifiedEventsForTask(taskId)
        assertEquals(3, result.events.size)
        assertTrue(result.verification.isValid)
        assertEquals(3, result.verification.verifiedCount)
        assertNull(result.verification.failureReason)
    }

    @Test
    fun testTamperedPayloadDetectedOnRetrieval() = runTest(testDispatcher) {
        val taskId = "task-tamper-payload"

        val event1 = repository.appendEvent(
            TaskEvent.createWithHash(
                taskId = taskId,
                type = EventType.TASK_CREATED,
                payloadJson = "{\"request\":\"Legitimate request\"}",
                prevHash = EventHasher.GENESIS_HASH
            )
        )

        val event2 = repository.appendEvent(
            TaskEvent.createWithHash(
                taskId = taskId,
                type = EventType.TOOL_EXECUTED,
                payloadJson = "{\"output\":\"authorized output\"}",
                prevHash = event1.eventHash
            )
        )

        // Direct DB Tamper: Alter the payload of event2 in Room without updating eventHash
        eventDao.insertEvent(
            EventEntity(
                eventId = event2.eventId,
                taskId = event2.taskId,
                type = event2.type.name,
                timestamp = event2.timestamp,
                payloadJson = "{\"output\":\"MALICIOUS_INJECTED_OUTPUT\"}",
                prevHash = event2.prevHash,
                eventHash = event2.eventHash,
                traceId = event2.traceId
            )
        )

        val result = repository.getVerifiedEventsForTask(taskId)
        assertFalse(result.verification.isValid)
        assertEquals(1, result.verification.brokenAtIndex)
        assertNotNull(result.verification.failureReason)
        assertTrue(result.verification.failureReason!!.contains("Tamper detected"))
    }

    @Test
    fun testBrokenHashLinkDetectedOnRetrieval() = runTest(testDispatcher) {
        val taskId = "task-broken-link"

        val event1 = repository.appendEvent(
            TaskEvent.createWithHash(
                taskId = taskId,
                type = EventType.TASK_CREATED,
                payloadJson = "{\"request\":\"Check link\"}",
                prevHash = EventHasher.GENESIS_HASH
            )
        )

        val event2 = repository.appendEvent(
            TaskEvent.createWithHash(
                taskId = taskId,
                type = EventType.STEP_STARTED,
                payloadJson = "{\"stepId\":\"step1\"}",
                prevHash = event1.eventHash
            )
        )

        // Tamper DB: Alter prevHash link of event2 to a foreign hash
        eventDao.insertEvent(
            EventEntity(
                eventId = event2.eventId,
                taskId = event2.taskId,
                type = event2.type.name,
                timestamp = event2.timestamp,
                payloadJson = event2.payloadJson,
                prevHash = "FORGED_PREVIOUS_HASH",
                eventHash = event2.eventHash,
                traceId = event2.traceId
            )
        )

        val result = repository.getVerifiedEventsForTask(taskId)
        assertFalse(result.verification.isValid)
        assertEquals(1, result.verification.brokenAtIndex)
        assertTrue(result.verification.failureReason!!.contains("Chain broken"))
    }

    @Test
    fun testAppendEventsBatchChainsDeterministically() = runTest(testDispatcher) {
        val taskId = "task-batch-append"

        val batch = listOf(
            TaskEvent(taskId = taskId, type = EventType.TASK_CREATED, payloadJson = "{\"req\":\"start\"}"),
            TaskEvent(taskId = taskId, type = EventType.PLAN_CREATED, payloadJson = "{\"plan\":\"p1\"}"),
            TaskEvent(taskId = taskId, type = EventType.TASK_COMPLETED, payloadJson = "{\"summary\":\"done\"}")
        )

        val persistedBatch = repository.appendEvents(batch)
        assertEquals(3, persistedBatch.size)

        assertEquals(EventHasher.GENESIS_HASH, persistedBatch[0].prevHash)
        assertEquals(persistedBatch[0].eventHash, persistedBatch[1].prevHash)
        assertEquals(persistedBatch[1].eventHash, persistedBatch[2].prevHash)

        val verified = repository.getVerifiedEventsForTask(taskId)
        assertTrue(verified.verification.isValid)
        assertEquals(3, verified.verification.verifiedCount)
    }
}
