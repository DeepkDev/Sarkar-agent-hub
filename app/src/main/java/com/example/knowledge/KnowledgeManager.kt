package com.example.knowledge

import com.example.contracts.KnowledgeDocument
import com.example.contracts.KnowledgeSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KnowledgeManager {

    private val initialDocs = listOf(
        KnowledgeDocument(
            id = "doc-arch-01",
            title = "SARKAR Agent Hub Architecture & State Machine",
            category = "Architecture",
            content = "SARKAR Agent Hub is a decoupled AI agent orchestration platform. Tasks follow an explicit finite state machine: PENDING -> PLANNING -> (WAITING_FOR_PERMISSION <-> EXECUTING) -> VERIFYING -> COMPLETED. Bounded budgets prevent infinite execution loops. The system uses event sourcing with SHA-256 tamper-evident hash chaining.",
            tags = listOf("architecture", "state-machine", "events", "sarkar"),
            source = "docs/ARCHITECTURE.md"
        ),
        KnowledgeDocument(
            id = "doc-policy-02",
            title = "Policy Engine & Permission Clearance Matrix",
            category = "Security",
            content = "The Policy Engine evaluates tool execution permissions into four distinct tiers: PUBLIC (immediate execution), SAFE (immediate execution and audit log), CONFIRMATION_REQUIRED (suspends task for operator approval with 10-minute expiry), and RESTRICTED (blocked by default, requires administrative clearance). All arguments and side-effects are inspected before dispatch.",
            tags = listOf("security", "policy", "permissions", "clearance"),
            source = "docs/SECURITY_POLICY.md"
        ),
        KnowledgeDocument(
            id = "doc-voice-03",
            title = "Voice Contract & TTS Specification for SARKAR Assistant",
            category = "Integration",
            content = "The voice response contract requires every completed task to produce a concise 'spokenSummary' in addition to the detailed 'fullText'. Spoken summaries must be free of Markdown formatting, numbers written in natural phonetics, and structured for text-to-speech synthesis supporting English, Hindi, and Hinglish.",
            tags = listOf("voice", "tts", "sarkar", "contract", "audio"),
            source = "docs/VOICE_CONTRACT.md"
        ),
        KnowledgeDocument(
            id = "doc-tools-04",
            title = "Tool Delegation Model for Device-Side Execution",
            category = "Tools",
            content = "When SARKAR Android Assistant connects, device-level capabilities such as camera capture, contact lookup, and local sensor telemetry are executed via DelegatedToolRequest. The Hub formulates the plan and dispatches delegated requests to the client, which validates and executes on-device before returning results.",
            tags = listOf("tools", "delegation", "android", "sensors"),
            source = "docs/TOOL_DELEGATION.md"
        ),
        KnowledgeDocument(
            id = "doc-audit-05",
            title = "Event-Sourcing and Tamper-Evident Audit Logging",
            category = "Auditing",
            content = "Every state transition produces an immutable TaskEvent. Each event incorporates the SHA-256 hash of the predecessor event, creating a tamper-evident audit trail from GENESIS to completion. Event streams can be replayed to inspect or debug historical task states at any step.",
            tags = listOf("audit", "cryptography", "sha256", "replay"),
            source = "docs/AUDIT_LOGGING.md"
        )
    )

    private val _documents = MutableStateFlow<List<KnowledgeDocument>>(initialDocs)
    val documents: StateFlow<List<KnowledgeDocument>> = _documents.asStateFlow()

    fun search(query: String, minScore: Float = 0.1f): List<KnowledgeSearchResult> {
        val queryTokens = query.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }
        if (queryTokens.isEmpty()) {
            return _documents.value.map { doc ->
                KnowledgeSearchResult(
                    document = doc,
                    relevanceScore = 0.5f,
                    matchingSnippet = doc.content.take(120) + "..."
                )
            }
        }

        return _documents.value.mapNotNull { doc ->
            val titleMatches = queryTokens.count { doc.title.lowercase().contains(it) } * 3.0f
            val tagMatches = queryTokens.count { doc.tags.any { tag -> tag.lowercase().contains(it) } } * 2.0f
            val contentMatches = queryTokens.count { doc.content.lowercase().contains(it) } * 1.0f
            val rawScore = titleMatches + tagMatches + contentMatches

            if (rawScore > 0) {
                val normalizedScore = (rawScore / (queryTokens.size * 3.0f)).coerceIn(0.1f, 1.0f)
                val snippet = extractSnippet(doc.content, queryTokens)
                KnowledgeSearchResult(doc, normalizedScore, snippet)
            } else null
        }.sortedByDescending { it.relevanceScore }
    }

    fun ingest(doc: KnowledgeDocument) {
        _documents.value = _documents.value + doc
    }

    private fun extractSnippet(content: String, tokens: List<String>): String {
        val lower = content.lowercase()
        val firstMatch = tokens.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (firstMatch - 40).coerceAtLeast(0)
        val end = (firstMatch + 120).coerceAtMost(content.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < content.length) "..." else ""
        return prefix + content.substring(start, end).trim() + suffix
    }
}
