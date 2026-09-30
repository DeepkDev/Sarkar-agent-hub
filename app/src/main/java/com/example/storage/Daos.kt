package com.example.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC LIMIT :limit")
    fun getTaskHistory(limit: Int = 100): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getTaskHistoryList(limit: Int = 100): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE taskId = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE taskId = :id")
    fun observeTaskById(id: String): Flow<TaskEntity?>

    @Query("SELECT status FROM tasks WHERE taskId = :id")
    suspend fun getTaskStatus(id: String): String?

    @Query("SELECT status FROM tasks WHERE taskId = :id")
    fun observeTaskStatus(id: String): Flow<String?>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY updatedAt DESC")
    fun getTasksByStatus(status: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun getTasksByStatusList(status: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED') ORDER BY createdAt DESC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED') ORDER BY createdAt DESC")
    suspend fun getActiveTasksList(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks")
    fun getTaskCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE status = :status")
    suspend fun getTaskCountByStatus(status: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE taskId = :id")
    suspend fun updateTaskStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM tasks WHERE maxSteps <= :maxSteps ORDER BY createdAt DESC")
    fun getTasksByMaxSteps(maxSteps: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE maxWallClockMs >= :minWallClockMs ORDER BY createdAt DESC")
    fun getTasksWithLongTimeout(minWallClockMs: Long): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET maxSteps = :maxSteps, maxToolCalls = :maxToolCalls, maxWallClockMs = :maxWallClockMs, updatedAt = :updatedAt WHERE taskId = :id")
    suspend fun updateTaskBudget(id: String, maxSteps: Int, maxToolCalls: Int, maxWallClockMs: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks WHERE taskId = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun getEventsForTask(taskId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE taskId = :taskId ORDER BY timestamp ASC")
    suspend fun getEventsForTaskList(taskId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE taskId = :taskId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEventForTask(taskId: String): EventEntity?

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT 200")
    fun getRecentEvents(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Query("DELETE FROM events WHERE taskId = :taskId")
    suspend fun deleteEventsForTask(taskId: String)
}

@Dao
interface TaskEventDao {
    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun getEventsForTask(taskId: String): Flow<List<TaskEventEntity>>

    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY timestamp ASC")
    suspend fun getEventsForTaskList(taskId: String): List<TaskEventEntity>

    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEventForTask(taskId: String): TaskEventEntity?

    @Query("SELECT * FROM task_events ORDER BY timestamp DESC LIMIT 200")
    fun getRecentEvents(): Flow<List<TaskEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TaskEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<TaskEventEntity>)

    @Query("DELETE FROM task_events WHERE taskId = :taskId")
    suspend fun deleteEventsForTask(taskId: String)
}

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_docs ORDER BY createdAt DESC")
    fun getAllDocs(): Flow<List<KnowledgeDocEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoc(doc: KnowledgeDocEntity)

    @Delete
    suspend fun deleteDoc(doc: KnowledgeDocEntity)
}

@Dao
interface PreferenceDao {
    @Query("SELECT value FROM preferences WHERE `key` = :key")
    suspend fun getPreference(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(pref: PreferenceEntity)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 300")
    fun getRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}
