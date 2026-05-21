package com.jxcode.cyclebell.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val enabled: Boolean,
    val startTimeEnabled: Boolean,
    val startTimeHour: Int?,
    val startTimeMinute: Int?,
    val startTimeSecond: Int?,
    val repeatEnabled: Boolean,
    val intervalDays: Int,
    val intervalHours: Int,
    val intervalMinutes: Int,
    val intervalSeconds: Int,
    val repeatEndType: RepeatEndType,
    val repeatEndAfterTimes: Int?,
    val repeatEndAtHour: Int?,
    val repeatEndAtMinute: Int?,
    val repeatEndAtSecond: Int?,
    val ringDurationSeconds: Int,
    val vibrate: Boolean,
    val ringtoneUri: String?,
    val completedCount: Int,
    val nextTriggerAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

enum class RepeatEndType {
    NEVER,
    AFTER_TIMES,
    AT_TIME
}
