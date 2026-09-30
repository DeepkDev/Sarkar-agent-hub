package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AgentHubViewModel
import com.example.ui.HubScreen
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentHubTheme {
                AgentHubApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentHubApp(viewModel: AgentHubViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val permissions by viewModel.pendingPermissions.collectAsState()
    val killSwitch by viewModel.killSwitchEngaged.collectAsState()

    // Handle back button on sub-screens
    BackHandler(enabled = currentScreen != HubScreen.OVERVIEW) {
        if (currentScreen == HubScreen.TASK_DETAIL) {
            viewModel.navigateTo(HubScreen.TASKS)
        } else {
            viewModel.navigateTo(HubScreen.OVERVIEW)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = VoidDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (killSwitch) CrimsonRuby else NeonEmerald)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentScreen) {
                                HubScreen.OVERVIEW -> "SARKAR AGENT HUB"
                                HubScreen.TASKS -> "Agent Tasks"
                                HubScreen.TASK_DETAIL -> "Task Telemetry"
                                HubScreen.TOOLS -> "Tool Registry & Policy"
                                HubScreen.PERMISSIONS -> "Approval Queue"
                                HubScreen.KNOWLEDGE -> "Demo Knowledge Store"
                                HubScreen.LOGS -> "Structured Audit Logs"
                                HubScreen.SETTINGS -> "Platform Settings"
                            },
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    if (currentScreen != HubScreen.OVERVIEW) {
                        IconButton(onClick = {
                            if (currentScreen == HubScreen.TASK_DETAIL) {
                                viewModel.navigateTo(HubScreen.TASKS)
                            } else {
                                viewModel.navigateTo(HubScreen.OVERVIEW)
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberCyan)
                        }
                    }
                },
                actions = {
                    if (permissions.isNotEmpty()) {
                        Badge(
                            containerColor = AlertAmber,
                            contentColor = VoidDark,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text("${permissions.size} pending", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextSecondary,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, BorderDark)
            ) {
                NavigationBarItem(
                    selected = currentScreen == HubScreen.OVERVIEW,
                    onClick = { viewModel.navigateTo(HubScreen.OVERVIEW) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Overview") },
                    label = { Text("Overview", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyanDark.copy(alpha = 0.3f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    ),
                    modifier = Modifier.testTag("nav_overview")
                )

                NavigationBarItem(
                    selected = currentScreen == HubScreen.TASKS || currentScreen == HubScreen.TASK_DETAIL,
                    onClick = { viewModel.navigateTo(HubScreen.TASKS) },
                    icon = { Icon(Icons.Default.List, contentDescription = "Tasks") },
                    label = { Text("Tasks", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyanDark.copy(alpha = 0.3f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    ),
                    modifier = Modifier.testTag("nav_tasks")
                )

                NavigationBarItem(
                    selected = currentScreen == HubScreen.TOOLS,
                    onClick = { viewModel.navigateTo(HubScreen.TOOLS) },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Tools") },
                    label = { Text("Tools", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyanDark.copy(alpha = 0.3f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    ),
                    modifier = Modifier.testTag("nav_tools")
                )

                NavigationBarItem(
                    selected = currentScreen == HubScreen.PERMISSIONS,
                    onClick = { viewModel.navigateTo(HubScreen.PERMISSIONS) },
                    icon = {
                        BadgedBox(badge = {
                            if (permissions.isNotEmpty()) {
                                Badge(containerColor = AlertAmber) {
                                    Text(permissions.size.toString(), color = VoidDark, fontSize = 9.sp)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Security, contentDescription = "Approvals")
                        }
                    },
                    label = { Text("Approvals", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyanDark.copy(alpha = 0.3f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    ),
                    modifier = Modifier.testTag("nav_approvals")
                )

                NavigationBarItem(
                    selected = currentScreen == HubScreen.LOGS,
                    onClick = { viewModel.navigateTo(HubScreen.LOGS) },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Logs") },
                    label = { Text("Logs", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyanDark.copy(alpha = 0.3f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    ),
                    modifier = Modifier.testTag("nav_logs")
                )

                NavigationBarItem(
                    selected = currentScreen == HubScreen.SETTINGS,
                    onClick = { viewModel.navigateTo(HubScreen.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyanDark.copy(alpha = 0.3f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                HubScreen.OVERVIEW -> OverviewScreen(viewModel)
                HubScreen.TASKS -> TasksScreen(viewModel)
                HubScreen.TASK_DETAIL -> TaskDetailScreen(viewModel)
                HubScreen.TOOLS -> ToolsScreen(viewModel)
                HubScreen.PERMISSIONS -> PermissionsScreen(viewModel)
                HubScreen.KNOWLEDGE -> KnowledgeScreen(viewModel)
                HubScreen.LOGS -> LogsScreen(viewModel)
                HubScreen.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}
