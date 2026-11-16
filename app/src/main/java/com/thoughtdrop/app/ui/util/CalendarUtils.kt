package com.thoughtdrop.app.ui.util

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import java.util.Calendar

object CalendarUtils {

    fun addToCalendar(
        context: Context,
        taskTitle: String,
        noteTitle: String,
        noteSummary: String,
        startTimeMillis: Long = getTomorrowMorningMillis()
    ) {
        val endTimeMillis = startTimeMillis + (60 * 60 * 1000L) // 1 hour duration
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, taskTitle)
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Action item from ThoughtDrop note: \"$noteTitle\"\n\nSummary:\n$noteSummary"
            )
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No Calendar app found on device", Toast.LENGTH_SHORT).show()
        }
    }

    fun setDeviceAlarm(
        context: Context,
        taskTitle: String,
        hour: Int = 9,
        minute: Int = 0
    ) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, taskTitle)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No Clock / Alarm app found on device", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sets a device alarm directly in the background without user interaction.
     * Uses AlarmClock.EXTRA_SKIP_UI = true.
     */
    fun setSilentDeviceAlarm(
        context: Context,
        taskTitle: String,
        hour: Int = 9,
        minute: Int = 0
    ): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, taskTitle)
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getTomorrowMorningMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getTodayEveningMillis(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (now.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    fun getInTwoDaysMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 2)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
