package com.jxcode.cyclebell.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY startTimeEnabled DESC, startTimeHour ASC, startTimeMinute ASC, startTimeSecond ASC, title COLLATE NOCASE ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminder(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET enabled = 0, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun disableReminder(id: Long, updatedAtMillis: Long)

    @Query(
        """
        UPDATE reminders
        SET enabled = :enabled,
            completedCount = 0,
            nextTriggerAtMillis = :nextTriggerAtMillis,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun restartReminder(id: Long, enabled: Boolean, nextTriggerAtMillis: Long?, updatedAtMillis: Long)

    @Query(
        """
        UPDATE reminders
        SET completedCount = :completedCount,
            nextTriggerAtMillis = :nextTriggerAtMillis,
            enabled = :enabled,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun updateAfterTrigger(
        id: Long,
        completedCount: Int,
        nextTriggerAtMillis: Long?,
        enabled: Boolean,
        updatedAtMillis: Long
    )
}
