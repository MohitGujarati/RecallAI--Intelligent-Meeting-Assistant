package com.example.recall_ai.ui.meetingdetail

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recall_ai.data.local.dao.ChatMessageDao
import com.example.recall_ai.data.local.entity.ChatMessage
import com.example.recall_ai.data.local.entity.Meeting
import com.example.recall_ai.data.local.entity.MeetingStatus
import com.example.recall_ai.data.local.entity.Summary
import com.example.recall_ai.data.local.entity.SummaryStatus
import com.example.recall_ai.data.local.entity.Transcript
import com.example.recall_ai.data.remote.api.GeminiChatService
import com.example.recall_ai.data.repository.RecordingRepository
import com.example.recall_ai.data.repository.SummaryRepository
import com.example.recall_ai.ui.navigation.Screen
import com.example.recall_ai.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI state types
// ─────────────────────────────────────────────────────────────────────────────

/**
 * All possible states for the Summary tab.
 *
 * This is a display-layer projection of the raw [Summary] entity +
 * [MeetingStatus]. The composable has zero when/if logic on domain
 * types — it switches only on this sealed class.
 *
 * State machine:
 *   WaitingForTranscription  meeting.status == STOPPED (chunks still processing)
 *          │
 *          ▼
 *   Pending                  summary row absent or status == PENDING
 *          │
 *          ▼
 *   Generating               status == GENERATING — token stream in-flight
 *         ╱ ╲
 *   Complete   Failed        COMPLETED or FAILED after stream ends
 *                │
 *            (retry) → Pending → Generating → Complete | Failed
 */
sealed class SummaryUiState {

    /**
     * Meeting is still transcribing. Summary pipeline has not triggered yet.
     *
     * @param completedChunks  Transcript rows already written (proxy for done chunks)
     * @param totalChunks      From Meeting.totalChunks — denominator for progress
     */
    data class WaitingForTranscription(
        val completedChunks: Int,
        val totalChunks:     Int
    ) : SummaryUiState()

    /** Summary row absent or PENDING — WorkManager not yet dispatched */
    object Pending : SummaryUiState()

    /**
     * LLM is streaming. Raw token buffer shown verbatim while sections
     * are shimmer-skeletonised.
     *
     * @param streamBuffer  Accumulated raw text — grows word-by-word from Room
     * @param retryCount    > 0 → show "Attempt N" badge so user knows it re-tried
     */
    data class Generating(
        val streamBuffer: String,
        val retryCount:   Int = 0
    ) : SummaryUiState()

    /**
     * Four structured sections are ready to display.
     * Every field is non-null — the ViewModel strips nulls before arriving here.
     */
    data class Complete(
        val title:       String,
        val summary:     String,
        val keyPoints:   List<String>,
        val actionItems: List<String>
    ) : SummaryUiState()

    /**
     * Generation failed after exhausting retries or a permanent API error.
     *
     * @param message    Specific user-facing error from [GeminiSummaryService]
     * @param retryCount How many times we've tried — shown as "Attempt N of 3"
     * @param canRetry   False once retryCount >= MAX_RETRIES — hides the button
     */
    data class Failed(
        val message:    String,
        val retryCount: Int,
        val canRetry:   Boolean
    ) : SummaryUiState()
}

/** State for the Transcript tab */
data class TranscriptUiState(
    val segments:    List<Transcript> = emptyList(),
    val isComplete:  Boolean          = false
)

/** Screen-level state driving the entire MeetingDetail UI */
data class MeetingDetailUiState(
    val meeting:    Meeting?          = null,
    val summary:    SummaryUiState    = SummaryUiState.Pending,
    val transcript: TranscriptUiState = TranscriptUiState()
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

private const val MAX_RETRY_COUNT = 5

@HiltViewModel
class MeetingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository,
    private val summaryRepository:   SummaryRepository,
    private val chatMessageDao:      ChatMessageDao,
    private val chatService:         GeminiChatService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val meetingId: Long =
        savedStateHandle.get<Long>(Screen.MeetingDetail.ARG) ?: -1L

    /**
     * Combined state — one snapshot covers all three Room flows.
     *
     * Why combine() and not three separate StateFlows?
     * Because Room emits independently — a separate meeting observer and
     * summary observer would briefly show mismatched states (e.g. meeting
     * is COMPLETED but summary not yet fetched). combine() serialises the
     * three into a single consistent update.
     *
     * Flow topology:
     *   observeMeeting(id)    ─┐
     *   observeSummary(id)    ─┼─▶ combine() ─▶ map to UiState ─▶ stateIn()
     *   observeTranscripts(id)─┘
     */
    val uiState: StateFlow<MeetingDetailUiState> = combine(
        recordingRepository.observeMeeting(meetingId),
        summaryRepository.observeSummary(meetingId),
        recordingRepository.observeTranscripts(meetingId)
    ) { meeting, summary, transcripts ->
        MeetingDetailUiState(
            meeting    = meeting,
            summary    = mapSummaryState(summary, meeting, transcripts.size),
            transcript = TranscriptUiState(
                segments   = transcripts,
                isComplete = meeting?.status == MeetingStatus.COMPLETED
            )
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeetingDetailUiState()
    )

    // ── User intent ───────────────────────────────────────────────────────

    /**
     * Resets the FAILED summary row to PENDING, then cancels the stale
     * WorkManager job and immediately re-enqueues a fresh one.
     *
     * Why cancel-and-reenqueue instead of just KEEP?
     * KEEP would no-op if a job already exists in the queue waiting on
     * backoff delay. We want the retry to start now, not after 60 s.
     */
    fun retrySummary() {
        viewModelScope.launch {
            summaryRepository.resetForRetry(meetingId)
            SummaryWorker.cancelAndReenqueue(context, meetingId)
        }
    }

    // ── State mapping ─────────────────────────────────────────────────────

    private fun mapSummaryState(
        summary:         Summary?,
        meeting:         Meeting?,
        completedChunks: Int
    ): SummaryUiState {

        // Meeting still transcribing — summary worker not yet triggered
        if (meeting?.status == MeetingStatus.STOPPED) {
            return SummaryUiState.WaitingForTranscription(
                completedChunks = completedChunks,
                totalChunks     = meeting.totalChunks
            )
        }

        if (summary == null) return SummaryUiState.Pending

        return when (summary.status) {

            SummaryStatus.PENDING -> SummaryUiState.Pending

            SummaryStatus.GENERATING -> SummaryUiState.Generating(
                streamBuffer = summary.streamBuffer,
                retryCount   = summary.retryCount
            )

            SummaryStatus.COMPLETED -> SummaryUiState.Complete(
                title       = summary.title       ?: "Summary",
                summary     = summary.summary     ?: "",
                keyPoints   = splitLines(summary.keyPoints),
                actionItems = splitLines(summary.actionItems)
            )

            SummaryStatus.FAILED -> SummaryUiState.Failed(
                message    = summary.errorMessage ?: "Summary generation failed.",
                retryCount = summary.retryCount,
                canRetry   = summary.retryCount < MAX_RETRY_COUNT
            )
        }
    }

    /**
     * Splits a newline-delimited string into trimmed, non-blank lines.
     *
     * The LLM sometimes prefixes lines with "• ", "- " or "* " even when
     * asked not to — strip those so our custom bullet rendering isn't doubled.
     */
    private fun splitLines(raw: String?): List<String> =
        raw.orEmpty()
            .split("\n")
            .map { it.trimStart('•', '-', '*', ' ').trim() }
            .filter { it.isNotBlank() }

    // ── Chat with transcript ──────────────────────────────────────────────

    /** Live chat messages from Room — persisted across screen re-entries */
    val chatMessages: StateFlow<List<ChatMessage>> = chatMessageDao
        .observeByMeetingId(meetingId)
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    fun askQuestion(question: String) {
        if (question.isBlank() || _isAiTyping.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAiTyping.value = true
            _chatError.value  = null

            // 1. Insert user message
            chatMessageDao.insert(
                ChatMessage(meetingId = meetingId, isUser = true, text = question)
            )

            // 2. Get transcript for context
            val transcripts = uiState.value.transcript.segments
            val transcriptText = transcripts.joinToString("\n") { it.text }

            if (transcriptText.isBlank()) {
                chatMessageDao.insert(
                    ChatMessage(meetingId = meetingId, isUser = false, text = "No transcript available to analyze.")
                )
                _isAiTyping.value = false
                return@launch
            }

            // 3. Insert empty AI message placeholder
            val aiMsgId = chatMessageDao.insert(
                ChatMessage(meetingId = meetingId, isUser = false, text = "")
            )

            // 4. Get conversation history (exclude the empty placeholder)
            val history = chatMessages.value.filter { it.text.isNotBlank() }

            // 5. Stream response tokens into the AI message row
            try {
                chatService.askQuestion(transcriptText, history, question)
                    .collect { token ->
                        chatMessageDao.appendText(aiMsgId, token)
                    }
            } catch (e: Exception) {
                Log.e("MeetingDetailVM", "Chat error: ${e.message}", e)
                // Update the AI message with error text
                chatMessageDao.appendText(aiMsgId, "\n\n⚠️ Error: Could not get response from Gemini.")
                _chatError.value = "Gemini didn't work — check your API key or try again"
            } finally {
                _isAiTyping.value = false
            }
        }
    }

    fun clearChatError() {
        _chatError.value = null
    }
}