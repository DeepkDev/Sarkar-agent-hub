package com.example.contracts

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LogEntry(
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val traceId: String = "",
    val taskId: String? = null,
    val component: String,
    val level: String, // "DEBUG", "INFO", "WARN", "ERROR"
    val event: String,
    val message: String,
    val durationMs: Long? = null,
    val error: String? = null
) {
    companion object {
        fun redact(raw: String): String {
            return raw
                .replace(Regex("(?i)(api[_-]?key|password|secret|token)[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,]+)[\"']?"), "$1: [REDACTED]")
                .replace(Regex("AIzaSy[A-Za-z0-9_-]{33}"), "[REDACTED_API_KEY]")
        }
    }
}
