package com.example.contracts

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class KnowledgeDocument(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val content: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val source: String = "local_demo_corpus",
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class KnowledgeSearchResult(
    val document: KnowledgeDocument,
    val relevanceScore: Float,
    val matchingSnippet: String
)
