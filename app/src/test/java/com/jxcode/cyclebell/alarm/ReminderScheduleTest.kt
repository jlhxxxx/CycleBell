package com.jxcode.cyclebell.alarm

import com.jxcode.cyclebell.data.ReminderEntity
import com.jxcode.cyclebell.data.RepeatEndType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {
    @Test
    fun recoverTwoDayReminderKeepsOriginalCadence() {
        val anchor = millis(2026, 1, 1, 8, 0, 0)
        val now = millis(2026, 1, 6, 12, 0, 0)

        val recovered = ReminderSchedule.recoverNext(
            reminder = reminder(
                repeatEnabled = true,
                intervalDays = 2,
                completedCount = 2,
                nextTriggerAtMillis = millis(2026, 1, 5, 8, 0, 0),
                scheduleAnchorAtMillis = anchor
            ),
            nowMillis = now
        )

        assertTrue(recovered.enabled)
        assertEquals(millis(2026, 1, 7, 8, 0, 0), recovered.nextTriggerAtMillis)
        assertEquals(anchor, recovered.scheduleAnchorAtMillis)
    }

    @Test
    fun recoverDailyReminderSkipsPastTriggers() {
        val anchor = millis(2026, 5, 23, 8, 30, 0)
        val now = millis(2026, 5, 28, 12, 0, 0)

        val recovered = ReminderSchedule.recoverNext(
            reminder = reminder(
                repeatEnabled = true,
                intervalDays = 1,
                nextTriggerAtMillis = anchor,
                scheduleAnchorAtMillis = anchor
            ),
            nowMillis = now
        )

        assertTrue(recovered.enabled)
        assertEquals(millis(2026, 5, 29, 8, 30, 0), recovered.nextTriggerAtMillis)
    }

    @Test
    fun recoverMissedOneTimeReminderDoesNotRingLate() {
        val triggerAt = millis(2026, 5, 23, 8, 30, 0)

        val recovered = ReminderSchedule.recoverNext(
            reminder = reminder(
                repeatEnabled = false,
                nextTriggerAtMillis = triggerAt,
                scheduleAnchorAtMillis = triggerAt
            ),
            nowMillis = millis(2026, 5, 28, 12, 0, 0)
        )

        assertFalse(recovered.enabled)
        assertEquals(null, recovered.nextTriggerAtMillis)
        assertEquals(triggerAt, recovered.scheduleAnchorAtMillis)
    }

    private fun reminder(
        repeatEnabled: Boolean,
        intervalDays: Int = 0,
        completedCount: Int = 0,
        nextTriggerAtMillis: Long?,
        scheduleAnchorAtMillis: Long?
    ) = ReminderEntity(
        id = 1,
        title = "Test",
        enabled = true,
        startTimeEnabled = true,
        startTimeHour = 8,
        startTimeMinute = 0,
        startTimeSecond = 0,
        repeatEnabled = repeatEnabled,
        intervalDays = intervalDays,
        intervalHours = 0,
        intervalMinutes = 0,
        intervalSeconds = 0,
        repeatEndType = RepeatEndType.NEVER,
        repeatEndAfterTimes = null,
        repeatEndAtHour = null,
        repeatEndAtMinute = null,
        repeatEndAtSecond = null,
        ringDurationSeconds = 5,
        vibrate = false,
        ringtoneUri = null,
        completedCount = completedCount,
        nextTriggerAtMillis = nextTriggerAtMillis,
        scheduleAnchorAtMillis = scheduleAnchorAtMillis,
        createdAtMillis = scheduleAnchorAtMillis ?: 0L,
        updatedAtMillis = scheduleAnchorAtMillis ?: 0L
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
