package com.example

import com.example.contracts.*
import com.example.core.EvaluationResult
import com.example.core.PolicyEngine
import com.example.core.TaskContext
import com.example.tools.CalculatorTool
import com.example.tools.DeleteDataTool
import com.example.tools.SendEmailTool
import com.example.tools.ToolExecutor
import com.example.tools.ToolRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ToolExecutorTest {

    private lateinit var registry: ToolRegistry
    private lateinit var policyEngine: PolicyEngine
    private lateinit var executor: ToolExecutor

    @Before
    fun setup() {
        registry = ToolRegistry()
        policyEngine = PolicyEngine()
        executor = ToolExecutor(registry, policyEngine)
    }

    @Test
    fun testCustomToolInterfaceAndManifest() = runTest {
        // Implement custom Tool interface
        val customManifest = ToolManifest(
            id = "custom_greeter",
            name = "Greeter Tool",
            version = "1.0.0",
            description = "Returns a personalized greeting",
            category = "Utilities",
            inputParameters = listOf(
                ToolParameter(name = "name", type = "string", description = "Target name", required = true)
            ),
            permissionLevel = PermissionLevel.PUBLIC,
            sideEffects = SideEffectType.NONE
        )

        val customTool = object : Tool {
            override val manifest = customManifest
            override suspend fun execute(args: Map<String, Any?>): ToolResult {
                val name = args["name"] as? String ?: "World"
                return ToolResult(
                    toolId = manifest.id,
                    success = true,
                    data = "Hello, $name!",
                    executionTimeMs = 5L
                )
            }
        }

        registry.register(customTool)

        val result = executor.execute("custom_greeter", mapOf("name" to "Alice"))
        assertTrue(result.success)
        assertTrue(result.data.contains("Hello, Alice!"))
        assertTrue(result.data.contains("<tool_data_boundary>"))
    }

    @Test
    fun testSafeToolAllowedAndExecuted() = runTest {
        registry.register(CalculatorTool())

        val result = executor.execute("calculator", mapOf("expression" to "10 + 5 * 2"))
        assertTrue(result.success)
        assertTrue(result.data.contains("20"))
        assertTrue(result.data.contains("<tool_data_boundary>"))
    }

    @Test
    fun testRestrictedToolBlockedByPolicyEngine() = runTest {
        // DeleteDataTool has PermissionLevel.RESTRICTED
        registry.register(DeleteDataTool())

        val result = executor.execute("delete_data", mapOf("target" to "database_backup"))
        assertFalse(result.success)
        assertTrue(result.data.contains("Blocked by PolicyEngine"))
        assertTrue(result.data.contains("RESTRICTED"))
        assertEquals(CapabilityStatus.DISABLED, result.status)
    }

    @Test
    fun testConfirmationRequiredToolBlockedWithoutClearance() = runTest {
        // SendEmailTool has PermissionLevel.CONFIRMATION_REQUIRED
        registry.register(SendEmailTool())

        val contextWithoutApproval = TaskContext(taskId = "task-email-1", grantedPermissions = emptySet())
        val result = executor.execute("send_email", mapOf("to" to "admin@example.com", "subject" to "Alert", "body" to "Hello"), contextWithoutApproval)

        assertFalse(result.success)
        assertTrue(result.data.contains("PolicyEngine clearance required"))
    }

    @Test
    fun testConfirmationRequiredToolAllowedWithGrantedPermission() = runTest {
        registry.register(SendEmailTool())

        val contextWithApproval = TaskContext(
            taskId = "task-email-2",
            grantedPermissions = setOf("send_email")
        )
        val result = executor.execute(
            toolId = "send_email",
            arguments = mapOf("to" to "ops@example.com", "subject" to "Status", "body" to "Server healthy"),
            context = contextWithApproval
        )

        assertTrue(result.success)
        assertTrue(result.data.contains("Email sent successfully"))
        assertTrue(result.data.contains("<tool_data_boundary>"))
    }

    @Test
    fun testGlobalKillSwitchHaltsAllTools() = runTest {
        registry.register(CalculatorTool())

        // Engage emergency kill switch
        policyEngine.config = PolicyConfig(killSwitchEngaged = true)

        val result = executor.execute("calculator", mapOf("expression" to "2 + 2"))
        assertFalse(result.success)
        assertTrue(result.data.contains("Blocked by PolicyEngine"))
        assertTrue(result.data.contains("Kill Switch"))
    }

    @Test
    fun testMissingRequiredArgumentFails() = runTest {
        registry.register(CalculatorTool())

        // Missing required 'expression' parameter
        val result = executor.execute("calculator", emptyMap())
        assertFalse(result.success)
        assertTrue(result.data.contains("Missing required argument 'expression'"))
    }

    @Test
    fun testUnregisteredToolReturnsNotConfigured() = runTest {
        val result = executor.execute("unknown_missing_tool", emptyMap())
        assertFalse(result.success)
        assertEquals(CapabilityStatus.NOT_CONFIGURED, result.status)
        assertTrue(result.data.contains("is not registered"))
    }

    @Test
    fun testExecuteWithAgentTaskOriginAndPermissions() = runTest {
        registry.register(SendEmailTool())

        val approvedTask = AgentTask(
            taskId = "task-99",
            request = "Email notification",
            origin = OriginSource.WEB,
            permissionEvents = listOf(
                PermissionEventRecord(
                    permissionId = "perm-99",
                    toolId = "send_email",
                    decision = "APPROVED"
                )
            )
        )

        val result = executor.execute(
            toolId = "send_email",
            arguments = mapOf("to" to "client@domain.com", "subject" to "Welcome", "body" to "Welcome aboard"),
            task = approvedTask
        )

        assertTrue(result.success)
        assertTrue(result.data.contains("Email sent successfully"))
    }
}
