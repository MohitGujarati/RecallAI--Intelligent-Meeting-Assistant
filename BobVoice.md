# BobVoice.md — Zero to Hero: How Bob (Live AI Voice) Works in RecallAI

> A complete technical reference for the Viva / Live AI voice feature.
> Intended for developers and AI coding agents rebuilding or extending this feature.
> Last updated: 2026-04-12

---

## Table of Contents

1. [What Is Bob?](#1-what-is-bob)
2. [High-Level Architecture](#2-high-level-architecture)
3. [The Underlying Technology: Gemini Live API](#3-the-underlying-technology-gemini-live-api)
4. [Layer-by-Layer Breakdown](#4-layer-by-layer-breakdown)
   - 4.1 [State Machine: LiveAiState](#41-state-machine-liveaistate)
   - 4.2 [State Bridge: LiveAiStateHolder](#42-state-bridge-liveaistateHolder)
   - 4.3 [WebSocket Client: GeminiLiveClient](#43-websocket-client-geminiliveClient)
   - 4.4 [Audio Input: AudioRecorder](#44-audio-input-audiorecorder)
   - 4.5 [Audio Output: AudioTrackManager](#45-audio-output-audiotrackmanager)
   - 4.6 [Session Orchestrator: LiveAiRepository](#46-session-orchestrator-liveairepository)
   - 4.7 [Foreground Service: LiveAiService](#47-foreground-service-liveaiservice)
   - 4.8 [ViewModel: LiveAiViewModel](#48-viewmodel-liveaiviewmodel)
   - 4.9 [UI Screen: LiveAiScreen](#49-ui-screen-liveaiscreen)
   - 4.10 [Mascot Animation: LiveMascot (Bob)](#410-mascot-animation-livemascot-bob)
5. [Context System: What Bob Knows](#5-context-system-what-bob-knows)
   - 5.1 [Single-Meeting Mode](#51-single-meeting-mode)
   - 5.2 [Global Mode (Home Screen Bob)](#52-global-mode-home-screen-bob)
   - 5.3 [Smart Context Swap (30+ min meetings)](#53-smart-context-swap-30-min-meetings)
6. [Function Calling: Bob as an Agent](#6-function-calling-bob-as-an-agent)
   - 6.1 [ToolRegistry](#61-toolregistry)
   - 6.2 [How a Tool Call Flows End-to-End](#62-how-a-tool-call-flows-end-to-end)
   - 6.3 [set_reminder Tool (Full Detail)](#63-set_reminder-tool-full-detail)
   - 6.4 [Adding a New Tool](#64-adding-a-new-tool)
7. [Dependency Injection Setup](#7-dependency-injection-setup)
8. [Navigation & Entry Points](#8-navigation--entry-points)
9. [Critical Audio Pipeline Details](#9-critical-audio-pipeline-details)
   - 9.1 [Echo Prevention (isSpeaking Guard)](#91-echo-prevention-isspeaking-guard)
   - 9.2 [Barge-In / Interrupt](#92-barge-in--interrupt)
   - 9.3 [Sample Rate Mismatch (16kHz vs 24kHz)](#93-sample-rate-mismatch-16khz-vs-24khz)
   - 9.4 [Dedicated Playback Thread](#94-dedicated-playback-thread)
10. [WebSocket Message Protocol (Exact JSON)](#10-websocket-message-protocol-exact-json)
    - 10.1 [Setup Frame (Client → Server)](#101-setup-frame-client--server)
    - 10.2 [Audio Input (Client → Server)](#102-audio-input-client--server)
    - 10.3 [Context Update (Client → Server)](#103-context-update-client--server)
    - 10.4 [Function Response (Client → Server)](#104-function-response-client--server)
    - 10.5 [Server Audio Reply (Server → Client)](#105-server-audio-reply-server--client)
    - 10.6 [Turn Complete (Server → Client)](#106-turn-complete-server--client)
    - 10.7 [Function Call (Server → Client)](#107-function-call-server--client)
11. [Bob's Visual Animations — State Matrix](#11-bobs-visual-animations--state-matrix)
    - 11.1 [Canvas Drawing Structure](#111-canvas-drawing-structure)
    - 11.2 [Per-State Animation Values](#112-per-state-animation-values)
12. [Known Pitfalls & Hard-Won Lessons](#12-known-pitfalls--hard-won-lessons)
13. [Full Session Lifecycle (Step-by-Step)](#13-full-session-lifecycle-step-by-step)
14. [File Map (Quick Reference)](#14-file-map-quick-reference)

---

## 1. What Is Bob?

Bob is the animated AI mascot that powers the **Live AI voice feature** in RecallAI. Bob is a real-time, bidirectional voice assistant. The user speaks — Bob listens and responds with synthesized speech. There is no "submit button" or text chat involved; it is a true walkie-talkie-style conversation.

Bob has two operational modes:

| Mode | Entry Point | Context |
|------|-------------|---------|
| **Meeting mode** | `MeetingDetailScreen → "Ask Bob"` | A specific meeting's transcript + summary |
| **Global mode** | Dashboard `"Ask Bob"` FAB | All completed meeting summaries combined |

The Viva use-case (question-and-answer back-and-forth) is exactly what this feature is designed for. Bob asks (or answers) a question, waits for the user to respond, then replies — continuously, for as long as the session is active.

---

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  LiveAiScreen (Compose UI)                                   │
│    └── LiveAiViewModel ──reads──▶ LiveAiStateHolder          │
└──────────────┬───────────────────────────────────────────────┘
               │  startForegroundService(intent)
               ▼
┌──────────────────────────────────────────────────────────────┐
│  LiveAiService (Foreground Service, keeps mic alive)         │
│    ├── Guards: RecordingService active? AudioFocus granted?  │
│    └── repository.startSession(meetingId, serviceScope)      │
└──────────────┬───────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────┐
│  LiveAiRepository (Orchestrator, NOT @Singleton)             │
│    ├── [1] Build context prompt (DB reads)                   │
│    ├── [2] AudioTrackManager.start() + GeminiLiveClient.connect()│
│    ├── [3] Launch 4 parallel coroutines:                     │
│    │     ├── launchAudioInput()   → mic → WebSocket         │
│    │     ├── launchAudioOutput()  → WebSocket → speaker     │
│    │     ├── launchNetworkEvents() → turnComplete/error     │
│    │     └── launchFunctionCallHandler() → agent tools      │
│    └── [4] launchSmartContextSwap() (if meeting ≥ 30 min)   │
└──────────────┬───────────────────────────────────────────────┘
               │ WebSocket (OkHttp)
               ▼
┌──────────────────────────────────────────────────────────────┐
│  GeminiLiveClient (WebSocket wrapper)                        │
│    ├── connect(context, tools) — sends Setup frame          │
│    ├── sendAudio(pcmBytes) — streams mic data               │
│    ├── Flows: incomingAudioFlow, turnCompleteFlow,           │
│    │         errorFlow, functionCallFlow                     │
│    └── sendFunctionResponse(callId, name, result)           │
└──────────────┬───────────────────────────────────────────────┘
               │ wss://generativelanguage.googleapis.com/...
               ▼
         Gemini Live API
```

**Key design decisions:**
- `LiveAiRepository` and `GeminiLiveClient` are **NOT singletons**. A fresh instance is injected per `LiveAiService` lifecycle to avoid WebSocket leaks between sessions.
- State is communicated through the singleton `LiveAiStateHolder` (a `StateFlow<LiveAiState>`), not through the service directly. This allows the ViewModel (which outlives the screen) to observe state without binding to the service.

---

## 3. The Underlying Technology: Gemini Live API

The feature uses Google's **Gemini Live (BidiGenerateContent) API** — a WebSocket-based, bidirectional streaming API.

| Property | Value |
|----------|-------|
| Protocol | WebSocket |
| URL | `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key={API_KEY}` |
| Model | `gemini-3.1-flash-live-preview` |
| Input audio | 16kHz, 16-bit PCM mono, base64-encoded |
| Output audio | 24kHz, 16-bit PCM mono, base64-encoded |
| Voice | `Zephyr` (configured in setup frame) |
| Function calling | Yes — declared in setup frame, fired via `toolCall` messages |

The API is configured in `config/AiModelConfig.kt`:
```kotlin
GEMINI_LIVE_MODEL    = "gemini-3.1-flash-live-preview"
GEMINI_LIVE_BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
GEMINI_API_KEY       = BuildConfig.GEMINI_API_KEY  // from local.properties
```

---

## 4. Layer-by-Layer Breakdown

### 4.1 State Machine: LiveAiState

**File:** `service/LiveAiState.kt`

Bob's entire behavior is driven by a sealed class state machine:

```kotlin
sealed class LiveAiState {
    object Idle : LiveAiState()
    data class Connecting(val meetingId: Long) : LiveAiState()
    data class Listening(val meetingId: Long) : LiveAiState()
    data class Speaking(val meetingId: Long) : LiveAiState()
    data class PerformingAction(val meetingId: Long) : LiveAiState()
    data class Stopped(val meetingId: Long) : LiveAiState()
    data class Error(val meetingId: Long, val message: String) : LiveAiState()
}
```

**State transitions:**
```
Idle
  └─[startSession()]──▶ Connecting
                            └─[WebSocket open + setup ACK]──▶ Listening
                                    ├─[incoming audio chunks]──▶ Speaking
                                    │       └─[turnComplete or interruptAi()]──▶ Listening
                                    ├─[function call from Gemini]──▶ PerformingAction
                                    │       └─[function executed + response sent]──▶ Listening
                                    └─[stopSession() / error]──▶ Stopped / Error
```

### 4.2 State Bridge: LiveAiStateHolder

**File:** `service/LiveAiState.kt` (same file, second class)

```kotlin
@Singleton
class LiveAiStateHolder @Inject constructor() {
    private val _state = MutableStateFlow<LiveAiState>(LiveAiState.Idle)
    val state: StateFlow<LiveAiState> = _state.asStateFlow()

    fun emit(newState: LiveAiState) { _state.value = newState }

    val isActive: Boolean  // true if Connecting/Listening/Speaking/PerformingAction
    val currentMeetingId: Long?  // extracted from whichever state holds meetingId

    fun resetIfTerminal()  // resets Stopped/Error → Idle (called in ViewModel init)
}
```

This is the **only shared state** between `LiveAiService` → `LiveAiRepository` (writes) and `LiveAiViewModel` → `LiveAiScreen` (reads). Because it's `@Singleton`, both the service and the UI observe the same `StateFlow` without any binding or IPC needed.

### 4.3 WebSocket Client: GeminiLiveClient

**File:** `data/remote/api/GeminiLiveClient.kt`

This is the lowest-level layer. It wraps OkHttp WebSocket with:

**Exposed flows:**
| Flow | Type | What it carries |
|------|------|----------------|
| `incomingAudioFlow` | `Flow<ByteArray>` | Raw PCM bytes (24kHz) for speaker playback |
| `turnCompleteFlow` | `Flow<Unit>` | Signals Bob finished speaking |
| `errorFlow` | `Flow<String>` | WebSocket or API errors |
| `functionCallFlow` | `Flow<FunctionCallEvent>` | Agent tool invocations from Gemini |

**Key methods:**
```kotlin
suspend fun connect(initialContext: String, toolDeclarations: List<Map<String, Any?>>)
fun sendAudio(pcmBytes: ByteArray)
fun updateContext(newContext: String)
fun sendFunctionResponse(callId: String, functionName: String, result: Map<String, Any?>)
fun disconnect()
```

`connect()` suspends until the server sends a `setupComplete` acknowledgement (or times out after 30 seconds). This guarantees the audio pipeline only starts after the server is ready.

**Message parsing in `handleIncomingMessage()`:**
The method handles 4 categories of incoming messages:
1. `setupComplete` → releases the `CompletableDeferred` that `connect()` is awaiting
2. `serverContent.modelTurn.parts` → audio bytes, text, and function calls (inside `parts`)
3. `toolCall.functionCalls[]` → function calls (top-level — this is where Live API sends them)
4. `error` → API error messages

**CRITICAL NOTE:** The Gemini Live API sends function calls in TWO possible locations:
- Inside `serverContent.modelTurn.parts[].functionCall` (REST API style, sometimes seen in Live too)
- Inside `toolCall.functionCalls[]` at the root level (Live API primary location)

Both are handled. If you only handle one location, tool calls will be silently dropped.

### 4.4 Audio Input: AudioRecorder

**File:** `service/audio/AudioRecorder.kt`

Wraps Android's `AudioRecord` and emits a cold `Flow<AudioEvent>`:

```kotlin
sealed class AudioEvent {
    data class PcmData(val bytes: ByteArray, val timestampMs: Long) : AudioEvent()
    object SilenceDetected : AudioEvent()
    object AudioResumed : AudioEvent()
}
```

**Audio spec:** 16kHz, 16-bit PCM, Mono — matches what Gemini's transcription model expects.

The flow reads 3,200 bytes (0.1 second) at a time. It also computes RMS and emits `SilenceDetected` after 10 consecutive seconds of silence — this is used by the recording feature (not directly by Live AI, which ignores silence and just sends everything while mic is unmuted).

### 4.5 Audio Output: AudioTrackManager

**File:** `service/audio/AudioTrackManager.kt`

Plays incoming PCM from Gemini through the phone's speaker:

```kotlin
fun start()             // initialize AudioTrack (24kHz, low-latency mode)
fun write(pcmData: ByteArray)  // blocking write — must run on dedicated thread
fun flush()             // instant buffer clear for barge-in / interrupt
fun stopAndRelease()    // cleanup
```

**Audio spec:** 24kHz, 16-bit PCM, Mono — this is what Gemini outputs. Note this is different from the input (16kHz).

**Buffer strategy:** 4x the minimum buffer size to prevent underruns (choppy audio). Uses `PERFORMANCE_MODE_LOW_LATENCY` for minimal delay.

**IMPORTANT:** `write()` is a blocking call. It must never run on `Dispatchers.IO` shared pool or `Dispatchers.Default` — doing so causes audio stuttering because those pools are shared with other work. This is why `LiveAiRepository` creates a dedicated single-thread dispatcher (`audioPlaybackDispatcher`) specifically for `launchAudioOutput()`.

### 4.6 Session Orchestrator: LiveAiRepository

**File:** `reposotory/LiveAiRepository.kt`

This is the brain of the entire feature. It is **NOT a singleton** — a fresh instance is created per session. It orchestrates everything:

```kotlin
fun startSession(meetingId: Long, scope: CoroutineScope)
fun interruptAi()
fun stopSession()
```

**`startSession()` does 4 things in order:**
1. **Build context:** Fetch meeting/summary data from Room → build system prompt string
2. **Start hardware:** `audioTrackManager.start()` → `geminiLiveClient.connect(context, tools)`
3. **Launch 4 coroutines in parallel:**
   - `launchAudioInput()` — mic flow → `geminiLiveClient.sendAudio()`
   - `launchAudioOutput(meetingId)` — `incomingAudioFlow` → `audioTrackManager.write()`
   - `launchNetworkEvents(meetingId)` — handles `turnCompleteFlow` + `errorFlow`
   - `launchFunctionCallHandler(meetingId)` — handles `functionCallFlow`
4. **Smart context swap** (if single-meeting and meeting ≥ 30 minutes)

**The `isSpeaking` flag:**
```kotlin
@Volatile private var isSpeaking = false
```

This volatile flag is the echo prevention mechanism. When the first audio chunk arrives from Gemini (`launchAudioOutput`), `isSpeaking` flips to `true` and `launchAudioInput` stops forwarding mic bytes. When `turnComplete` fires (`launchNetworkEvents`), `isSpeaking` flips back to `false` and the mic opens again. This prevents Bob from "hearing himself" and creating feedback loops.

### 4.7 Foreground Service: LiveAiService

**File:** `service/LiveAiService.kt`

An Android Foreground Service that keeps the mic lock and WebSocket connection alive even when the app is backgrounded.

**Actions (Intent extras):**
| Action | Effect |
|--------|--------|
| `ACTION_START` + `EXTRA_MEETING_ID` | Run guards, request audio focus, call `repository.startSession()` |
| `ACTION_STOP` | Call `repository.stopSession()`, abandon audio focus, `stopSelf()` |
| `ACTION_INTERRUPT` | Call `repository.interruptAi()` |

**Guards before starting:**
1. If `RecordingService` is active → show Toast, `stopSelf()` (mic conflict prevention)
2. If `liveAiStateHolder.isActive` already → ignore (idempotent)
3. Request audio focus via `AudioFocusHandler` → if denied → emit Error state, `stopSelf()`

**Notification:** Shows "Recall AI is listening" with a Stop button. Low importance so it doesn't pop over the user.

**`onDestroy()`:** Always calls `repository.stopSession()` and `audioFocusHandler.abandonFocus()` — this is the safety net for process death or system kills.

### 4.8 ViewModel: LiveAiViewModel

**File:** `ui/liveai/LiveAiViewModel.kt`

Thin ViewModel — it never touches the repository directly. All it does is:
1. On `init`: Call `stateHolder.resetIfTerminal()` to clear Stopped/Error state from prior sessions
2. Expose `uiState: StateFlow<LiveAiState>` from `LiveAiStateHolder`
3. Translate user actions to service intents:

```kotlin
fun startSession(meetingId: Long) → ContextCompat.startForegroundService(...)
fun stopSession()                  → application.startService(stopIntent)
fun interruptAi()                  → application.startService(interruptIntent)
```

This pattern keeps the ViewModel clean (no coroutines, no heavy deps) and lets the foreground service handle the lifecycle.

### 4.9 UI Screen: LiveAiScreen

**File:** `ui/liveai/LiveAiScreen.kt`

A Compose screen with three layers stacked in a `Box`:

1. **`TopStatusBar`** — shows "LIVE" dot, title "Recall AI", and current status text
2. **`FluidLightAuraIndicator`** — animated Canvas blobs that shift color based on state
3. **`LiveMascot`** — the Bob character (see section 4.10)
4. **Bottom controls row:**
   - "Interrupt" button (when Speaking) / "Hold" button (when not Speaking) → `viewModel.interruptAi()`
   - "End" button → `viewModel.stopSession()` + `onNavigateBack()`

**`LaunchedEffect(meetingId)`:** Starts the session automatically when the screen opens (only if Idle).
**`LaunchedEffect(uiState)`:** Navigates back automatically when state becomes `Stopped`.

**Color system:**
```
Background: LightBackground = #F8F9FA
Aura (normal): Blue #7BAAF7, Purple #BA8DFA, Cyan #70DDE6
Aura (action): Amber #FFA726, Gold #FFD54F, Orange #FF8A65
```

### 4.10 Mascot Animation: LiveMascot (Bob)

**File:** `ui/liveai/LiveMascot.kt`

Bob is drawn entirely on a Compose `Canvas`. He is a rounded-rectangle body with:
- **Eyes:** 2 round-rect eyes that blink, squint, go wide, etc.
- **Tentacles:** 4 rounded-rectangle appendages that lift and droop based on emotion
- **Orbiting dots:** 3 amber dots that orbit around him during `PerformingAction`

All animations are driven by `InfiniteTransition` and `animateFloatAsState` / `animateColorAsState`.

See [Section 11](#11-bobs-visual-animations--state-matrix) for the complete per-state animation breakdown.

---

## 5. Context System: What Bob Knows

### 5.1 Single-Meeting Mode

When launched from `MeetingDetailScreen` with a real `meetingId`:

```kotlin
// In LiveAiRepository.startSession():
val meeting = meetingDao.getById(meetingId)
val existingSummary = summaryDao.getByMeetingId(meetingId)

initialContext = LiveAiContextPromptBuilder.buildInitialPrompt(
    title = meeting.title,
    defaultSummary = existingSummary?.summary,
    toolInstructions = toolRegistry.getPromptInstructions()
)
```

The system prompt tells Gemini: "You are Recall AI. You're discussing the meeting titled X. Here is its summary. Answer questions about it."

### 5.2 Global Mode (Home Screen Bob)

When launched with `meetingId = 0L`:

```kotlin
val allSummaries = summaryDao.getAllCompletedSummaries()
initialContext = LiveAiContextPromptBuilder.buildGlobalPrompt(
    allSummaries,
    toolInstructions = toolRegistry.getPromptInstructions()
)
```

This fetches up to 20 completed summaries and provides all of them to Bob. The user can then ask "What did we decide in yesterday's standup?" or "What are all my open action items?" and Bob will answer from the combined context.

### 5.3 Smart Context Swap (30+ min meetings)

For meetings longer than 30 minutes, after the session connects, a background coroutine:
1. Fetches the **full raw transcript** (not just the summary) from `TranscriptDao`
2. Builds a dense, LLM-optimized context from it (no XML tags, pure data)
3. Calls `geminiLiveClient.updateContext(densePrompt)`

This hot-swaps the context mid-session via a `clientContent` WebSocket message without interrupting the audio stream. The user might not even notice — the conversation just continues but Bob now has deeper knowledge.

---

## 6. Function Calling: Bob as an Agent

Bob can take actions during a voice conversation. The current implementation supports setting reminders and to-dos. This is implemented via the Gemini Live API's function calling feature.

### 6.1 ToolRegistry

**File:** `data/remote/tools/ToolRegistry.kt`

`ToolRegistry` is the single source of truth for all tools. Adding a new tool requires changes only in this one file.

```kotlin
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>,  // JSON Schema format
    val promptInstructions: String       // natural language for the system prompt
)

fun interface ToolHandler {
    suspend fun execute(args: Map<String, Any?>, meetingId: Long?): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val error: String? = null
)
```

`ToolRegistry` provides three things to the rest of the system:
- `getDeclarations()` → JSON-ready list sent in the WebSocket setup frame so Gemini knows what tools exist
- `getPromptInstructions()` → natural language telling Gemini **when** to use each tool (appended to the system prompt)
- `execute(name, args, meetingId)` → dispatches to the right handler when Gemini calls a tool

### 6.2 How a Tool Call Flows End-to-End

```
1. User says: "Hey Bob, remind me to call Sarah in 20 minutes"

2. Bob (Gemini) responds verbally: "Sure, should I go ahead and set that?"

3. User says: "Yes"

4. Gemini sends via WebSocket:
   { "toolCall": { "functionCalls": [{ "name": "set_reminder", "args": {...}, "id": "..." }] } }

5. GeminiLiveClient.handleIncomingMessage() parses this → emits FunctionCallEvent to functionCallFlow

6. LiveAiRepository.launchFunctionCallHandler() collects the event:
   a. Emits LiveAiState.PerformingAction(meetingId)
      → Bob turns AMBER, wobbles side-to-side, orbiting dots appear
   b. Calls: toolRegistry.execute("set_reminder", args, meetingId)
      → handleSetReminder() inserts Reminder into Room DB
      → ReminderAlarmScheduler.schedule() sets an AlarmManager alarm
   c. geminiLiveClient.sendFunctionResponse(callId, "set_reminder", result)
      → sends { "toolResponse": { "functionResponses": [...] } } back to Gemini
   d. Emits LiveAiState.Listening(meetingId)
      → Bob returns to normal indigo color

7. Gemini receives the toolResponse → speaks: "Done! I've set a reminder for 20 minutes from now."
```

### 6.3 set_reminder Tool (Full Detail)

**Schema sent to Gemini:**
```json
{
  "name": "set_reminder",
  "description": "Set a reminder or alarm for the user...",
  "parameters": {
    "type": "OBJECT",
    "properties": {
      "title": { "type": "STRING", "description": "Short description of the reminder" },
      "minutes_from_now": { "type": "INTEGER", "description": "Minutes from now..." },
      "is_timed": { "type": "BOOLEAN", "description": "true if alarm, false for todo" }
    },
    "required": ["title", "is_timed"]
  }
}
```

**Handler logic:**
```kotlin
private suspend fun handleSetReminder(args, meetingId): ToolResult {
    val title = args["title"] as? String ?: "Reminder"
    val isTimed = args["is_timed"] as? Boolean ?: false
    val minutesFromNow = (args["minutes_from_now"] as? Number)?.toInt()

    val triggerAtMillis = if (isTimed && minutesFromNow != null)
        System.currentTimeMillis() + (minutesFromNow * 60_000L)
    else null

    val reminder = Reminder(
        meetingId = meetingId,  // null for global mode
        type = if (isTimed) ReminderType.ALARM else ReminderType.TODO,
        title = title,
        triggerAtMillis = triggerAtMillis
    )

    val insertedId = reminderDao.insert(reminder)

    if (isTimed && triggerAtMillis != null) {
        alarmScheduler.schedule(reminder.copy(id = insertedId))
        // → AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)
        // → PendingIntent targets ReminderAlarmReceiver
    }

    return ToolResult(success = true, data = mapOf(...))
}
```

### 6.4 Adding a New Tool

All changes go in `ToolRegistry.kt` only:

```kotlin
// Step 1: Define it
"my_new_tool" to Pair(
    ToolDefinition(
        name = "my_new_tool",
        description = "What this tool does (Gemini reads this to decide when to call it)",
        parameters = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "param1" to mapOf("type" to "STRING", "description" to "..."),
            ),
            "required" to listOf("param1")
        ),
        promptInstructions = """
            |When the user asks about X, use my_new_tool.
            |Always confirm before calling it.
        """.trimMargin()
    ),
    // Step 2: Write the handler
    ToolHandler { args, meetingId -> handleMyNewTool(args, meetingId) }
)

// Step 3: Implement the handler
private suspend fun handleMyNewTool(args: Map<String, Any?>, meetingId: Long?): ToolResult {
    // Do work...
    return ToolResult(success = true, data = mapOf("result" to "done"))
}
```

The system (GeminiLiveClient, LiveAiRepository, LiveAiContextPromptBuilder) picks it up automatically.

---

## 7. Dependency Injection Setup

**LiveAiModule** (`di/LiveAiModule.kt`):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LiveAiModule {
    @Provides  // NO @Singleton — fresh per session!
    fun provideGeminiLiveClient(okHttpClient, gson, @Named("gemini_api_key") apiKey): GeminiLiveClient

    @Provides  // NO @Singleton — fresh per session!
    fun provideAudioTrackManager(): AudioTrackManager
}
```

**Why NOT singleton?** Each `LiveAiService` session needs a fresh `GeminiLiveClient` (a new WebSocket) and a fresh `AudioTrackManager` (a new `AudioTrack` hardware buffer). If they were singletons, the second session would reuse a closed WebSocket and a released AudioTrack, causing crashes.

**What IS singleton:**
- `LiveAiStateHolder` — the state bridge (must be shared between service and ViewModel)
- `ToolRegistry` — stateless tool catalogue (safe to share)
- `ReminderAlarmScheduler` — wraps `AlarmManager` (system resource, safe to share)
- `AudioRecorder` — stateless factory for cold flows (safe to share)

**What uses `@AndroidEntryPoint`:**
- `LiveAiService` — Hilt-injected foreground service

---

## 8. Navigation & Entry Points

**Files:** `ui/navigation/Screen.kt`, `ui/navigation/AppNavigation.kt`

```kotlin
// Screen definition
data class LiveAi(val meetingId: Long) : Screen() {
    override val route = "live_ai/{meetingId}"
    // meetingId = 0L means GLOBAL mode
}
```

**Entry points:**
1. **From Dashboard:** "Ask Bob" FAB → `LiveAi(0L)` (global mode, all meetings)
2. **From MeetingDetailScreen:** "Ask Bob" button → `LiveAi(meeting.id)` (single-meeting mode)

**Navigation flow:**
```
Dashboard ──onNavigateToGlobalLiveAi──▶ LiveAi(0L)
MeetingDetail ──onNavigateToLiveAi(id)──▶ LiveAi(id)
```

When `LiveAiState.Stopped` is emitted, `LiveAiScreen`'s `LaunchedEffect(uiState)` automatically calls `onNavigateBack()`.

---

## 9. Critical Audio Pipeline Details

### 9.1 Echo Prevention (isSpeaking Guard)

Without this, Bob hears himself through the microphone and starts responding to his own speech — "I see what you mean" mid-sentence.

```kotlin
@Volatile private var isSpeaking = false

// In launchAudioInput:
audioRecorder.pcmFlow().collect { event ->
    if (event is AudioRecorder.AudioEvent.PcmData) {
        if (!isSpeaking) {  // ← GUARD
            geminiLiveClient.sendAudio(event.bytes)
        }
    }
}

// In launchAudioOutput:
geminiLiveClient.incomingAudioFlow.collect { pcmBytes ->
    if (!isSpeaking) {
        isSpeaking = true  // mute mic
        stateHolder.emit(LiveAiState.Speaking(meetingId))
    }
    audioTrackManager.write(pcmBytes)
}

// In launchNetworkEvents (turnComplete):
geminiLiveClient.turnCompleteFlow.collect {
    isSpeaking = false  // unmute mic
    audioTrackManager.flush()
    stateHolder.emit(LiveAiState.Listening(meetingId))
}
```

`@Volatile` ensures the flag is always read from main memory, not a CPU cache — essential for safe cross-thread access without a full `synchronized` block.

### 9.2 Barge-In / Interrupt

The user can tap "Interrupt" while Bob is speaking:

```kotlin
fun interruptAi() {
    isSpeaking = false         // open mic immediately
    audioTrackManager.flush()  // discard buffered audio Bob was about to play
    stateHolder.emit(LiveAiState.Listening(meetingId))
}
```

`audioTrackManager.flush()` calls `AudioTrack.pause()` + `AudioTrack.flush()` + `AudioTrack.play()` — this instantly cuts off any audio that was buffered but not yet played, so the user doesn't hear trailing words.

### 9.3 Sample Rate Mismatch (16kHz vs 24kHz)

**Input (mic → Gemini):** 16kHz
**Output (Gemini → speaker):** 24kHz

These are two different hardware resources (`AudioRecord` at 16kHz, `AudioTrack` at 24kHz). The Gemini Live API handles the internal conversion — we just need to make sure our `AudioRecord` uses 16kHz and our `AudioTrack` uses 24kHz. Mixing these up will cause chipmunk-speed or slow-motion voice.

### 9.4 Dedicated Playback Thread

```kotlin
private val audioPlaybackDispatcher = Executors.newSingleThreadExecutor { r ->
    Thread(r, "LiveAI-AudioPlayback").apply { priority = Thread.MAX_PRIORITY }
}.asCoroutineDispatcher()

private fun CoroutineScope.launchAudioOutput(meetingId: Long) = launch(audioPlaybackDispatcher) {
    // AudioTrack.write() blocks here — dedicated thread prevents starvation
}
```

`AudioTrack.write()` is a blocking call. If it runs on `Dispatchers.IO` (a shared pool of threads), other I/O tasks can starve the audio thread, causing gaps (stuttering). The dedicated `MAX_PRIORITY` thread ensures audio always gets CPU time.

---

## 10. WebSocket Message Protocol (Exact JSON)

### 10.1 Setup Frame (Client → Server)

Sent immediately when the WebSocket opens:

```json
{
  "setup": {
    "model": "models/gemini-3.1-flash-live-preview",
    "systemInstruction": {
      "parts": [{ "text": "<your context prompt>" }]
    },
    "generationConfig": {
      "responseModalities": ["AUDIO"],
      "mediaResolution": "MEDIA_RESOLUTION_MEDIUM",
      "speechConfig": {
        "voiceConfig": {
          "prebuiltVoiceConfig": { "voiceName": "Zephyr" }
        }
      }
    },
    "contextWindowCompression": {
      "triggerTokens": 104857,
      "slidingWindow": { "targetTokens": 52428 }
    },
    "tools": [{
      "functionDeclarations": [
        {
          "name": "set_reminder",
          "description": "...",
          "parameters": { "type": "OBJECT", "properties": {...}, "required": [...] }
        }
      ]
    }]
  }
}
```

**CRITICAL:** `contextWindowCompression` must be a sibling of `generationConfig` inside `setup` — NOT nested inside `generationConfig`. Placing it inside `generationConfig` causes the server to silently ignore the setup frame (WebSocket closes with code 1007).

**Server responds with:**
```json
{ "setupComplete": {} }
```

### 10.2 Audio Input (Client → Server)

Sent continuously while mic is open and `isSpeaking == false`:

```json
{
  "realtimeInput": {
    "audio": {
      "mimeType": "audio/pcm",
      "data": "<base64-encoded 16kHz PCM bytes>"
    }
  }
}
```

**NOTE:** The field is `"audio"` not `"mediaChunks"`. The old v1alpha API used `mediaChunks` — using it on v1beta causes silent audio drops.

### 10.3 Context Update (Client → Server)

Hot-swaps context mid-session (Smart Context Swap):

```json
{
  "clientContent": {
    "turns": [{
      "role": "user",
      "parts": [{ "text": "SYSTEM UPDATE: Deeper meeting context now available:\n<dense transcript>" }]
    }],
    "turnComplete": true
  }
}
```

### 10.4 Function Response (Client → Server)

Sent after executing a tool, so Gemini can continue the conversation:

```json
{
  "toolResponse": {
    "functionResponses": [{
      "id": "<callId from the toolCall message>",
      "name": "set_reminder",
      "response": {
        "success": true,
        "reminder_id": 42,
        "type": "alarm",
        "message": "Alarm set for 20 minutes from now"
      }
    }]
  }
}
```

### 10.5 Server Audio Reply (Server → Client)

Bob's voice, as raw PCM:

```json
{
  "serverContent": {
    "modelTurn": {
      "parts": [{
        "inlineData": {
          "mimeType": "audio/pcm;rate=24000",
          "data": "<base64-encoded 24kHz PCM bytes>"
        }
      }]
    }
  }
}
```

Multiple `parts` may arrive in sequence before `turnComplete`. Each is decoded and written to the `AudioTrack` immediately.

### 10.6 Turn Complete (Server → Client)

Bob finished speaking:

```json
{
  "serverContent": {
    "turnComplete": true
  }
}
```

This is what triggers `isSpeaking = false` and re-opens the mic.

### 10.7 Function Call (Server → Client)

Gemini wants to call a tool:

```json
{
  "toolCall": {
    "functionCalls": [{
      "id": "set_reminder_1713888000000",
      "name": "set_reminder",
      "args": {
        "title": "Call Sarah",
        "is_timed": true,
        "minutes_from_now": 20
      }
    }]
  }
}
```

**This is a TOP-LEVEL key** `"toolCall"` — NOT inside `"serverContent"`. This is the main way the Live API delivers function calls. The code handles both this location and the `serverContent.modelTurn.parts[].functionCall` location to be safe.

---

## 11. Bob's Visual Animations — State Matrix

### 11.1 Canvas Drawing Structure

Bob is drawn in a single `Canvas` composable. Drawing order (bottom → top):
1. **Tentacles** (4 rounded-rects below the body)
2. **Body / Head** (large rounded-rect)
3. **Eyes** (2 rounded-rects on the head)
4. **Orbiting dots** (3 circles, only visible in `PerformingAction`)

All positions are relative to `centerX` / `centerY`, which are computed from canvas size. This makes Bob scale correctly to any `Modifier.size()`.

### 11.2 Per-State Animation Values

| State | Body Color | Eye Scale | Eye Behavior | Tentacles | Float Speed | Float Amplitude | Wobble | Orbit Dots |
|-------|-----------|-----------|-------------|-----------|-------------|----------------|--------|------------|
| `Idle` | Indigo (#3F51B5) | 1.0x | Blinks every 4s | Neutral (0) | 2500ms | ±12px | No | No |
| `Connecting` | Indigo | 0.4x (squint) | Static squint | Slightly down (+5) | 2500ms | ±12px | No | No |
| `Listening` | Indigo | 1.3x (wide) | Static wide | Raised high (-24) | 4000ms | ±2px | No | No |
| `Speaking` | Indigo (pulses α 0.8–1.0) | 1.0x | Blinks | Neutral (0) | 1000ms | ±15px | No | No |
| `PerformingAction` | Amber (#FF8F00, pulses) | 0.5x (squint) | Static squint | Raised (-12) | 600ms | ±5px | ±8px horizontal | Yes (3 amber dots) |
| `Error` | Red (#D32F2F) | 0.1x (slits) | Static slits | Flat/drooping (+15) | 2500ms | ±12px | No | No |

**Aura (FluidLightAuraIndicator) per state:**

| State | Colors | Speed |
|-------|--------|-------|
| `Idle`, `Connecting`, `Error` | Blue, Purple, Cyan (dim on Connecting/Error) | 6000ms |
| `Listening` | Blue, Purple, Cyan | 4000ms |
| `Speaking` | Blue, Purple, Cyan | 2000ms |
| `PerformingAction` | Amber, Gold, Orange | 1200ms (fastest) |

---

## 12. Known Pitfalls & Hard-Won Lessons

### P1: `contextWindowCompression` placement
**Wrong:** Inside `generationConfig`
**Correct:** Sibling of `generationConfig` inside `setup`
The server closes with code 1007 silently if this is wrong.

### P2: `realtimeInput.audio` vs `realtimeInput.mediaChunks`
**Wrong (old v1alpha):** `"mediaChunks": [{"mimeType":"audio/pcm","data":"..."}]`
**Correct (v1beta):** `"audio": {"mimeType":"audio/pcm","data":"..."}`
Using the old format causes audio to be silently ignored — the session opens fine but Bob never responds to voice.

### P3: toolCall is TOP-LEVEL, not inside serverContent
**Wrong assumption:** `serverContent.modelTurn.parts[].functionCall` (REST API pattern)
**Correct:** `toolCall.functionCalls[]` at the JSON root
Both locations are handled in code, but if you only implement one, tool calls from the other location are silently dropped.

### P4: AudioTrack.write() must run on a dedicated thread
**Wrong:** Running on `Dispatchers.IO` shared pool
**Correct:** Dedicated single-thread `Executors.newSingleThreadExecutor()` at `MAX_PRIORITY`
Without this, audio stutters because the shared pool gets preempted by other I/O.

### P5: GeminiLiveClient and AudioTrackManager must NOT be singletons
**Wrong:** `@Singleton` → second session reuses a closed WebSocket / released AudioTrack → crashes
**Correct:** No scope annotation → Hilt provides a fresh instance per injection point (per service lifecycle)

### P6: Sample rates are different
Mic input: 16kHz (AudioRecord)
Speaker output: 24kHz (AudioTrack)
Mixing these up results in chipmunk speed (too fast) or slowed audio (too slow).

### P7: isSpeaking must be @Volatile
Without `@Volatile`, the JVM may cache the flag value per-thread, causing the mic guard to fail and echoing Bob's own voice back to the API.

### P8: Binary vs Text WebSocket frames
OkHttp has separate callbacks: `onMessage(webSocket, text: String)` and `onMessage(webSocket, bytes: ByteString)`. The Gemini Live API sometimes sends JSON as binary frames. Both callbacks must delegate to the same `handleIncomingMessage()` function.

---

## 13. Full Session Lifecycle (Step-by-Step)

```
USER taps "Ask Bob" (global) or "Ask Bob" (meeting detail)
  │
  ▼
LiveAiScreen opens → LaunchedEffect triggers viewModel.startSession(meetingId)
  │
  ▼
LiveAiViewModel calls ContextCompat.startForegroundService(LiveAiService.startIntent(meetingId))
  │
  ▼
LiveAiService.onStartCommand(ACTION_START):
  1. Check: RecordingService active? → NO (or Toast + stopSelf)
  2. Check: LiveAiState already active? → NO (or ignore)
  3. Request audio focus via AudioFocusHandler → YES (or Error + stopSelf)
  4. startForeground(notification)
  5. repository.startSession(meetingId, serviceScope)
  │
  ▼
LiveAiRepository.startSession():
  1. stateHolder.emit(Connecting)
  2. [DB reads] Fetch meeting + summary / all summaries
  3. Build context prompt string
  4. audioTrackManager.start() → initializes 24kHz AudioTrack
  5. geminiLiveClient.connect(contextPrompt, toolDeclarations):
     a. OkHttp opens WebSocket to Gemini
     b. onOpen → sendSetupFrame(context, tools)
     c. Server sends { "setupComplete": {} }
     d. _setupComplete.complete(Unit) → connect() returns
  6. stateHolder.emit(Listening)
  7. Launch parallel:
     a. launchAudioInput() → begins streaming mic PCM
     b. launchAudioOutput() → begins collecting Gemini audio
     c. launchNetworkEvents() → watching turnComplete + errors
     d. launchFunctionCallHandler() → watching for tool calls
  8. (if single meeting ≥ 30min) launchSmartContextSwap() → hot-swap context after fetch
  │
  ▼  [SESSION IS LIVE — bidirectional voice conversation happening]
  │
  ▼  [USER SPEAKS]
AudioRecord.read() → pcmFlow emits PcmData → launchAudioInput sends to WebSocket
  │
  ▼  [GEMINI RESPONDS]
Server sends { serverContent: { modelTurn: { parts: [{ inlineData: { data: "..." } }] } } }
  → GeminiLiveClient emits to incomingAudioFlow
  → launchAudioOutput: isSpeaking=true, emit Speaking, AudioTrack.write(pcmBytes)
  → (mic is muted during playback)
  │
  ▼  [BOB FINISHES SPEAKING]
Server sends { serverContent: { turnComplete: true } }
  → launchNetworkEvents: isSpeaking=false, audioTrackManager.flush(), emit Listening
  → (mic is open again)
  │
  ▼  [BOB CALLS A TOOL]
Server sends { toolCall: { functionCalls: [...] } }
  → GeminiLiveClient emits FunctionCallEvent to functionCallFlow
  → launchFunctionCallHandler:
      emit PerformingAction → execute tool → send toolResponse → emit Listening
  │
  ▼  [USER TAPS END]
viewModel.stopSession() → startService(stopIntent)
  → LiveAiService.handleStop()
  → repository.stopSession(): cancel job, disconnect WebSocket, release AudioTrack
  → audioFocusHandler.abandonFocus()
  → stopForeground() + stopSelf()
  → stateHolder.emit(Stopped)
  │
  ▼
LiveAiScreen LaunchedEffect detects Stopped → onNavigateBack()
```

---

## 14. File Map (Quick Reference)

| Layer | File | Role |
|-------|------|------|
| State | `service/LiveAiState.kt` | `LiveAiState` sealed class + `LiveAiStateHolder` singleton |
| WebSocket | `data/remote/api/GeminiLiveClient.kt` | Gemini Live WebSocket client, 4 flows |
| Audio In | `service/audio/AudioRecorder.kt` | Mic → 16kHz PCM flow |
| Audio Out | `service/audio/AudioTrackManager.kt` | 24kHz PCM → Speaker |
| Orchestrator | `reposotory/LiveAiRepository.kt` | Session lifecycle, 4 parallel pipelines |
| Service | `service/LiveAiService.kt` | Foreground service, audio focus, guards |
| ViewModel | `ui/liveai/LiveAiViewModel.kt` | UI state relay, intent dispatch |
| Screen | `ui/liveai/LiveAiScreen.kt` | Compose UI, FluidLightAuraIndicator |
| Mascot | `ui/liveai/LiveMascot.kt` | Bob Canvas animation |
| Tools | `data/remote/tools/ToolRegistry.kt` | All tool definitions + handlers |
| Context | `utils/LiveAiContextPromptBuilder.kt` | System prompt construction |
| DI | `di/LiveAiModule.kt` | Non-singleton provides for client + track |
| Model cfg | `config/AiModelConfig.kt` | Model names, URLs, API key |
| Reminders | `data/local/entity/Reminder.kt` | Alarm/Todo entity |
| Scheduler | `service/reminder/ReminderAlarmScheduler.kt` | AlarmManager wrapper |
| Navigation | `ui/navigation/Screen.kt` | `LiveAi(meetingId)` screen definition |

---

*End of BobVoice.md — this document covers every file, every design decision, and every pitfall in the Bob Live AI voice implementation.*
