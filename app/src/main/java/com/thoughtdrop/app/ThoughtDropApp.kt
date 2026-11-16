package com.thoughtdrop.app

import android.app.Application
import android.util.Log
import com.thoughtdrop.app.data.local.AppDatabase
import com.thoughtdrop.app.data.repository.SettingsRepository
import java.io.File

class ThoughtDropApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ThoughtDrop", "Fatal crash on thread ${thread.name}", throwable)
            try {
                val crashFile = File(filesDir, "crash_log.txt")
                crashFile.writeText(throwable.stackTraceToString())
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: ThoughtDropApp
            private set
    }
}
