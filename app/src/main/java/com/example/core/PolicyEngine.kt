package com.example.core

import com.example.contracts.*
import java.util.UUID

/**
 * Result of evaluating a tool execution request against policy rules.
 */
enum class EvaluationResult {
    ALLOW,
    REQUIRE_CONFIRMATION,
    DENY;

    fun toPolicyDecisionType(): PolicyDecisionType = when (this) {
        ALLOW -> PolicyDecisionType.ALLOW
        REQUIRE_CONFIRMATION -> PolicyDecisionType.REQUIRE_CONFIRMATION
        DENY -> PolicyDecisionType.DENY
    }

    companion object {
        fun fromPolicyDecisionType(type: PolicyDecisionType): EvaluationResult = when (type) {
            PolicyDecisionType.ALLOW -> ALLOW
            PolicyDecisionType.REQUIRE_CONFIRMATION -> REQUIRE_CONFIRMATION
            PolicyDecisionType.DENY -> DENY
        }
    }
}

/**
 * Represents a tool execution request subjected to policy evaluation.
 */
data class ToolRequest(
    val toolId: String,
    val toolName: String = toolId,
    val permissionLevel: PermissionLevel = PermissionLevel.SAFE,
    val sideEffects: SideEffectType = SideEffectType.NONE,
    val argumentsJson: String = "{}",
    val enabled: Boolean = true,
    val status: CapabilityStatus = CapabilityStatus.IMPLEMENTED
)

/**
 * Encapsulates the execution context of the invoking task.
 */
data class TaskContext(
    val taskId: String = "task-default",
    val origin: OriginSource = OriginSource.WEB,
    val dryRun: Boolean = false,
    val userRole: String = "USER",
    val traceId: String = "",
    val grantedPermissions: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Comprehensive result of policy evaluation with human-readable rationale
 * and permission metadata.
 */
data class PolicyEvaluationResult(
    val result: EvaluationResult,
    val reason: String,
    val permissionId: String? = null,
    val expiresAt: Long? = null
) {
    val isAllowed: Boolean get() = result == EvaluationResult.ALLOW
    val isConfirmationRequired: Boolean get() = result == EvaluationResult.REQUIRE_CONFIRMATION
    val isDenied: Boolean get() = result == EvaluationResult.DENY

    fun toPolicyDecision(): PolicyDecision = PolicyDecision(
        type = result.toPolicyDecisionType(),
        reason = reason,
        permissionId = permissionId,
        expiresAt = expiresAt
    )
}

/**
 * Deterministic PolicyEngine class evaluating tool execution requests
 * against security policies, permissions, side-effect classifications,
 * origin constraints, and emergency kill switches.
 */
class PolicyEngine(
    var config: PolicyConfig = PolicyConfig(),
    private val definedPermissions: Map<String, PermissionLevel> = emptyMap(),
    private val customRule: ((ToolRequest, TaskContext) -> EvaluationResult?)? = null
) {

    /**
     * Evaluates a [ToolRequest] in the context of [TaskContext] and returns
     * a [PolicyEvaluationResult] with the decision (ALLOW, REQUIRE_CONFIRMATION, DENY).
     */
    fun evaluate(
        request: ToolRequest,
        context: TaskContext
    ): PolicyEvaluationResult {
        // 1. Check Global Emergency Kill Switch
        if (config.killSwitchEngaged) {
            return PolicyEvaluationResult(
                result = EvaluationResult.DENY,
                reason = "Execution halted by Emergency Global Kill Switch"
            )
        }

        // 2. Check disabled or inactive tools
        if (!request.enabled || request.status == CapabilityStatus.DISABLED) {
            return PolicyEvaluationResult(
                result = EvaluationResult.DENY,
                reason = "Tool '${request.toolName}' is currently disabled"
            )
        }

        // 3. Check if permission was already granted previously for this task
        if (context.grantedPermissions.contains(request.toolId)) {
            return PolicyEvaluationResult(
                result = EvaluationResult.ALLOW,
                reason = "Tool '${request.toolName}' was explicitly approved by operator for task '${context.taskId}'"
            )
        }

        // 4. Evaluate Configured Policy Rules (Origin & Tool overrides)
        for (rule in config.defaultRules) {
            val matchesOrigin = rule.origin == null || rule.origin == context.origin
            val matchesTool = rule.toolId == null || rule.toolId.equals(request.toolId, ignoreCase = true)

            if (matchesOrigin && matchesTool && rule.forceDecision != null) {
                val evalResult = EvaluationResult.fromPolicyDecisionType(rule.forceDecision)
                return PolicyEvaluationResult(
                    result = evalResult,
                    reason = "Policy rule matched: ${rule.description}"
                )
            }
        }

        // 5. Evaluate Custom Rule Hook if registered
        val customDecision = customRule?.invoke(request, context)
        if (customDecision != null) {
            return PolicyEvaluationResult(
                result = customDecision,
                reason = "Custom programmatic policy rule applied"
            )
        }

        // 6. Check Untrusted Origin Constraints (e.g. SARKAR untrusted source)
        if (context.origin == OriginSource.SARKAR && request.sideEffects != SideEffectType.NONE) {
            return PolicyEvaluationResult(
                result = EvaluationResult.DENY,
                reason = "Untrusted origin '${context.origin.name}' is prohibited from executing side-effecting tools"
            )
        }

        // 7. Resolve Effective Permission Level (Explicit override in definedPermissions or Tool manifest)
        val effectivePermission = definedPermissions[request.toolId] ?: request.permissionLevel

        // 8. Evaluate Standard Defined Permission Level
        return when (effectivePermission) {
            PermissionLevel.PUBLIC, PermissionLevel.SAFE -> {
                PolicyEvaluationResult(
                    result = EvaluationResult.ALLOW,
                    reason = "Tool '${request.toolName}' has ${effectivePermission.name} clearance"
                )
            }

            PermissionLevel.CONFIRMATION_REQUIRED -> {
                val permissionId = "perm-${UUID.randomUUID().toString().take(8)}"
                val expiry = System.currentTimeMillis() + (10 * 60 * 1000L) // 10 minutes default
                val sideEffects = when (request.sideEffects) {
                    SideEffectType.WRITE -> "Modifies internal state/data"
                    SideEffectType.EXTERNAL -> "Communicates with external network services"
                    SideEffectType.READ -> "Accesses sensitive read context"
                    SideEffectType.NONE -> "No persistent side effects"
                }
                PolicyEvaluationResult(
                    result = EvaluationResult.REQUIRE_CONFIRMATION,
                    reason = "Tool '${request.toolName}' requires explicit operator approval before execution. Side effects: $sideEffects",
                    permissionId = permissionId,
                    expiresAt = expiry
                )
            }

            PermissionLevel.RESTRICTED -> {
                PolicyEvaluationResult(
                    result = EvaluationResult.DENY,
                    reason = "Tool '${request.toolName}' is RESTRICTED and blocked by default"
                )
            }
        }
    }

    /**
     * Quick check that directly returns the [EvaluationResult] enum (ALLOW, REQUIRE_CONFIRMATION, DENY).
     */
    fun checkPermission(
        request: ToolRequest,
        context: TaskContext
    ): EvaluationResult = evaluate(request, context).result

    /**
     * Backward-compatible evaluation function taking [ToolManifest] and [AgentTask].
     */
    fun evaluate(
        tool: ToolManifest,
        argsJson: String,
        task: AgentTask,
        policyConfig: PolicyConfig = this.config
    ): PolicyDecision {
        val request = tool.toToolRequest(argsJson)
        val context = task.toTaskContext()

        // Temporarily apply policyConfig if different
        val effectiveEngine = if (policyConfig != this.config) {
            PolicyEngine(config = policyConfig, definedPermissions = this.definedPermissions, customRule = this.customRule)
        } else {
            this
        }

        return effectiveEngine.evaluate(request, context).toPolicyDecision()
    }

    companion object {
        val default = PolicyEngine()

        /**
         * Static evaluation matching previous signature for seamless backward-compatibility.
         */
        fun evaluate(
            tool: ToolManifest,
            argsJson: String,
            task: AgentTask,
            config: PolicyConfig = PolicyConfig()
        ): PolicyDecision {
            return PolicyEngine(config).evaluate(tool, argsJson, task, config)
        }

        /**
         * Static evaluation for [ToolRequest] and [TaskContext].
         */
        fun evaluate(
            request: ToolRequest,
            context: TaskContext
        ): PolicyEvaluationResult {
            return default.evaluate(request, context)
        }
    }
}

// Extension helper functions for mapping
fun ToolManifest.toToolRequest(argsJson: String = "{}"): ToolRequest = ToolRequest(
    toolId = id,
    toolName = name,
    permissionLevel = permissionLevel,
    sideEffects = sideEffects,
    argumentsJson = argsJson,
    enabled = enabled,
    status = status
)

fun ToolManifest.toToolRequest(arguments: Map<String, Any?>): ToolRequest = ToolRequest(
    toolId = id,
    toolName = name,
    permissionLevel = permissionLevel,
    sideEffects = sideEffects,
    argumentsJson = org.json.JSONObject(arguments).toString(),
    enabled = enabled,
    status = status
)

fun AgentTask.toTaskContext(): TaskContext {
    val granted = permissionEvents
        .filter { it.decision == "APPROVED" }
        .map { it.toolId }
        .toSet()
    return TaskContext(
        taskId = taskId,
        origin = origin,
        dryRun = dryRun,
        traceId = traceId,
        grantedPermissions = granted
    )
}
