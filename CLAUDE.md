# RECALLAI CODEBASE MAP
> Machine-readable architecture reference. Update only the section(s) touched by each new feature.
> Sections: [DB] [DI] [NAV] [VM] [REPO] [API] [UI] [FLOW] [BUILD] [WORKERS] [SERVICES] [MODELS] [CONFIG] [RECEIVERS]

---

## [DB] ROOM DATABASE
**File:** `data/AppDatabase.kt` | **Version:** 4 | **Migration:** `MIGRATION_3_4` (in `DatabaseModule`) + `fallbackToDestructiveMigration()` fallback

### Entities → DAOs mapping
| Entity | Table | Key Fields | DAO |
|--------|-------|-----------|-----|
| `Meeting` | `meetings` | id,title,startTime,endTime,durationSeconds,status(RECORDING/PAUSED/STOPPED/COMPLETED/STOPPED_LOW_STORAGE/ERROR),pauseReason(NONE/PHONE_CALL/AUDIO_FOCUS_LOSS),audioSource(BUILT_IN/WIRED_HEADSET/BLUETOOTH),totalChunks,iconEmoji,iconColorHex | `MeetingDao` |
| `AudioChunk` | `audio_chunks` | id,meetingId(FK),chunkIndex,filePath,startTime,durationMs,overlapMs,transcriptionStatus(PENDING/IN_PROGRESS/COMPLETED/FAILED),retryCount,lastError,fileSizeBytes | `AudioChunkDao` |
| `Transcript` | `transcripts` | id,meetingId(FK),chunkId(FK),chunkIndex,text,confidence,source(WHISPER/GEMINI/MOCK/ANDROID_SPEECH),detectedLanguage,timestamp | `TranscriptDao` |
| `Summary` | `summaries` | id,meetingId(FK),status(PENDING/GENERATING/COMPLETED/FAILED),title,summary,actionItems,keyPoints,streamBuffer,errorMessage,retryCount | `SummaryDao` |
| `ChatMessage` | `chat_messages` | id,meetingId(FK),isUser,text,timestamp | `ChatMessageDao` |
| `Reminder` | `reminders` | id,meetingId(FK nullable),type(ALARM/TODO),title,description,triggerAtMillis,status(PENDING/FIRED/DISMISSED/COMPLETED/CANCELLED),createdAt,updatedAt | `ReminderDao` |

### DAO Key Methods (non-obvious only)
```
MeetingDao:       getActiveSession(), observeCompleted(), updateTitleAndIcon(), finalizeSession()
AudioChunkDao:    getPendingChunks(), getFailedChunks(), resetStuckChunks(), resetFailedToPending()
TranscriptDao:    getFullTranscriptText()→SQL group_concat ordered by chunkIndex
SummaryDao:       appendStreamToken(meetingId,token,now), markCompleted(meetingId,title,summary,actionItems,keyPoints), 
                  getGeneratingSummaries(), getAllCompletedSummaries(), observeAllCompletedSummaries()
ChatMessageDao:   appendText(messageId, token)  ← streaming chat tokens
ReminderDao:      insert()→Long, observeAll(), observeAllTodos(), observePendingAlarms(),
                  getPendingAlarmsList() (one-shot for boot reschedule), updateStatus(), deleteById()
```

### Migration history
| From → To | What changed | File |
|-----------|-------------|------|
| 3 → 4 | Added `reminders` table + `index_reminders_meetingId` | `DatabaseModule.MIGRATION_3_4` |

### Add new entity checklist
1. Create `data/local/entity/Foo.kt`
2. Create `data/local/dao/FooDao.kt`
3. Add `Foo::class` to `@Database(entities=[...])` in `AppDatabase.kt`
4. Bump `version = N` in `AppDatabase.kt`
5. **Write a `Migration` object** in `DatabaseModule.kt` (CREATE TABLE SQL) and add to `.addMigrations()`
6. Add `provideFooDao(db)` in `di/DatabaseModule.kt`
7. If entity has enums: add `@TypeConverter` pairs in `Converters.kt`

---

## [DI] HILT MODULES
**All modules in:** `di/`

| Module | Scope | Provides |
|--------|-------|---------|
| `DatabaseModule` | @Singleton | AppDatabase, MIGRATION_3_4, all 6 DAOs |
| `NetworkModule` | @Singleton | Gson(lenient), OkHttpClient(connect15s/write60s/readInfinite), Retrofit(WHISPER_BASE_URL), WhisperApiService, @Named("openai_api_key")="", @Named("gemini_api_key")=BuildConfig.GEMINI_API_KEY |
| `RepositoryModule` | @Singleton | TranscriptionService→GeminiTranscriptionService, SummaryService→GeminiSummaryService |
| `FirebaseModule` | @Singleton | FirebaseAuth |
| `ServiceModule` | @Singleton | AudioManager, NotificationManager, WorkManager |
| `LiveAiModule` | NOT singleton | GeminiLiveClient (fresh/session), AudioTrackManager (fresh/session) |
| `AuthModule` | — | Authrepository |

### Named qualifiers
- `@Named("gemini_api_key")` → BuildConfig.GEMINI_API_KEY
- `@Named("openai_api_key")` → "" (disabled)

### Add new singleton service checklist
1. Write class with `@Inject constructor`
2. If needs special wiring: add `@Provides` in relevant module
3. Inject into ViewModel/Repository via constructor

---

## [NAV] NAVIGATION
**Files:** `ui/navigation/Screen.kt`, `ui/navigation/AppNavigation.kt`

```
Screen objects/data classes:
  Login         → "login"
  SignUp        → "signup"  
  Account       → "account"
  Dashboard     → "dashboard"
  Recording     → "recording"
  AllRecalls    → "all_recalls"
  ActionItems   → "action_items"
  Reminders     → "reminders"
  MeetingDetail(meetingId:Long) → "meeting/{meetingId}"  [LongType]
  LiveAi(meetingId:Long)        → "live_ai/{meetingId}"  [LongType]  ← meetingId=0L means GLOBAL mode

Route graph:
  Login ──onLoginSuccess──→ Dashboard
  Login ──onSignUpClick───→ SignUp
  SignUp ──onSignUpSuccess→ Dashboard
  SignUp ──onGuestClick───→ Dashboard
  Dashboard ──onNavigateToRecording──────→ Recording
  Dashboard ──onNavigateToMeeting(id)────→ MeetingDetail(id)
  Dashboard ──onNavigateToAllRecalls─────→ AllRecalls
  Dashboard ──onNavigateToGlobalLiveAi───→ LiveAi(0L)
  Dashboard ──onNavigateToActionItems────→ ActionItems
  Dashboard ──onNavigateToReminders──────→ Reminders
  Dashboard ──onNavigateToAccount────────→ Account
  MeetingDetail ──onNavigateToLiveAi(id)→ LiveAi(id)
  Account ──onSignedOut──→ Login [popUpTo entire back stack]
```

### Add new screen checklist
1. Add `data class/object Foo(params)` to `Screen.kt`
2. Add `composable(Screen.Foo.route, arguments=[...])` block in `AppNavigation.kt`
3. Pass lambda callbacks to parent screen

---

## [VM] VIEWMODELS
**All @HiltViewModel**

| ViewModel | File | Key Deps | Exposed State | Key Functions |
|-----------|------|----------|---------------|---------------|
| `DashboardViewModel` | `ui/dashbord/` | RecordingRepository | meetings:Flow, recordingState, isRecordingActive | deleteMeeting(), updateMeetingDetails() |
| `ActionItemsViewModel` | `ui/dashbord/` | SummaryRepository,RecordingRepository | checkedItems:Set<String>, actionGroups:List<MeetingActionGroup> | toggleItem(key) |
| `MeetingDetailViewModel` | `ui/meetingdetail/` | SavedStateHandle,RecordingRepository,SummaryRepository,ChatMessageDao,GeminiChatService,Context,VoiceHelper | uiState,chatMessages,isAiTyping,isAiSpeaking,chatError | retrySummary(),askQuestion(),askVoiceQuestion(),stopAiSpeaking(),clearChatError() |
| `LiveAiViewModel` | `ui/liveai/` | Application,LiveAiStateHolder | uiState:LiveAiState | startSession(meetingId),stopSession(),interruptAi() |
| `RemindersViewModel` | `ui/reminders/` | ReminderDao,ReminderAlarmScheduler | uiState:RemindersUiState(alarms,todos) | markCompleted(),dismissAlarm(),deleteReminder() |

### UiState sealed classes
```
SummaryUiState:   Loading | Generating(streamBuffer) | Completed(title,summary,actionItems,keyPoints) | Failed(error,canRetry)
LiveAiState:      Idle | Connecting(meetingId) | Listening(meetingId) | Speaking(meetingId) | PerformingAction(meetingId) | Stopped(meetingId) | Error(meetingId,msg)
RecordingState:   Idle | Recording(meetingId) | Paused(meetingId) | Stopped(meetingId) | Error(meetingId,msg)
RemindersUiState: data class — alarms:List<Reminder>, todos:List<Reminder>
```

---

## [REPO] REPOSITORIES
**Path pattern:** `reposotory/` (typo in codebase — note the folder is `reposotory`) or `recovery/` for SummaryRepository

| Repository | Scope | File Location | Key Public API |
|------------|-------|--------------|----------------|
| `RecordingRepository` | @Singleton | `reposotory/RecordingRepository.kt` | startRecording(mode), stopRecording(), pauseRecording(), resumeRecording(), observeAllMeetings(), deleteMeeting(id), updateMeetingDetails() |
| `TranscriptionRepository` | @Singleton | `reposotory/TranscriptionRepository.kt` | transcribeChunk(chunkId)→TranscribeChunkResult, retryAllFailed(id), getFullTranscript(id) |
| `SummaryRepository` | @Singleton | `recovery/SummaryRepository.kt` | generateSummary(meetingId), resetForRetry(id), observeSummary(id), observeAllCompletedSummaries() |
| `LiveAiRepository` | NOT @Singleton | `reposotory/LiveAiRepository.kt` | startSession(meetingId,scope), stopSession(), interruptAi(); GLOBAL_MEETING_ID=0L; handles function calls (set_reminder) via launchFunctionCallHandler → emits PerformingAction state |

---

## [API] NETWORK / AI SERVICES
**Base path:** `data/remote/`

### Transcription
- Interface: `TranscriptionService.transcribe(audioFile,language,prompt)→Flow<TranscriptionResult>`
- Impl (active): `GeminiTranscriptionService` → POST `{GEMINI_BASE_URL}/{TRANSCRIPTION_MODEL}:generateContent?key=...` base64 inline audio, temp=0.0, maxTokens=1024
- Impl (disabled): `WhisperTranscriptionService` (no API key)
- Impl (dev): `MockTranscriptionService` (400ms delay)

### Summary
- Interface: `SummaryService.generateSummary(transcript,title)→Flow<SummaryStreamEvent>`
- Impl (active): `GeminiSummaryService` → one-shot POST, XML-tagged output parsed by `SummaryParser`
- Output tags: `<title>`, `<summary>`, `<keyPoints>`, `<actionItems>`
- Token flush: every 10 tokens → Room.streamBuffer

### Chat
- `GeminiChatService.askQuestion(transcript,history,question)→Flow<String>` tokens
- Streams tokens → `ChatMessageDao.appendText(messageId, token)` for live UI

### Streaming SSE
- `GeminiStreamingClient.stream(requestBody)→Flow<GeminiStreamResponse>`
- URL: `{GEMINI_BASE_URL}/{CHAT_MODEL}:streamGenerateContent?alt=sse&key=...`

### Live Bidirectional Audio
- `GeminiLiveClient` → WebSocket `{GEMINI_LIVE_BASE_URL}?key=...`
- Model: gemini-3.1-flash-live-preview | Audio: 24kHz PCM | Bidirectional mic+speaker
- **Function calling (agent tools):** Setup frame declares `tools[].functionDeclarations` (e.g. `set_reminder`)
- **Incoming function calls:** Parsed from top-level `toolCall.functionCalls[]` in WebSocket messages (NOT inside `serverContent`) → emitted via `functionCallFlow`
- **Function responses:** `sendFunctionResponse(callId, name, result)` sends `toolResponse.functionResponses[]` back to Gemini
- **Flows exposed:** `incomingAudioFlow`, `turnCompleteFlow`, `errorFlow`, `functionCallFlow`

### DTOs
```
TranscriptionResult(sealed):  Success(text), RetryableError(msg), PermanentError(msg)
SummaryStreamEvent(sealed):   Token(text), Complete(fullText), Error(msg)
SummaryGenerationResult:      Success, AlreadyComplete, RetryableFailure, PermanentFailure
GeminiStreamResponse:         candidates[GeminiCandidate(content(GeminiContent(parts[GeminiPart(text)])))]
FunctionCallEvent:            callId, functionName, args:Map<String,Any?>  (inner class of GeminiLiveClient)
```

---

## [UI] COMPOSE SCREENS
**All in:** `ui/`

| Screen | ViewModel | File | Tabs/Sub-components |
|--------|-----------|------|---------------------|
| `DashboardScreen` | DashboardViewModel | `ui/dashbord/DashboardScreen.kt` | FeatureCards, BottomFabBar, AskBobFab |
| `RecordingScreen` | — | `ui/recording/RecordingScreen.kt` | Mode selector (NATIVE/AI) |
| `AllRecallsScreen` | — | `ui/dashbord/AllRecallsScreen.kt` | — |
| `ActionItemsScreen` | ActionItemsViewModel | `ui/dashbord/ActionItemsScreen.kt` | Grouped by meeting |
| `MeetingDetailScreen` | MeetingDetailViewModel | `ui/meetingdetail/MeetingDetailScreen.kt` | Tab: Transcript \| Summary \| Chat(ChatTabContent) |
| `LiveAiScreen` | LiveAiViewModel | `ui/liveai/LiveAiScreen.kt` | LiveMascot animation, FluidLightAuraIndicator |
| `RemindersScreen` | RemindersViewModel | `ui/reminders/RemindersScreen.kt` | Alarms list, Todos checklist |
| `LoginScreen/SignupScreen/AccountScreen` | Auth | `ui/login/` | Firebase Auth UI |

### Theme / Colors (key)
**File:** `ui/theme/Color.kt`
```
ColorNavy=#0D3E5E  ColorSurface=#FFF  ColorBackground=#F6F7F8  ColorSurfaceVariant=#F1F3F5
ColorBorder=#E5E7EB  ColorOnSurfaceDim=#6B7280  ColorTextSlate400=#94A3B8  ColorTextSlate900=#0F172A
IndigoFab=#3D3DAA  IndigoHigh=#5A5AEE  ColorRecordRed=#EF4444  ColorDone=#22C55E
ColorProcessing=#3B82F6  ColorWarning=#F59E0B  ColorError=#EF4444
```

### LiveMascot ("Bob") — Animation State Matrix
**Files:** `ui/liveai/LiveMascot.kt`, `ui/liveai/LiveAiScreen.kt`

Bob is a Canvas-drawn character (indigo rounded-rect body, 2 eyes, 4 tentacles) with state-driven animations:

| State | Body Color | Eyes | Tentacles | Float | Unique Effects | Aura Colors | Status Text |
|-------|-----------|------|-----------|-------|---------------|-------------|-------------|
| Idle | Indigo | 1.0x, blinks | Neutral 0 | Med 2500ms ±12px | — | Blue/Purple/Cyan | "Initializing..." |
| Connecting | Indigo | 0.4x squint | Raised +5 | Med 2500ms ±12px | — | Blue/Purple/Cyan (dim) | "Connecting..." |
| Listening | Indigo | 1.3x wide | High -24 | Slow 4000ms ±2px | — | Blue/Purple/Cyan | "Listening..." |
| Speaking | Indigo (pulses) | 1.0x, blinks | Neutral 0 | Fast 1000ms ±15px | Alpha pulse 0.8–1.0 | Blue/Purple/Cyan (fast) | "Speaking..." |
| **PerformingAction** | **Amber** (pulses) | **0.5x squint** | Raised -12 | Quick 600ms ±5px | **Horizontal wobble ±8px, 3 orbiting amber dots** | **Amber/Gold/Orange** (fastest 1200ms) | **"Working..."** |
| Error | Red | 0.1x slits | Flat +15 | Med 2500ms ±12px | — | Blue/Purple/Cyan (slow) | "Connection Lost" |

---

## [FLOW] DATA FLOWS (end-to-end)

### Recording → Transcription → Summary
```
User taps Record
  → RecordingRepository.startRecording(mode)
  → RecordingService.ACTION_START
    [AI mode]  AudioRecorder → 30s .m4a chunks → ChunkManager saves
               → TranscriptionWorker.enqueue(chunkId)
                 → Mark IN_PROGRESS → file guard → continuity prompt
                 → GeminiTranscriptionService.transcribe() → Flow<TranscriptionResult>
                 → Insert Transcript row, mark chunk COMPLETED, delete audio file
                 → checkMeetingCompletion() → if all chunks done → SummaryWorker.enqueue(meetingId)
                   → Idempotency check → fetch getFullTranscriptText()
                   → Insert Summary(GENERATING) → GeminiSummaryService.generateSummary()
                   → Batch 10 tokens → appendStreamToken() → streamBuffer in Room
                   → On Complete → SummaryParser.parse() → markCompleted()
    [NATIVE]   ContinuousRecognitionManager (Android SpeechRecognizer) → live Transcript rows

UI observes via Flow:
  Transcripts: TranscriptDao.observeTranscriptsForMeeting() → MeetingDetailViewModel
  Summary:     SummaryDao.observeByMeetingId() → shows streamBuffer while GENERATING, structured fields when COMPLETED
```

### Chat Q&A
```
User types question → MeetingDetailViewModel.askQuestion(q)
  → Insert ChatMessage(isUser=true)
  → GeminiChatService.askQuestion(transcript, chatHistory, q) → Flow<String> tokens
  → Insert empty ChatMessage(isUser=false, id=X)
  → Collect tokens → ChatMessageDao.appendText(X, token)
  → UI observes ChatMessageDao.observeByMeetingId() → live streaming effect
```

### Live AI Voice
```
LiveAiViewModel.startSession(meetingId)
  → startForegroundService(LiveAiService, EXTRA_MEETING_ID)
  → Guard: reject if RecordingService active (mic conflict)
  → LiveAiRepository.startSession(meetingId, scope)
    Context: meetingId=0L → aggregate all SummaryDao.getAllCompletedSummaries()
             meetingId≠0L → single meeting transcript + summary
    → GeminiLiveClient.connect(WebSocket)
    → Launch parallel:
        INPUT:   AudioRecorder.pcmFlow() → sendAudioBytes (skip when isSpeaking=true, echo guard)
        OUTPUT:  incomingAudioFlow → AudioTrackManager.write() (24kHz)
        EVENTS:  turn-complete, errors → LiveAiStateHolder
        FUNCS:   functionCallFlow → launchFunctionCallHandler (see below)
    → If meeting ≥30min: launchSmartContextSwap() after 10s → push updated context
  → User taps Stop → LiveAiService.handleStop() → WebSocket.close(), release AudioTrack+AudioFocus
```

### Live AI Function Calling (Agent Actions)
```
Gemini invokes a declared tool (e.g. set_reminder)
  → WebSocket message: { "toolCall": { "functionCalls": [...] } }
  → GeminiLiveClient.handleIncomingMessage() parses toolCall (top-level, NOT inside serverContent)
  → Emits FunctionCallEvent to functionCallFlow
  → LiveAiRepository.launchFunctionCallHandler collects:
    1. Emits LiveAiState.PerformingAction(meetingId) → Bob turns amber, wobbles, orbiting dots
    2. Dispatches to handler (e.g. handleSetReminder)
    3. Handler: insert Reminder to Room → schedule AlarmManager (if timed)
    4. Sends toolResponse back via sendFunctionResponse()
    5. Emits LiveAiState.Listening(meetingId) → Bob returns to normal
  → Gemini receives toolResponse and speaks confirmation to user

IMPORTANT: toolCall is a TOP-LEVEL WebSocket message key — NOT nested inside serverContent.modelTurn.parts.
          The REST API uses functionCall inside parts; the Live WebSocket uses toolCall at root level.
```

### Reminder Lifecycle (Alarm)
```
set_reminder(is_timed=true, minutes_from_now=N)
  → Reminder(type=ALARM, triggerAtMillis=now+N*60000) inserted in Room
  → ReminderAlarmScheduler.schedule() → AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP)
  → PendingIntent targets ReminderAlarmReceiver with EXTRA_REMINDER_ID
  → [Time passes, app may be closed]
  → AlarmManager fires → ReminderAlarmReceiver.onReceive()
    → goAsync() + Dispatchers.IO
    → ReminderDao.updateStatus(FIRED) → post HIGH-priority notification
  → [Device reboots] → BootRescheduleReceiver re-schedules all PENDING alarms
```

### Process Death Recovery
```
RecallApplication.onCreate → ProcessDeathRecoveryManager.recover()
  → Reset AudioChunks stuck IN_PROGRESS → PENDING
  → Re-enqueue TranscriptionWorker for each PENDING chunk
  → Reset Summaries stuck GENERATING → re-enqueue SummaryWorker
  → Clean up stale LiveAi state in LiveAiStateHolder
```

---

## [BUILD] BUILD CONFIG
**Files:** `app/build.gradle.kts`, `gradle/libs.versions.toml`

```
compileSdk=36  minSdk=24  targetSdk=36  Java=VERSION_11  jvmTarget=11

BuildConfigFields (sourced from local.properties):
  GEMINI_API_KEY  →  BuildConfig.GEMINI_API_KEY  →  AiModelConfig.GEMINI_API_KEY
  OPENAI_API_KEY  →  BuildConfig.OPENAI_API_KEY  →  AiModelConfig.OPENAI_API_KEY (currently "")

Key deps versions:
  compose-bom=2024.02.00  material3=bundled  navigation-compose=2.7.x
  hilt=2.50  room=2.6.1  retrofit=2.9.0  okhttp=4.12.0
  coroutines=1.7.3  lifecycle=2.7.0  work=2.9.0
  firebase-bom=34.11.0  play-services-auth=21.2.0

Plugins: kotlin-android, kotlin-compose, kapt, hilt, google-services
```

---

## [WORKERS] WORKMANAGER
**Path:** `worker/`

| Worker | Input Keys | Unique Name | Backoff | Retry |
|--------|-----------|-------------|---------|-------|
| `TranscriptionWorker` | KEY_CHUNK_ID, KEY_MEETING_ID | "transcription_chunk_{chunkId}" | Exponential 30s | Result.retry() on RetryableFailure |
| `SummaryWorker` | KEY_MEETING_ID | "summary_{meetingId}" | Exponential 60s | Max 3 attempts (hard cap) |

Both are `@HiltWorker`. TranscriptionWorker has network constraint.

---

## [SERVICES] ANDROID SERVICES
**Path:** `service/`

| Service | Type | Actions | Guards |
|---------|------|---------|--------|
| `RecordingService` | Foreground (mic) | ACTION_START, ACTION_STOP, ACTION_PAUSE, ACTION_RESUME | — |
| `LiveAiService` | Foreground (mic) | ACTION_START, ACTION_STOP | Rejects if RecordingService active |

**Supporting components:**
```
RecordingStateHolder  → StateFlow<RecordingState>  (singleton, shared across Service+VM)
LiveAiStateHolder     → StateFlow<LiveAiState>     (singleton) — includes PerformingAction state
AudioRecorder         → PCM stream or .m4a chunks
ChunkManager          → saves 30s chunks, CHUNK_DURATION_MS=30000, OVERLAP_MS=2000
AudioTrackManager     → 24kHz playback for Live AI
ContinuousRecognitionManager → Android SpeechRecognizer wrapper (NATIVE mode)
PhoneCallHandler      → pause on incoming call
AudioFocusHandler     → request/abandon audio focus
StorageMonitor        → stop on low storage
ProcessDeathRecoveryManager → resets stuck DB rows on app launch
ReminderAlarmScheduler → @Singleton, schedule/cancel via AlarmManager (exact or inexact on API 31+)
```

---

## [RECEIVERS] BROADCAST RECEIVERS
**Path:** `receiver/`

| Receiver | Trigger | Action | AndroidEntryPoint |
|----------|---------|--------|-------------------|
| `ReminderAlarmReceiver` | AlarmManager fires | Reads Reminder from Room, marks FIRED, posts high-priority notification | Yes |
| `BootRescheduleReceiver` | `ACTION_BOOT_COMPLETED` | Re-schedules all PENDING alarms; marks missed ones as FIRED | Yes |

Both use `goAsync()` + `CoroutineScope(Dispatchers.IO)` pattern for safe background DB work.

---

## [MODELS] KEY ENUMS & SEALED CLASSES
```kotlin
MeetingStatus:        RECORDING, PAUSED, STOPPED, COMPLETED, STOPPED_LOW_STORAGE, ERROR
TranscriptionStatus:  PENDING, IN_PROGRESS, COMPLETED, FAILED
SummaryStatus:        PENDING, GENERATING, COMPLETED, FAILED
TranscriptSource:     WHISPER, GEMINI, MOCK, ANDROID_SPEECH
PauseReason:          NONE, PHONE_CALL, AUDIO_FOCUS_LOSS
AudioSource:          BUILT_IN, WIRED_HEADSET, BLUETOOTH
TranscriptionMode:    NATIVE, AI
ReminderType:         ALARM, TODO
ReminderStatus:       PENDING, FIRED, DISMISSED, COMPLETED, CANCELLED

RecordingState(sealed):  Idle | Recording(meetingId) | Paused(meetingId) | Stopped(meetingId) | Error(meetingId,msg)
LiveAiState(sealed):     Idle | Connecting(meetingId) | Listening(meetingId) | Speaking(meetingId) | PerformingAction(meetingId) | Stopped(meetingId) | Error(meetingId,msg)
SummaryUiState(sealed):  Loading | Generating(streamBuffer) | Completed(title,summary,actionItems,keyPoints) | Failed(error,canRetry)
TranscribeChunkResult:   Success | RetryableFailure | PermanentFailure
SummaryGenerationResult: Success | AlreadyComplete | RetryableFailure | PermanentFailure
```

---

## [CONFIG] AI MODEL CONFIG
**File:** `config/AiModelConfig.kt`

```
GEMINI_TRANSCRIPTION_MODEL = "gemini-3-flash-preview"
GEMINI_SUMMARY_MODEL       = "gemini-3-flash-preview"
GEMINI_CHAT_MODEL          = "gemini-3-flash-preview"
GEMINI_LIVE_MODEL          = "gemini-3.1-flash-live-preview"
WHISPER_MODEL              = "whisper-1"
GEMINI_BASE_URL            = "https://generativelanguage.googleapis.com/v1beta/models"
GEMINI_LIVE_BASE_URL       = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
WHISPER_BASE_URL           = "https://api.openai.com/"
OPENAI_API_KEY             = ""   ← disabled
GEMINI_API_KEY             = BuildConfig.GEMINI_API_KEY
```

---

## [AUTH] AUTHENTICATION
**Path:** `Auth/`
- Firebase Auth (`FirebaseAuth` via `FirebaseModule`)
- `Authrepository` (note lowercase 'r' in filename) — auth state + sign in/out
- `AuthUiState` — sealed states for login/signup screens
- Google Sign-In via `play-services-auth`
- On sign-out: navigate to Login, pop entire back stack

---

## NEW FEATURE QUICK-START GUIDE

### Feature needs a new screen
→ Update: `Screen.kt`, `AppNavigation.kt`, new `*Screen.kt` + optional `*ViewModel.kt`

### Feature needs new data stored locally
→ Update: new entity `.kt`, new DAO `.kt`, `AppDatabase.kt` (entities list + version bump), `DatabaseModule.kt` (new @Provides + Migration SQL)
→ **CRITICAL:** Always write a `Migration` object — `fallbackToDestructiveMigration()` is a dev safety net, NOT a strategy. Users lose all meetings if it triggers.
→ Add enum TypeConverters in `Converters.kt` if the entity has enums

### Feature needs to call Gemini
→ Reuse: `GeminiStreamingClient.stream()` or `GeminiChatService` pattern
→ Key: inject `@Named("gemini_api_key")` + `OkHttpClient` + `Gson`

### Feature needs background work
→ Add `@HiltWorker` class in `worker/`, enqueue via `WorkManager` (injected from `ServiceModule`)

### Feature needs a foreground service
→ Follow `LiveAiService` pattern: ACTION_START/STOP, guard against conflicts, update `AndroidManifest.xml`

### Feature needs global AI context (all meetings)
→ Use `SummaryDao.getAllCompletedSummaries()` — already returns all COMPLETED summaries ordered by date

### Feature needs a new Live AI agent tool (function calling)
1. Add `functionDeclarations` entry in `GeminiLiveClient.sendSetupFrame()` tools array
2. Add handler case in `LiveAiRepository.launchFunctionCallHandler()` when-block
3. Write a `handleXxx()` suspend function — do work, then call `geminiLiveClient.sendFunctionResponse()`
4. State flow: PerformingAction → [do work] → Listening (automatic in the collector)
5. Update `LiveAiContextPromptBuilder` system prompt so the model knows when to invoke the tool

### Feature needs system alarms / scheduled notifications
→ Follow `ReminderAlarmScheduler` pattern: `AlarmManager.setExactAndAllowWhileIdle()` + `BroadcastReceiver`
→ Declare receiver in `AndroidManifest.xml` (exported=false)
→ Use `goAsync()` + coroutine in receiver for DB work
→ Handle device reboots via `BootRescheduleReceiver`

---

## [MANIFEST] PERMISSIONS & COMPONENTS

### Permissions
```
RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE
POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM
INTERNET, READ_PHONE_STATE, BLUETOOTH_CONNECT
RECEIVE_BOOT_COMPLETED, WAKE_LOCK
```

### Declared components
```
Activity:   MainActivity
Services:   RecordingService (fg:mic), LiveAiService (fg:mic)
Receivers:  ReminderAlarmReceiver (exported=false), BootRescheduleReceiver (BOOT_COMPLETED)
Providers:  InitializationProvider (WorkManager init disabled for Hilt)
```

---
*Last updated: 2026-04-04 | DB version: 4 | Note: folder typo `reposotory` (not `repository`)*
