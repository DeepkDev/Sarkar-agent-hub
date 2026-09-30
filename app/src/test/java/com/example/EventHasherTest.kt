package com.example

import com.example.contracts.EventType
import com.example.contracts.TaskEvent
import com.example.util.EventHasher
import org.junit.Assert.*
import org.junit.Test

class EventHasherTest {

    @Test
    fun testGenerateHashProducesValidSha256Hex() {
        val event = TaskEvent.createWithHash(
            taskId = "task-test-1",
            type = EventType.TASK_CREATED,
            payloadJson = "{\"request\":\"Verify hash utility\"}",
            prevHash = EventHasher.GENESIS_HASH
        )

        val computedHash = EventHasher.generateHash(event, EventHasher.GENESIS_HASH)

        assertNotNull(computedHash)
        // SHA-256 in hex must be exactly 64 characters
        assertEquals(64, computedHash.length)
        assertTrue(computedHash.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(event.eventHash, computedHash)
        assertTrue(EventHasher.verifyEvent(event, EventHasher.GENESIS_HASH))
    }

    @Test
    fun testHashChainingTamperDetection() {
        val event1 = TaskEvent.createWithHash(
            taskId = "task-chain-1",
            type = EventType.TASK_CREATED,
            payloadJson = "{\"request\":\"Initial step\"}",
            prevHash = EventHasher.GENESIS_HASH
        )

        val event2 = TaskEvent.createWithHash(
            taskId = "task-chain-1",
            type = EventType.PLAN_CREATED,
            payloadJson = "{\"steps\":[\"step1\"]}",
            prevHash = event1.eventHash
        )

        val event3 = TaskEvent.createWithHash(
            taskId = "task-chain-1",
            type = EventType.STEP_STARTED,
            payloadJson = "{\"stepId\":\"step1\"}",
            prevHash = event2.eventHash
        )

        // All intact
        assertTrue(EventHasher.verifyEvent(event1, EventHasher.GENESIS_HASH))
        assertTrue(EventHasher.verifyEvent(event2, event1.eventHash))
        assertTrue(EventHasher.verifyEvent(event3, event2.eventHash))

        // Tamper 1: Modify payload of event2
        val tamperedEvent2 = event2.copy(payloadJson = "{\"steps\":[\"malicious_step\"]}")
        assertFalse(EventHasher.verifyEvent(tamperedEvent2, event1.eventHash))

        // Tamper 2: Break chain link (wrong previousHash passed)
        assertFalse(EventHasher.verifyEvent(event3, "CORRUPTED_PREVIOUS_HASH"))

        // Tamper 3: Change event type
        val tamperedTypeEvent = event1.copy(type = EventType.TASK_FAILED)
        assertFalse(EventHasher.verifyEvent(tamperedTypeEvent, EventHasher.GENESIS_HASH))
    }

    @Test
    fun testSha256Consistency() {
        val input = "SARKAR_AGENT_HUB_TEST_SEED"
        val hash1 = EventHasher.sha256(input)
        val hash2 = EventHasher.sha256(input)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
    }
}
