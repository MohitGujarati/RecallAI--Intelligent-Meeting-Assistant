package com.example.recall_ai.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recall_ai.data.local.dao.ReminderDao
import com.example.recall_ai.data.local.entity.Reminder
import com.example.recall_ai.data.local.entity.ReminderStatus
import com.example.recall_ai.data.local.entity.ReminderType
import com.example.recall_ai.service.reminder.ReminderAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemindersUiState(
    val alarms: List<Reminder> = emptyList(),
    val todos: List<Reminder> = emptyList()
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: ReminderAlarmScheduler
) : ViewModel() {

    val uiState: StateFlow<RemindersUiState> = reminderDao.observeAll()
        .map { all ->
            RemindersUiState(
                alarms = all.filter { it.type == ReminderType.ALARM }
                    .sortedByDescending { it.triggerAtMillis },
                todos = all.filter { it.type == ReminderType.TODO }
                    .sortedByDescending { it.createdAt }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun markCompleted(reminderId: Long) {
        viewModelScope.launch {
            reminderDao.updateStatus(reminderId, ReminderStatus.COMPLETED)
        }
    }

    fun dismissAlarm(reminderId: Long) {
        viewModelScope.launch {
            reminderDao.updateStatus(reminderId, ReminderStatus.DISMISSED)
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            alarmScheduler.cancel(reminderId)
            reminderDao.deleteById(reminderId)
        }
    }
}
