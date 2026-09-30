package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contracts.*
import com.example.core.EventSourcing
import com.example.core.EventStream
import com.example.core.Orchestrator
import com.example.core.AgentState
import com.example.core.AgentStateManager
import com.example.core.AgentStateType
import com.example.core.TaskReplayService
import com.example.core.DefaultTaskReplayService
import com.example.knowledge.KnowledgeManager
import com.example.providers.AIProvider
import com.example.providers.MockProvider
import com.example.providers.MockSimulationMode
import com.example.providers.ProviderRegistry
import com.example.storage.*
import com.example.tools.ToolExecutor
import com.example.tools.ToolRegistry
import com.example.tts.VoiceAssistantSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class HubScreen {
    OVERVIEW,
    TASKS,
    TASK_DETAIL,
    TOOLS,
    PERMISSIONS,
    KNOWLEDGE,
    LOGS,
    SETTINGS
}

class AgentHubViewModel(application: Application) : AndroidViewModel(application) {

    val knowledgeManager = KnowledgeManager()
    val toolRegistry = ToolRegistry(knowledgeManager)
    val toolExecutor = ToolExecutor(toolRegistry)
    val providerRegistry = ProviderRegistry()
    val orchestrator = Orchestrator(toolRegistry, toolExecutor)
    private val database = AgentHubDatabase.getDatabase(application)
    val taskRepository: TaskRepository = RoomTaskRepository(database.taskDao())
    val eventRepository: EventRepository = RoomEventRepository(database.eventDao())
    val taskReplayService: TaskReplayService = DefaultTaskReplayService(eventRepository)
    val speaker = VoiceAssistantSpeaker(application)
    val stateManager: AgentStateManager = orchestrator.stateManager
    val agentState: StateFlow<AgentState> = orchestrator.stateManager.state
    val agentStateType: StateFlow<AgentStateType> = orchestrator.stateManager.stateType

    // Navigation State
    private val _currentScreen = MutableStateFlow(HubScreen.OVERVIEW)
    val currentScreen: StateFlow<HubScreen> = _currentScreen.asStateFlow()

    // Tasks State
    private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    private val _selectedTaskId = MutableStateFlow<String?>(null)
    val selectedTaskId: StateFlow<String?> = _selectedTaskId.asStateFlow()

    // Selected Task Details & Replay
    private val _selectedTask = MutableStateFlow<AgentTask?>(null)
    val selectedTask: StateFlow<AgentTask?> = _selectedTask.asStateFlow()

    private val _selectedEvents = MutableStateFlow<List<TaskEvent>>(emptyList())
    val selectedEvents: StateFlow<List<TaskEvent>> = _selectedEvents.asStateFlow()

    private val _replayIndex = MutableStateFlow<Int?>(null)
    val replayIndex: StateFlow<Int?> = _replayIndex.asStateFlow()

    private val _auditIntegrityStatus = MutableStateFlow("All events cryptographically verified")
    val auditIntegrityStatus: StateFlow<String> = _auditIntegrityStatus.asStateFlow()

    // Pending Permissions
    private val _pendingPermissions = MutableStateFlow<List<PendingPermission>>(emptyList())
    val pendingPermissions: StateFlow<List<PendingPermission>> = _pendingPermissions.asStateFlow()

    // Logs
    private val _auditLogs = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntity>> = _auditLogs.asStateFlow()

    // Policy Config & Kill Switch
    private val _killSwitchEngaged = MutableStateFlow(false)
    val killSwitchEngaged: StateFlow<Boolean> = _killSwitchEngaged.asStateFlow()

    // Tool Test Runner Output
    private val _lastToolTestResult = MutableStateFlow<ToolResult?>(null)
    val lastToolTestResult: StateFlow<ToolResult?> = _lastToolTestResult.asStateFlow()

    // Knowledge Search State
    private val _knowledgeSearchResults = MutableStateFlow<List<KnowledgeSearchResult>>(emptyList())
    val knowledgeSearchResults: StateFlow<List<KnowledgeSearchResult>> = _knowledgeSearchResults.asStateFlow()

    // Real-Time Event Stream
    val eventStream: EventStream = orchestrator.eventStream
    private val _streamEvents = MutableStateFlow<List<TaskEvent>>(emptyList())
    val streamEvents: StateFlow<List<TaskEvent>> = _streamEvents.asStateFlow()

    private val _isStreamLive = MutableStateFlow(true)
    val isStreamLive: StateFlow<Boolean> = _isStreamLive.asStateFlow()

    fun toggleStreamLive() {
        _isStreamLive.value = !_isStreamLive.value
    }

    fun exportStreamSse(taskId: String? = null): String {
        val list = if (taskId != null) {
            eventStream.getEventsForTask(taskId)
        } else {
            _streamEvents.value
        }
        return list.joinToString("\n") { eventStream.toSseFormat(it) }
    }

    init {
        // Load initial tasks from Room via TaskRepository
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.observeAllTasks().collect { loadedTasks ->
                _tasks.value = loadedTasks
            }
        }

        // Listen for live events from Orchestrator EventStream
        viewModelScope.launch {
            orchestrator.eventStream.events.collect { event ->
                if (_isStreamLive.value) {
                    _streamEvents.value = (_streamEvents.value + event).takeLast(200)
                }

                // Record in Room Database via EventRepository with cryptographic hash chaining
                val persistedEvent = eventRepository.appendEvent(event)

                // Log entry
                database.logDao().insertLog(
                    AuditLogEntity(
                        timestamp = persistedEvent.timestamp,
                        traceId = persistedEvent.traceId.ifBlank { persistedEvent.eventHash.take(10) },
                        taskId = persistedEvent.taskId,
                        component = "Orchestrator",
                        level = if (persistedEvent.type == EventType.TASK_FAILED || persistedEvent.type == EventType.PERMISSION_DENIED) "WARN" else "INFO",
                        event = persistedEvent.type.name,
                        message = persistedEvent.payloadJson,
                        error = null
                    )
                )

                // Refresh task state in list
                val updatedTask = orchestrator.getTask(event.taskId)
                if (updatedTask != null) {
                    _tasks.value = _tasks.value.filter { it.taskId != updatedTask.taskId } + updatedTask
                    // Persist to Room via TaskRepository
                    taskRepository.saveTask(updatedTask)
                }

                // If currently viewing this task, refresh events and view
                if (_selectedTaskId.value == event.taskId) {
                    refreshSelectedTask(event.taskId)
                }

                _pendingPermissions.value = orchestrator.getPendingPermissions()
            }
        }

        // Observe recent logs
        viewModelScope.launch(Dispatchers.IO) {
            database.logDao().getRecentLogs().collect { logs ->
                _auditLogs.value = logs
            }
        }

        // Initial knowledge search
        searchKnowledge("")
    }

    fun navigateTo(screen: HubScreen, taskId: String? = null) {
        if (taskId != null) {
            _selectedTaskId.value = taskId
            refreshSelectedTask(taskId)
        }
        _currentScreen.value = screen
    }

    fun refreshSelectedTask(taskId: String) {
        val inMemory = orchestrator.getTask(taskId)
        if (inMemory != null) {
            val events = orchestrator.getEvents(taskId)
            _selectedEvents.value = events
            _selectedTask.value = if (_replayIndex.value != null) {
                EventSourcing.replayState(inMemory.request, events, _replayIndex.value)
            } else {
                inMemory
            }
            val (valid, msg) = EventSourcing.verifyChainIntegrity(events)
            _auditIntegrityStatus.value = msg
        } else {
            // Fetch from database via TaskRepository
            viewModelScope.launch(Dispatchers.IO) {
                val task = taskRepository.getTask(taskId)
                if (task != null) {
                    val verifiedSequence = eventRepository.getVerifiedEventsForTask(taskId)
                    _selectedEvents.value = verifiedSequence.events
                    _selectedTask.value = task
                    _auditIntegrityStatus.value = if (verifiedSequence.verification.isValid) {
                        "All ${verifiedSequence.events.size} events verified cryptographically intact"
                    } else {
                        verifiedSequence.verification.failureReason ?: "Cryptographic verification failed"
                    }
                }
            }
        }
    }

    fun setReplayIndex(index: Int?) {
        _replayIndex.value = index
        val taskId = _selectedTaskId.value ?: return
        refreshSelectedTask(taskId)
    }

    fun submitTask(
        request: String,
        origin: OriginSource = OriginSource.WEB,
        dryRun: Boolean = false,
        budgetOverride: BudgetConfig? = null
    ) {
        viewModelScope.launch {
            val provider = providerRegistry.activeProvider.value
            val task = orchestrator.submitTask(
                request = request,
                provider = provider,
                origin = origin,
                dryRun = dryRun,
                budgetOverride = budgetOverride,
                scope = viewModelScope
            )
            _tasks.value = listOf(task) + _tasks.value.filter { it.taskId != task.taskId }
            taskRepository.saveTask(task)
            navigateTo(HubScreen.TASK_DETAIL, task.taskId)
        }
    }

    fun decidePermission(permissionId: String, approve: Boolean) {
        orchestrator.decidePermission(permissionId, approve)
        _pendingPermissions.value = orchestrator.getPendingPermissions()
    }

    fun cancelTask(taskId: String) {
        orchestrator.cancelTask(taskId)
        refreshSelectedTask(taskId)
    }

    fun toggleTool(toolId: String, enabled: Boolean) {
        toolRegistry.setToolEnabled(toolId, enabled)
    }

    fun testExecuteTool(toolId: String, args: Map<String, Any?>) {
        viewModelScope.launch {
            val res = toolExecutor.execute(toolId, args)
            _lastToolTestResult.value = res
        }
    }

    fun setProvider(providerId: String) {
        providerRegistry.setActiveProvider(providerId)
    }

    fun setMockSimulationMode(mode: MockSimulationMode) {
        providerRegistry.mockProvider.simulationMode = mode
    }

    fun toggleKillSwitch(enabled: Boolean) {
        _killSwitchEngaged.value = enabled
        orchestrator.policyConfig = orchestrator.policyConfig.copy(killSwitchEngaged = enabled)
    }

    fun searchKnowledge(query: String) {
        _knowledgeSearchResults.value = knowledgeManager.search(query)
    }

    fun speakSpokenSummary(text: String, lang: String = "en") {
        speaker.speak(text, lang)
    }

    fun stopSpeaking() {
        speaker.stop()
    }

    // Quick 1-click Preset Demos
    fun runPreset(presetName: String) {
        when (presetName) {
            "SAFE_CALC" -> {
                submitTask(
                    request = "Calculate total server cluster cost for 45 nodes at 12 dollars per hour plus 25 dollars overhead",
                    origin = OriginSource.WEB
                )
            }
            "APPROVAL_FLOW" -> {
                submitTask(
                    request = "Send approval notification email to system administrator regarding high memory utilization",
                    origin = OriginSource.WEB
                )
            }
            "RESTRICTED_BLOCK" -> {
                submitTask(
                    request = "Permanently delete user audit archive record rec-demo-9999",
                    origin = OriginSource.WEB
                )
            }
            "SARKAR_ORIGIN_TASK" -> {
                submitTask(
                    request = "Check system status and query knowledge base for voice TTS specification",
                    origin = OriginSource.SARKAR
                )
            }
            "DRY_RUN_PREVIEW" -> {
                submitTask(
                    request = "Analyze current date/time and verify math benchmark formula",
                    origin = OriginSource.API,
                    dryRun = true
                )
            }
            "FAILURE_SIMULATION" -> {
                setMockSimulationMode(MockSimulationMode.TOOL_FAILURE_SIM)
                submitTask(
                    request = "Run stress test with forced verification failure",
                    origin = OriginSource.WEB
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speaker.shutdown()
    }

    // Entity Mappers delegated to TaskRepository
    private fun AgentTask.toEntity(): TaskEntity = with(RoomTaskRepository) { this@toEntity.toEntity() }
    private fun TaskEntity.toAgentTask(): AgentTask = with(RoomTaskRepository) { this@toAgentTask.toDomain() }
}
