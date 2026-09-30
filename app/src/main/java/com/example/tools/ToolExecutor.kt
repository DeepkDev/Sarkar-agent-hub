package com.example.tools

import com.example.contracts.*
import com.example.core.EvaluationResult
import com.example.core.PolicyEngine
import com.example.core.PolicyEvaluationResult
import com.example.core.TaskContext
import com.example.core.toToolRequest
import com.example.core.toTaskContext
import kotlinx.coroutines.withTimeout

/**
 * Execution engine for [Tool]s that enforces [PolicyEngine] clearance rules,
 * parameter validation, timeout controls, and prompt-injection data boundary wrapping.
 */
class ToolExecutor(
    private val registry: ToolRegistry,
    val policyEngine: PolicyEngine = PolicyEngine()
) {

    /**
     * Executes a tool identified by [toolId] with default context while enforcing [PolicyEngine] evaluation.
     */
    suspend fun execute(
        toolId: String,
        arguments: Map<String, Any?>,
        timeoutMs: Long = 10_000L
    ): ToolResult {
        return execute(
            toolId = toolId,
            arguments = arguments,
            context = TaskContext(),
            timeoutMs = timeoutMs
        )
    }

    /**
     * Executes a tool with an explicit [TaskContext] (including origin, task ID, and granted permissions).
     */
    suspend fun execute(
        toolId: String,
        arguments: Map<String, Any?>,
        context: TaskContext,
        timeoutMs: Long = 10_000L
    ): ToolResult {
        val tool = registry.getTool(toolId)
            ?: return ToolResult.notConfigured(
                toolId = toolId,
                message = "Tool '$toolId' is not registered in ToolRegistry"
            )

        return executeToolInstance(tool, arguments, context, timeoutMs)
    }

    /**
     * Executes a tool for an active [AgentTask], extracting origin and approved operator clearances.
     */
    suspend fun execute(
        toolId: String,
        arguments: Map<String, Any?>,
        task: AgentTask,
        timeoutMs: Long = task.budgetConfig.perToolTimeoutMs,
        policyConfig: PolicyConfig = policyEngine.config
    ): ToolResult {
        val granted = task.permissionEvents
            .filter { it.decision == "APPROVED" }
            .map { it.toolId }
            .toSet()

        val context = TaskContext(
            taskId = task.taskId,
            origin = task.origin,
            grantedPermissions = granted
        )

        val effectiveEngine = if (policyConfig != policyEngine.config) {
            PolicyEngine(config = policyConfig)
        } else {
            policyEngine
        }

        val tool = registry.getTool(toolId)
            ?: return ToolResult.notConfigured(
                toolId = toolId,
                message = "Tool '$toolId' is not registered in ToolRegistry"
            )

        return executeToolInstance(tool, arguments, context, timeoutMs, effectiveEngine)
    }

    /**
     * Direct execution of a [Tool] instance with policy clearance enforcement.
     */
    suspend fun execute(
        tool: Tool,
        arguments: Map<String, Any?>,
        context: TaskContext = TaskContext(),
        timeoutMs: Long = 10_000L
    ): ToolResult {
        return executeToolInstance(tool, arguments, context, timeoutMs)
    }

    /**
     * Evaluates policy clearance for a tool call without executing it.
     */
    fun checkPolicy(
        toolId: String,
        arguments: Map<String, Any?> = emptyMap(),
        context: TaskContext = TaskContext()
    ): PolicyEvaluationResult? {
        val tool = registry.getTool(toolId) ?: return null
        return policyEngine.evaluate(tool.manifest.toToolRequest(arguments), context)
    }

    /**
     * Internal execution pipeline implementing:
     * 1. Enabled / disabled checks
     * 2. PolicyEngine clearance evaluation (ALLOW / REQUIRE_CONFIRMATION / DENY)
     * 3. Parameter validation
     * 4. Timeout enforcement
     * 5. Data boundary wrapping (anti-prompt injection)
     */
    private suspend fun executeToolInstance(
        tool: Tool,
        arguments: Map<String, Any?>,
        context: TaskContext,
        timeoutMs: Long,
        engine: PolicyEngine = policyEngine
    ): ToolResult {
        val manifest = tool.manifest
        val toolId = manifest.id

        // 1. Check Tool Status & Enablement
        if (!manifest.enabled || manifest.status == CapabilityStatus.DISABLED) {
            return ToolResult(
                toolId = toolId,
                success = false,
                data = "Tool '$toolId' is currently DISABLED",
                executionTimeMs = 0L,
                status = CapabilityStatus.DISABLED
            )
        }

        // 2. ENFORCE POLICY ENGINE EVALUATION
        val toolRequest = manifest.toToolRequest(arguments)
        val policyEvaluation = engine.evaluate(toolRequest, context)

        when (policyEvaluation.result) {
            EvaluationResult.DENY -> {
                return ToolResult(
                    toolId = toolId,
                    success = false,
                    data = "Execution blocked by PolicyEngine: ${policyEvaluation.reason}",
                    executionTimeMs = 0L,
                    status = CapabilityStatus.DISABLED
                )
            }

            EvaluationResult.REQUIRE_CONFIRMATION -> {
                // If not explicitly approved by operator in grantedPermissions, reject execution
                if (!context.grantedPermissions.contains(toolId)) {
                    return ToolResult(
                        toolId = toolId,
                        success = false,
                        data = "PolicyEngine clearance required: ${policyEvaluation.reason}",
                        executionTimeMs = 0L,
                        status = CapabilityStatus.IMPLEMENTED
                    )
                }
            }

            EvaluationResult.ALLOW -> {
                // Policy cleared; proceed to execution
            }
        }

        // 3. Validate Required Arguments
        for (param in manifest.inputParameters) {
            if (param.required && (!arguments.containsKey(param.name) || arguments[param.name] == null)) {
                return ToolResult(
                    toolId = toolId,
                    success = false,
                    data = "Missing required argument '${param.name}' for tool '$toolId'",
                    executionTimeMs = 0L,
                    status = manifest.status
                )
            }
        }

        // 4. Execute with Timeout & Data Boundary Wrapping
        val effectiveTimeout = timeoutMs.coerceAtLeast(500L).coerceAtMost(manifest.timeoutMs)
        val startTime = System.currentTimeMillis()

        return try {
            withTimeout(effectiveTimeout) {
                val rawResult = tool.execute(arguments)
                val duration = System.currentTimeMillis() - startTime
                val securedData = wrapDataBoundary(rawResult.data)
                rawResult.copy(data = securedData, executionTimeMs = duration)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ToolResult(
                toolId = toolId,
                success = false,
                data = "Tool execution timed out after ${effectiveTimeout}ms",
                executionTimeMs = effectiveTimeout,
                status = manifest.status
            )
        } catch (e: Exception) {
            ToolResult(
                toolId = toolId,
                success = false,
                data = "Tool execution failed: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime,
                status = manifest.status
            )
        }
    }

    private fun wrapDataBoundary(raw: String): String {
        return "<tool_data_boundary>\n$raw\n</tool_data_boundary>"
    }
}
