package com.example.recall_ai.ui.meetingdetail

import android.widget.Toast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recall_ai.data.local.entity.MeetingStatus   // ← FIX B1: import entity enum, remove local redefinition
import com.example.recall_ai.data.local.entity.Transcript
import com.example.recall_ai.ui.theme.ColorBackground
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorDone
import com.example.recall_ai.ui.theme.ColorError
import com.example.recall_ai.ui.theme.ColorNavy
import com.example.recall_ai.ui.theme.ColorOnBackground
import com.example.recall_ai.ui.theme.ColorOnSurfaceDim
import com.example.recall_ai.ui.theme.ColorProcessing
import com.example.recall_ai.ui.theme.ColorRecordRed
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorSurfaceVariant
import com.example.recall_ai.ui.theme.ColorWarning
import com.example.recall_ai.ui.theme.ColorTextSlate900

// ─────────────────────────────────────────────────────────────────────────────
// FIX B1: DELETED the local `enum class MeetingStatus { RECORDING, PAUSED, STOPPED, COMPLETED }`
// That local definition:
//   (a) shadowed com.example.recall_ai.data.local.entity.MeetingStatus in scope
//   (b) was missing STOPPED_LOW_STORAGE and ERROR, causing the `when` in StatusPill
//       to reference values that didn't exist on the local enum → COMPILE ERROR
//   (c) caused a type-mismatch when passing uiState.meeting?.status (entity type)
//       to DetailTopBar which accepted the local type → COMPILE ERROR
// The entity enum already has all 6 values we need.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MeetingDetailScreen(
    meetingId:          Long,
    onNavigateBack:     () -> Unit,
    onNavigateToLiveAi: (Long) -> Unit = {},   // ← FIX B4: add navigation callback
    viewModel:          MeetingDetailViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiTyping   by viewModel.isAiTyping.collectAsStateWithLifecycle()
    val chatError    by viewModel.chatError.collectAsStateWithLifecycle()
    var selectedTab  by remember { mutableIntStateOf(0) }
    val context       = LocalContext.current
    val isAiSpeaking by viewModel.isAiSpeaking.collectAsStateWithLifecycle()

    // FIX B1: now uses the entity MeetingStatus which has RECORDING defined
    val isRecordingActive = uiState.meeting?.status == MeetingStatus.RECORDING

    LaunchedEffect(uiState.summary) {
        if (uiState.summary is SummaryUiState.Failed) {
            Toast.makeText(
                context,
                "Gemini didn't work — check your API key or try again",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(chatError) {
        chatError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearChatError()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = ColorBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            DetailTopBar(
                title          = uiState.meeting?.title ?: "Meeting",
                meetingStatus  = uiState.meeting?.status,   // FIX B1: entity type matches param type
                onNavigateBack = onNavigateBack
            )

            DetailTabRow(
                selectedTab   = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            AnimatedContent(
                targetState    = selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label          = "tabContent"
            ) { tab ->
                when (tab) {
                    0 -> SummaryTabContent(
                        summaryState = uiState.summary,
                        onRetry      = viewModel::retrySummary
                    )
                    1 -> TranscriptTabContent(
                        transcriptState = uiState.transcript
                    )
                    2 -> ChatTabContent(
                        messages           = chatMessages,
                        isAiTyping         = isAiTyping,
                        isAiSpeaking       = isAiSpeaking,
                        isRecordingActive  = isRecordingActive,
                        onSend             = viewModel::askQuestion,
                        // FIX B3: onLiveAiClick is () -> Unit.
                        // viewModel::askVoiceQuestion is (String) -> Unit — wrong type.
                        // Correct intent: tapping the mic button navigates to the Live AI screen.
                        onLiveAiClick      = { onNavigateToLiveAi(meetingId) },
                        onStopSpeakingClick = viewModel::stopAiSpeaking,
                        modifier           = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar — now accepts entity MeetingStatus (no longer ambiguous)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(
    title:          String,
    meetingStatus:  MeetingStatus?,   // entity type — all 6 values available
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = ColorOnBackground
            )
        }

        Column(
            modifier            = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text     = title,
                style    = MaterialTheme.typography.titleLarge.copy(
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 20.sp,
                    letterSpacing = (-0.5).sp
                ),
                color    = ColorNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meetingStatus != null) {
                Spacer(modifier = Modifier.height(2.dp))
                StatusPill(status = meetingStatus)
            }
        }

        Box(modifier = Modifier.size(48.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab row
// ─────────────────────────────────────────────────────────────────────────────

private val TABS = listOf("Summary", "Transcript", "Chat")

@Composable
private fun DetailTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor   = ColorBackground,
        contentColor     = ColorNavy,
        indicator        = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                height   = 2.dp,
                color    = ColorNavy
            )
        },
        divider = { HorizontalDivider(color = ColorBorder, thickness = 0.5.dp) }
    ) {
        TABS.forEachIndexed { index, label ->
            Tab(
                selected = selectedTab == index,
                onClick  = { onTabSelected(index) },
                text     = {
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                    )
                },
                selectedContentColor   = ColorNavy,
                unselectedContentColor = ColorOnSurfaceDim
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryTabContent(
    summaryState: SummaryUiState,
    onRetry:      () -> Unit,
    modifier:     Modifier = Modifier
) {
    AnimatedContent(
        targetState    = summaryState,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 }) togetherWith
                    fadeOut(tween(200))
        },
        label = "summaryState"
    ) { state ->
        when (state) {
            is SummaryUiState.WaitingForTranscription ->
                TranscriptionProgressState(state.completedChunks, state.totalChunks, modifier)
            is SummaryUiState.Pending    -> PendingState(modifier)
            is SummaryUiState.Generating -> GeneratingState(state.streamBuffer, state.retryCount, modifier)
            is SummaryUiState.Complete   -> CompleteSummaryState(state, modifier)
            is SummaryUiState.Failed     -> FailedState(state, onRetry, modifier)
        }
    }
}

@Composable
private fun TranscriptionProgressState(completed: Int, total: Int, modifier: Modifier = Modifier) {
    val progress          = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    val animatedProgress by animateFloatAsState(progress, tween(500, easing = FastOutSlowInEasing), label = "progress")
    CenteredStateColumn(modifier) {
        Text(
            text  = "Transcribing audio…",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ColorOnBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "$completed of $total segments done",
            style = MaterialTheme.typography.bodySmall,
            color = ColorOnSurfaceDim
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress      = { animatedProgress },
            modifier      = Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color         = ColorNavy,
            trackColor    = ColorBorder,
            strokeCap     = StrokeCap.Round
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Summary will generate once transcription completes",
            style     = MaterialTheme.typography.labelSmall,
            color     = ColorOnSurfaceDim.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PendingState(modifier: Modifier = Modifier) {
    CenteredStateColumn(modifier) {
        PulsingDot(ColorNavy, 10.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "Preparing summary…",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ColorOnBackground
        )
        Spacer(Modifier.height(6.dp))
        Text("AI is warming up", style = MaterialTheme.typography.bodySmall, color = ColorOnSurfaceDim)
    }
}

@Composable
private fun GeneratingState(streamBuffer: String, retryCount: Int, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (retryCount > 0) item { RetryAttemptBadge(retryCount) }
        if (streamBuffer.isNotBlank()) {
            item {
                SectionCard(label = "Generating…") {
                    Text(
                        text  = streamBuffer,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, lineHeight = 22.sp),
                        color = ColorOnSurfaceDim
                    )
                    BlinkingCursor()
                }
            }
        }
        item { ShimmerSectionCard(3) }
        item { ShimmerSectionCard(4) }
        item { ShimmerSectionCard(3) }
    }
}

@Composable
private fun CompleteSummaryState(state: SummaryUiState.Complete, modifier: Modifier = Modifier) {
    LazyColumn(
        state               = rememberLazyListState(),
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(label = "Summary", icon = "📝") {
                Text(text = state.summary, style = MaterialTheme.typography.bodyMedium, color = ColorTextSlate900, lineHeight = 24.sp)
            }
        }
        if (state.keyPoints.isNotEmpty()) {
            item {
                SectionCard(label = "Key Points", icon = "🔑") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.keyPoints.forEach { BulletRow(it) }
                    }
                }
            }
        }
        if (state.actionItems.isNotEmpty()) {
            item {
                SectionCard(label = "Action Items", icon = "✅") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.actionItems.forEachIndexed { i, item -> ActionItemRow(i + 1, item) }
                    }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun FailedState(state: SummaryUiState.Failed, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredStateColumn(modifier) {
        Box(
            modifier         = Modifier.size(56.dp).clip(CircleShape)
                .background(ColorError.copy(alpha = 0.08f))
                .border(0.5.dp, ColorError.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", style = MaterialTheme.typography.titleLarge, color = ColorError)
        }
        Spacer(Modifier.height(16.dp))
        Text("Summary failed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ColorOnBackground)
        Spacer(Modifier.height(8.dp))
        Text(text = state.message, style = MaterialTheme.typography.bodySmall, color = ColorOnSurfaceDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.7f))
        if (state.retryCount > 0) {
            Spacer(Modifier.height(6.dp))
            Text("Attempt ${state.retryCount} of 5", style = MaterialTheme.typography.labelSmall, color = ColorWarning.copy(alpha = 0.8f))
        }
        if (state.canRetry) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = ColorNavy, contentColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry Summary")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transcript tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TranscriptTabContent(transcriptState: TranscriptUiState, modifier: Modifier = Modifier) {
    if (transcriptState.segments.isEmpty()) {
        CenteredStateColumn(modifier) {
            PulsingDot(ColorNavy, 8.dp)
            Spacer(Modifier.height(14.dp))
            Text("Waiting for transcript…", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ColorOnBackground)
            Spacer(Modifier.height(6.dp))
            Text("Audio segments are being processed", style = MaterialTheme.typography.bodySmall, color = ColorOnSurfaceDim)
        }
        return
    }
    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(items = transcriptState.segments, key = { _, t -> t.id }) { _, segment ->
            TranscriptSegmentCard(segment)
        }
        if (!transcriptState.isComplete) item { TranscribingFooter() }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun TranscriptSegmentCard(segment: Transcript) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Segment ${segment.chunkIndex + 1}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = ColorNavy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                segment.confidence?.let { conf ->
                    val confPct   = (conf * 100).toInt()
                    val confColor = when { confPct >= 90 -> ColorDone; confPct >= 70 -> ColorWarning; else -> ColorError }
                    Text("$confPct%", style = MaterialTheme.typography.labelSmall, color = confColor)
                }
                segment.detectedLanguage?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = ColorOnSurfaceDim) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(segment.text, style = MaterialTheme.typography.bodyMedium, color = ColorTextSlate900, lineHeight = 24.sp)
    }
}

@Composable
private fun TranscribingFooter() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PulsingDot(ColorNavy, 6.dp)
        Spacer(Modifier.width(8.dp))
        Text("Transcription in progress…", style = MaterialTheme.typography.labelSmall, color = ColorOnSurfaceDim)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(label: String, icon: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth()
            .shadow(0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(ColorNavy.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 16.sp)
                }
            }
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp), color = ColorTextSlate900)
        }
        content()
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(ColorNavy))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ColorTextSlate900, lineHeight = 24.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionItemRow(index: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier         = Modifier.padding(top = 2.dp).size(20.dp).clip(RoundedCornerShape(4.dp))
                .background(ColorNavy.copy(alpha = 0.08f))
                .border(1.dp, ColorNavy.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) { Text("$index", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = ColorNavy) }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ColorTextSlate900, lineHeight = 24.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RetryAttemptBadge(attempt: Int) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(ColorWarning.copy(alpha = 0.08f))
            .border(0.5.dp, ColorWarning.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text("Attempt $attempt of 5", style = MaterialTheme.typography.labelSmall, color = ColorWarning) }
}

// FIX B1: when expression now exhaustive — entity MeetingStatus has all 6 values
@Composable
private fun StatusPill(status: MeetingStatus) {
    val (label, color) = when (status) {
        MeetingStatus.COMPLETED           -> "Done"         to ColorDone
        MeetingStatus.RECORDING           -> "Recording"    to ColorRecordRed
        MeetingStatus.PAUSED              -> "Paused"       to ColorWarning
        MeetingStatus.STOPPED             -> "Transcribing" to ColorProcessing
        MeetingStatus.STOPPED_LOW_STORAGE -> "Low storage"  to ColorError
        MeetingStatus.ERROR               -> "Error"        to ColorError
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(1f, 0f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "cursorAlpha")
    Text("▌", style = MaterialTheme.typography.bodyMedium, color = ColorNavy.copy(alpha = alpha), modifier = Modifier.padding(start = 2.dp))
}

@Composable
private fun PulsingDot(color: Color, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dotAlpha")
    Box(Modifier.size(size).clip(CircleShape).alpha(alpha).background(color))
}

@Composable
private fun CenteredStateColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(32.dp)) { content() }
    }
}