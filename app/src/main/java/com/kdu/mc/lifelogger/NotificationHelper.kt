package com.kdu.mc.lifelogger

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

const val CHANNEL_ID = "life_logger_reminders"
const val CHANNEL_NAME = "Life Logger Reminders"
const val EXTRA_NOTE_TITLE = "note_title"
const val EXTRA_NOTE_ID = "note_id"

// ─── BroadcastReceiver ────────────────────────────────────────────────────────

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ReminderReceiver", "onReceive fired!")
        val title = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "Reminder"
        val noteId = intent.getIntExtra(EXTRA_NOTE_ID, 0)
        showNotification(context, noteId, title)
    }
}

// ─── Show notification ────────────────────────────────────────────────────────

fun showNotification(context: Context, id: Int, title: String) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders from Life Logger calendar notes"
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val contentPendingIntent = PendingIntent.getActivity(
        context, id, launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("📅 Life Logger Reminder")
        .setContentText(title)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .setContentIntent(contentPendingIntent)
        .build()

    manager.notify(id, notification)
    Log.d("ReminderReceiver", "Notification posted id=$id title=$title")
}

// ─── Check if exact alarms are allowed ───────────────────────────────────────

fun canScheduleExactAlarms(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
}

// ─── Open system settings to grant exact alarm permission ────────────────────

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

// ─── Schedule reminder ────────────────────────────────────────────────────────

fun scheduleReminder(context: Context, noteId: Int, noteTitle: String, triggerAtMillis: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = buildIntent(context, noteId, noteTitle)
    val pendingIntent = buildPendingIntent(context, noteId, intent)

    Log.d("scheduleReminder", "Scheduling noteId=$noteId at $triggerAtMillis, now=${System.currentTimeMillis()}, diff=${triggerAtMillis - System.currentTimeMillis()}ms")

    try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d("scheduleReminder", "setExactAndAllowWhileIdle scheduled OK")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // No exact alarm permission — use inexact (fires within a few minutes)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d("scheduleReminder", "setAndAllowWhileIdle scheduled (inexact)")
            }
            else -> {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d("scheduleReminder", "setExact scheduled OK")
            }
        }
    } catch (e: SecurityException) {
        Log.e("scheduleReminder", "SecurityException: ${e.message}")
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}

// ─── Cancel reminder ──────────────────────────────────────────────────────────

fun cancelReminder(context: Context, noteId: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, noteId, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}

// ─── Create notification channel ─────────────────────────────────────────────

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders from Life Logger calendar notes"
            enableVibration(true)
            enableLights(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun buildIntent(context: Context, noteId: Int, noteTitle: String): Intent {
    return Intent(context, ReminderReceiver::class.java).apply {
        action = "com.kdu.mc.lifelogger.REMINDER_$noteId"
        putExtra(EXTRA_NOTE_TITLE, noteTitle)
        putExtra(EXTRA_NOTE_ID, noteId)
    }
}

private fun buildPendingIntent(context: Context, noteId: Int, intent: Intent): PendingIntent {
    return PendingIntent.getBroadcast(
        context, noteId, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
