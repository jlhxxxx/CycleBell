package com.jxcode.cyclebell.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jxcode.cyclebell.MainActivity
import com.jxcode.cyclebell.R
import com.jxcode.cyclebell.data.AppDatabase
import com.jxcode.cyclebell.data.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_TRIGGER_REMINDER) return

        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(context.applicationContext, reminderId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarm(context: Context, reminderId: Long) {
        val reminderDao = AppDatabase.getInstance(context).reminderDao()
        val reminder = reminderDao.getReminder(reminderId) ?: return
        if (!reminder.enabled) return

        showReminderNotification(context, reminder)
        playReminderAlert(context, reminder)

        val completedCount = reminder.completedCount + 1
        val previousTriggerAtMillis = reminder.nextTriggerAtMillis ?: System.currentTimeMillis()
        val next = ReminderSchedule.nextAfterTrigger(
            reminder = reminder,
            completedCount = completedCount,
            previousTriggerAtMillis = previousTriggerAtMillis
        )

        reminderDao.updateAfterTrigger(
            id = reminder.id,
            completedCount = completedCount,
            nextTriggerAtMillis = next.nextTriggerAtMillis,
            enabled = next.enabled,
            updatedAtMillis = System.currentTimeMillis()
        )

        if (next.enabled) {
            AlarmScheduler(context).schedule(
                reminder.copy(
                    completedCount = completedCount,
                    nextTriggerAtMillis = next.nextTriggerAtMillis
                )
            )
        }
    }

    private fun showReminderNotification(context: Context, reminder: ReminderEntity) {
        val channelId = "cycle_bell_reminder_${reminder.id}"
        ensureNotificationChannel(context, channelId)

        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminder.title)
            .setContentText("Time to ring")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Android 13+ requires notification permission.
        }
    }

    private suspend fun playReminderAlert(context: Context, reminder: ReminderEntity) {
        val ringtone = RingtoneManager.getRingtone(context, reminder.soundUri()) ?: return
        val durationMillis = reminder.ringDurationSeconds.coerceIn(1, 120) * 1_000L
        val vibrator = if (reminder.vibrate) context.reminderVibrator() else null
        try {
            ringtone.play()
            vibrator?.vibrateReminder(durationMillis)
            delay(durationMillis)
        } finally {
            ringtone.stop()
            vibrator?.cancel()
        }
    }

    private fun ensureNotificationChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(channelId, "Cycle Bell", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Cycle Bell reminder notifications"
            setSound(null, null)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun ReminderEntity.soundUri(): Uri {
        return ringtoneUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun Context.reminderVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    private fun Vibrator.vibrateReminder(durationMillis: Long) {
        if (!hasVibrator()) return
        val pattern = longArrayOf(0L, 400L, 200L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrate(VibrationEffect.createWaveform(pattern, 1))
        } else {
            @Suppress("DEPRECATION")
            vibrate(pattern, 1)
        }
    }
}
