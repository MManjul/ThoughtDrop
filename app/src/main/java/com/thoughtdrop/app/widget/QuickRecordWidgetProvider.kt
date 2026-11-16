package com.thoughtdrop.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.thoughtdrop.app.QuickRecordActivity
import com.thoughtdrop.app.R

class QuickRecordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, QuickRecordActivity::class.java).apply {
                action = "com.thoughtdrop.app.ACTION_QUICK_RECORD"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_quick_record).apply {
                try {
                    setImageViewResource(R.id.widget_button, R.drawable.ic_quick_mic_dark)
                    setImageViewResource(R.id.widget_orb, R.drawable.img_widget_orb)
                } catch (_: Exception) {}
                setOnClickPendingIntent(R.id.widget_button, pendingIntent)
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
