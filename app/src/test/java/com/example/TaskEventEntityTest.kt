package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.contracts.EventType
import com.example.model.TaskEvent
import com.example.storage.AgentHubDatabase
import com.example.storage.TaskEventDao
import com.example.storage.TaskEventEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class TaskEventEntityTest {

    private lateinit var database: AgentHubDatabase
    private lateinit var dao: TaskEventDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AgentHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.taskEventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testTaskEventEntityFieldMappingAndDaoPersistence() = runTest {
        val eventId = UUID.randomUUID().toString()
        val taskId = "task-room-entity-001"
        val timestamp = 1727670000000L
        val traceId = "trace-404"
        val prevHash = "GENESIS"
        val eventHash = "sha256_mock_hash_value"
        val payload = "{\"instruction\":\"Execute file scan\"}"

        val entity = TaskEventEntity(
            eventId = eventId,
            taskId = taskId,
            eventType = "TASK_CREATED",
            payload = payload,
            timestamp = timestamp,
            traceId = traceId,
            prevHash = prevHash,
            eventHash = eventHash
        )

        dao.insertEvent(entity)

        val retrievedList = dao.getEventsForTaskList(taskId)
        assertEquals(1, retrievedList.size)

        val retrieved = retrievedList.first()
        assertEquals(eventId, retrieved.eventId)
        assertEquals(taskId, retrieved.taskId)
        assertEquals("TASK_CREATED", retrieved.eventType)
        assertEquals(payload, retrieved.payload)
        assertEquals(timestamp, retrieved.timestamp)
        assertEquals(traceId, retrieved.traceId)
        assertEquals(prevHash, retrieved.prevHash)
        assertEquals(eventHash, retrieved.eventHash)

        // Verify latest event query
        val latest = dao.getLatestEventForTask(taskId)
        assertNotNull(latest)
        assertEquals(eventId, latest?.eventId)

        // Verify contract mapping
        val contractEvent = retrieved.toContractTaskEvent()
        assertEquals(taskId, contractEvent.taskId)
        assertEquals(EventType.TASK_CREATED, contractEvent.type)
        assertEquals(payload, contractEvent.payloadJson)
        assertEquals(prevHash, contractEvent.prevHash)

        // Verify entity roundtrip from contract
        val fromContract = TaskEventEntity.fromContract(contractEvent)
        assertEquals(retrieved.eventId, fromContract.eventId)
        assertEquals(retrieved.eventType, fromContract.eventType)
    }

    @Test
    fun testChainedEventSequencePersistence() = runTest {
        val taskId = "task-chain-dao"

        val e1 = TaskEventEntity(
            taskId = taskId,
            eventType = "TASK_CREATED",
            payload = "{\"action\":\"start\"}",
            timestamp = 1000L,
            prevHash = "GENESIS",
            eventHash = "hash1"
        )
        val e2 = TaskEventEntity(
            taskId = taskId,
            eventType = "PLAN_CREATED",
            payload = "{\"steps\":[\"stepA\"]}",
            timestamp = 2000L,
            prevHash = "hash1",
            eventHash = "hash2"
        )
        val e3 = TaskEventEntity(
            taskId = taskId,
            eventType = "TASK_COMPLETED",
            payload = "{\"status\":\"done\"}",
            timestamp = 3000L,
            prevHash = "hash2",
            eventHash = "hash3"
        )

        dao.insertEvents(listOf(e1, e2, e3))

        val events = dao.getEventsForTaskList(taskId)
        assertEquals(3, events.size)
        assertEquals("hash1", events[0].eventHash)
        assertEquals("hash1", events[1].prevHash)
        assertEquals("hash2", events[2].prevHash)

        val latest = dao.getLatestEventForTask(taskId)
        assertEquals("hash3", latest?.eventHash)

        // Delete events for task
        dao.deleteEventsForTask(taskId)
        val remaining = dao.getEventsForTaskList(taskId)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun testModelEventConversion() {
        val modelEvent = TaskEvent.TaskCreated(
            taskId = "task-poly",
            request = "Perform analysis",
            prevHash = "GENESIS",
            eventHash = "hash_created"
        )

        val entity = TaskEventEntity.fromModel(modelEvent)
        assertEquals("task-poly", entity.taskId)
        assertEquals("TASK_CREATED", entity.eventType)
        assertEquals("GENESIS", entity.prevHash)

        val restoredModel = entity.toModelTaskEvent() as? TaskEvent.TaskCreated
        assertNotNull(restoredModel)
        assertEquals("Perform analysis", restoredModel?.request)
    }
}
