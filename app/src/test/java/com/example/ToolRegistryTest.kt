package com.example

import com.example.contracts.*
import com.example.tools.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    @Before
    fun setup() {
        registry = ToolRegistry()
    }

    @Test
    fun testDefaultToolsRegistered() {
        assertTrue(registry.size() >= 9)
        assertTrue(registry.contains("calculator"))
        assertTrue(registry.contains("datetime"))
        assertTrue(registry.contains("text_processing"))
        assertTrue(registry.contains("json_processing"))
        assertTrue(registry.contains("web_search"))
        assertTrue(registry.contains("file_operation"))
        assertTrue(registry.contains("send_email"))
        assertTrue(registry.contains("delete_data"))

        val calc = registry.getTool("calculator")
        assertNotNull(calc)
        assertEquals("calculator", calc!!.manifest.id)
    }

    @Test
    fun testMetadataValidatorPassesValidManifest() {
        val validManifest = ToolManifest(
            id = "custom_tool",
            name = "Custom Tool",
            version = "1.2.0",
            description = "A perfectly valid tool description",
            category = "Utility",
            inputParameters = listOf(
                ToolParameter(name = "query", type = "string", description = "Query string", required = true),
                ToolParameter(name = "limit", type = "number", description = "Max results", required = false, default = "10")
            ),
            permissionLevel = PermissionLevel.SAFE,
            sideEffects = SideEffectType.NONE,
            status = CapabilityStatus.IMPLEMENTED
        )

        val result = ToolMetadataValidator.validate(validManifest)
        assertTrue(result.isValid)
        assertTrue(result is ToolValidationResult.Valid)
    }

    @Test
    fun testMetadataValidatorCatchesInvalidManifest() {
        // Invalid ID with space, invalid semver, duplicate parameter, invalid type
        val invalidManifest = ToolManifest(
            id = "invalid tool id!",
            name = "",
            version = "not_semver",
            description = "tiny",
            category = "",
            inputParameters = listOf(
                ToolParameter(name = "dup", type = "string", description = "First param"),
                ToolParameter(name = "dup", type = "unsupported_type", description = "Second param")
            ),
            permissionLevel = PermissionLevel.SAFE,
            sideEffects = SideEffectType.NONE,
            status = CapabilityStatus.IMPLEMENTED
        )

        val result = ToolMetadataValidator.validate(invalidManifest)
        assertFalse(result.isValid)
        assertTrue(result is ToolValidationResult.Invalid)

        val invalid = result as ToolValidationResult.Invalid
        assertTrue(invalid.issues.any { it.path == "id" })
        assertTrue(invalid.issues.any { it.path == "name" })
        assertTrue(invalid.issues.any { it.path == "version" })
        assertTrue(invalid.issues.any { it.path == "description" })
        assertTrue(invalid.issues.any { it.path == "category" })
        assertTrue(invalid.issues.any { it.code == "duplicate_parameter" })
        assertTrue(invalid.issues.any { it.code == "invalid_type" })
    }

    @Test
    fun testStrictRegistrationThrowsOnInvalidTool() {
        val invalidManifest = ToolManifest(
            id = "bad id",
            name = "Bad",
            description = "short",
            category = "Test",
            inputParameters = emptyList(),
            permissionLevel = PermissionLevel.SAFE,
            sideEffects = SideEffectType.NONE,
            status = CapabilityStatus.IMPLEMENTED
        )

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(invalidManifest, strict = true) {
                ToolResult(toolId = "bad id", success = true, data = "ok", executionTimeMs = 1L)
            }
        }
    }

    @Test
    fun testArgumentValidationPassesAndFailsCorrectly() {
        val manifest = ToolManifest(
            id = "email_sender",
            name = "Email Sender",
            description = "Dispatches email messages",
            category = "Communication",
            inputParameters = listOf(
                ToolParameter(name = "recipient", type = "string", description = "Recipient email", required = true),
                ToolParameter(name = "priority", type = "number", description = "Priority number", required = false, default = "1")
            ),
            permissionLevel = PermissionLevel.CONFIRMATION_REQUIRED,
            sideEffects = SideEffectType.EXTERNAL,
            status = CapabilityStatus.IMPLEMENTED
        )

        registry.register(manifest) {
            ToolResult(toolId = "email_sender", success = true, data = "Sent", executionTimeMs = 10L)
        }

        // Missing required 'recipient'
        val missingReq = registry.validateArguments("email_sender", mapOf("priority" to 1))
        assertFalse(missingReq.isValid)
        val missingIssues = (missingReq as ToolValidationResult.Invalid).issues
        assertEquals("recipient", missingIssues[0].path)
        assertEquals("missing_required_field", missingIssues[0].code)

        // Valid arguments
        val validArgs = registry.validateArguments("email_sender", mapOf("recipient" to "alice@example.com", "priority" to 2))
        assertTrue(validArgs.isValid)

        // Invalid type for priority
        val wrongTypeArgs = registry.validateArguments("email_sender", mapOf("recipient" to "alice@example.com", "priority" to true))
        assertFalse(wrongTypeArgs.isValid)
    }

    @Test
    fun testAiProviderLookupFunctionality() {
        // 1. Available tools filtering
        val initialAvailable = registry.getAvailableTools()
        assertTrue(initialAvailable.any { it.id == "calculator" })

        registry.setToolEnabled("calculator", false)
        val afterDisable = registry.getAvailableTools()
        assertFalse(afterDisable.any { it.id == "calculator" })
        assertFalse(registry.isToolEnabled("calculator"))

        registry.setToolEnabled("calculator", true)
        assertTrue(registry.isToolEnabled("calculator"))

        // 2. Lookup by category
        val mathTools = registry.findToolsByCategory("Math & Logic")
        assertTrue(mathTools.any { it.id == "calculator" })

        // 3. Lookup by permission level
        val safeTools = registry.findToolsByPermission(PermissionLevel.SAFE)
        assertTrue(safeTools.isNotEmpty())
        assertTrue(safeTools.all { it.permissionLevel == PermissionLevel.SAFE })

        // 4. Fuzzy search
        val searchCalc = registry.searchTools("arithmetic expressions")
        assertTrue(searchCalc.any { it.id == "calculator" })

        // 5. Prompt formatted output for AI models
        val promptDoc = registry.formatToolsForPrompt()
        assertTrue(promptDoc.contains("Available Tools"))
        assertTrue(promptDoc.contains("calculator"))
        assertTrue(promptDoc.contains("Parameters"))

        // 6. Tool declarations for AI function calling
        val declarations = registry.getToolDeclarationsForAi()
        assertTrue(declarations.isNotEmpty())
        val calcDecl = declarations.find { it["name"] == "calculator" }
        assertNotNull(calcDecl)
        val params = calcDecl!!["parameters"] as Map<*, *>
        assertEquals("OBJECT", params["type"])
    }

    @Test
    fun testUnregisterTool() {
        assertTrue(registry.contains("datetime"))
        val unregistered = registry.unregister("datetime")
        assertTrue(unregistered)
        assertFalse(registry.contains("datetime"))
        assertNull(registry.getTool("datetime"))
    }
}
