# Project Dependencies & Architectural Justifications

Every library and dependency in SARKAR Agent Hub is intentionally selected and justified:

| Dependency | Purpose | Architectural Justification |
|---|---|---|
| `androidx.compose.material3` | User Interface | Standard modern Android UI toolkit adhering strictly to Material Design 3 guidelines. |
| `androidx.room:room-runtime` & `room-ktx` | Local Persistence | Robust local SQLite persistence for tasks, audit logs, and demo knowledge documents. |
| `com.squareup.moshi:moshi-kotlin` | Serialization | High-performance JSON serialization for contract models and structured tool outputs. |
| `com.squareup.okhttp3:okhttp` | HTTP Networking | Resilient HTTP client with configurable connection and read timeouts for AI provider calls. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | Asynchronous Concurrency | Non-blocking execution of agent loops, tool timeouts, and state updates. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | MVVM State Management | Decouples UI composables from backend business logic and maintains UI state across lifecycle events. |
| `java.security.MessageDigest` | Cryptographic Integrity | Native JDK SHA-256 implementation used for tamper-evident event hash chaining. |
| `android.speech.tts.TextToSpeech` | Voice Contract Synthesis | Native Android TTS synthesizer supporting voice playback of agent `spokenSummary`. |
