package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contracts.BudgetConfig
import com.example.contracts.OriginSource
import com.example.contracts.TaskStatus
import com.example.ui.AgentHubViewModel
import com.example.ui.HubScreen
import com.example.ui.components.TaskStatusBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TasksScreen(
    viewModel: AgentHubViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    var selectedFilter by remember { mutableStateOf<TaskStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredTasks = tasks.filter { task ->
        (selectedFilter == null || task.status == selectedFilter) &&
        (searchQuery.isBlank() || task.request.contains(searchQuery, ignoreCase = true) || task.taskId.contains(searchQuery, ignoreCase = true))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar & Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_search_field"),
                placeholder = { Text("Search by task ID or prompt...", color = TextTertiary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextTertiary)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderDark,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("ALL (${tasks.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == TaskStatus.EXECUTING,
                        onClick = { selectedFilter = TaskStatus.EXECUTING },
                        label = { Text("RUNNING", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == TaskStatus.WAITING_FOR_PERMISSION,
                        onClick = { selectedFilter = TaskStatus.WAITING_FOR_PERMISSION },
                        label = { Text("APPROVAL REQ", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlertAmber.copy(alpha = 0.2f),
                            selectedLabelColor = AlertAmber
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == TaskStatus.COMPLETED,
                        onClick = { selectedFilter = TaskStatus.COMPLETED },
                        label = { Text("COMPLETED", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = NeonEmerald
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == TaskStatus.FAILED,
                        onClick = { selectedFilter = TaskStatus.FAILED },
                        label = { Text("FAILED", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonRuby.copy(alpha = 0.2f),
                            selectedLabelColor = CrimsonRuby
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task List
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FilterListOff, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No tasks match filter criteria", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredTasks, key = { it.taskId }) { task ->
                        val dateStr = remember(task.createdAt) {
                            SimpleDateFormat("HH:mm:ss • MMM dd", Locale.US).format(Date(task.createdAt))
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                .clickable { viewModel.navigateTo(HubScreen.TASK_DETAIL, task.taskId) }
                                .testTag("task_card_${task.taskId}"),
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
                                        Text(
                                            text = task.taskId,
                                            color = CyberCyan,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(SurfaceElevatedDark, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = task.origin.name,
                                                color = TextSecondary,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    TaskStatusBadge(task.status)
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = task.request,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateStr,
                                        color = TextTertiary,
                                        fontSize = 11.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Tools: ${task.toolCalls.size}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                        if (task.dryRun) {
                                            Text(
                                                text = "DRY RUN",
                                                color = CyberCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button to Create Task
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("create_task_fab"),
            containerColor = CyberCyan,
            contentColor = VoidDark
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Task")
        }

        // Create Task Dialog
        if (showCreateDialog) {
            CreateTaskDialog(
                onDismiss = { showCreateDialog = false },
                onSubmit = { request, origin, dryRun, maxSteps, maxCalls, wallClockSec ->
                    viewModel.submitTask(
                        request = request,
                        origin = origin,
                        dryRun = dryRun,
                        budgetOverride = BudgetConfig(
                            maxSteps = maxSteps,
                            maxToolCalls = maxCalls,
                            maxWallClockMs = wallClockSec * 1000L
                        )
                    )
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, OriginSource, Boolean, Int, Int, Long) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var selectedOrigin by remember { mutableStateOf(OriginSource.WEB) }
    var dryRun by remember { mutableStateOf(false) }
    var maxSteps by remember { mutableStateOf(10) }
    var maxCalls by remember { mutableStateOf(15) }
    var wallClockSec by remember { mutableStateOf(60L) }
    var showAdvancedBudget by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text(
                text = "Dispatch New Agent Task",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Goal or Instruction:",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text("e.g. Calculate server costs or search knowledge base", color = TextTertiary, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("create_task_prompt_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    text = "Origin Source:",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OriginSource.values().forEach { origin ->
                        FilterChip(
                            selected = selectedOrigin == origin,
                            onClick = { selectedOrigin = origin },
                            label = { Text(origin.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dry Run Mode", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Plan & policy check only, no tools executed", color = TextTertiary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = dryRun,
                        onCheckedChange = { dryRun = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyanDark)
                    )
                }

                TextButton(onClick = { showAdvancedBudget = !showAdvancedBudget }) {
                    Text(
                        text = if (showAdvancedBudget) "Hide Budget Limits ▲" else "Show Hard Budget Limits ▼",
                        color = CyberCyan,
                        fontSize = 11.sp
                    )
                }

                if (showAdvancedBudget) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Max Steps ($maxSteps)", color = TextSecondary, fontSize = 11.sp)
                            Slider(
                                value = maxSteps.toFloat(),
                                onValueChange = { maxSteps = it.toInt() },
                                valueRange = 2f..20f,
                                modifier = Modifier.width(140.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Max Tool Calls ($maxCalls)", color = TextSecondary, fontSize = 11.sp)
                            Slider(
                                value = maxCalls.toFloat(),
                                onValueChange = { maxCalls = it.toInt() },
                                valueRange = 2f..30f,
                                modifier = Modifier.width(140.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (prompt.isNotBlank()) {
                        onSubmit(prompt, selectedOrigin, dryRun, maxSteps, maxCalls, wallClockSec)
                    }
                },
                enabled = prompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = VoidDark),
                modifier = Modifier.testTag("create_task_submit_button")
            ) {
                Text("Dispatch Task", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
