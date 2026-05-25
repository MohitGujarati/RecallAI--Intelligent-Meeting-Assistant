# RecallAI--Intelligent-Meeting-Assistant
Recall AI – Intelligent Meeting Assistant | Kotlin, Jetpack Compose, Coroutines, Flow, Room, MVVM, Hilt | Feb 2026-Present                                                                                                                              
• Reactive UI & Architecture: Architected a declarative interface from scratch using Jetpack Compose and MVVM, leveraging
Hilt for scalable dependency injection and ViewModels to seamlessly bridge the UI and data layers without tight coupling

• Asynchronous AI Integration: Engineered a real-time streaming pipeline using Kotlin Coroutines and Flow
(StateFlow/SharedFlow) to manage complex concurrency, integrating the Gemini API / OpenAPI to handle concurrent live audio
transcription and AI-powered chat.

• Offline-First Persistence: Developed a highly responsive local database utilizing Room with normalized schemas, leveraging
native SQL-to-Flow reactive queries to instantly reflect asynchronous data updates across the application. 






# RecallAI — Intelligent Meeting Assistant

> An Android app that records meetings, transcribes audio in real-time using Gemini AI, generates structured summaries, and lets you have voice conversations with an AI assistant (Bob) that knows everything about your meetings.

---

## What It Does

| Capability | Description |
|---|---|
| **AI Transcription** | Records meetings and transcribes audio in 30-second chunks using Gemini AI — even in the background |
| **Smart Summaries** | Auto-generates structured summaries with key points and action items after every meeting |
| **Ask Bob** | Voice-to-voice AI assistant that has full context of all your past meetings — ask questions, get answers, set reminders |
| **Live AI Mode** | Bidirectional real-time voice conversation with Gemini Live API during or after a meeting |
| **AI Chat** | Text-based Q&A on any meeting transcript with streaming responses |
| **Smart Reminders** | Bob can set timed alarms and to-dos for you mid-conversation — they survive app restarts and phone reboots |
| **Offline-First** | All data stored locally in Room; app recovers gracefully from process death or low storage |

---

## Demo

## App Screenshots

### Core Navigation
| Login Page | Home Page | Recording Page |
|:---:|:---:|:---:|
| <img src="https://github.com/user-attachments/assets/efda7895-9dbb-4cc4-9bcb-99efdd2dc845" width="250" /> | <img src="https://github.com/user-attachments/assets/388b57e6-9e0a-477a-838c-f3ad4abeee2a" width="250" /> | <img src="https://github.com/user-attachments/assets/8cef572c-5d70-47a0-8d60-4d8babc3fee4" width="250" /> |

### Meeting Details (Summary, Transcript & Chat)
| Summary | Transcript | Chat | Meeting Options |
|:---:|:---:|:---:|:---:|
| <img src="https://github.com/user-attachments/assets/8a953b28-9bd2-4ae1-be15-bc769d89814d" width="200" /> | <img src="https://github.com/user-attachments/assets/0a5ce794-dc8e-44be-a072-bf00c7bd56fe" width="200" /> | <img src="https://github.com/user-attachments/assets/d9fb916d-98ec-47e8-b05a-e9b2ee0a91fc" width="200" /> | <img src="https://github.com/user-attachments/assets/02b5bb88-87ce-4c6f-a1ca-08ee3ace8a40" width="200" /> |

### Smart AI Assistant & Task Management -> Able to call tools to set reminders and todos and summarize full meeting on the fly, voice-to-voice feaure 
| Ask Bob (AI Assistant) | Action Items (To-dos) | Reminders Page | Notification |
|:---:|:---:|:---:|:---:|
| <img src="https://github.com/user-attachments/assets/2eb920eb-270d-4d75-91eb-cb7ee1c397cb" width="200" /> | <img src="https://github.com/user-attachments/assets/8a7ebb48-7bdd-4b8b-9374-3b2ddcdeb7ff" width="200" /> | <img src="https://github.com/user-attachments/assets/593198ce-5e55-40eb-9406-da79b1a2a044" width="200" /> | <img src="https://github.com/user-attachments/assets/208c150e-b6a6-4ea9-8a4b-fd371e80defb" width="200" /> |


---

## Tech Stack

### Android & Kotlin
- **Kotlin** — 100% Kotlin codebase
- **Jetpack Compose** — fully declarative UI, no XML layouts
- **MVVM + Hilt** — clean architecture with constructor-injected ViewModels
- **Kotlin Coroutines + Flow** — StateFlow, SharedFlow, cold flows for reactive pipelines
- **Room** — normalized local database (v4) with SQL-to-Flow reactive queries
- **WorkManager** — reliable background transcription and summary jobs with retry logic
- **Foreground Services** — continuous recording and live AI even when app is backgrounded
- **AlarmManager** — exact timed reminders that survive device reboots

### AI & Networking
- **Gemini REST API** — audio transcription and structured summary generation (streamed token-by-token)
- **Gemini Live WebSocket API** — bidirectional real-time voice (24kHz PCM audio in/out)
- **Gemini Function Calling** — Bob can invoke agent tools (e.g. `set_reminder`) mid-conversation
- **Retrofit + OkHttp** — REST networking with infinite read timeout for streaming
- **Firebase Auth** — Google Sign-In + guest mode

### Architecture Patterns
- Repository pattern isolating data sources from ViewModels
- Process death recovery — resets stuck DB rows and re-enqueues workers on app launch
- Chunk-based audio pipeline with overlap for gapless transcription continuity
- Streaming token flush — summaries and chat responses appear word-by-word in the UI

---

## Architecture Overview

```
UI (Compose Screens)
    │
    ▼
ViewModels (Hilt, StateFlow)
    │
    ├── Repositories
    │       ├── RecordingRepository  →  RecordingService (Foreground)
    │       ├── TranscriptionRepository  →  TranscriptionWorker  →  Gemini REST
    │       ├── SummaryRepository  →  SummaryWorker  →  Gemini REST (streaming)
    │       └── LiveAiRepository  →  LiveAiService (Foreground)  →  Gemini WebSocket
    │
    └── Room Database (6 tables: meetings, audio_chunks, transcripts, summaries, chat_messages, reminders)
```

---

## Key Engineering Highlights

### Real-Time Streaming Pipeline
Transcription and summary tokens stream from Gemini directly into Room, which triggers Flow updates that the UI observes — no polling, no manual refresh. The user sees words appear in real time.

### Voice-to-Voice with Function Calling
Bob (the AI mascot) uses the Gemini Live bidirectional WebSocket. When Bob wants to set a reminder mid-conversation, Gemini emits a `toolCall` event, the app handles it (inserts to DB, schedules AlarmManager), sends a `toolResponse`, and Bob speaks the confirmation — all in under a second.

### Resilient Background Processing
WorkManager transcription jobs use exponential backoff and chunk-level idempotency keys so a job killed mid-flight picks up exactly where it left off. Process death recovery resets any `IN_PROGRESS` chunks to `PENDING` on next launch.

### Smart Context for Bob
In Global mode, Bob receives summaries from **all** past meetings as context. For a specific meeting, it gets the full transcript + summary. Context is refreshed mid-session for long conversations (>30 min) via a smart context swap.

---

## Project Structure

```
app/src/main/java/com/example/recall_ai/
├── Auth/                   Firebase auth repository + UI states
├── config/                 AI model config (URLs, model IDs)
├── data/
│   ├── local/              Room entities, DAOs, type converters
│   └── remote/             Gemini & Whisper API clients, DTOs, parsers
├── di/                     Hilt modules (DB, Network, Repo, Firebase, WorkManager)
├── model/                  Sealed classes (RecordingState, LiveAiState, etc.)
├── receiver/               BroadcastReceivers (alarm firing, boot reschedule)
├── recovery/               Process death recovery manager
├── reposotory/             Recording, Transcription, LiveAi repositories
├── recovery/               Summary repository
├── service/                RecordingService, LiveAiService (foreground)
├── ui/
│   ├── dashboard/          Home screen, Action Items, All Recalls
│   ├── liveai/             Bob mascot screen + animations
│   ├── meetingdetail/      Transcript / Summary / Chat tabs
│   ├── recording/          Recording screen (Native / AI mode)
│   ├── reminders/          Alarms + Todos screen
│   └── theme/              Compose theme, colors, typography
└── worker/                 TranscriptionWorker, SummaryWorker (HiltWorker)
```

---

## Setup

1. Clone the repo
2. Add your API key to `local.properties` (not committed):
   ```
   GEMINI_API_KEY=your_key_here
   ```
3. Add `google-services.json` from Firebase Console to `app/`
4. Run on a device or emulator (API 24+)

---

## Permissions Required

| Permission | Reason |
|---|---|
| `RECORD_AUDIO` | Meeting recording and live voice input |
| `POST_NOTIFICATIONS` | Reminder alerts |
| `SCHEDULE_EXACT_ALARM` | Timed reminders |
| `FOREGROUND_SERVICE` | Background recording and live AI |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |
| `READ_PHONE_STATE` | Auto-pause on incoming calls |

---

## Built By

**Mohit Gujarati** — [mohitgujarati11@gmail.com](mailto:mohitgujarati11@gmail.com)

> Built from scratch — no boilerplate starter, no Firebase ML Kit, no pre-built transcription widget. Every AI integration, streaming pipeline, and recovery mechanism is hand-engineered.

