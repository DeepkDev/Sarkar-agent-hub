# SARKAR AGENT HUB — Architecture & Technical Specification

## 1. System Overview & Philosophy

**SARKAR Agent Hub** is a decoupled, model-agnostic AI agent orchestration engine and mission control plane. It is architected from first principles under six non-negotiable axioms:

1. **Decoupled Independence:** Standalone platform. SARKAR Assistant connects only via public REST, SSE, and delegated device tool protocols.
2. **Honesty Over Hallucination:** Every capability explicitly declares its state: `IMPLEMENTED`, `PLACEHOLDER`, `NOT_CONFIGURED`, or `DISABLED`.
3. **Model-Agnostic Core:** Providers adhere to standard capability contracts. Zero vendor lock-in.
4. **Secure by Default:** Deterministic policy engine gates all operations before tool execution. Raw outputs wrapped in data boundary envelopes.
5. **Bounded Autonomy:** Hard per-task budgets enforce caps on steps, tool calls, replans, retries, and wall-clock execution time.
6. **Event Sourcing & Cryptographic Auditing:** State is derived from an append-only log of SHA-256 hash-chained events.

---

## 2. Architecture & Execution Flow

```
                      +------------------------------------+
                      |    Client / UI / SARKAR API        |
                      +-----------------+------------------+
                                        | (Task Request)
                                        v
                      +-----------------+------------------+
                      |         Task State Machine         |
                      |  (PENDING -> PLANNING -> EXECUTING)|
                      +-----------------+------------------+
                                        |
                 +----------------------+----------------------+
                 |                                             |
                 v                                             v
     +-----------+-----------+                     +-----------+-----------+
     |   Role 1: Planner     |                     |   Policy & Clearance  |
     | (Request -> Steps)    |                     | (Deterministic Rules) |
     +-----------+-----------+                     +-----------+-----------+
                 |                                             |
                 v                                             v
     +-----------+-----------+                     +-----------+-----------+
     |   Role 2: Executor    |                     |     Tool Registry     |
     | (Select Tool & Args)  |                     | (Safe Built-ins + MCP)|
     +-----------+-----------+                     +-----------+-----------+
                 |                                             |
                 +----------------------+----------------------+
                                        | (Execution Envelope)
                                        v
                      +-----------------+------------------+
                      |       Role 3: Verifier             |
                      | (Tool Output vs Step Acceptance)   |
                      +-----------------+------------------+
                                        |
                 +----------------------+----------------------+
                 | (PASS)                                      | (REPLAN / RETRY)
                 v                                             v
    +------------+-----------+                    +------------+-----------+
    |   Role 4: Summarizer   |                    | Bounded Replanning     |
    | (Spoken + Full Report) |                    | (Max 2 Replans Cap)    |
    +------------+-----------+                    +------------------------+
                 |
                 v
    +------------+-----------+
    | Tamper-Evident Event   |
    | Sourced Audit Trail    |
    +------------------------+
```

---

## 3. Finite State Machine (FSM)

The task lifecycle is strictly typed. Illegal transitions immediately throw `IllegalStateException`:

```
   [PENDING]
       │
       ▼
   [PLANNING] ──────── (Dry Run) ────────► [COMPLETED]
       │
       ▼
   [EXECUTING] ◄─────────────────────────► [WAITING_FOR_PERMISSION]
       │                                            │ (Expired)
       ▼                                            ▼
   [VERIFYING] ─── (Replan / Retry) ───► [PLANNING] / [FAILED] / [EXPIRED]
       │
       ▼
   [COMPLETED]
```

**Terminal States:** `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`.

---

## 4. Policy Engine & Permission Clearance

Every tool invocation is deterministically evaluated by `PolicyEngine.evaluate(tool, args, task, config)`:

| Level | Behavior | Expiration | UI / API Action |
|---|---|---|---|
| `PUBLIC` / `SAFE` | Immediately executed and logged | N/A | Logged in audit trail |
| `CONFIRMATION_REQUIRED` | Suspends execution; task moves to `WAITING_FOR_PERMISSION` | 10 Minutes | Operator must click Approve or Deny |
| `RESTRICTED` | Blocked by default; requires administrative policy override | N/A | Hard rejection logged to audit trail |

### Origin Constraints:
- `SARKAR` origin tasks are prohibited from calling filesystem write/manipulation tools.
- Global Emergency Kill Switch immediately suspends all tool executions platform-wide.

---

## 5. Built-in Tools Matrix

| Tool ID | Name | Permission | Side-Effects | Status |
|---|---|---|---|---|
| `calculator` | Safe Math Evaluator | `SAFE` | `NONE` | `IMPLEMENTED` |
| `datetime` | Timezone-Aware Clock | `SAFE` | `NONE` | `IMPLEMENTED` |
| `text_processing` | Text Utility Suite | `SAFE` | `NONE` | `IMPLEMENTED` |
| `json_processing` | JSON Parser/Validator | `SAFE` | `NONE` | `IMPLEMENTED` |
| `knowledge_search`| Local Demo Store Query | `SAFE` | `READ` | `IMPLEMENTED` |
| `web_search` | Live Web Search | `SAFE` | `EXTERNAL`| `NOT_CONFIGURED` |
| `file_operation` | Sandboxed Filesystem | `CONFIRMATION_REQUIRED` | `WRITE` | `PLACEHOLDER` |
| `send_email` | Notification Dispatcher| `CONFIRMATION_REQUIRED` | `EXTERNAL`| `PLACEHOLDER` (Approval Demo) |
| `delete_data` | Record Purge Tool | `RESTRICTED` | `WRITE` | `PLACEHOLDER` (Block Demo) |
| `mcp_adapter_stub`| MCP Protocol Bridge | `SAFE` | `EXTERNAL`| `PLACEHOLDER` |

---

## 6. SARKAR Android Assistant Integration Specification

### Communication Flow:
1. SARKAR submits user voice requests via `POST /api/v1/agent/task` with `origin: "sarkar"`.
2. SARKAR streams live event state updates over SSE `GET /api/v1/agent/task/:id/events`.
3. If an action requires physical device access (e.g. camera, sensor telemetry), Hub dispatches a `DelegatedToolRequest`:
   ```json
   {
     "delegationId": "del-89211",
     "taskId": "task-39201",
     "deviceToolName": "android.camera.capture",
     "parameters": { "resolution": "1080p", "lens": "back" }
   }
   ```
4. SARKAR executes the tool on-device and posts results back to the Hub.
5. The completed task yields the **Voice Contract**:
   ```json
   {
     "spokenSummary": "Server cluster deployment completed with 3 nodes active and verified.",
     "fullText": "### Deployment Report\n- Node 1: Running (42ms)\n- Node 2: Running (48ms)...",
     "language": "hi-IN"
   }
   ```
   The `spokenSummary` is strictly optimized for text-to-speech without markdown artifacts.

---

## 7. Adding New AI Providers

Implement the `AIProvider` interface:
```kotlin
interface AIProvider {
    val id: String
    val displayName: String
    fun capabilities(): ProviderCapabilities
    suspend fun health(): ProviderHealth
    suspend fun plan(req: PlanRequest): Plan
    suspend fun decideTool(req: ToolDecisionRequest): ToolDecision
    suspend fun verify(req: VerifyRequest): VerificationResult
    suspend fun summarize(req: SummarizeRequest): FinalResponse
    fun normalizeError(e: Throwable): AgentError
}
```
Register the provider in `ProviderRegistry`. Zero core logic modifications required.
