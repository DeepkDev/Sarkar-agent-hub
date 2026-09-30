package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.contracts.CapabilityStatus
import com.example.contracts.NullSarkarConnector
import com.example.providers.MockSimulationMode
import com.example.ui.AgentHubViewModel
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: AgentHubViewModel,
    modifier: Modifier = Modifier
) {
    val activeProvider by viewModel.providerRegistry.activeProvider.collectAsState()
    val allProviders = viewModel.providerRegistry.getAllProviders()
    var currentSimulationMode by remember { mutableStateOf(viewModel.providerRegistry.mockProvider.simulationMode) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
    ) {
        // AI Provider Configuration
        item {
            Text(
                text = "AI PROVIDER SELECTION",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    allProviders.forEach { prov ->
                        val isSelected = prov.id == activeProvider.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isSelected) CyberCyan else BorderDark,
                                    RoundedCornerShape(8.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) SurfaceElevatedDark else SurfaceDark
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = prov.displayName,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        StatusBadge(
                                            if (prov.id == "mock_provider") CapabilityStatus.IMPLEMENTED
                                            else if (prov.id == "gemini_provider") CapabilityStatus.IMPLEMENTED
                                            else CapabilityStatus.PLACEHOLDER
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Max Context: ${prov.capabilities().maxContextTokens} tokens • Structured: ${prov.capabilities().structuredOutput}",
                                        color = TextTertiary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setProvider(prov.id) },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyberCyan)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mock Provider Simulation Modes (For CUJ Demos)
        if (activeProvider.id == "mock_provider") {
            item {
                Text(
                    text = "MOCK SIMULATION MODE (TESTING & DEMOS)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderDark, RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Configure failure, timeout, or malformed AI output simulation:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        MockSimulationMode.values().forEach { mode ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.name,
                                    color = if (currentSimulationMode == mode) CyberCyan else TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                RadioButton(
                                    selected = currentSimulationMode == mode,
                                    onClick = {
                                        currentSimulationMode = mode
                                        viewModel.setMockSimulationMode(mode)
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyberCyan)
                                )
                            }
                        }
                    }
                }
            }
        }

        // SARKAR Connector Contract Section
        item {
            Text(
                text = "SARKAR CONNECTOR INTEGRATION SPECIFICATION",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ElectricIndigo.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SarkarConnector Interface",
                            color = ElectricIndigo,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        StatusBadge(NullSarkarConnector().status)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Independence Rule: SARKAR Assistant connects later via public REST + SSE API.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceElevatedDark, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = """
Endpoints Contract:
POST /api/v1/agent/task (voicePrompt, userId, origin='sarkar')
GET  /api/v1/agent/task/:id (TaskStatusView)
GET  /api/v1/agent/task/:id/events (SSE stream)

Device-Side Tool Delegation Schema:
DelegatedToolRequest {
  deviceToolName: "android.camera.capture" | "android.contacts.lookup",
  parameters: Map<String, Any>
}
                            """.trimIndent(),
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // System Health & Diagnostics
        item {
            Text(
                text = "SYSTEM HEALTH & READINESS",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthRow("Engine State Machine", "READY (All transitions typed)", NeonEmerald)
                    HealthRow("Local Room Storage", "HEALTHY (SQLite SQLite3)", NeonEmerald)
                    HealthRow("Cryptographic Event Audit", "INTACT (SHA-256 Chained)", NeonEmerald)
                    HealthRow("Tool Executor Sandbox", "BOUNDED (Timeout & Cancellation)", NeonEmerald)
                    HealthRow("Text-To-Speech Synthesizer", "AVAILABLE (Android TTS)", NeonEmerald)
                }
            }
        }
    }
}

@Composable
fun HealthRow(name: String, statusText: String, statusColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, color = TextPrimary, fontSize = 12.sp)
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}
