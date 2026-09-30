package com.example.tools

import com.example.contracts.*
import com.example.knowledge.KnowledgeManager
import org.json.JSONArray
import org.json.JSONObject

class KnowledgeSearchTool(private val knowledgeManager: KnowledgeManager) : BaseTool {
    override val manifest = ToolManifest(
        id = "knowledge_search",
        name = "Local Knowledge Search",
        version = "1.0.0",
        description = "Searches the local demo knowledge store and returns scored snippets and source citations.",
        category = "Knowledge",
        inputParameters = listOf(
            ToolParameter("query", "string", "Keywords or query phrase to search in knowledge base")
        ),
        permissionLevel = PermissionLevel.SAFE,
        sideEffects = SideEffectType.READ,
        status = CapabilityStatus.IMPLEMENTED
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val startTime = System.currentTimeMillis()
        val query = args["query"]?.toString() ?: ""
        val results = knowledgeManager.search(query)

        val jsonArray = JSONArray()
        for (res in results.take(3)) {
            val item = JSONObject().apply {
                put("title", res.document.title)
                put("snippet", res.matchingSnippet)
                put("source", res.document.source)
                put("score", "%.2f".format(res.relevanceScore))
            }
            jsonArray.put(item)
        }

        val output = JSONObject().apply {
            put("query", query)
            put("resultsFound", results.size)
            put("matches", jsonArray)
            put("storeType", "LOCAL_DEMO_STORE")
        }

        return ToolResult(
            toolId = "knowledge_search",
            success = true,
            data = output.toString(),
            executionTimeMs = System.currentTimeMillis() - startTime,
            status = CapabilityStatus.IMPLEMENTED
        )
    }
}
