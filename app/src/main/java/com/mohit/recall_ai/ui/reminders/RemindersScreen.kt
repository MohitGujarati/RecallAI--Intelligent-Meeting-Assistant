package com.mohit.recall_ai.ui.reminders

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.recall_ai.data.local.entity.Reminder
import com.mohit.recall_ai.data.local.entity.ReminderStatus
import com.mohit.recall_ai.ui.theme.ColorBackground
import com.mohit.recall_ai.ui.theme.ColorBorder
import com.mohit.recall_ai.ui.theme.ColorDone
import com.mohit.recall_ai.ui.theme.ColorNavy
import com.mohit.recall_ai.ui.theme.ColorOnSurfaceDim
import com.mohit.recall_ai.ui.theme.ColorRecordRed
import com.mohit.recall_ai.ui.theme.ColorSurface
import com.mohit.recall_ai.ui.theme.ColorTextSlate400
import com.mohit.recall_ai.ui.theme.ColorTextSlate900
import com.mohit.recall_ai.ui.theme.ColorWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RemindersScreen(
    onNavigateBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val totalCount = state.alarms.size + state.todos.size

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Top bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ColorNavy
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reminders",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = ColorNavy
                    )
                    Text(
                        text = "$totalCount items",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorOnSurfaceDim
                    )
                }
            }

            if (totalCount == 0) {
                // ── Empty state ──────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = ColorTextSlate400,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No reminders yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ColorOnSurfaceDim
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ask Bob to set reminders during a Live AI session",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSlate400
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ── Alarms section ───────────────────────────────
                    if (state.alarms.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Alarms",
                                count = state.alarms.size
                            )
                        }
                        items(state.alarms, key = { it.id }) { reminder ->
                            AlarmCard(
                                reminder = reminder,
                                onDismiss = { viewModel.dismissAlarm(reminder.id) },
                                onDelete = { viewModel.deleteReminder(reminder.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // ── Todos section ────────────────────────────────
                    if (state.todos.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "To-Dos",
                                count = state.todos.size
                            )
                        }
                        items(state.todos, key = { it.id }) { reminder ->
                            TodoCard(
                                reminder = reminder,
                                onToggle = {
                                    if (reminder.status == ReminderStatus.COMPLETED) {
                                        // Already completed — no un-complete for now
                                    } else {
                                        viewModel.markCompleted(reminder.id)
                                    }
                                },
                                onDelete = { viewModel.deleteReminder(reminder.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION HEADER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = ColorNavy
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(ColorNavy.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = ColorNavy
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ALARM CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AlarmCard(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val isFired = reminder.status == ReminderStatus.FIRED
    val isDismissed = reminder.status == ReminderStatus.DISMISSED
    val isPending = reminder.status == ReminderStatus.PENDING

    val statusColor = when {
        isPending -> ColorWarning
        isFired -> ColorRecordRed
        isDismissed -> ColorOnSurfaceDim
        else -> ColorOnSurfaceDim
    }
    val statusText = when {
        isPending -> "Pending"
        isFired -> "Fired"
        isDismissed -> "Dismissed"
        else -> reminder.status.name
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Alarm icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Alarm,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ColorTextSlate900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Trigger time
                if (reminder.triggerAtMillis != null) {
                    val timeText = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        .format(Date(reminder.triggerAtMillis))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorOnSurfaceDim
                    )
                    Text(
                        text = "  ·  ",
                        color = ColorOnSurfaceDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                // Status badge
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = statusColor
                )
            }
        }

        // Actions
        if (isFired) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Dismiss",
                    tint = ColorDone,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete",
                tint = ColorOnSurfaceDim,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TODO CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TodoCard(
    reminder: Reminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = reminder.status == ReminderStatus.COMPLETED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isCompleted) "Completed" else "Mark complete",
            tint = if (isCompleted) ColorDone else ColorTextSlate400,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (isCompleted) ColorOnSurfaceDim else ColorTextSlate900,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val dateText = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                .format(Date(reminder.createdAt))
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = ColorTextSlate400
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete",
                tint = ColorOnSurfaceDim,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
