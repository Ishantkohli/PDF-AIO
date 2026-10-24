package com.example.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * System broadcast receiver invoked by Android OS when this application has been updated
 * to a new version (ACTION_MY_PACKAGE_REPLACED).
 *
 * Automatically and permanently purges any downloaded APK installation files from cache
 * to preserve user device storage space.
 */
class UpdateCleanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("UpdateCleanReceiver", "PDF AIO package replaced/updated! Automatically deleting update APKs...")
            UpdateManager.cleanCachedApks(context)
        }
    }
}
