package com.example.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

/**
 * Handles posting official Android system notifications when a new version
 * of PDF AIO is published on GitHub.
 */
object UpdateNotificationHelper {

    private const val TAG = "UpdateNotification"
    const val CHANNEL_ID = "pdf_aio_app_updates_channel"
    const val NOTIFICATION_ID = 10088

    /**
     * Ensure the high-priority system notification channel exists on Android 8.0+ (API 26+).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PDF AIO App Updates"
            val descriptionText = "Notifications when a new version of PDF AIO is available to download"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Post a system shade notification informing the user that a new version is available.
     * Tapping the notification automatically launches PDF AIO and pops up the update dialog.
     */
    fun showUpdateNotification(context: Context, releaseInfo: AppReleaseInfo) {
        try {
            createNotificationChannel(context)

            // Intent to launch MainActivity with ACTION_OPEN_UPDATE_DIALOG
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_OPEN_UPDATE_DIALOG"
                putExtra("open_update_dialog", true)
                putExtra("version_name", releaseInfo.versionName)
                putExtra("release_title", releaseInfo.releaseTitle)
                putExtra("release_notes", releaseInfo.releaseNotes)
                putExtra("download_url", releaseInfo.downloadUrl)
                putExtra("apk_size", releaseInfo.apkSize)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                pendingIntentFlags
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.pdf_aio_logo)
                .setContentTitle("PDF AIO ( By Ishant)")
                .setContentText("Hey User, a New Update is availble to download..\nPress on Notification To Update it..")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle("PDF AIO ( By Ishant)")
                        .bigText(
                            "Hey User, a New Update is availble to download..\nPress on Notification To Update it..\n\nNew Version: v${releaseInfo.versionName}\nNotes: ${releaseInfo.releaseTitle}"
                        )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Posted system update notification for version v${releaseInfo.versionName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post system update notification: ${e.message}", e)
        }
    }
}
