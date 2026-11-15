package com.example.data.legal

import android.content.Context
import com.example.BuildConfig

/**
 * Tracks whether the user has accepted all PDF (AIO) Terms, Conditions, and Policies
 * for the currently installed app versionCode.
 *
 * Behavior guarantees:
 * - Fresh install: no recorded versionCode -> returns needsConsent = true (FIRST_INSTALL).
 * - Reinstall: android:allowBackup="false" clears private SharedPreferences on uninstall ->
 *   returns needsConsent = true (FIRST_INSTALL).
 * - App update: new BuildConfig.VERSION_CODE != recorded acceptedVersionCode ->
 *   returns needsConsent = true (APP_UPDATE).
 * - Accepted current version: needsConsent = false until updated or reinstalled.
 * - Denied: acceptance is never persisted, so the gate re-appears on next launch.
 */
object LegalConsentManager {

    enum class ConsentTriggerReason {
        FIRST_INSTALL,
        APP_UPDATE
    }

    private const val PREFS_NAME = "legal_consent_prefs"
    private const val KEY_ACCEPTED_VERSION_CODE = "accepted_version_code"
    private const val KEY_ACCEPTED_VERSION_NAME = "accepted_version_name"
    private const val KEY_ACCEPTED_AT_MILLIS = "accepted_at_millis"

    fun needsConsent(
        context: Context,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
    ): Boolean {
        val acceptedCode = getAcceptedVersionCode(context)
        return acceptedCode <= 0L || acceptedCode != currentVersionCode
    }

    fun getTriggerReason(
        context: Context,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
    ): ConsentTriggerReason {
        val acceptedCode = getAcceptedVersionCode(context)
        return if (acceptedCode > 0L && acceptedCode != currentVersionCode) {
            ConsentTriggerReason.APP_UPDATE
        } else {
            ConsentTriggerReason.FIRST_INSTALL
        }
    }

    fun getAcceptedVersionCode(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_ACCEPTED_VERSION_CODE, -1L)
    }

    fun recordAcceptance(
        context: Context,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.edit()
            .putLong(KEY_ACCEPTED_VERSION_CODE, currentVersionCode)
            .putString(KEY_ACCEPTED_VERSION_NAME, currentVersionName)
            .putLong(KEY_ACCEPTED_AT_MILLIS, System.currentTimeMillis())
            .commit()
    }

    fun clearConsent(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.edit().clear().commit()
    }
}
