package com.example.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Periodic 8-hour background GitHub Releases update check receiver, rescheduled on device boot.
 */
class UpdateCheckAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            schedulePeriodicCheck(context)
        }

        if (action == ACTION_CHECK_UPDATE || action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    UpdateManager.checkForUpdates(context.applicationContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Background update check failed: ${e.message}")
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    companion object {
        private const val TAG = "UpdateAlarmReceiver"
        const val ACTION_CHECK_UPDATE = "com.example.update.ACTION_CHECK_UPDATE_BACKGROUND"
        private const val REQUEST_CODE = 2001
        private const val INTERVAL_8_HOURS_MS = 8L * 60L * 60L * 1000L

        fun schedulePeriodicCheck(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, UpdateCheckAlarmReceiver::class.java).apply {
                    action = ACTION_CHECK_UPDATE
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
                val firstTriggerAt = SystemClock.elapsedRealtime() + INTERVAL_8_HOURS_MS

                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    firstTriggerAt,
                    INTERVAL_8_HOURS_MS,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not schedule periodic update check: ${e.message}")
            }
        }

        fun cancelScheduledAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, UpdateCheckAlarmReceiver::class.java).apply {
                    action = ACTION_CHECK_UPDATE
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_NO_CREATE
                }
                val existing = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
                if (existing != null) {
                    alarmManager.cancel(existing)
                    existing.cancel()
                }
            } catch (_: Exception) {
            }
        }
    }
}
