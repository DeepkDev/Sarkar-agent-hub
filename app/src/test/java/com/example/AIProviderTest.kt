package com.example

import com.example.contracts.*
import com.example.providers.AIProvider
import com.example.providers.MockProvider
import com.example.providers.MockSimulationMode
import com.example.providers.ProviderRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AIProviderTest {

    private lateinit var mockProvider: MockProvider
    private lateinit var registry: ProviderRegistry

    private val calculatorManifest = ToolManifest(
        id = "calculator",
        name = "Calculator",
        description = "Evaluates math expressions",
        category = "Math",
        inputParameters = emptyList(),
        permissionLevel = PermissionLevel.SAFE,
        sideEffects = SideEffectType.NONE,
        status = CapabilityStatus.IMPLEMENTED
    )

    @Before
    fun setup() {
        mockProvider = MockProvider()
        registry = ProviderRegistry()
    }

    @Test
    fun testGenerateStructuredRequest() = runTest {
        val req = GenerateRequest(
            prompt = "What is the capital of France?",
            systemPrompt = "You are a concise encyclopedia.",
            temperature = 0.2f
        )
        val response = mockProvider.generate(req)

        assertNotNull(response)
        assertFalse(response.text.isBlank())
        assertTrue(response.tokensUsed > 0)
        assertEquals("STOP", response.finishReason)
    }

    @Test
    fun testGenerateConvenienceOverload() = runTest {
        val prompt = "Say hello to the agent"
        val text = mockProvider.generate(prompt)

        assertNotNull(text)
        assertTrue(text.contains("Hello"))
    }

    @Test
    fun testPlanMethod() = runTest {
        val planReq = PlanRequest(
            userGoal = "Calculate 42 * 10",
            availableTools = listOf(calculatorManifest)
        )
        val plan = mockProvider.plan(planReq)

        assertNotNull(plan)
        assertTrue(plan.steps.isNotEmpty())
        assertEquals("calculator", plan.steps[0].expectedTool)
    }

    @Test
    fun testDecideToolMethod() = runTest {
        val step = PlanStep(
            id = "step-1",
            description = "Calculate arithmetic expression",
            expectedTool = "calculator"
        )
        val decisionReq = ToolDecisionRequest(
            step = step,
            previousResults = emptyList(),
            availableTools = listOf(calculatorManifest)
        )
        val decision = mockProvider.decideTool(decisionReq)

        assertNotNull(decision)
        assertEquals("calculator", decision.toolId)
        assertTrue(decision.arguments.isNotEmpty())
    }

    @Test
    fun testVerifyMethod() = runTest {
        val step = PlanStep(
            id = "step-1",
            description = "Calculate math",
            expectedTool = "calculator"
        )
        val toolResult = ToolResult(
            toolId = "calculator",
            success = true,
            data = "420",
            executionTimeMs = 45L
        )
        val verifyReq = VerifyRequest(
            step = step,
            toolResult = toolResult
        )
        val result = mockProvider.verify(verifyReq)

        assertNotNull(result)
        assertEquals(VerificationOutcome.PASS, result.outcome)
        assertTrue(result.reason.isNotBlank())
    }

    @Test
    fun testPluggableProviderSwitching() {
        val all = registry.getAllProviders()
        assertTrue(all.size >= 3)
        assertTrue(all.any { it.id == "mock_provider" })
        assertTrue(all.any { it.id == "gemini_provider" })
        assertTrue(all.any { it.id == "local_provider_stub" })

        assertEquals("mock_provider", registry.activeProvider.value.id)

        val switched = registry.setActiveProvider("gemini_provider")
        assertTrue(switched)
        assertEquals("gemini_provider", registry.activeProvider.value.id)

        val switchedBack = registry.setActiveProvider("mock_provider")
        assertTrue(switchedBack)
        assertEquals("mock_provider", registry.activeProvider.value.id)
    }

    @Test
    fun testCustomPluggableProviderImplementation() = runTest {
        // Demonstrate implementing custom third-party provider adhering to AIProvider interface
        val customProvider = object : AIProvider {
            override val id: String = "custom_test_provider"
            override val displayName: String = "Custom Test Model"

            override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
                structuredOutput = true,
                toolCalling = true,
                streaming = true
            )

            override suspend fun health(): ProviderHealth = ProviderHealth.IMPLEMENTED

            override suspend fun generate(req: GenerateRequest): GenerateResponse =
                GenerateResponse(text = "Custom: ${req.prompt}", tokensUsed = 10)

            override suspend fun plan(req: PlanRequest): Plan =
                Plan(explanation = "Custom plan", steps = emptyList())

            override suspend fun decideTool(req: ToolDecisionRequest): ToolDecision =
                ToolDecision(toolId = "noop", arguments = emptyMap(), rationale = "custom")

            override suspend fun verify(req: VerifyRequest): VerificationResult =
                VerificationResult(outcome = VerificationOutcome.PASS, reason = "custom pass")

            override suspend fun summarize(req: SummarizeRequest): FinalResponse =
                FinalResponse(spokenSummary = "Done", fullText = "Execution complete")

            override fun normalizeError(e: Throwable): AgentError =
                AgentError(code = ErrorCode.UNKNOWN_ERROR, message = e.message ?: "error")
        }

        val generated = customProvider.generate("test prompt")
        assertEquals("Custom: test prompt", generated)

        val plan = customProvider.plan(PlanRequest(userGoal = "run", availableTools = emptyList()))
        assertEquals("Custom plan", plan.explanation)
    }
}
