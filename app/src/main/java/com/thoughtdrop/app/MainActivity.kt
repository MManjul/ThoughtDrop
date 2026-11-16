package com.thoughtdrop.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.thoughtdrop.app.ui.screens.HomeScreen
import com.thoughtdrop.app.ui.theme.ThoughtDropTheme
import com.thoughtdrop.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var hasRecordPermission = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasRecordPermission = isGranted
        if (isGranted && intent?.action == ACTION_QUICK_RECORD) {
            viewModel.startRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            checkRecordPermission()
        } catch (_: Exception) {}

        setContent {
            ThoughtDropTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onRequestRecordAudioPermission = {
                        try {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } catch (_: Exception) {}
                    },
                    hasRecordPermission = hasRecordPermission
                )
            }
        }

        try {
            handleIntent(intent)
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            handleIntent(intent)
        } catch (_: Exception) {}
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_RECORD) {
            if (hasRecordPermission) {
                viewModel.startRecording()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun checkRecordPermission() {
        hasRecordPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_QUICK_RECORD = "com.thoughtdrop.app.ACTION_QUICK_RECORD"
    }
}
