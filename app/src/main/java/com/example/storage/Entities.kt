package com.example.storage

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.contracts.TaskBudget

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
        Index(value = ["origin"])
    ]
)
data class TaskEntity(
    @PrimaryKey val taskId: String,
    val status: String,
    val request: String,
    val origin: String = "WEB",
    val dryRun: Boolean = false,
    val planJson: String? = null,
    val toolCallsJson: String = "[]",
    val finalResponseJson: String? = null,
    val errorsJson: String = "",
    val budgetUsageJson: String = "{}",
    val traceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val currentStepId: String? = null,
    val permissionEventsJson: String? = null,
    val budgetConfigJson: String? = null,
    @Embedded val budget: TaskBudget = TaskBudget()
)

/** Typealias mapping Task to TaskEntity for architecture compatibility. */
typealias Task = TaskEntity

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val eventId: String,
    val taskId: String,
    val type: String,
    val timestamp: Long,
    val payloadJson: String,
    val prevHash: String,
    val eventHash: String,
    val traceId: String = ""
)

@Entity(tableName = "knowledge_docs")
data class KnowledgeDocEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val category: String,
    val tagsJson: String,
    val source: String,
    val createdAt: Long
)

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val traceId: String,
    val taskId: String?,
    val component: String,
    val level: String,
    val event: String,
    val message: String,
    val error: String?
)
