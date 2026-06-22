package com.jxcode.cyclebell.data

import androidx.room.TypeConverter

class ReminderTypeConverters {
    @TypeConverter
    fun repeatEndTypeToString(type: RepeatEndType): String = type.name

    @TypeConverter
    fun stringToRepeatEndType(value: String): RepeatEndType = RepeatEndType.valueOf(value)
}
