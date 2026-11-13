package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class PdfAioAllInOneWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_all_in_one)

            // Setup Pending Intents for deep-linking into MainActivity
            setupPendingIntent(context, views, R.id.btn_widget_scan, "open_scanner")
            setupPendingIntent(context, views, R.id.btn_widget_pdf_word, "open_pdf_to_word")
            setupPendingIntent(context, views, R.id.btn_widget_word_pdf, "open_word_to_pdf")
            setupPendingIntent(context, views, R.id.btn_widget_img_pdf, "open_images_to_pdf")
            setupPendingIntent(context, views, R.id.btn_widget_pdf_img, "open_pdf_to_images")
            setupPendingIntent(context, views, R.id.btn_widget_developer, "open_credits")

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun setupPendingIntent(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        action: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("shortcut_action", action)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }
}
