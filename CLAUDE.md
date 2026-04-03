# RECALLAI CODEBASE MAP
> Machine-readable architecture reference. Update only the section(s) touched by each new feature.
> Sections: [DB] [DI] [NAV] [VM] [REPO] [API] [UI] [FLOW] [BUILD] [WORKERS] [SERVICES] [MODELS] [CONFIG]

---

## [DB] ROOM DATABASE
**File:** `data/AppDatabase.kt` | **Version:** 3 | **Migration:** `fallbackToDestructiveMigration()` (dev only)

### Entities → DAOs mapping
| Entity | Table | Key Fields | DAO |
|--------|-------|-----------|-----|
| `Meeting` | `meetings` | id,title,startTime,endTime,durationSeconds,status(RECORDING/PAUSED/STOPPED/COMPLETED/STOPPED_LOW_STORAGE/ERROR),pauseReason(NONE/PHONE_CALL/AUDIO_FOCUS_LOSS),audioSource(BUILT_IN/WIRED_HEADSET/BLUETOOTH),totalChunks,iconEmoji,iconColorHex | `MeetingDao` |
| `AudioChunk` | `audio_chunks` | id,meetingId(FK),chunkIndex,filePath,startTime,durationMs,overlapMs,transcriptionStatus(PENDING/IN_PROGRESS/COMPLETED/FAILED),retryCount,lastError,fileSizeBytes | `AudioChunkDao` |
| `Transcript` | `transcripts` | id,meetingId(FK),chunkId(FK),chunkIndex,text,confidence,source(WHISPER/GEMINI/MOCK/ANDROID_SPEECH),detectedLanguage,timestamp | `TranscriptDao` |
| `Summary` | `summaries` | id,meetingId(FK),status(PENDING/GENERATING/COMPLETED/FAILED),title,summary,actionItems,keyPoints,streamBuffer,errorMessage,retryCount | `SummaryDao` |
| `ChatMessage` | `chat_messages` | id,meetingId(FK),isUser,text,timestamp | `ChatMessageDao` |

### DAO Key Methods (non-obvious only)
```
MeetingDao:       getActiveSession(), observeCompleted(), updateTitleAndIcon(), finalizeSession()
AudioChunkDao:    getPendingChunks(), getFailedChunks(), resetStuckChunks(), resetFailedToPending()
TranscriptDao:    getFullTranscriptText()→SQL group_concat ordered by chunkIndex
SummaryDao:       appendStreamToken(meetingId,token,now), markCompleted(meetingId,title,summary,actionItems,keyPoints), 
                  getGeneratingSummaries(), getAllCompletedSummaries(), observeAllCompletedSummaries()
ChatMessageDao:   appendText(messageId, token)  ← streaming chat tokens
```

### Add new entity checklist
1. Create `data/local/entity/Foo.kt`
2. Create `data/local/dao/FooDao.kt`
3. Add `Foo::class` to `@Database(entities=[...])` in `AppDatabase.kt`
4. Bump `version = N` in `AppDatabase.kt`
5. Add `provideFooDao(db)` in `di/DatabaseModule.kt`

---

## [DI] HILT MODULES
**All modules in:** `di/`

| Module | Scope | Provides |
|--------|-------|---------|
| `DatabaseModule` | @Singleton | AppDatabase, all 5 DAOs |
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
| `LiveAiViewModel` | `ui/liveai/` | Application,LiveAiStateHolder | uiState:LiveAiState | startSession(meetingId),stopSession() |

### UiState sealed classes
```
SummaryUiState:   Loading | Generating(streamBuffer) | Completed(title,summary,actionItems,keyPoints) | Failed(error,canRetry)
LiveAiState:      Idle | Connecting(meetingId) | Listening(meetingId) | Speaking(meetingId) | Stopped(meetingId) | Error(meetingId,msg)
RecordingState:   Idle | Recording(meetingId) | Paused(meetingId) | Stopped(meetingId) | Error(meetingId,msg)
```

---

## [REPO] REPOSITORIES
**Path pattern:** `reposotory/` (typo in codebase — note the folder is `reposotory`) or `recovery/` for SummaryRepository

| Repository | Scope | File Location | Key Public API |
|------------|-------|--------------|----------------|
| `RecordingRepository` | @Singleton | `reposotory/RecordingRepository.kt` | startRecording(mode), stopRecording(), pauseRecording(), resumeRecording(), observeAllMeetings(), deleteMeeting(id), updateMeetingDetails() |
| `TranscriptionRepository` | @Singleton | `reposotory/TranscriptionRepository.kt` | transcribeChunk(chunkId)→TranscribeChunkResult, retryAllFailed(id), getFullTranscript(id) |
| `SummaryRepository` | @Singleton | `recovery/SummaryRepository.kt` | generateSummary(meetingId), resetForRetry(id), observeSummary(id), observeAllCompletedSummaries() |
| `LiveAiRepository` | NOT @Singleton | `reposotory/LiveAiRepository.kt` | startSession(meetingId,scope), stopSession(); GLOBAL_MEETING_ID=0L |

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

### DTOs
```
TranscriptionResult(sealed):  Success(text), RetryableError(msg), PermanentError(msg)
SummaryStreamEvent(sealed):   Token(text), Complete(fullText), Error(msg)
SummaryGenerationResult:      Success, AlreadyComplete, RetryableFailure, PermanentFailure
GeminiStreamResponse:         candidates[GeminiCandidate(content(GeminiContent(parts[GeminiPart(text)])))]
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
| `LiveAiScreen` | LiveAiViewModel | `ui/liveai/LiveAiScreen.kt` | LiveMascot animation |
| `LoginScreen/SignupScreen/AccountScreen` | Auth | `ui/login/` | Firebase Auth UI |

### Theme / Colors (key)
**File:** `ui/theme/Color.kt`
```
ColorNavy=#0D3E5E  ColorSurface=#FFF  ColorBackground=#F6F7F8  ColorSurfaceVariant=#F1F3F5
ColorBorder=#E5E7EB  ColorOnSurfaceDim=#6B7280  ColorTextSlate400=#94A3B8  ColorTextSlate900=#0F172A
IndigoFab=#3D3DAA  IndigoHigh=#5A5AEE  ColorRecordRed=#EF4444  ColorDone=#22C55E
ColorProcessing=#3B82F6  ColorWarning=#F59E0B  ColorError=#EF4444
```

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
    → If meeting ≥30min: launchSmartContextSwap() after 10s → push updated context
  → User taps Stop → LiveAiService.handleStop() → WebSocket.close(), release AudioTrack+AudioFocus
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
LiveAiStateHolder     → StateFlow<LiveAiState>     (singleton)
AudioRecorder         → PCM stream or .m4a chunks
ChunkManager          → saves 30s chunks, CHUNK_DURATION_MS=30000, OVERLAP_MS=2000
AudioTrackManager     → 24kHz playback for Live AI
ContinuousRecognitionManager → Android SpeechRecognizer wrapper (NATIVE mode)
PhoneCallHandler      → pause on incoming call
AudioFocusHandler     → request/abandon audio focus
StorageMonitor        → stop on low storage
ProcessDeathRecoveryManager → resets stuck DB rows on app launch
```

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

RecordingState(sealed):  Idle | Recording(meetingId) | Paused(meetingId) | Stopped(meetingId) | Error(meetingId,msg)
LiveAiState(sealed):     Idle | Connecting(meetingId) | Listening(meetingId) | Speaking(meetingId) | Stopped(meetingId) | Error(meetingId,msg)
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
→ Update: new entity `.kt`, new DAO `.kt`, `AppDatabase.kt` (entities list + version bump), `DatabaseModule.kt` (new @Provides)

### Feature needs to call Gemini
→ Reuse: `GeminiStreamingClient.stream()` or `GeminiChatService` pattern
→ Key: inject `@Named("gemini_api_key")` + `OkHttpClient` + `Gson`

### Feature needs background work
→ Add `@HiltWorker` class in `worker/`, enqueue via `WorkManager` (injected from `ServiceModule`)

### Feature needs a foreground service
→ Follow `LiveAiService` pattern: ACTION_START/STOP, guard against conflicts, update `AndroidManifest.xml`

### Feature needs global AI context (all meetings)
→ Use `SummaryDao.getAllCompletedSummaries()` — already returns all COMPLETED summaries ordered by date

---
*Last updated: 2026-04-02 | DB version: 3 | Note: folder typo `reposotory` (not `repository`)*
