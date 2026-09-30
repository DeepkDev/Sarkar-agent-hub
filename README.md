# SARKAR AGENT HUB

> Standalone, production-grade AI agent orchestration platform with event-sourced execution, deterministic policy clearance, bounded autonomy budgets, and voice-ready contracts.

---

## 1. Compliance with Non-Negotiable Rules

1. **Independence:** Completely decoupled from SARKAR Android Assistant. SARKAR connects via public REST + SSE API and delegated tool execution protocols.
2. **Honesty:** Every capability carries an explicit status badge:
   - `IMPLEMENTED`
   - `PLACEHOLDER`
   - `NOT_CONFIGURED`
   - `DISABLED`
3. **Model-Agnostic:** AIProvider interface with Mock Provider (zero API keys needed), Gemini Provider (with graceful `NOT_CONFIGURED` handling), and Local LLM Provider stub.
4. **Secure by Default:** Pure deterministic policy engine, raw outputs wrapped in `<tool_data_boundary>` envelopes, no `eval`, sandboxed math parser.
5. **Bounded Autonomy:** Enforced caps on steps, tool calls, replans, retries, and wall-clock execution time.
6. **MVP First:** Working foundation with Room local persistence, interactive time-travel replay, and Android TTS voice playback.

---

## 2. Capability Status Matrix

| Component | Type | Clearance | Status | Description |
|---|---|---|---|---|
| **Core State Machine** | Architecture | SAFE | `IMPLEMENTED` | Explicit FSM (`PENDING` -> `PLANNING` -> `EXECUTING` -> `VERIFYING` -> `COMPLETED`) |
| **Event Sourcing** | Audit Engine | SAFE | `IMPLEMENTED` | Append-only event log with SHA-256 tamper-evident hash chaining and time-travel replay |
| **Policy Engine** | Security | SAFE | `IMPLEMENTED` | Clearance levels (`SAFE`, `CONFIRMATION_REQUIRED`, `RESTRICTED`), origin rules, global kill switch |
| **Math Calculator** | Tool | `SAFE` | `IMPLEMENTED` | Safe recursive descent arithmetic evaluator without `eval` |
| **Current Date & Time** | Tool | `SAFE` | `IMPLEMENTED` | Timezone-aware timestamp, ISO formatting, and calendar calculations |
| **Text Processing** | Tool | `SAFE` | `IMPLEMENTED` | Word count, character count, casing transformation, regex extract, whitespace cleanup |
| **JSON Processing** | Tool | `SAFE` | `IMPLEMENTED` | Syntax validation, pretty-formatting, key/path queries |
| **Knowledge Search** | Tool | `SAFE` | `IMPLEMENTED` | BM25 keyword scoring across local demo document store with citations |
| **Web Search** | Tool | `SAFE` | `NOT_CONFIGURED` | Live web search engine bridge |
| **File Operation** | Tool | `CONFIRMATION_REQUIRED` | `PLACEHOLDER` | Sandboxed filesystem access stub |
| **Send Email** | Tool | `CONFIRMATION_REQUIRED` | `PLACEHOLDER` | Demonstration of approval gate flow (safe simulation) |
| **Delete Data** | Tool | `RESTRICTED` | `PLACEHOLDER` | Demonstration of hard blocked policy rejection |
| **MCP Adapter** | Integration | `SAFE` | `PLACEHOLDER` | Model Context Protocol bridge stub |
| **Mock Provider** | Provider | SAFE | `IMPLEMENTED` | Deterministic provider with failure, timeout, malformed output simulation modes |
| **Gemini Provider** | Provider | SAFE | `IMPLEMENTED` | Direct REST API integration with `gemini-3.5-flash` via BuildConfig |
| **Local LLM Provider** | Provider | SAFE | `PLACEHOLDER` | Ollama / llama.cpp bridge stub |
| **SARKAR Connector** | Connector | SAFE | `NOT_CONFIGURED` | Reference interface + `NullSarkarConnector` with delegation contract |
| **Voice Contract (TTS)** | Audio | SAFE | `IMPLEMENTED` | Android TextToSpeech synthesis for `spokenSummary` results |

---

## 3. Quick 1-Click Demos Available in App

1. **Safe Math Benchmark:** Parses arithmetic expressions, runs step verification, and vocalizes results.
2. **Approval Flow (Send Email):** Gated by `CONFIRMATION_REQUIRED`, suspends task for operator review with Approve/Deny buttons and 10-minute expiry.
3. **Restricted Policy Block (Delete Data):** Gated by `RESTRICTED`, policy engine blocks execution without hang.
4. **SARKAR Origin & Knowledge Query:** Origin set to `SARKAR`, searches local documents, validates origin restrictions, generates voice-ready phonetic summary.
5. **Dry Run Preview:** Plans and verifies tool policies without execution.
6. **Time-Travel Replay:** Scrub the interactive slider on any completed task to replay execution step-by-step.

---

## 4. Known Limitations

- **Web Search:** Marked `NOT_CONFIGURED` pending external search provider keys.
- **Device-Side Tools:** Native mobile hardware tools (camera, contacts) execute when a live SARKAR Assistant client connects and delegates requests.
- **Local LLM:** Localhost Ollama endpoint is stubbed as `PLACEHOLDER`.
