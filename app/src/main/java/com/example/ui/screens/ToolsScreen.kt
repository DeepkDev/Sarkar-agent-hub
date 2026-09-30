package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.contracts.CapabilityStatus
import com.example.contracts.ToolManifest
import com.example.ui.AgentHubViewModel
import com.example.ui.components.PermissionLevelBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun ToolsScreen(
    viewModel: AgentHubViewModel,
    modifier: Modifier = Modifier
) {
    val tools by viewModel.toolRegistry.tools.collectAsState()
    val killSwitch by viewModel.killSwitchEngaged.collectAsState()
    val testResult by viewModel.lastToolTestResult.collectAsState()

    var selectedToolForTest by remember { mutableStateOf<ToolManifest?>(null) }
    var testParam1 by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
    ) {
        // Global Policy & Kill Switch Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, if (killSwitch) CrimsonRuby else BorderDark, RoundedCornerShape(10.dp)),
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
                            Text(
                                text = "GLOBAL EMERGENCY KILL SWITCH",
                                color = if (killSwitch) CrimsonRuby else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (killSwitch) "All tool execution is globally blocked" else "Normal policy evaluation active",
                                color = if (killSwitch) CrimsonRuby else TextTertiary,
                                fontSize = 11.sp
                            )
                        }

                        Switch(
                            checked = killSwitch,
                            onCheckedChange = { viewModel.toggleKillSwitch(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CrimsonRuby,
                                checkedTrackColor = CrimsonRuby.copy(alpha = 0.5f),
                                uncheckedThumbColor = TextTertiary
                            ),
                            modifier = Modifier.testTag("kill_switch_toggle")
                        )
                    }
                }
            }
        }

        // Interactive Tool Runner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevatedDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "INTERACTIVE SANDBOX TOOL RUNNER",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Execute any registered tool directly with custom parameters to test safety, sandbox constraints, and output envelopes.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = testParam1,
                        onValueChange = { testParam1 = it },
                        placeholder = { Text("Input parameter e.g. '(150 * 3) + 45' for calc or 'Asia/Kolkata' for datetime", color = TextTertiary, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("tool_test_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val expr = if (testParam1.isNotBlank()) testParam1 else "(45 * 2) + 10"
                                viewModel.testExecuteTool("calculator", mapOf("expression" to expr))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = VoidDark),
                            modifier = Modifier.testTag("test_calculator_btn")
                        ) {
                            Text("Run Calculator", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val tz = if (testParam1.isNotBlank()) testParam1 else "UTC"
                                viewModel.testExecuteTool("datetime", mapOf("timezone" to tz))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo, contentColor = Color.White),
                            modifier = Modifier.testTag("test_datetime_btn")
                        ) {
                            Text("Run DateTime", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark, RoundedCornerShape(6.dp))
                                .border(1.dp, if (testResult!!.success) NeonEmerald.copy(alpha = 0.4f) else CrimsonRuby.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tool: ${testResult!!.toolId} (${testResult!!.executionTimeMs}ms)",
                                        color = CyberCyan,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    StatusBadge(testResult!!.status)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = testResult!!.data,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tool Manifest Catalog
        item {
            Text(
                text = "TOOL REGISTRY MANIFESTS (${tools.size})",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        items(tools, key = { it.id }) { tool ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tool.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${tool.id})",
                                    color = TextTertiary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(tool.status)
                                PermissionLevelBadge(tool.permissionLevel)
                            }
                        }

                        Switch(
                            checked = tool.enabled && tool.status != CapabilityStatus.DISABLED,
                            onCheckedChange = { viewModel.toggleTool(tool.id, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyanDark)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tool.description,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Side-Effects: ${tool.sideEffects.name} • Timeout: ${tool.timeoutMs}ms",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Source: ${tool.source.name}",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
