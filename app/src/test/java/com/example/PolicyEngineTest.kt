package com.example

import com.example.contracts.*
import com.example.core.EvaluationResult
import com.example.core.PolicyEngine
import com.example.core.TaskContext
import com.example.core.ToolRequest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PolicyEngineTest {

    private lateinit var policyEngine: PolicyEngine

    @Before
    fun setup() {
        policyEngine = PolicyEngine()
    }

    @Test
    fun testAllowDecisionForSafeTool() {
        val request = ToolRequest(
            toolId = "read_weather",
            toolName = "Weather Reader",
            permissionLevel = PermissionLevel.SAFE,
            sideEffects = SideEffectType.NONE
        )
        val context = TaskContext(
            taskId = "task-001",
            origin = OriginSource.WEB
        )

        val result = policyEngine.evaluate(request, context)

        assertEquals(EvaluationResult.ALLOW, result.result)
        assertTrue(result.isAllowed)
        assertFalse(result.isConfirmationRequired)
        assertFalse(result.isDenied)
        assertTrue(result.reason.contains("SAFE"))

        // Direct enum check
        assertEquals(EvaluationResult.ALLOW, policyEngine.checkPermission(request, context))
    }

    @Test
    fun testRequireConfirmationForConfirmationRequiredTool() {
        val request = ToolRequest(
            toolId = "send_email",
            toolName = "Email Sender",
            permissionLevel = PermissionLevel.CONFIRMATION_REQUIRED,
            sideEffects = SideEffectType.EXTERNAL
        )
        val context = TaskContext(
            taskId = "task-002",
            origin = OriginSource.WEB
        )

        val result = policyEngine.evaluate(request, context)

        assertEquals(EvaluationResult.REQUIRE_CONFIRMATION, result.result)
        assertTrue(result.isConfirmationRequired)
        assertNotNull(result.permissionId)
        assertTrue(result.permissionId!!.startsWith("perm-"))
        assertNotNull(result.expiresAt)
        assertTrue(result.expiresAt!! > System.currentTimeMillis())
        assertTrue(result.reason.contains("explicit operator approval"))

        // Direct enum check
        assertEquals(EvaluationResult.REQUIRE_CONFIRMATION, policyEngine.checkPermission(request, context))
    }

    @Test
    fun testDenyDecisionForRestrictedTool() {
        val request = ToolRequest(
            toolId = "format_disk",
            toolName = "Disk Formatter",
            permissionLevel = PermissionLevel.RESTRICTED,
            sideEffects = SideEffectType.WRITE
        )
        val context = TaskContext(
            taskId = "task-003",
            origin = OriginSource.WEB
        )

        val result = policyEngine.evaluate(request, context)

        assertEquals(EvaluationResult.DENY, result.result)
        assertTrue(result.isDenied)
        assertTrue(result.reason.contains("RESTRICTED"))

        // Direct enum check
        assertEquals(EvaluationResult.DENY, policyEngine.checkPermission(request, context))
    }

    @Test
    fun testEmergencyKillSwitchOverridesEverythingToDeny() {
        val engineWithKillSwitch = PolicyEngine(
            config = PolicyConfig(killSwitchEngaged = true)
        )

        val safeRequest = ToolRequest(
            toolId = "read_clock",
            permissionLevel = PermissionLevel.PUBLIC,
            sideEffects = SideEffectType.NONE
        )
        val context = TaskContext(taskId = "task-004")

        val result = engineWithKillSwitch.evaluate(safeRequest, context)

        assertEquals(EvaluationResult.DENY, result.result)
        assertTrue(result.reason.contains("Emergency Global Kill Switch"))
    }

    @Test
    fun testDisabledToolDeniesExecution() {
        val disabledRequest = ToolRequest(
            toolId = "read_news",
            permissionLevel = PermissionLevel.SAFE,
            enabled = false
        )
        val context = TaskContext(taskId = "task-005")

        val result = policyEngine.evaluate(disabledRequest, context)

        assertEquals(EvaluationResult.DENY, result.result)
        assertTrue(result.reason.contains("disabled"))
    }

    @Test
    fun testPreviouslyGrantedPermissionAllowsToolWithoutReConfirmation() {
        val confirmationTool = ToolRequest(
            toolId = "delete_file",
            permissionLevel = PermissionLevel.CONFIRMATION_REQUIRED,
            sideEffects = SideEffectType.WRITE
        )
        // Context with delete_file pre-approved
        val context = TaskContext(
            taskId = "task-006",
            grantedPermissions = setOf("delete_file")
        )

        val result = policyEngine.evaluate(confirmationTool, context)

        assertEquals(EvaluationResult.ALLOW, result.result)
        assertTrue(result.reason.contains("explicitly approved"))
    }

    @Test
    fun testDefinedPermissionsMapOverridesDefaultLevel() {
        val engineWithOverrides = PolicyEngine(
            definedPermissions = mapOf(
                "custom_tool" to PermissionLevel.CONFIRMATION_REQUIRED
            )
        )

        val toolRequest = ToolRequest(
            toolId = "custom_tool",
            permissionLevel = PermissionLevel.SAFE // Manifest says SAFE, but policy overrides to CONFIRMATION_REQUIRED
        )
        val context = TaskContext(taskId = "task-007")

        val result = engineWithOverrides.evaluate(toolRequest, context)

        assertEquals(EvaluationResult.REQUIRE_CONFIRMATION, result.result)
    }

    @Test
    fun testOriginPolicyRulesEnforcement() {
        val customConfig = PolicyConfig(
            defaultRules = listOf(
                PolicyRule(
                    origin = OriginSource.SARKAR,
                    toolId = "network_request",
                    forceDecision = PolicyDecisionType.DENY,
                    description = "SARKAR origin cannot access network"
                )
            )
        )
        val engine = PolicyEngine(config = customConfig)

        val request = ToolRequest(
            toolId = "network_request",
            permissionLevel = PermissionLevel.SAFE
        )
        val sarkatContext = TaskContext(taskId = "task-008", origin = OriginSource.SARKAR)
        val userContext = TaskContext(taskId = "task-009", origin = OriginSource.WEB)

        assertEquals(EvaluationResult.DENY, engine.checkPermission(request, sarkatContext))
        assertEquals(EvaluationResult.ALLOW, engine.checkPermission(request, userContext))
    }

    @Test
    fun testProgrammaticCustomRuleHook() {
        val engine = PolicyEngine(
            customRule = { req, ctx ->
                if (req.toolId == "special_override" && ctx.userRole == "ADMIN") {
                    EvaluationResult.ALLOW
                } else if (req.toolId == "special_override") {
                    EvaluationResult.DENY
                } else {
                    null
                }
            }
        )

        val request = ToolRequest(toolId = "special_override", permissionLevel = PermissionLevel.RESTRICTED)
        val adminContext = TaskContext(taskId = "admin-task", userRole = "ADMIN")
        val userContext = TaskContext(taskId = "user-task", userRole = "USER")

        assertEquals(EvaluationResult.ALLOW, engine.checkPermission(request, adminContext))
        assertEquals(EvaluationResult.DENY, engine.checkPermission(request, userContext))
    }
}
