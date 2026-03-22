package com.example.recall_ai.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recall_ai.data.local.entity.Meeting
import com.example.recall_ai.data.local.entity.MeetingStatus
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
import com.example.recall_ai.ui.theme.ColorTextSlate400
import com.example.recall_ai.ui.theme.ColorTextSlate900
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Emoji & color palette for Edit Recall dialog ──────────────────────────────

private val emojiOptions = listOf("📅", "⏱", "🎙", "✏️", "👥", "💡", "🎵", "❤️", "📋", "🗂️","🎙", "✨", "⚡", "👥", "🧠", "🎯", "☕", "💬", "📌")

private val colorOptions = listOf(
    "#FFFFFF", // white
    "#F8D7DA", // rose
    "#E8D5F5", // lavender
    "#FFF9C4", // cream
    "#D6EAF8", // light blue
    "#E8DAEF"  // mauve
)

private fun parseColor(hex: String?): Color {
    if (hex == null) return Color(0xFFE8EAF0)
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color(0xFFE8EAF0) }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DASHBOARD SCREEN
// ═══════════════════════════════════════════════════════════════════════════════


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToRecording:  () -> Unit,
    onNavigateToMeeting:    (Long) -> Unit,
    onNavigateToAllRecalls: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val meetings          by viewModel.meetings.collectAsStateWithLifecycle()
    val recordingState    by viewModel.recordingState.collectAsStateWithLifecycle()
    val isActiveRecording by viewModel.isRecordingActive.collectAsStateWithLifecycle()

    // Edit dialog state
    var editMeeting by remember { mutableStateOf<Meeting?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = ColorBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                DashboardAppBar()

                LazyColumn(
                    modifier          = Modifier.weight(1f),
                    contentPadding    = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { DashboardGreeting() }
                    item { FeatureCards() }

                    if (meetings.isEmpty()) {
                        item { EmptyState(modifier = Modifier.padding(top = 48.dp)) }
                    } else {
                        // Section header
                        item {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    text  = "Recent Recalls",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp
                                    ),
                                    color = ColorOnBackground
                                )
                                Text(
                                    text     = "View All",
                                    style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color    = ColorNavy,
                                    modifier = Modifier.clickable { onNavigateToAllRecalls() }
                                )
                            }
                        }

                        // Show latest 3 with swipe-to-delete
                        itemsIndexed(
                            items = meetings.take(3),
                            key   = { _, m -> m.id }
                        ) { index, meeting ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteMeeting(meeting)
                                        true
                                    } else false
                                }
                            )

                            AnimatedVisibility(
                                visible = dismissState.currentValue != SwipeToDismissBoxValue.EndToStart,
                                enter   = fadeIn(tween(200)) + slideInVertically(tween(200 + index * 30)),
                                exit    = fadeOut(tween(150))
                            ) {
                                SwipeToDismissBox(
                                    state                       = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = { SwipeDeleteBackground(progress = dismissState.progress) },
                                    content = {
                                        MeetingRow(
                                            meeting = meeting,
                                            onClick = { onNavigateToMeeting(meeting.id) },
                                            onEditClick = { editMeeting = meeting }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // FAB
            CaptureNotesFab(
                isActive = isActiveRecording,
                onClick  = { onNavigateToRecording() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            )
        }
    }

    // ── Edit Recall Bottom Sheet ──────────────────────────────────────────
    editMeeting?.let { meeting ->
        EditRecallSheet(
            meeting   = meeting,
            onDismiss = { editMeeting = null },
            onSave    = { title, emoji, color ->
                viewModel.updateMeetingDetails(meeting.id, title, emoji, color)
                editMeeting = null
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EDIT RECALL BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRecallSheet(
    meeting:   Meeting,
    onDismiss: () -> Unit,
    onSave:    (title: String, emoji: String?, color: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name  by remember { mutableStateOf(meeting.title) }
    var emoji by remember { mutableStateOf(meeting.iconEmoji) }
    var color by remember { mutableStateOf(meeting.iconColorHex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = ColorSurface,
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text  = "Edit Recall",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = ColorOnBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = "Update your meeting details and icon",
                style = MaterialTheme.typography.bodySmall,
                color = ColorOnSurfaceDim
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Meeting Name
            Text(
                text  = "Meeting Name",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ColorOnBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ColorNavy,
                    unfocusedBorderColor = ColorBorder
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Select Icon (emoji)
            Text(
                text  = "Select Icon",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ColorOnBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                emojiOptions.forEach { e ->
                    val isSelected = emoji == e
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ColorNavy else ColorSurfaceVariant)
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                if (isSelected) ColorNavy else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = e, fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Icon Background Color
            Text(
                text  = "Icon Background Color",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ColorOnBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                colorOptions.forEach { hex ->
                    val isSelected = color == hex
                    val c = parseColor(hex)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) ColorNavy else ColorBorder,
                                CircleShape
                            )
                            .clickable { color = hex }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Buttons
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cancel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, ColorBorder, RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = ColorOnBackground
                    )
                }
                // Save
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ColorNavy)
                        .clickable { onSave(name, emoji, color) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "Save Changes",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// APP BAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DashboardAppBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        IconButton(onClick = { }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = ColorNavy)
        }
        Text(
            text  = "RECALL",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp
            ),
            color = ColorNavy
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ColorNavy.copy(alpha = 0.15f))
                .border(1.5.dp, ColorNavy.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = "Profile", tint = ColorNavy, modifier = Modifier.size(18.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GREETING
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DashboardGreeting() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text  = "Hi, Mohit",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = ColorOnBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = "Ready to capture a new thought?",
            style = MaterialTheme.typography.bodyMedium,
            color = ColorOnSurfaceDim
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FEATURE CARDS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeatureCards() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureCard("To-Do", "12 active tasks today", Icons.Default.TaskAlt, ColorNavy)
        FeatureCard("Memories", "Browse notes & chats", Icons.Default.Folder, ColorNavy)
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, icon: ImageVector, iconTint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ColorTextSlate900)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = ColorTextSlate400)
        }
        Icon(icon, contentDescription = title, tint = iconTint.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MEETING ROW — emoji icon + title + meta + 3-dot menu
// ═══════════════════════════════════════════════════════════════════════════════


@Composable
fun MeetingRow(
    meeting:     Meeting,
    onClick:     () -> Unit,
    onEditClick: () -> Unit = {}
) {
    val (defaultEmoji, defaultBg) = defaultMeetingStyle(meeting.id)
    val emoji = meeting.iconEmoji ?: defaultEmoji
    val bgColor = parseColor(meeting.iconColorHex ?: defaultBg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ColorSurface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = meeting.title,
                style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color    = ColorTextSlate900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatDate(meeting.startTime), style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
                if (meeting.durationSeconds > 0) {
                    Text("•", style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
                    Text(formatDuration(meeting.durationSeconds), style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
                }
                Text("•", style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
                Text("Audio", style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
            }
        }

        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "Edit", tint = ColorTextSlate400, modifier = Modifier.size(18.dp))
        }
    }
}

private fun defaultMeetingStyle(meetingId: Long): Pair<String, String> {
    return when ((meetingId % 3).toInt()) {
        0    -> "🎙" to "#D6EAF8"
        1    -> "📝" to "#D5F5E3"
        else -> "💬" to "#E8D5F5"
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SWIPE DELETE BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SwipeDeleteBackground(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(ColorError.copy(alpha = (progress * 0.8f).coerceIn(0f, 0.8f)))
            .padding(end = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint     = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text  = "DELETE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 9.sp
                ),
                color = Color.White
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PULSE DOT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PulseDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "dotAlpha"
    )
    Box(modifier = Modifier.size(size).clip(CircleShape).alpha(alpha).background(color))
}

// ═══════════════════════════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(ColorNavy.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = ColorNavy, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("No recordings yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ColorOnBackground, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Tap Capture Note below\nto start your first session", style = MaterialTheme.typography.bodyMedium, color = ColorOnSurfaceDim, textAlign = TextAlign.Center)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CAPTURE NOTE FAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CaptureNotesFab(isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(50), ambientColor = ColorNavy.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(50))
            .background(if (isActive) ColorRecordRed else ColorNavy)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (isActive) {
                PulseDot(Color.White, 8.dp)
                Spacer(Modifier.width(10.dp))
                Text("Recording in progress…", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            } else {
                Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("CAPTURE NOTE", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.White)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

internal fun formatDate(epochMs: Long): String = dateFormat.format(Date(epochMs))

internal fun formatDuration(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return if (h > 0) "%dh %02dm".format(h, m)
    else if (m > 0)   "%02d mins".format(m)
    else              "${s}s"
}