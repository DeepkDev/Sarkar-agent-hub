package com.example.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class,
        EventEntity::class,
        TaskEventEntity::class,
        KnowledgeDocEntity::class,
        PreferenceEntity::class,
        AuditLogEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AgentHubDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun eventDao(): EventDao
    abstract fun taskEventDao(): TaskEventDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun logDao(): LogDao

    fun taskRepository(): TaskRepository = RoomTaskRepository(taskDao())
    fun eventRepository(): EventRepository = RoomEventRepository(eventDao())
    fun taskReplayService(): com.example.core.TaskReplayService = com.example.core.DefaultTaskReplayService(eventRepository())

    companion object {
        @Volatile
        private var INSTANCE: AgentHubDatabase? = null

        fun getDatabase(context: Context): AgentHubDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgentHubDatabase::class.java,
                    "agent_hub_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
