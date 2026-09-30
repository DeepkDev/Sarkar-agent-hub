package com.example

import com.example.contracts.EventType
import com.example.contracts.TaskEvent
import com.example.core.DefaultEventStream
import com.example.core.EventStream
import com.example.core.SseFormatter
import com.example.model.TaskEvent as ModelTaskEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class EventStreamTest {

    private lateinit var eventStream: EventStream
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        eventStream = DefaultEventStream(bufferCapacity = 100, replayCapacity = 10)
    }

    @Test
    fun testPublishAndCollectEvents() = runTest(testDispatcher) {
        val collected = mutableListOf<TaskEvent>()
        val job = launch {
            eventStream.events.collect { collected.add(it) }
        }

        val event1 = TaskEvent(taskId = "task-stream-1", type = EventType.TASK_CREATED, payloadJson = "{\"goal\":\"analyze\"}")
        val event2 = TaskEvent(taskId = "task-stream-1", type = EventType.STEP_STARTED, payloadJson = "{\"step\":\"1\"}")

        eventStream.emit(event1)
        eventStream.emit(event2)

        assertEquals(2, collected.size)
        assertEquals("task-stream-1", collected[0].taskId)
        assertEquals(EventType.TASK_CREATED, collected[0].type)
        assertEquals(EventType.STEP_STARTED, collected[1].type)

        job.cancel()
    }

    @Test
    fun testStreamFilteredByTask() = runTest(testDispatcher) {
        val task1Events = mutableListOf<TaskEvent>()
        val job = launch {
            eventStream.streamForTask("task-A").collect { task1Events.add(it) }
        }

        eventStream.emit(TaskEvent(taskId = "task-A", type = EventType.TASK_CREATED, payloadJson = "{}"))
        eventStream.emit(TaskEvent(taskId = "task-B", type = EventType.TASK_CREATED, payloadJson = "{}"))
        eventStream.emit(TaskEvent(taskId = "task-A", type = EventType.STEP_STARTED, payloadJson = "{}"))

        assertEquals(2, task1Events.size)
        assertTrue(task1Events.all { it.taskId == "task-A" })

        job.cancel()
    }

    @Test
    fun testStreamFilteredByType() = runTest(testDispatcher) {
        val permEvents = mutableListOf<TaskEvent>()
        val job = launch {
            eventStream.streamForType(EventType.PERMISSION_REQUESTED).collect { permEvents.add(it) }
        }

        eventStream.emit(TaskEvent(taskId = "task-1", type = EventType.TASK_CREATED, payloadJson = "{}"))
        eventStream.emit(TaskEvent(taskId = "task-1", type = EventType.PERMISSION_REQUESTED, payloadJson = "{\"tool\":\"db_write\"}"))
        eventStream.emit(TaskEvent(taskId = "task-2", type = EventType.PERMISSION_REQUESTED, payloadJson = "{\"tool\":\"file_write\"}"))
        eventStream.emit(TaskEvent(taskId = "task-1", type = EventType.TASK_COMPLETED, payloadJson = "{}"))

        assertEquals(2, permEvents.size)
        assertTrue(permEvents.all { it.type == EventType.PERMISSION_REQUESTED })

        job.cancel()
    }

    @Test
    fun testInterceptorNotification() {
        val intercepted = mutableListOf<TaskEvent>()
        eventStream.registerInterceptor { intercepted.add(it) }

        val event = TaskEvent(taskId = "task-interceptor", type = EventType.TOOL_EXECUTED, payloadJson = "{\"tool\":\"bash\"}")
        eventStream.emit(event)

        assertEquals(1, intercepted.size)
        assertEquals("task-interceptor", intercepted[0].taskId)
    }

    @Test
    fun testSseFormatting() {
        val event = TaskEvent.createWithHash(
            taskId = "task-sse-test",
            type = EventType.TASK_CREATED,
            payloadJson = "{\"intent\":\"export sse\"}",
            prevHash = "GENESIS"
        )

        val sse = SseFormatter.format(event)
        assertTrue(sse.startsWith("id: ${event.eventId}"))
        assertTrue(sse.contains("event: TASK_CREATED"))
        assertTrue(sse.contains("\"taskId\":\"task-sse-test\""))
        assertTrue(sse.contains("\"prevHash\":\"GENESIS\""))
        assertTrue(sse.endsWith("\n\n"))
    }

    @Test
    fun testModelEventEmission() = runTest(testDispatcher) {
        val collected = mutableListOf<TaskEvent>()
        val job = launch {
            eventStream.events.collect { collected.add(it) }
        }

        val modelEvent = ModelTaskEvent.TaskCreated(
            taskId = "task-poly-stream",
            request = "Process images"
        )
        eventStream.emit(modelEvent)

        assertEquals(1, collected.size)
        assertEquals("task-poly-stream", collected[0].taskId)
        assertEquals(EventType.TASK_CREATED, collected[0].type)

        job.cancel()
    }

    @Test
    fun testRecentEventsRetrievalAndClear() {
        eventStream.emit(TaskEvent(taskId = "task-rec", type = EventType.TASK_CREATED, payloadJson = "{}"))
        eventStream.emit(TaskEvent(taskId = "task-rec", type = EventType.PLAN_CREATED, payloadJson = "{}"))

        val recents = eventStream.getRecentEvents(10)
        assertEquals(2, recents.size)

        val taskEvents = eventStream.getEventsForTask("task-rec")
        assertEquals(2, taskEvents.size)

        eventStream.clearTask("task-rec")
        assertTrue(eventStream.getEventsForTask("task-rec").isEmpty())
    }
}
