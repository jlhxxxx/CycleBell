package com.jxcode.cyclebell.alarm

import android.content.Context
import com.jxcode.cyclebell.data.AppDatabase
import com.jxcode.cyclebell.data.ReminderDao
import com.jxcode.cyclebell.data.ReminderEntity

object ReminderRecovery {
    suspend fun recover(context: Context) {
        val dao = AppDatabase.getInstance(context).reminderDao()
        recover(dao, AlarmScheduler(context))
    }

    suspend fun recover(
        reminderDao: ReminderDao,
        alarmScheduler: AlarmScheduler,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        reminderDao.getEnabledReminders().forEach { reminder ->
            recoverReminder(reminderDao, alarmScheduler, reminder, nowMillis)
        }
    }

    private suspend fun recoverReminder(
        reminderDao: ReminderDao,
        alarmScheduler: AlarmScheduler,
        reminder: ReminderEntity,
        nowMillis: Long
    ) {
        val recovered = ReminderSchedule.recoverNext(reminder, nowMillis)
        if (!recovered.enabled) {
            alarmScheduler.cancel(reminder.id)
            reminderDao.updateScheduleState(
                id = reminder.id,
                enabled = false,
                nextTriggerAtMillis = null,
                scheduleAnchorAtMillis = recovered.scheduleAnchorAtMillis,
                updatedAtMillis = nowMillis
            )
            return
        }

        val scheduledReminder = reminder.copy(
            enabled = true,
            nextTriggerAtMillis = recovered.nextTriggerAtMillis,
            scheduleAnchorAtMillis = recovered.scheduleAnchorAtMillis
        )

        if (
            reminder.nextTriggerAtMillis != recovered.nextTriggerAtMillis ||
            reminder.scheduleAnchorAtMillis != recovered.scheduleAnchorAtMillis
        ) {
            reminderDao.updateScheduleState(
                id = reminder.id,
                enabled = true,
                nextTriggerAtMillis = recovered.nextTriggerAtMillis,
                scheduleAnchorAtMillis = recovered.scheduleAnchorAtMillis,
                updatedAtMillis = nowMillis
            )
        }

        alarmScheduler.schedule(scheduledReminder)
    }
}
