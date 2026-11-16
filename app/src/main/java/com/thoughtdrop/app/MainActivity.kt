package com.thoughtdrop.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.thoughtdrop.app.ui.screens.HomeScreen
import com.thoughtdrop.app.ui.theme.ThoughtDropTheme
import com.thoughtdrop.app.ui.util.BiometricAuthManager
import com.thoughtdrop.app.ui.viewmodel.MainViewModel

/**
 * MainActivity for ThoughtDrop
 * Inherits from FragmentActivity to support AndroidX BiometricPrompt (Fingerprint / PIN / Pattern)
 * Implements Option A: Instant quick-record capture, device-lock protected note browsing.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var hasRecordPermission = false
    private var wasInBackground = false

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

        // If device lock is enabled, initiate authentication immediately on launch
        if (viewModel.settingsRepo.isDeviceLockEnabled()) {
            viewModel.lockApp()
            triggerBiometricUnlock()
        } else {
            viewModel.unlockApp()
        }

        setContent {
            ThoughtDropTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onRequestRecordAudioPermission = {
                        try {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } catch (_: Exception) {}
                    },
                    hasRecordPermission = hasRecordPermission,
                    onTriggerBiometricUnlock = {
                        triggerBiometricUnlock()
                    }
                )
            }
        }

        try {
            handleIntent(intent)
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        // App went to background
        wasInBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (wasInBackground) {
            wasInBackground = false
            if (viewModel.settingsRepo.isDeviceLockEnabled()) {
                viewModel.lockApp()
                triggerBiometricUnlock()
            }
        }
    }

    private fun triggerBiometricUnlock() {
        if (!viewModel.settingsRepo.isDeviceLockEnabled()) {
            viewModel.unlockApp()
            return
        }

        BiometricAuthManager.authenticate(
            activity = this,
            title = "Unlock ThoughtDrop",
            subtitle = "Confirm fingerprint, PIN, or pattern to view your notes",
            onSuccess = {
                viewModel.unlockApp()
            },
            onError = {
                // Keep locked; user can tap unlock button to retry
            }
        )
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
