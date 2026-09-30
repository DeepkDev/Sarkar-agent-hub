package com.example.providers

import com.example.BuildConfig
import com.example.contracts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : AIProvider {
    override val id: String = "gemini_provider"
    override val displayName: String = "Google Gemini 3.5 Flash"

    private val apiKey: String
        get() = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null)?.toString() ?: ""
        } catch (_: Throwable) {
            ""
        }

    override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        structuredOutput = true,
        toolCalling = true,
        streaming = true,
        maxContextTokens = 128_000,
        vision = true
    )

    override suspend fun health(): ProviderHealth {
        return if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            ProviderHealth.NOT_CONFIGURED
        } else {
            ProviderHealth.IMPLEMENTED
        }
    }

    override suspend fun generate(req: GenerateRequest): GenerateResponse {
        if (health() == ProviderHealth.NOT_CONFIGURED) {
            throw IllegalStateException("GeminiProvider is NOT_CONFIGURED. Please provide a GEMINI_API_KEY or switch to MockProvider.")
        }

        val promptText = if (!req.systemPrompt.isNullOrBlank()) {
            "${req.systemPrompt}\n\nUser: ${req.prompt}"
        } else {
            req.prompt
        }

        val text = callGeminiRest(promptText)
        return GenerateResponse(
            text = text,
            tokensUsed = text.length / 4,
            finishReason = "STOP",
            model = "gemini-3.5-flash"
        )
    }

    override suspend fun plan(req: PlanRequest): Plan {
        if (health() == ProviderHealth.NOT_CONFIGURED) {
            throw IllegalStateException("GeminiProvider is NOT_CONFIGURED. Please provide a GEMINI_API_KEY or switch to MockProvider.")
        }

        val prompt = """
            You are the Planner agent in SARKAR Agent Hub.
            User Goal: "${req.userGoal}"
            Available Tools: ${req.availableTools.filter { it.enabled }.map { "${it.id}: ${it.description}" }}
            
            Produce a strictly formatted JSON plan with this structure:
            {
              "explanation": "Brief rationale",
              "steps": [
                {
                  "id": "step-1",
                  "description": "Step action description",
                  "expectedTool": "tool_id"
                }
              ]
            }
            Respond with JSON ONLY.
        """.trimIndent()

        val jsonStr = callGeminiRest(prompt)
        val cleanJson = cleanMarkdownJson(jsonStr)
        val parsed = JSONObject(cleanJson)
        val explanation = parsed.optString("explanation", "")
        val stepsArr = parsed.optJSONArray("steps")
        val steps = mutableListOf<PlanStep>()
        if (stepsArr != null) {
            for (i in 0 until stepsArr.length()) {
                val s = stepsArr.getJSONObject(i)
                steps.add(
                    PlanStep(
                        id = s.optString("id", "step-${i+1}"),
                        description = s.optString("description", ""),
                        expectedTool = s.optString("expectedTool", "")
                    )
                )
            }
        }
        return Plan(explanation = explanation, steps = steps)
    }

    override suspend fun decideTool(req: ToolDecisionRequest): ToolDecision {
        if (health() == ProviderHealth.NOT_CONFIGURED) {
            throw IllegalStateException("GeminiProvider is NOT_CONFIGURED.")
        }

        val toolManifest = req.availableTools.find { it.id == req.step.expectedTool }
        val prompt = """
            You are the Executor agent in SARKAR Agent Hub.
            Current Step: "${req.step.description}"
            Target Tool: "${req.step.expectedTool}"
            Tool Manifest: ${toolManifest?.inputParameters}
            
            Produce a JSON object containing the exact arguments map for this tool.
            Format:
            {
              "arguments": { "param_name": "param_value" },
              "rationale": "Why these arguments were chosen"
            }
            Respond with JSON ONLY.
        """.trimIndent()

        val jsonStr = callGeminiRest(prompt)
        val cleanJson = cleanMarkdownJson(jsonStr)
        val parsed = JSONObject(cleanJson)
        val argsObj = parsed.optJSONObject("arguments") ?: JSONObject()
        val argsMap = mutableMapOf<String, Any?>()
        argsObj.keys().forEach { k -> argsMap[k] = argsObj.get(k) }

        return ToolDecision(
            toolId = req.step.expectedTool,
            arguments = argsMap,
            rationale = parsed.optString("rationale", "Generated by Gemini")
        )
    }

    override suspend fun verify(req: VerifyRequest): VerificationResult {
        if (health() == ProviderHealth.NOT_CONFIGURED) {
            throw IllegalStateException("GeminiProvider is NOT_CONFIGURED.")
        }

        val prompt = """
            You are the Verifier agent in SARKAR Agent Hub.
            Step Criteria: "${req.step.description}"
            Tool Output: "${req.toolResult.data}"
            
            Did this tool execution satisfy the step criteria?
            Produce JSON with:
            {
              "outcome": "PASS" | "RETRY" | "REPLAN" | "FAIL",
              "reason": "Detailed reason"
            }
            Respond with JSON ONLY.
        """.trimIndent()

        val jsonStr = callGeminiRest(prompt)
        val cleanJson = cleanMarkdownJson(jsonStr)
        val parsed = JSONObject(cleanJson)
        val outcomeStr = parsed.optString("outcome", "PASS").uppercase()
        val outcome = try { VerificationOutcome.valueOf(outcomeStr) } catch (_: Exception) { VerificationOutcome.PASS }

        return VerificationResult(
            outcome = outcome,
            reason = parsed.optString("reason", "Verified by Gemini")
        )
    }

    override suspend fun summarize(req: SummarizeRequest): FinalResponse {
        if (health() == ProviderHealth.NOT_CONFIGURED) {
            throw IllegalStateException("GeminiProvider is NOT_CONFIGURED.")
        }

        val prompt = """
            You are the Summarizer agent in SARKAR Agent Hub.
            User Goal: "${req.userGoal}"
            Executed Steps: ${req.toolResults.map { "${it.toolId} (success=${it.successful})" }}
            Language: "${req.language}"
            
            Produce a JSON with:
            {
              "spokenSummary": "Concise 1-2 sentence spoken summary for voice TTS without markdown",
              "fullText": "Comprehensive detailed markdown report of the execution",
              "language": "${req.language}"
            }
            Respond with JSON ONLY.
        """.trimIndent()

        val jsonStr = callGeminiRest(prompt)
        val cleanJson = cleanMarkdownJson(jsonStr)
        val parsed = JSONObject(cleanJson)

        return FinalResponse(
            spokenSummary = parsed.optString("spokenSummary", "Task execution finished."),
            fullText = parsed.optString("fullText", "Execution completed."),
            language = parsed.optString("language", req.language)
        )
    }

    override fun normalizeError(e: Throwable): AgentError {
        return AgentError(
            code = ErrorCode.PROVIDER_UNAVAILABLE,
            message = e.message ?: "Gemini API request failed"
        )
    }

    private suspend fun callGeminiRest(promptText: String): String = withContext(Dispatchers.IO) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val bodyJson = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", promptText) })
                    })
                })
            }
            put("contents", contents)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini API HTTP ${response.code}: ${response.body?.string()}")
            }
            val respBody = response.body?.string() ?: ""
            val json = JSONObject(respBody)
            val candidates = json.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val parts = firstCandidate.getJSONObject("content").getJSONArray("parts")
            parts.getJSONObject(0).getString("text")
        }
    }

    private fun cleanMarkdownJson(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
