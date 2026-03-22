package com.example.recall_ai.data.repository

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.recall_ai.data.local.dao.AudioChunkDao
import com.example.recall_ai.data.local.dao.MeetingDao
import com.example.recall_ai.data.local.dao.TranscriptDao
import com.example.recall_ai.data.local.entity.AudioChunk
import com.example.recall_ai.data.local.entity.Meeting
import com.example.recall_ai.data.local.entity.Transcript
import com.example.recall_ai.model.RecordingState
import com.example.recall_ai.model.TranscriptionMode
import com.example.recall_ai.service.RecordingService
import com.example.recall_ai.service.RecordingStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for all recording-related data access.
 *
 * ViewModels interact ONLY with this class — never with DAOs or the
 * Service directly. This decouples the UI layer from both the database
 * schema and the service implementation.
 *
 * Separation of concerns:
 *   RecordingService  → hardware (mic, AudioRecord, foreground lifecycle)
 *   RecordingRepository → data access + service start/stop commands
 *   ViewModel         → UI state derivation + user intent handling
 */
@Singleton
class RecordingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meetingDao: MeetingDao,
    private val audioChunkDao: AudioChunkDao,
    private val transcriptDao: TranscriptDao,
    private val stateHolder: RecordingStateHolder
) {

    // ── Recording state (service → ViewModel) ────────────────────────────

    /** Live recording state from the active service session */
    val recordingState: StateFlow<RecordingState> = stateHolder.state

    /**
     * Reset singleton state to Idle if it's in a terminal state (Stopped, Error, etc).
     * Called when RecordingViewModel initializes so the UI starts clean.
     */
    fun resetRecordingStateIfTerminal() = stateHolder.resetIfTerminal()

    // ── Service control ──────────────────────────────────────────────────

    fun startRecording(mode: TranscriptionMode = TranscriptionMode.NATIVE) {
        val intent = RecordingService.startIntent(context, mode)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopRecording() {
        context.startService(RecordingService.stopIntent(context))
    }

    fun pauseRecording() {
        context.startService(RecordingService.pauseIntent(context))
    }

    fun resumeRecording() {
        context.startService(RecordingService.resumeIntent(context))
    }

    // ── Meeting queries ──────────────────────────────────────────────────

    /** Dashboard: all meetings newest-first */
    fun observeAllMeetings(): Flow<List<Meeting>> =
        meetingDao.observeAll()

    /** Detail screen: live-updates meeting status during transcription */
    fun observeMeeting(meetingId: Long): Flow<Meeting?> =
        meetingDao.observeById(meetingId)

    suspend fun getMeeting(meetingId: Long): Meeting? =
        meetingDao.getById(meetingId)

    suspend fun deleteMeeting(meetingId: Long) {
        meetingDao.deleteById(meetingId)
        // Room CASCADE deletes chunks, transcripts, and summary automatically
    }

    suspend fun updateMeetingDetails(id: Long, title: String, emoji: String?, color: String?) {
        meetingDao.updateTitleAndIcon(id, title, emoji, color)
    }

    // ── Chunk queries ────────────────────────────────────────────────────

    fun observeChunks(meetingId: Long): Flow<List<AudioChunk>> =
        audioChunkDao.observeChunksForMeeting(meetingId)

    suspend fun getChunks(meetingId: Long): List<AudioChunk> =
        audioChunkDao.getChunksForMeeting(meetingId)

    /** Resets all FAILED chunks to PENDING so the transcription worker retries them all */
    suspend fun retryAllFailedChunks(meetingId: Long) {
        audioChunkDao.resetFailedToPending(meetingId)
    }

    // ── Transcript queries ───────────────────────────────────────────────

    /** Transcript screen: live stream of incoming transcript segments */
    fun observeTranscripts(meetingId: Long): Flow<List<Transcript>> =
        transcriptDao.observeTranscriptsForMeeting(meetingId)

    /** Returns the full concatenated transcript text for summary generation */
    suspend fun getFullTranscriptText(meetingId: Long): String? =
        transcriptDao.getFullTranscriptText(meetingId)

    /**
     * Returns true when all chunks have been successfully transcribed.
     * Used to decide when to trigger summary generation.
     */
    suspend fun isTranscriptionComplete(meetingId: Long): Boolean {
        val totalChunks      = audioChunkDao.getChunkCount(meetingId)
        val completedChunks  = audioChunkDao.getCompletedChunkCount(meetingId)
        return totalChunks > 0 && totalChunks == completedChunks
    }
}