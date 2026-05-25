package com.mohit.recall_ai.ui.meetingdetail

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohit.recall_ai.data.local.dao.ChatMessageDao
import com.mohit.recall_ai.data.local.entity.ChatMessage
import com.mohit.recall_ai.data.local.entity.Meeting
import com.mohit.recall_ai.data.local.entity.MeetingStatus
import com.mohit.recall_ai.data.local.entity.Summary
import com.mohit.recall_ai.data.local.entity.SummaryStatus
import com.mohit.recall_ai.data.local.entity.Transcript
import com.mohit.recall_ai.data.remote.api.GeminiChatService
import com.mohit.recall_ai.data.repository.RecordingRepository
import com.mohit.recall_ai.data.repository.SummaryRepository
import com.mohit.recall_ai.ui.navigation.Screen
import com.mohit.recall_ai.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI state types
// ─────────────────────────────────────────────────────────────────────────────

sealed class SummaryUiState {
    data class WaitingForTranscription(
        val completedChunks: Int,
        val totalChunks:     Int
    ) : SummaryUiState()

    object Pending : SummaryUiState()

    data class Generating(
        val streamBuffer: String,
        val retryCount:   Int = 0
    ) : SummaryUiState()

    data class Complete(
        val title:       String,
        val summary:     String,
        val keyPoints:   List<String>,
        val actionItems: List<String>
    ) : SummaryUiState()

    data class Failed(
        val message:    String,
        val retryCount: Int,
        val canRetry:   Boolean
    ) : SummaryUiState()
}

data class TranscriptUiState(
    val segments:    List<Transcript> = emptyList(),
    val isComplete:  Boolean          = false
)

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
    @ApplicationContext private val context: Context,
    private val voiceHelper:         VoiceHelper // FIXED: Injected VoiceHelper here
) : ViewModel() {

    val meetingId: Long =
        savedStateHandle.get<Long>(Screen.MeetingDetail.ARG) ?: -1L

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

    fun retrySummary() {
        viewModelScope.launch {
            summaryRepository.resetForRetry(meetingId)
            SummaryWorker.cancelAndReenqueue(context, meetingId)
        }
    }

    private fun mapSummaryState(
        summary:         Summary?,
        meeting:         Meeting?,
        completedChunks: Int
    ): SummaryUiState {
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

    private fun splitLines(raw: String?): List<String> =
        raw.orEmpty()
            .split("\n")
            .map { it.trimStart('•', '-', '*', ' ').trim() }
            .filter { it.isNotBlank() }

    // ── Chat with transcript ──────────────────────────────────────────────

    val chatMessages: StateFlow<List<ChatMessage>> = chatMessageDao
        .observeByMeetingId(meetingId)
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping.asStateFlow()

    // FIXED: Accessing the instance property, not statically
    val isAiSpeaking: StateFlow<Boolean> = voiceHelper.isSpeaking

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    fun askQuestion(question: String) {
        if (question.isBlank() || _isAiTyping.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAiTyping.value = true
            _chatError.value  = null

            chatMessageDao.insert(
                ChatMessage(meetingId = meetingId, isUser = true, text = question)
            )

            val transcripts = uiState.value.transcript.segments
            val transcriptText = transcripts.joinToString("\n") { it.text }

            if (transcriptText.isBlank()) {
                chatMessageDao.insert(
                    ChatMessage(meetingId = meetingId, isUser = false, text = "No transcript available to analyze.")
                )
                _isAiTyping.value = false
                return@launch
            }

            val aiMsgId = chatMessageDao.insert(
                ChatMessage(meetingId = meetingId, isUser = false, text = "")
            )

            val history = chatMessages.value.filter { it.text.isNotBlank() }

            try {
                chatService.askQuestion(transcriptText, history, question)
                    .collect { token ->
                        chatMessageDao.appendText(aiMsgId, token)
                    }
            } catch (e: Exception) {
                Log.e("MeetingDetailVM", "Chat error: ${e.message}", e)
                chatMessageDao.appendText(aiMsgId, "\n\n⚠️ Error: Could not get response from Gemini.")
                _chatError.value = "Gemini didn't work — check your API key or try again"
            } finally {
                _isAiTyping.value = false
            }
        }
    }

    /**
     * Voice-to-Voice orchestration.
     */
    fun askVoiceQuestion(text: String) {
        viewModelScope.launch {
            // 1. Interrupt any current TTS
            voiceHelper.stop()

            // 2. Start the text-based chat pipeline
            askQuestion(text)

            // 3. Wait slightly to ensure askQuestion() has time to set _isAiTyping = true
            delay(100)

            // 4. Suspend and wait until the AI finishes typing (streaming)
            isAiTyping.first { !it }

            // 5. Fetch the last AI response directly from the StateFlow
            val lastMessage = chatMessages.value.lastOrNull { !it.isUser }
            val finalizedText = lastMessage?.text ?: ""

            // 6. Trigger the Text-to-Speech engine if it's a valid response
            if (finalizedText.isNotBlank() && !finalizedText.contains("⚠️ Error")) {
                voiceHelper.speak(finalizedText)
            }
        }
    }

    fun stopAiSpeaking() {
        voiceHelper.stop()
    }

    fun clearChatError() {
        _chatError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // FIXED: Clean up the TTS engine to prevent memory leaks when screen closes
        voiceHelper.shutdown()
    }
}