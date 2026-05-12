package com.jj.myclock.data

import androidx.room.TypeConverter

class ReminderTypeConverters {
    @TypeConverter
    fun reminderModeToString(mode: ReminderMode): String = mode.name

    @TypeConverter
    fun stringToReminderMode(value: String): ReminderMode = ReminderMode.valueOf(value)
}
