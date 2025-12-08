package cc.unitmesh.server.workflow

import cc.unitmesh.server.workflow.models.WorkflowEvent
import cc.unitmesh.server.workflow.store.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * In-memory EventStore implementation for testing
 */
class InMemoryEventStore : EventStore {
    private val events = mutableMapOf<String, MutableList<WorkflowEvent>>()

    override suspend fun appendEvent(event: WorkflowEvent): Long {
        events.getOrPut(event.workflowId) { mutableListOf() }.add(event)
        return event.sequenceNumber
    }

    override suspend fun appendEvents(events: List<WorkflowEvent>): List<Long> {
        return events.map { appendEvent(it) }
    }

    override suspend fun getEvents(workflowId: String, fromSequence: Long, toSequence: Long?): List<WorkflowEvent> {
        val workflowEvents = events[workflowId] ?: return emptyList()
        return workflowEvents.filter { event ->
            event.sequenceNumber >= fromSequence && (toSequence == null || event.sequenceNumber <= toSequence)
        }.sortedBy { it.sequenceNumber }
    }

    override suspend fun getLatestSequence(workflowId: String): Long {
        return events[workflowId]?.maxOfOrNull { it.sequenceNumber } ?: 0L
    }

    override suspend fun getEventsByType(workflowId: String, eventType: String): List<WorkflowEvent> {
        return events[workflowId]?.filter { it.eventType == eventType } ?: emptyList()
    }

    override suspend fun deleteEvents(workflowId: String) {
        events.remove(workflowId)
    }

    override suspend fun getEventCount(workflowId: String): Long {
        return events[workflowId]?.size?.toLong() ?: 0L
    }
}

class InMemoryEventStoreTest {

    @Test
    fun `test append and retrieve events`() = runBlocking {
        val store = InMemoryEventStore()
        val workflowId = "test-workflow-1"

        val event1 = WorkflowEvent(
            id = "event-1",
            workflowId = workflowId,
            sequenceNumber = 1,
            eventType = "WorkflowStarted",
            eventData = """{"task": "test task"}""",
            timestamp = System.currentTimeMillis()
        )

        val event2 = WorkflowEvent(
            id = "event-2",
            workflowId = workflowId,
            sequenceNumber = 2,
            eventType = "ToolCall",
            eventData = """{"toolName": "read-file"}""",
            timestamp = System.currentTimeMillis()
        )

        store.appendEvent(event1)
        store.appendEvent(event2)

        val events = store.getEvents(workflowId)
        assertEquals(2, events.size)
        assertEquals("WorkflowStarted", events[0].eventType)
        assertEquals("ToolCall", events[1].eventType)
    }

    @Test
    fun `test get events with sequence range`() = runBlocking {
        val store = InMemoryEventStore()
        val workflowId = "test-workflow-2"

        for (i in 1..10) {
            store.appendEvent(WorkflowEvent(
                id = "event-$i",
                workflowId = workflowId,
                sequenceNumber = i.toLong(),
                eventType = "Event$i",
                eventData = "{}",
                timestamp = System.currentTimeMillis()
            ))
        }

        val events = store.getEvents(workflowId, fromSequence = 3, toSequence = 7)
        assertEquals(5, events.size)
        assertEquals(3L, events.first().sequenceNumber)
        assertEquals(7L, events.last().sequenceNumber)
    }

    @Test
    fun `test get latest sequence`() = runBlocking {
        val store = InMemoryEventStore()
        val workflowId = "test-workflow-3"

        assertEquals(0L, store.getLatestSequence(workflowId))

        store.appendEvent(WorkflowEvent(
            id = "event-1",
            workflowId = workflowId,
            sequenceNumber = 5,
            eventType = "Test",
            eventData = "{}",
            timestamp = System.currentTimeMillis()
        ))

        assertEquals(5L, store.getLatestSequence(workflowId))
    }

    @Test
    fun `test get events by type`() = runBlocking {
        val store = InMemoryEventStore()
        val workflowId = "test-workflow-4"

        store.appendEvent(WorkflowEvent("1", workflowId, 1, "ToolCall", "{}", System.currentTimeMillis()))
        store.appendEvent(WorkflowEvent("2", workflowId, 2, "ToolResult", "{}", System.currentTimeMillis()))
        store.appendEvent(WorkflowEvent("3", workflowId, 3, "ToolCall", "{}", System.currentTimeMillis()))

        val toolCalls = store.getEventsByType(workflowId, "ToolCall")
        assertEquals(2, toolCalls.size)
    }

    @Test
    fun `test delete events`() = runBlocking {
        val store = InMemoryEventStore()
        val workflowId = "test-workflow-5"

        store.appendEvent(WorkflowEvent("1", workflowId, 1, "Test", "{}", System.currentTimeMillis()))
        assertEquals(1L, store.getEventCount(workflowId))

        store.deleteEvents(workflowId)
        assertEquals(0L, store.getEventCount(workflowId))
    }
}

