package com.example.util

import com.example.contracts.TaskEvent
import java.security.MessageDigest

/**
 * Utility for generating and verifying cryptographic SHA-256 hashes
 * across the task event stream to ensure tamper-evident auditing.
 */
object EventHasher {

    const val GENESIS_HASH = "GENESIS"

    /**
     * Generates a deterministic SHA-256 hash for a [TaskEvent] chained to [previousHash].
     *
     * @param event The TaskEvent whose contents are hashed.
     * @param previousHash The hash of the immediately preceding event in the sequence (or "GENESIS" for the first event).
     * @return 64-character lowercase hex string representation of the SHA-256 digest.
     */
    fun generateHash(event: TaskEvent, previousHash: String): String {
        val content = "${event.eventId}|${event.taskId}|${event.traceId}|${event.type.name}|${event.timestamp}|${event.payloadJson}|$previousHash"
        return sha256(content)
    }

    /**
     * Verifies if an event's recorded eventHash matches the calculated hash from its contents and [previousHash].
     */
    fun verifyEvent(event: TaskEvent, previousHash: String): Boolean {
        val computed = generateHash(event, previousHash)
        return computed.equals(event.eventHash, ignoreCase = true)
    }

    /**
     * Computes the SHA-256 hash of a raw string using UTF-8 encoding.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
