package com.jj.myclock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY enabled DESC, nextTriggerAtMillis IS NULL, nextTriggerAtMillis ASC, updatedAtMillis DESC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminder(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET enabled = :enabled, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAtMillis: Long)
}
