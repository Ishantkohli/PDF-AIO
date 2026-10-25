package com.example.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background BroadcastReceiver that runs periodically (or on device reboot)
 * to query GitHub Releases for new updates and trigger system shade notifications.
 */
class UpdateCheckAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("UpdateAlarmReceiver", "Received background update check action: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = UpdateManager.checkForUpdates(context)
                result.onSuccess { releaseInfo ->
                    if (releaseInfo != null) {
                        Log.i("UpdateAlarmReceiver", "Background check found new release v${releaseInfo.versionName}! Posting notification...")
                        UpdateNotificationHelper.showUpdateNotification(context, releaseInfo)
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateAlarmReceiver", "Error in background update check: ${e.message}", e)
            } finally {
                // Schedule next alarm in 8 hours
                scheduleNextAlarm(context)
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK_UPDATE = "com.example.update.ACTION_CHECK_UPDATE_BACKGROUND"

        /**
         * Schedule periodic background update check alarm (runs every 8 hours).
         */
        fun scheduleNextAlarm(context: Context) {
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

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2001,
                    intent,
                    flags
                )

                val intervalMs = 8 * 60 * 60 * 1000L // 8 hours
                val triggerAtMs = System.currentTimeMillis() + intervalMs

                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    intervalMs,
                    pendingIntent
                )
                Log.d("UpdateAlarmReceiver", "Next background update alarm scheduled in 8 hours.")
            } catch (e: Exception) {
                Log.e("UpdateAlarmReceiver", "Failed to schedule background update alarm: ${e.message}", e)
            }
        }
    }
}
