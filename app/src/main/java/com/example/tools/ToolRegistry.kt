package com.example.tools

import com.example.contracts.*
import com.example.knowledge.KnowledgeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Detailed validation issue indicating the schema path, error code, and failure description.
 */
data class ValidationIssue(
    val path: String,
    val message: String,
    val code: String = "custom_error"
)

/**
 * Result of metadata or argument validation using Zod-equivalent declarative schema rules.
 */
sealed class ToolValidationResult {
    object Valid : ToolValidationResult()
    data class Invalid(val issues: List<ValidationIssue>) : ToolValidationResult() {
        val errorMessage: String get() = issues.joinToString("; ") { "[${it.path}]: ${it.message}" }
    }

    val isValid: Boolean get() = this is Valid
}

/**
 * Zod-equivalent schema validator for tool metadata and input arguments.
 */
object ToolMetadataValidator {
    private val ID_REGEX = Regex("^[a-zA-Z0-9_-]{2,64}$")
    private val SEMVER_REGEX = Regex("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$")
    val ALLOWED_PARAM_TYPES = setOf("string", "number", "boolean", "object", "array")

    /**
     * Validates a [ToolManifest] against strict declarative schema rules:
     * - id: non-blank, alphanumeric with underscores/hyphens, 2-64 chars
     * - name: non-blank, 2-100 characters
     * - version: semver compliance (e.g. 1.0.0)
     * - description: >= 5 characters, <= 2000 characters
     * - category: non-blank
     * - timeoutMs: 1..300,000 ms
     * - rateLimitPerMinute: 1..10,000
     * - inputParameters: valid names, unique within tool, valid types
     */
    fun validate(manifest: ToolManifest): ToolValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // 1. Tool ID validation
        if (manifest.id.isBlank()) {
            issues.add(ValidationIssue("id", "Tool ID cannot be empty or blank", "too_small"))
        } else if (!ID_REGEX.matches(manifest.id)) {
            issues.add(
                ValidationIssue(
                    "id",
                    "Tool ID '${manifest.id}' must be alphanumeric with underscores or hyphens (2-64 characters)",
                    "invalid_string"
                )
            )
        }

        // 2. Tool Name validation
        if (manifest.name.isBlank()) {
            issues.add(ValidationIssue("name", "Tool name cannot be empty or blank", "too_small"))
        } else if (manifest.name.trim().length < 2 || manifest.name.trim().length > 100) {
            issues.add(ValidationIssue("name", "Tool name length must be between 2 and 100 characters", "invalid_length"))
        }

        // 3. Semantic Version validation
        if (manifest.version.isNotBlank() && !SEMVER_REGEX.matches(manifest.version)) {
            issues.add(
                ValidationIssue(
                    "version",
                    "Tool version '${manifest.version}' is not valid semver (e.g. '1.0.0')",
                    "invalid_format"
                )
            )
        }

        // 4. Description validation
        if (manifest.description.trim().length < 5) {
            issues.add(ValidationIssue("description", "Tool description must be at least 5 characters long", "too_small"))
        } else if (manifest.description.length > 2000) {
            issues.add(ValidationIssue("description", "Tool description exceeds 2000 characters", "too_big"))
        }

        // 5. Category validation
        if (manifest.category.isBlank()) {
            issues.add(ValidationIssue("category", "Tool category cannot be blank", "too_small"))
        }

        // 6. Timeout and Rate limits
        if (manifest.timeoutMs <= 0 || manifest.timeoutMs > 300_000L) {
            issues.add(ValidationIssue("timeoutMs", "Timeout must be between 1ms and 300,000ms", "invalid_range"))
        }
        if (manifest.rateLimitPerMinute <= 0 || manifest.rateLimitPerMinute > 10_000) {
            issues.add(ValidationIssue("rateLimitPerMinute", "Rate limit must be between 1 and 10,000 calls/min", "invalid_range"))
        }

        // 7. Input parameters validation
        val paramNames = mutableSetOf<String>()
        manifest.inputParameters.forEachIndexed { index, param ->
            val path = "inputParameters[$index]"
            if (param.name.isBlank()) {
                issues.add(ValidationIssue("$path.name", "Parameter name cannot be blank", "too_small"))
            } else if (!paramNames.add(param.name)) {
                issues.add(ValidationIssue("$path.name", "Duplicate parameter name '${param.name}' in tool manifest", "duplicate_parameter"))
            }

            val paramTypeLower = param.type.lowercase().trim()
            if (paramTypeLower !in ALLOWED_PARAM_TYPES) {
                issues.add(
                    ValidationIssue(
                        "$path.type",
                        "Invalid parameter type '${param.type}'. Allowed types: $ALLOWED_PARAM_TYPES",
                        "invalid_type"
                    )
                )
            }
        }

        return if (issues.isEmpty()) ToolValidationResult.Valid else ToolValidationResult.Invalid(issues)
    }

    /**
     * Zod-equivalent argument validator checking runtime arguments against a tool's parameter schema.
     */
    fun validateArguments(manifest: ToolManifest, args: Map<String, Any?>): ToolValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        for (param in manifest.inputParameters) {
            val value = args[param.name]
            if (value == null) {
                if (param.required && param.default == null) {
                    issues.add(
                        ValidationIssue(
                            path = param.name,
                            message = "Missing required argument '${param.name}'",
                            code = "missing_required_field"
                        )
                    )
                }
            } else {
                // Type verification
                val expected = param.type.lowercase().trim()
                val isValidType = when (expected) {
                    "string" -> value is String
                    "number" -> value is Number || (value is String && value.toDoubleOrNull() != null)
                    "boolean" -> value is Boolean || (value is String && (value.equals("true", true) || value.equals("false", true)))
                    "object" -> value is Map<*, *> || value is JSONObject
                    "array" -> value is List<*> || value is Array<*> || value is JSONArray
                    else -> true
                }

                if (!isValidType) {
                    issues.add(
                        ValidationIssue(
                            path = param.name,
                            message = "Expected type '$expected' for '${param.name}', but received '${value::class.simpleName}'",
                            code = "invalid_type"
                        )
                    )
                }
            }
        }

        return if (issues.isEmpty()) ToolValidationResult.Valid else ToolValidationResult.Invalid(issues)
    }
}

/**
 * Manages tool registration, metadata validation (Zod-equivalent logic),
 * and provides rich lookup functionality for AI Providers.
 */
class ToolRegistry(val knowledgeManager: KnowledgeManager = KnowledgeManager()) {
    private val toolMap = ConcurrentHashMap<String, BaseTool>()
    private val _tools = MutableStateFlow<List<ToolManifest>>(emptyList())
    val tools: StateFlow<List<ToolManifest>> = _tools.asStateFlow()

    init {
        // Register standard built-in tools
        register(CalculatorTool())
        register(DateTimeTool())
        register(TextProcessingTool())
        register(JsonProcessingTool())
        register(KnowledgeSearchTool(knowledgeManager))
        register(WebSearchTool())
        register(FileOperationTool())
        register(SendEmailTool())
        register(DeleteDataTool())
        register(McpToolAdapterStub())
    }

    /**
     * Registers a tool after performing Zod-equivalent metadata validation.
     *
     * @param tool The tool to register.
     * @param strict If true and validation fails, an [IllegalArgumentException] is thrown.
     * @return [ToolValidationResult] indicating validation success or issues.
     */
    fun register(tool: BaseTool, strict: Boolean = false): ToolValidationResult {
        val validation = ToolMetadataValidator.validate(tool.manifest)
        if (!validation.isValid && strict) {
            val invalid = validation as ToolValidationResult.Invalid
            throw IllegalArgumentException("Cannot register tool '${tool.manifest.id}': ${invalid.errorMessage}")
        }

        toolMap[tool.manifest.id] = tool
        refreshList()
        return validation
    }

    /**
     * Functional convenience registration overload taking a manifest and execution block.
     */
    fun register(
        manifest: ToolManifest,
        strict: Boolean = false,
        executor: suspend (Map<String, Any?>) -> ToolResult
    ): ToolValidationResult {
        val customTool = object : BaseTool {
            override val manifest: ToolManifest = manifest
            override suspend fun execute(args: Map<String, Any?>): ToolResult = executor(args)
        }
        return register(customTool, strict)
    }

    /**
     * Unregisters a tool by ID.
     */
    fun unregister(id: String): Boolean {
        val removed = toolMap.remove(id) != null
        if (removed) refreshList()
        return removed
    }

    /**
     * Checks if a tool ID is currently registered.
     */
    fun contains(id: String): Boolean = toolMap.containsKey(id)

    /**
     * Returns total number of registered tools.
     */
    fun size(): Int = toolMap.size

    /**
     * Retrieves an executable tool instance by ID.
     */
    fun getTool(id: String): BaseTool? = toolMap[id]

    /**
     * Retrieves a tool manifest by ID.
     */
    fun getManifest(id: String): ToolManifest? = toolMap[id]?.manifest

    /**
     * Retrieves all registered tool instances.
     */
    fun getAllTools(): List<BaseTool> = toolMap.values.toList()

    /**
     * Retrieves all tool manifests.
     */
    fun getAllManifests(): List<ToolManifest> = toolMap.values.map { it.manifest }

    /**
     * Retrieves all currently enabled tools available for AI Provider planning and execution.
     */
    fun getAvailableTools(): List<ToolManifest> =
        toolMap.values.map { it.manifest }.filter { it.enabled && it.status != CapabilityStatus.DISABLED }

    /**
     * Looks up tool manifests by category.
     */
    fun findToolsByCategory(category: String): List<ToolManifest> =
        toolMap.values.map { it.manifest }.filter { it.category.equals(category, ignoreCase = true) }

    /**
     * Looks up tool manifests matching a specific permission level.
     */
    fun findToolsByPermission(level: PermissionLevel): List<ToolManifest> =
        toolMap.values.map { it.manifest }.filter { it.permissionLevel == level }

    /**
     * Performs fuzzy keyword search across tool ID, name, description, and category.
     */
    fun searchTools(query: String): List<ToolManifest> {
        if (query.isBlank()) return getAllManifests()
        val terms = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        return toolMap.values.map { it.manifest }.filter { manifest ->
            val corpus = "${manifest.id} ${manifest.name} ${manifest.description} ${manifest.category}".lowercase()
            terms.all { term -> corpus.contains(term) }
        }
    }

    /**
     * Formats available tools into a structured Markdown string designed for AI Provider system prompts.
     */
    fun formatToolsForPrompt(onlyEnabled: Boolean = true): String {
        val list = if (onlyEnabled) getAvailableTools() else getAllManifests()
        if (list.isEmpty()) return "No tools available."

        return buildString {
            append("Available Tools (${list.size}):\n")
            list.forEach { t ->
                append("### `${t.id}`: ${t.name}\n")
                append("- **Description**: ${t.description}\n")
                append("- **Category**: ${t.category}\n")
                append("- **Permission**: ${t.permissionLevel.name}\n")
                append("- **Side Effects**: ${t.sideEffects.name}\n")
                if (t.inputParameters.isNotEmpty()) {
                    append("- **Parameters**:\n")
                    t.inputParameters.forEach { p ->
                        val req = if (p.required) "required" else "optional"
                        val def = if (p.default != null) ", default: ${p.default}" else ""
                        append("  - `${p.name}` (${p.type}, $req$def): ${p.description}\n")
                    }
                } else {
                    append("- **Parameters**: None\n")
                }
                append("\n")
            }
        }.trimEnd()
    }

    /**
     * Generates standard OpenAPI/Gemini function declaration schemas for AI Providers.
     */
    fun getToolDeclarationsForAi(onlyEnabled: Boolean = true): List<Map<String, Any?>> {
        val list = if (onlyEnabled) getAvailableTools() else getAllManifests()

        return list.map { tool ->
            val properties = mutableMapOf<String, Any?>()
            val required = mutableListOf<String>()

            tool.inputParameters.forEach { p ->
                val prop = mutableMapOf<String, Any?>(
                    "type" to p.type.uppercase(),
                    "description" to p.description
                )
                if (p.default != null) {
                    prop["default"] = p.default
                }
                properties[p.name] = prop
                if (p.required) {
                    required.add(p.name)
                }
            }

            mapOf(
                "name" to tool.id,
                "description" to tool.description,
                "parameters" to mapOf(
                    "type" to "OBJECT",
                    "properties" to properties,
                    "required" to required
                )
            )
        }
    }

    /**
     * Validates proposed tool execution arguments against the tool's parameter schema.
     */
    fun validateArguments(toolId: String, args: Map<String, Any?>): ToolValidationResult {
        val manifest = getManifest(toolId)
            ?: return ToolValidationResult.Invalid(
                listOf(ValidationIssue(path = "toolId", message = "Tool '$toolId' is not registered", code = "not_found"))
            )
        return ToolMetadataValidator.validateArguments(manifest, args)
    }

    /**
     * Enables or disables a tool.
     */
    fun setToolEnabled(id: String, enabled: Boolean) {
        val tool = toolMap[id] ?: return
        val currentStatus = if (enabled) {
            if (tool.manifest.status == CapabilityStatus.DISABLED) CapabilityStatus.IMPLEMENTED else tool.manifest.status
        } else {
            CapabilityStatus.DISABLED
        }
        val updated = object : BaseTool {
            override val manifest: ToolManifest = tool.manifest.copy(enabled = enabled, status = currentStatus)
            override suspend fun execute(args: Map<String, Any?>) = tool.execute(args)
        }
        toolMap[id] = updated
        refreshList()
    }

    /**
     * Checks if a tool is enabled.
     */
    fun isToolEnabled(id: String): Boolean = toolMap[id]?.manifest?.enabled == true

    /**
     * Clears all registered tools.
     */
    fun clear() {
        toolMap.clear()
        refreshList()
    }

    private fun refreshList() {
        _tools.value = toolMap.values.map { it.manifest }
    }
}
