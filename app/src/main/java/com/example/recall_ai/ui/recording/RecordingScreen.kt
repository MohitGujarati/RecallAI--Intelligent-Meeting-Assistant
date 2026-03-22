package com.example.recall_ai.ui.recording

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recall_ai.model.TranscriptionMode
import com.example.recall_ai.ui.theme.ColorBackground
import com.example.recall_ai.ui.theme.ColorBorder
import com.example.recall_ai.ui.theme.ColorNavy
import com.example.recall_ai.ui.theme.ColorOnBackground
import com.example.recall_ai.ui.theme.ColorOnSurfaceDim
import com.example.recall_ai.ui.theme.ColorRecordRed
import com.example.recall_ai.ui.theme.ColorSurface
import com.example.recall_ai.ui.theme.ColorSurfaceVariant
import com.example.recall_ai.ui.theme.ColorTextSlate900
import com.example.recall_ai.ui.theme.ColorWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal Recording Screen — clean light design matching the Stitch reference.
 *
 * Layout (top to bottom):
 *   1. Top bar: Back arrow + centered "New Recording" title with date + red dot
 *   2. Transcript area: live text with faded older segments
 *   3. Timer: MM : SS boxes with labels
 *   4. Controls: Play / Pause / Stop
 */
@Composable
fun RecordingScreen(
    onNavigateBack: () -> Unit,
    viewModel:      RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMode by viewModel.transcriptionMode.collectAsStateWithLifecycle()

    // ── Bug 2 fix: guard LaunchedEffect so stale Finished state is ignored ──
    var wasEverActive by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when {
            uiState is RecordingUiState.Active   -> wasEverActive = true
            uiState is RecordingUiState.Finished && wasEverActive -> onNavigateBack()
        }
    }

    // Permission launcher
    var permissionDenied by remember { mutableStateOf(false) }
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) viewModel.startRecording()
        else permissionDenied = true
    }

    fun requestPermissionsAndRecord() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        micPermLauncher.launch(perms)
    }

    val activeState = uiState as? RecordingUiState.Active
    val isActive = activeState != null
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault()) }
    val dateText = remember { dateFormat.format(Date()).uppercase(Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = ColorBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ═══════════════════════════════════════════════════════════════
            // 1. TOP BAR — back arrow, centered title, red dot
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = ColorNavy
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text  = "New Recording",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = ColorNavy
                    )
                    Text(
                        text  = dateText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.5.sp
                        ),
                        color = ColorOnSurfaceDim
                    )
                }

                // Red recording dot or empty placeholder
                if (isActive) {
                    PulseDot(color = ColorRecordRed, size = 10.dp)
                } else {
                    Box(modifier = Modifier.size(10.dp))
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 2. TRANSCRIPT AREA — takes remaining space above timer
            // ═══════════════════════════════════════════════════════════════
            val transcripts = activeState?.liveTranscripts ?: emptyList()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                if (isActive && transcripts.isNotEmpty()) {
                    LiveTranscriptArea(transcripts = transcripts)
                } else if (isActive) {
                    // Recording started but no transcript yet
                    Text(
                        text = "Transcribing live... Speak clearly to capture every detail of your session.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 26.sp
                        ),
                        color = ColorTextSlate900,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                } else {
                    // Idle state
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Mode toggle for idle state
                        TranscriptionModeToggle(
                            currentMode = currentMode,
                            onToggle    = { viewModel.toggleMode() }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Tap record to start",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorOnSurfaceDim
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // 3. TIMER — two boxes: MM : SS
            // ═══════════════════════════════════════════════════════════════
            val timerText = activeState?.timerText ?: "00:00"
            val parts = timerText.split(":")
            val minutes = parts.getOrElse(0) { "00" }
            val seconds = parts.getOrElse(1) { "00" }

            HorizontalDivider(
                color     = ColorBorder,
                thickness = 0.5.dp,
                modifier  = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Minutes box
                TimerBox(value = minutes, label = "MINUTES")

                Text(
                    text  = ":",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light
                    ),
                    color = ColorOnSurfaceDim,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Seconds box
                TimerBox(value = seconds, label = "SECONDS")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════════════════════════════
            // 4. CONTROLS — Play | Pause | Stop
            // ═══════════════════════════════════════════════════════════════
            RecordingControls(
                isActive   = isActive,
                isPaused   = activeState?.isPaused == true,
                onRecord   = { requestPermissionsAndRecord() },
                onPause    = { /* TODO: pause */ },
                onStop     = { viewModel.stopRecording() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Permission denied banner ──────────────────────────────────
            AnimatedVisibility(visible = permissionDenied) {
                PermissionDeniedBanner(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LIVE TRANSCRIPT AREA
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LiveTranscriptArea(transcripts: List<String>) {
    val listState = rememberLazyListState()

    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            listState.animateScrollToItem(transcripts.size - 1)
        }
    }

    LazyColumn(
        state              = listState,
        modifier           = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding     = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(
            items = transcripts,
            key   = { index, _ -> index }
        ) { index, text ->
            // Older text more faded, newest text bold
            val isLatest = index == transcripts.size - 1
            val alphaValue = if (isLatest) 1f
                else (0.35f + (index.toFloat() / transcripts.size) * 0.5f).coerceIn(0.35f, 0.85f)

            Text(
                text     = text,
                style    = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isLatest) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 22.sp
                ),
                color    = ColorTextSlate900,
                modifier = Modifier.alpha(alphaValue)
            )
        }

        // "Transcribing live..." prompt at bottom
        item {
            Text(
                text = "Transcribing live... Speak clearly to capture every detail of your session.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 26.sp
                ),
                color = ColorTextSlate900
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TIMER BOX
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TimerBox(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ColorSurfaceVariant)
                .border(1.dp, ColorBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = value,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize   = 32.sp
                ),
                color = ColorNavy
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.sp,
                fontSize      = 9.sp
            ),
            color = ColorOnSurfaceDim
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CONTROLS — Idle: Record button / Active: Pause (center) + Stop (right)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RecordingControls(
    isActive:  Boolean,
    isPaused:  Boolean,
    onRecord:  () -> Unit,
    onPause:   () -> Unit,
    onStop:    () -> Unit
) {
    if (!isActive) {
        // ── Idle — large, inviting record button ──────────────────────
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation    = 12.dp,
                    shape        = CircleShape,
                    ambientColor = ColorNavy.copy(alpha = 0.25f)
                )
                .clip(CircleShape)
                .background(ColorNavy)
                .clickable { onRecord() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Start recording",
                tint     = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    } else {
        // ── Active — center pause (primary), side stop (secondary) ───
        // Fitts's Law: biggest target in center for the most-used action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Spacer to balance the row
            Spacer(modifier = Modifier.weight(1f))

            // ── PAUSE / RESUME — primary action, center, large ───────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation    = 16.dp,
                        shape        = CircleShape,
                        ambientColor = ColorNavy.copy(alpha = 0.3f)
                    )
                    .clip(CircleShape)
                    .background(ColorNavy)
                    .clickable { onPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint     = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── STOP — secondary action, smaller, right side ─────────
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation    = 4.dp,
                        shape        = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.1f)
                    )
                    .clip(CircleShape)
                    .background(ColorSurface)
                    .border(1.5.dp, ColorBorder, CircleShape)
                    .clickable { onStop() },
                contentAlignment = Alignment.Center
            ) {
                // Filled square icon for "stop"
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ColorRecordRed)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PULSE DOT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PulseDot(
    color: Color,
    size:  androidx.compose.ui.unit.Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .alpha(alpha)
            .background(color)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// TRANSCRIPTION MODE TOGGLE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TranscriptionModeToggle(
    currentMode: TranscriptionMode,
    onToggle:    () -> Unit,
    modifier:    Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(ColorSurfaceVariant)
            .border(0.5.dp, ColorBorder, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ModeSelectorPill(
            label      = "Native",
            isSelected = currentMode == TranscriptionMode.NATIVE,
            onClick    = { if (currentMode != TranscriptionMode.NATIVE) onToggle() }
        )
        Spacer(modifier = Modifier.width(4.dp))
        ModeSelectorPill(
            label      = "AI",
            isSelected = currentMode == TranscriptionMode.AI,
            onClick    = { if (currentMode != TranscriptionMode.AI) onToggle() }
        )
    }
}

@Composable
private fun ModeSelectorPill(
    label:      String,
    isSelected: Boolean,
    onClick:    () -> Unit
) {
    val bgColor  = if (isSelected) ColorNavy else Color.Transparent
    val txtColor = if (isSelected) Color.White else ColorOnSurfaceDim

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = txtColor
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PERMISSION DENIED BANNER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PermissionDeniedBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.06f))
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text  = "Microphone permission is required to record. " +
                    "Please grant it in Settings → Apps → Recall → Permissions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}