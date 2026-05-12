package com.jj.myclock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val enabled: Boolean,
    val mode: ReminderMode,
    val startDateTimeMillis: Long,
    val ringtoneUri: String?,
    val intervalMinutes: Int?,
    val repeatCount: Int?,
    val intervalDays: Int?,
    val completedCount: Int,
    val nextTriggerAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
