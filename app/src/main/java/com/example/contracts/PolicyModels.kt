package com.example.contracts

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class PolicyDecisionType {
    ALLOW,
    REQUIRE_CONFIRMATION,
    DENY
}

@JsonClass(generateAdapter = true)
data class PolicyDecision(
    val type: PolicyDecisionType,
    val reason: String,
    val permissionId: String? = null,
    val expiresAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class PendingPermission(
    val permissionId: String = UUID.randomUUID().toString().take(8),
    val taskId: String,
    val stepId: String,
    val toolId: String,
    val argumentsJson: String,
    val sideEffectsSummary: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (10 * 60 * 1000L), // 10 minutes default
    val status: String = "PENDING" // "PENDING", "APPROVED", "DENIED", "EXPIRED"
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

@JsonClass(generateAdapter = true)
data class PolicyRule(
    val id: String = UUID.randomUUID().toString().take(6),
    val origin: OriginSource? = null, // null means all origins
    val toolId: String? = null,       // null means all tools
    val forceDecision: PolicyDecisionType? = null,
    val description: String
)

@JsonClass(generateAdapter = true)
data class PolicyConfig(
    val killSwitchEngaged: Boolean = false,
    val defaultRules: List<PolicyRule> = listOf(
        PolicyRule(
            origin = OriginSource.SARKAR,
            toolId = "file_operation",
            forceDecision = PolicyDecisionType.DENY,
            description = "SARKAR-origin tasks are strictly prevented from filesystem manipulation"
        ),
        PolicyRule(
            toolId = "delete_data",
            forceDecision = PolicyDecisionType.DENY,
            description = "Restricted data deletion blocked by default without admin unlock"
        )
    )
)
