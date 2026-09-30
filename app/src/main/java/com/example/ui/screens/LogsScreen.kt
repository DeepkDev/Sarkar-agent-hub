package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contracts.EventType
import com.example.contracts.LogEntry
import com.example.contracts.TaskEvent
import com.example.ui.AgentHubViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogsScreen(
    viewModel: AgentHubViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val logs by viewModel.auditLogs.collectAsState()
    val streamEvents by viewModel.streamEvents.collectAsState()
    val isStreamLive by viewModel.isStreamLive.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Tabs: Audit Logs vs Real-time Event Stream
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = CyberCyan,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "AUDIT LOGS (${logs.size})",
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) CyberCyan else TextSecondary
                    )
                },
                modifier = Modifier.testTag("tab_audit_logs")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isStreamLive) NeonEmerald else AlertAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "EVENT STREAM (${streamEvents.size})",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) CyberCyan else TextSecondary
                        )
                    }
                },
                modifier = Modifier.testTag("tab_event_stream")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            AuditLogsView(logs = logs, context = context)
        } else {
            LiveEventStreamView(
                streamEvents = streamEvents,
                isStreamLive = isStreamLive,
                onToggleLive = { viewModel.toggleStreamLive() },
                onExportSse = {
                    val sseContent = viewModel.exportStreamSse()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("AgentHub_EventStream_SSE", sseContent))
                    Toast.makeText(context, "Copied ${streamEvents.size} SSE events to clipboard", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun LiveEventStreamView(
    streamEvents: List<TaskEvent>,
    isStreamLive: Boolean,
    onToggleLive: () -> Unit,
    onExportSse: () -> Unit
) {
    var selectedTypeFilter by remember { mutableStateOf<EventType?>(null) }
    var taskFilterText by remember { mutableStateOf("") }

    val filteredEvents = streamEvents.filter { event ->
        (selectedTypeFilter == null || event.type == selectedTypeFilter) &&
                (taskFilterText.isBlank() || event.taskId.contains(taskFilterText, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Stream status header & actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isStreamLive) NeonEmerald else AlertAmber, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStreamLive) "LIVE BROADCAST" else "STREAM PAUSED",
                    color = if (isStreamLive) NeonEmerald else AlertAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CyanDark.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        "${filteredEvents.size} events",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Play / Pause stream toggle
                IconButton(
                    onClick = onToggleLive,
                    modifier = Modifier
                        .size(32.dp)
                        .background(SurfaceDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .testTag("toggle_stream_button")
                ) {
                    Icon(
                        imageVector = if (isStreamLive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isStreamLive) "Pause Stream" else "Resume Stream",
                        tint = if (isStreamLive) AlertAmber else NeonEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Copy SSE button
                OutlinedButton(
                    onClick = onExportSse,
                    modifier = Modifier.testTag("copy_sse_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy SSE", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Event type filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedTypeFilter == null,
                onClick = { selectedTypeFilter = null },
                label = { Text("ALL", fontSize = 10.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanDark.copy(alpha = 0.3f),
                    selectedLabelColor = CyberCyan
                )
            )

            listOf(
                EventType.TASK_CREATED,
                EventType.STEP_STARTED,
                EventType.TOOL_EXECUTED,
                EventType.PERMISSION_REQUESTED,
                EventType.TASK_COMPLETED
            ).forEach { type ->
                FilterChip(
                    selected = selectedTypeFilter == type,
                    onClick = { selectedTypeFilter = if (selectedTypeFilter == type) null else type },
                    label = { Text(type.name.replace("_", " ").take(12), fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanDark.copy(alpha = 0.3f),
                        selectedLabelColor = CyberCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (streamEvents.isEmpty()) "Waiting for live task events..." else "No events matching current filter",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredEvents.reversed(), key = { it.eventId }) { event ->
                    EventStreamCard(event = event)
                }
            }
        }
    }
}

@Composable
private fun EventStreamCard(event: TaskEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val timeStr = timeFormat.format(Date(event.timestamp))

    val typeColor = when (event.type) {
        EventType.TASK_CREATED -> CyberCyan
        EventType.PLAN_CREATED -> CyberCyan
        EventType.STEP_STARTED -> ElectricIndigo
        EventType.TOOL_REQUESTED -> ElectricIndigo
        EventType.TOOL_EXECUTED -> NeonEmerald
        EventType.PERMISSION_REQUESTED -> AlertAmber
        EventType.PERMISSION_GRANTED -> NeonEmerald
        EventType.PERMISSION_DENIED -> CrimsonRuby
        EventType.VERIFICATION_PASSED -> NeonEmerald
        EventType.VERIFICATION_FAILED -> CrimsonRuby
        EventType.TASK_COMPLETED -> NeonEmerald
        EventType.TASK_FAILED -> CrimsonRuby
        EventType.TASK_CANCELLED -> AlertAmber
        else -> TextSecondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Timestamp, Event Type, Task ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, typeColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = event.type.name,
                            color = typeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = event.taskId,
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cryptographic Chain Link Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "prev: ${event.prevHash.take(8)}...",
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "→",
                    color = CyberCyan.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
                Text(
                    text = "hash: ${event.eventHash.take(8)}...",
                    color = NeonEmerald,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (event.traceId.isNotBlank()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = event.traceId.take(12),
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // JSON Payload block
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = VoidDark,
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Text(
                    text = LogEntry.redact(event.payloadJson),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun AuditLogsView(
    logs: List<com.example.storage.AuditLogEntity>,
    context: Context
) {
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    val filteredLogs = logs.filter {
        selectedLevel == null || it.level == selectedLevel
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header & Copy JSON button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STRUCTURED AUDIT LOGS",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            OutlinedButton(
                onClick = {
                    val jsonLines = filteredLogs.joinToString("\n") { log ->
                        "{\"timestamp\":${log.timestamp},\"traceId\":\"${log.traceId}\",\"taskId\":\"${log.taskId}\",\"component\":\"${log.component}\",\"level\":\"${log.level}\",\"event\":\"${log.event}\",\"message\":\"${LogEntry.redact(log.message.replace("\"", "\\\""))}\"}"
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("AgentHub_Logs", jsonLines))
                    Toast.makeText(context, "Copied ${filteredLogs.size} log records to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("copy_logs_button"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy JSON", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Level Filter Chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedLevel == null,
                onClick = { selectedLevel = null },
                label = { Text("ALL", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanDark.copy(alpha = 0.3f),
                    selectedLabelColor = CyberCyan
                )
            )
            listOf("INFO", "WARN", "ERROR").forEach { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = if (selectedLevel == level) null else level },
                    label = { Text(level, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (level) {
                            "ERROR" -> CrimsonRuby.copy(alpha = 0.3f)
                            "WARN" -> AlertAmber.copy(alpha = 0.3f)
                            else -> CyanDark.copy(alpha = 0.3f)
                        },
                        selectedLabelColor = when (level) {
                            "ERROR" -> CrimsonRuby
                            "WARN" -> AlertAmber
                            else -> CyberCyan
                        }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Logs List
        val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No log records matching filter",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredLogs.reversed()) { log ->
                    val levelColor = when (log.level) {
                        "ERROR" -> CrimsonRuby
                        "WARN" -> AlertAmber
                        else -> CyberCyan
                    }
                    val timeStr = timeFormat.format(Date(log.timestamp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = timeStr,
                                        color = TextTertiary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = log.level,
                                        color = levelColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "[${log.component}]",
                                        color = CyberCyan,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                if (!log.taskId.isNullOrBlank()) {
                                    Text(
                                        text = log.taskId,
                                        color = TextTertiary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "EVENT: ${log.event}",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = LogEntry.redact(log.message),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
