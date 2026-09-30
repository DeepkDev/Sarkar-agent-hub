package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contracts.StepStatus
import com.example.contracts.TaskStatus
import com.example.ui.AgentHubViewModel
import com.example.ui.components.TaskStatusBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TaskDetailScreen(
    viewModel: AgentHubViewModel,
    modifier: Modifier = Modifier
) {
    val task by viewModel.selectedTask.collectAsState()
    val events by viewModel.selectedEvents.collectAsState()
    val permissions by viewModel.pendingPermissions.collectAsState()
    val replayIndex by viewModel.replayIndex.collectAsState()
    val integrityMsg by viewModel.auditIntegrityStatus.collectAsState()
    val isSpeaking by viewModel.speaker.isSpeaking.collectAsState()

    if (task == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = CyberCyan)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading task telemetry...", color = TextSecondary)
            }
        }
        return
    }

    val currentTask = task!!
    val pendingForThisTask = permissions.find { it.taskId == currentTask.taskId && it.status == "PENDING" && !it.isExpired }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
    ) {
        // Task Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentTask.taskId,
                                    color = CyberCyan,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (currentTask.dryRun) {
                                    Box(
                                        modifier = Modifier
                                            .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("DRY RUN", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Origin: ${currentTask.origin.name} • Trace: ${currentTask.traceId}",
                                color = TextTertiary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        TaskStatusBadge(currentTask.status)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = currentTask.request,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )

                    if (currentTask.status == TaskStatus.EXECUTING || currentTask.status == TaskStatus.WAITING_FOR_PERMISSION) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cancelTask(currentTask.taskId) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRuby),
                                modifier = Modifier.testTag("cancel_task_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abort Execution", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // PENDING PERMISSION APPROVAL BANNER (If waiting for operator confirmation)
        if (pendingForThisTask != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, AlertAmber, RoundedCornerShape(10.dp))
                        .testTag("pending_permission_card"),
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevatedDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = AlertAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OPERATOR CLEARANCE REQUIRED",
                                color = AlertAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Target Tool: ${pendingForThisTask.toolId}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Arguments: ${pendingForThisTask.argumentsJson}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pendingForThisTask.sideEffectsSummary,
                            color = AlertAmber,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.decidePermission(pendingForThisTask.permissionId, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = VoidDark),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("permission_approve_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve Action", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.decidePermission(pendingForThisTask.permissionId, false) },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRuby, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("permission_deny_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Deny / Block", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // TIME-TRAVEL / EVENT REPLAY SCRUBBER
        if (events.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "TIME-TRAVEL REPLAY SLIDER",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            if (replayIndex != null) {
                                TextButton(onClick = { viewModel.setReplayIndex(null) }) {
                                    Text("Reset to Live", color = CyberCyan, fontSize = 11.sp)
                                }
                            }
                        }

                        val activeIdx = replayIndex ?: (events.size - 1)
                        Slider(
                            value = activeIdx.toFloat(),
                            onValueChange = { viewModel.setReplayIndex(it.toInt()) },
                            valueRange = 0f..(events.size - 1).coerceAtLeast(1).toFloat(),
                            steps = (events.size - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = CyberCyan,
                                activeTrackColor = CyberCyan,
                                inactiveTrackColor = BorderDark
                            ),
                            modifier = Modifier.testTag("time_travel_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Viewing Event #${activeIdx + 1} of ${events.size}: ${events.getOrNull(activeIdx)?.type ?: "GENESIS"}",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = integrityMsg,
                                color = NeonEmerald,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // LIVE PLAN & STEPS
        item {
            Text(
                text = "EXECUTION PLAN & STEPS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            val plan = currentTask.plan
            if (plan == null || plan.steps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Formulating plan...", color = TextTertiary, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plan.steps.forEachIndexed { index, step ->
                        val isCurrent = step.id == currentTask.currentStepId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (isCurrent) CyberCyan else BorderDark,
                                    shape = RoundedCornerShape(8.dp)
                                ),
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
                                            text = "Step ${index + 1}",
                                            color = CyberCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(SurfaceElevatedDark, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Tool: ${step.expectedTool}",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    StepStatusBadge(step.status)
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = step.description,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )

                                if (!step.toolOutput.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Output: ${step.toolOutput}",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FINAL RESPONSE & SARKAR VOICE CONTRACT CARD
        if (currentTask.finalResponse != null) {
            val response = currentTask.finalResponse!!
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SARKAR VOICE CONTRACT RESPONSE",
                                    color = NeonEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (isSpeaking) {
                                        viewModel.stopSpeaking()
                                    } else {
                                        viewModel.speakSpokenSummary(response.spokenSummary, response.language)
                                    }
                                },
                                modifier = Modifier.testTag("tts_play_button")
                            ) {
                                Icon(
                                    if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Speak",
                                    tint = CyberCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Spoken Summary (TTS Voice Contract):",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceElevatedDark, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = response.spokenSummary,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Detailed Execution Report:",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = response.fullText,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // BUDGET USAGE GAUGES
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "BUDGET UTILIZATION",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Steps: ${currentTask.budgetUsage.stepsUsed} / ${currentTask.budgetConfig.maxSteps}",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Tool Calls: ${currentTask.budgetUsage.toolCallsUsed} / ${currentTask.budgetConfig.maxToolCalls}",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Replans: ${currentTask.budgetUsage.replansUsed} / ${currentTask.budgetConfig.maxReplans}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Elapsed: ${currentTask.budgetUsage.wallClockMsUsed}ms / ${currentTask.budgetConfig.maxWallClockMs}ms",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // TOOL CALL RECORDS
        item {
            Text(
                text = "TOOL CALL TRACES (${currentTask.toolCalls.size})",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (currentTask.toolCalls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tool invocations recorded yet", color = TextTertiary, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    currentTask.toolCalls.forEach { record ->
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
                                    Text(
                                        text = record.toolId,
                                        color = CyberCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${record.durationMs}ms",
                                        color = TextTertiary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "In: ${record.argumentsJson}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Out: ${record.outputJson}",
                                    color = if (record.successful) NeonEmerald else CrimsonRuby,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepStatusBadge(status: StepStatus) {
    val (bg, fg) = when (status) {
        StepStatus.COMPLETED -> NeonEmerald.copy(alpha = 0.15f) to NeonEmerald
        StepStatus.RUNNING -> CyberCyan.copy(alpha = 0.15f) to CyberCyan
        StepStatus.PENDING -> TextTertiary.copy(alpha = 0.2f) to TextSecondary
        StepStatus.FAILED -> CrimsonRuby.copy(alpha = 0.15f) to CrimsonRuby
        StepStatus.SKIPPED -> TextTertiary.copy(alpha = 0.2f) to TextTertiary
    }

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.name,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
