package com.jxcode.cyclebell.data

import androidx.room.TypeConverter

class ReminderTypeConverters {
    @TypeConverter
    fun reminderModeToString(mode: ReminderMode): String = mode.name

    @TypeConverter
    fun stringToReminderMode(value: String): ReminderMode = ReminderMode.valueOf(value)

    @TypeConverter
    fun repeatEndTypeToString(type: RepeatEndType): String = type.name

    @TypeConverter
    fun stringToRepeatEndType(value: String): RepeatEndType = RepeatEndType.valueOf(value)
}
