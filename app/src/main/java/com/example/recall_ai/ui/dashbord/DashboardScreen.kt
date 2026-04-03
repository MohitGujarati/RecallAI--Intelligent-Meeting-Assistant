package com.example.recall_ai.ui.dashboard

import androidx.compose.animation.AnimatedVisibility

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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.defaultMinSize

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth

import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.unit.Dp
import com.example.recall_ai.R
import com.example.recall_ai.ui.theme.IndigoFab
import com.example.recall_ai.ui.theme.IndigoHigh
import com.example.recall_ai.ui.theme.PulseRing
import com.example.recall_ai.ui.theme.TealDark
import com.example.recall_ai.ui.theme.TealLight
import com.example.recall_ai.ui.theme.TealMid

// ── Emoji & color palette for Edit Recall dialog ──────────────────────────────

private val emojiOptions = listOf(
    "📅",
    "⏱",
    "🎙",
    "✏️",
    "👥",
    "💡",
    "🎵",
    "❤️",
    "📋",
    "🗂️",
    "🎙",
    "✨",
    "⚡",
    "👥",
    "🧠",
    "🎯",
    "☕",
    "💬",
    "📌"
)

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
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFE8EAF0)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DASHBOARD SCREEN
// ═══════════════════════════════════════════════════════════════════════════════


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToRecording: () -> Unit,
    onNavigateToMeeting: (Long) -> Unit,
    onNavigateToAllRecalls: () -> Unit,
    onNavigateToGlobalLiveAi: () -> Unit,
    onNavigateToActionItems: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val meetings by viewModel.meetings.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val isActiveRecording by viewModel.isRecordingActive.collectAsStateWithLifecycle()

    // Edit dialog state
    var editMeeting by remember { mutableStateOf<Meeting?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                DashboardAppBar(onProfileClick = onNavigateToAccount)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { DashboardGreeting() }
                    item { FeatureCards(onToDoClick = onNavigateToActionItems) }

                    if (meetings.isEmpty()) {
                        item { EmptyState(modifier = Modifier.padding(top = 48.dp)) }
                    } else {
                        // Section header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Recalls",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp
                                    ),
                                    color = ColorOnBackground
                                )
                                Text(
                                    text = "View All",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = ColorNavy,
                                    modifier = Modifier.clickable { onNavigateToAllRecalls() }
                                )
                            }
                        }

                        // Show latest 3 with swipe-to-delete
                        itemsIndexed(
                            items = meetings.take(3),
                            key = { _, m -> m.id }
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
                                enter = fadeIn(tween(200)) + slideInVertically(tween(200 + index * 30)),
                                exit = fadeOut(tween(150))
                            ) {
                                SwipeToDismissBox(
                                    state = dismissState,
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


        }
        // ─── Bottom FAB Bar ───────────────────────────────────────────────────────────
        BottomFabBar(
            isActiveRecording = isActiveRecording,
            onNavigateToRecording = onNavigateToRecording,
            onNavigateToGlobalLiveAi = onNavigateToGlobalLiveAi,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Bottom),
        )
    }

    // ── Edit Recall Bottom Sheet ──────────────────────────────────────────
    editMeeting?.let { meeting ->
        EditRecallSheet(
            meeting = meeting,
            onDismiss = { editMeeting = null },
            onSave = { title, emoji, color ->
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
    meeting: Meeting,
    onDismiss: () -> Unit,
    onSave: (title: String, emoji: String?, color: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(meeting.title) }
    var emoji by remember { mutableStateOf(meeting.iconEmoji) }
    var color by remember { mutableStateOf(meeting.iconColorHex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ColorSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Edit Recall",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = ColorOnBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Update your meeting details and icon",
                style = MaterialTheme.typography.bodySmall,
                color = ColorOnSurfaceDim
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Meeting Name
            Text(
                text = "Meeting Name",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ColorOnBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorNavy,
                    unfocusedBorderColor = ColorBorder
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Select Icon (emoji)
            Text(
                text = "Select Icon",
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
                text = "Icon Background Color",
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
                modifier = Modifier.fillMaxWidth(),
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
                        text = "Cancel",
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
                        text = "Save Changes",
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
private fun DashboardAppBar(onProfileClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { }, modifier = Modifier.size(40.dp)) {
      //      Icon(Icons.Default.Menu, contentDescription = "Menu", tint = ColorNavy)
        }
        Text(
            text = "RECALL",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp
            ),
            color = ColorNavy
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ColorNavy.copy(alpha = 0.15f))
                .border(1.5.dp, ColorNavy.copy(alpha = 0.1f), CircleShape)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Profile",
                tint = ColorNavy,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GREETING
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DashboardGreeting() {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text(
            text = "Helloooo  !!",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = ColorOnBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ready to capture a new thought?",
            style = MaterialTheme.typography.bodyMedium,
            color = ColorOnSurfaceDim
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FEATURE CARDS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeatureCards(onToDoClick: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Pass ColorNavy to tint the vector icon
        FeatureCard(
            title = "To-Do",
            subtitle = "View all action items", // Removed the \n
            icon = rememberVectorPainter(image = Icons.Default.TaskAlt),
            iconTint = ColorNavy,
            onClick = onToDoClick
        )

        // Pass 'null' for the tint so SuperBOB stays full-color!
        /*
        FeatureCard(
            title = "SuperBOB",
            subtitle = "Your mini assistant who can help you with tasks and chats, schedule, reminders and more", // Removed the \n
            icon = painterResource(id = R.drawable.ic_app_icon),
            iconTint = null
        )
         */

    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: Painter,
    iconTint: Color?, // Changed to nullable
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Added weight(1f) so long text wraps instead of pushing the icon off-screen
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ColorTextSlate900
            )
            Spacer(modifier = Modifier.height(4.dp)) // Let Compose handle the spacing instead of \n
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSlate400
            )
        }

        Spacer(modifier = Modifier.width(16.dp)) // Add breathing room between text and icon

        // Conditionally render an Icon or an Image
        if (iconTint != null) {
            Icon(
                painter = icon,
                contentDescription = title,
                tint = iconTint.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFFE0DFE4), shape = CircleShape)
                    .clip(CircleShape), // Optional, ensures no visual spillover
                contentAlignment = Alignment.Center // Centers the mascot inside the circle
            ) {
                Image(
                    painter = icon, // The SuperBOB graphic (must have a transparent background)
                    contentDescription = title,
                    modifier = Modifier
                        .size(36.dp) // Maintain the mascot's unique look without cramping it
                        .padding(2.dp) // Ensures internal spacing
                )
            }
        }
    }
}
// ═══════════════════════════════════════════════════════════════════════════════
// MEETING ROW — emoji icon + title + meta + 3-dot menu
// ═══════════════════════════════════════════════════════════════════════════════


@Composable
fun MeetingRow(
    meeting: Meeting,
    onClick: () -> Unit,
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
                text = meeting.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ColorTextSlate900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatDate(meeting.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSlate400
                )
                if (meeting.durationSeconds > 0) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTextSlate400
                    )
                    Text(
                        formatDuration(meeting.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTextSlate400
                    )
                }
                Text("•", style = MaterialTheme.typography.labelSmall, color = ColorTextSlate400)
                Text(
                    "Audio",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSlate400
                )
            }
        }

        IconButton(onClick = onEditClick) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Edit",
                tint = ColorTextSlate400,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun defaultMeetingStyle(meetingId: Long): Pair<String, String> {
    return when ((meetingId % 3).toInt()) {
        0 -> "🎙" to "#D6EAF8"
        1 -> "📝" to "#D5F5E3"
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
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "DELETE",
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
private fun PulseDot(color: Color, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .alpha(alpha)
        .background(color))
}

// ═══════════════════════════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ColorNavy.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = ColorNavy,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No recordings yet",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ColorOnBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Tap Capture Note below\nto start your first session",
            style = MaterialTheme.typography.bodyMedium,
            color = ColorOnSurfaceDim,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CAPTURE NOTE FAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BottomFabBar(
    isActiveRecording: Boolean,
    onNavigateToRecording: () -> Unit,
    onNavigateToGlobalLiveAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        AskBobFab(
            onClick = onNavigateToGlobalLiveAi,
            modifier = Modifier.weight(0.62f),
        )

        CaptureNotesFab(
            isActive = isActiveRecording,
            onClick = onNavigateToRecording,
            modifier = Modifier.weight(0.58f),
        )

    }
}

@Composable
fun CaptureNotesFab(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pulse animation when recording
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    // Press spring
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )

    Box(
        modifier = modifier.height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow / pulse ring (only when recording)
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(28.dp))
                    .background(PulseRing.copy(alpha = pulseAlpha)),
            )
        }

        // Main pill button
        Surface(
            modifier = Modifier
                .matchParentSize()
                .scale(pressScale)
                .shadow(
                    elevation = if (isActive) 18.dp else 8.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = TealLight.copy(alpha = 0.6f),
                    spotColor = TealLight.copy(alpha = 0.6f),
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { onClick() },
                    )
                },
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = if (isActive)
                                listOf(TealLight, TealMid)
                            else
                                listOf(TealMid, TealDark),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Mic icon — swap for your actual resource
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Mic, // Or Icons.Rounded.Mic
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isActive) "RECORDING…" else "CAPTURE NOTE",
                        color = if (isActive) Color.White else White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ─── Ask Bob FAB ──────────────────────────────────────────────
@Composable
fun AskBobFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow),
        label = "bobPressScale",
    )

    val shadowElevation = if (pressed) 4.dp else 10.dp

    Box(
        modifier = modifier
            .height(56.dp)
            .wrapContentWidth()
            .defaultMinSize(minWidth = 140.dp)
            .scale(pressScale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(28.dp),
                ambientColor = IndigoHigh.copy(alpha = 0.4f),
                spotColor = IndigoHigh.copy(alpha = 0.6f),
            )
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(IndigoHigh, IndigoFab),
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
            .padding(horizontal = 20.dp), 
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bob_icon),
                contentDescription = "Ask Bob Icon",
                tint = Color.Unspecified, 
                modifier = Modifier.size(58.dp) // Slightly scaled down to fit nicely
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "ASK BOB",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }
    }
}

// ─── Sparkle Ring ─────────────────────────────────────────────

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
    else if (m > 0) "%02d mins".format(m)
    else "${s}s"
}


