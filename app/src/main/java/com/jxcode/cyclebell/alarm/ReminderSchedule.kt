package com.jxcode.cyclebell.alarm

import com.jxcode.cyclebell.data.ReminderEntity
import com.jxcode.cyclebell.data.RepeatEndType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderSchedule {
    private const val MILLIS_PER_SECOND = 1_000L

    fun firstTrigger(reminder: ReminderEntity, nowMillis: Long = System.currentTimeMillis()): Long {
        if (!reminder.startTimeEnabled) {
            val delaySeconds = if (reminder.repeatEnabled) reminder.intervalSecondsTotal().coerceAtLeast(1L) else 0L
            return nowMillis + delaySeconds * MILLIS_PER_SECOND
        }

        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val hour = reminder.startTimeHour ?: now.hour
        val minute = reminder.startTimeMinute ?: 0
        val second = reminder.startTimeSecond ?: 0
        var start = LocalDateTime.of(LocalDate.from(now), LocalTime.of(hour, minute, second))
        if (!start.isAfter(now)) start = start.plusDays(1)

        return start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun withFreshSchedule(reminder: ReminderEntity, nowMillis: Long = System.currentTimeMillis()): ReminderEntity {
        val firstTrigger = firstTrigger(reminder, nowMillis)
        return reminder.copy(
            completedCount = 0,
            nextTriggerAtMillis = firstTrigger,
            scheduleAnchorAtMillis = firstTrigger
        )
    }

    fun nextAfterTrigger(
        reminder: ReminderEntity,
        completedCount: Int,
        previousTriggerAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): NextSchedule {
        if (!reminder.repeatEnabled) {
            return NextSchedule(enabled = false, nextTriggerAtMillis = null)
        }

        if (reminder.repeatEndType == RepeatEndType.AFTER_TIMES) {
            val maxTimes = reminder.repeatEndAfterTimes ?: 1
            if (completedCount >= maxTimes) {
                return NextSchedule(enabled = false, nextTriggerAtMillis = null)
            }
        }

        val intervalMillis = reminder.intervalSecondsTotal().coerceAtLeast(1L) * MILLIS_PER_SECOND
        var candidate = previousTriggerAtMillis + intervalMillis
        while (candidate <= nowMillis) {
            candidate += intervalMillis
        }

        if (reminder.repeatEndType == RepeatEndType.AT_TIME && isAfterEndTime(candidate, reminder)) {
            return NextSchedule(enabled = false, nextTriggerAtMillis = null)
        }

        return NextSchedule(enabled = true, nextTriggerAtMillis = candidate)
    }

    fun recoverNext(reminder: ReminderEntity, nowMillis: Long = System.currentTimeMillis()): NextSchedule {
        val nextTrigger = reminder.nextTriggerAtMillis
        val anchor = reminder.scheduleAnchorAtMillis ?: nextTrigger ?: firstTrigger(reminder, nowMillis)

        if (!reminder.repeatEnabled) {
            return if (nextTrigger != null && nextTrigger > nowMillis) {
                NextSchedule(enabled = true, nextTriggerAtMillis = nextTrigger, scheduleAnchorAtMillis = anchor)
            } else {
                NextSchedule(enabled = false, nextTriggerAtMillis = null, scheduleAnchorAtMillis = anchor)
            }
        }

        if (reminder.repeatEndType == RepeatEndType.AFTER_TIMES) {
            val maxTimes = reminder.repeatEndAfterTimes ?: 1
            if (reminder.completedCount >= maxTimes) {
                return NextSchedule(enabled = false, nextTriggerAtMillis = null, scheduleAnchorAtMillis = anchor)
            }
        }

        val intervalMillis = reminder.intervalSecondsTotal().coerceAtLeast(1L) * MILLIS_PER_SECOND
        var candidate = anchor
        while (candidate <= nowMillis) {
            candidate += intervalMillis
        }

        if (reminder.repeatEndType == RepeatEndType.AT_TIME && isAfterEndTime(candidate, reminder)) {
            return NextSchedule(enabled = false, nextTriggerAtMillis = null, scheduleAnchorAtMillis = anchor)
        }

        return NextSchedule(enabled = true, nextTriggerAtMillis = candidate, scheduleAnchorAtMillis = anchor)
    }

    fun ReminderEntity.intervalSecondsTotal(): Long {
        return intervalDays * 86_400L + intervalHours * 3_600L + intervalMinutes * 60L + intervalSeconds
    }

    private fun isAfterEndTime(candidateMillis: Long, reminder: ReminderEntity): Boolean {
        val candidate = LocalDateTime.ofInstant(Instant.ofEpochMilli(candidateMillis), ZoneId.systemDefault())
        val anchorMillis = reminder.scheduleAnchorAtMillis ?: reminder.nextTriggerAtMillis ?: candidateMillis
        val anchor = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchorMillis), ZoneId.systemDefault())
        if (candidate.toLocalDate() != anchor.toLocalDate()) return false

        val endTime = LocalTime.of(
            reminder.repeatEndAtHour ?: 0,
            reminder.repeatEndAtMinute ?: 0,
            reminder.repeatEndAtSecond ?: 0
        )
        val endDateTime = LocalDateTime.of(candidate.toLocalDate(), endTime)
        return candidate.isAfter(endDateTime)
    }
}

data class NextSchedule(
    val enabled: Boolean,
    val nextTriggerAtMillis: Long?,
    val scheduleAnchorAtMillis: Long? = null
)
