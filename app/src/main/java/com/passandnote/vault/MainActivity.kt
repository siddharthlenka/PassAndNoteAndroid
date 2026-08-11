package com.passandnote.vault

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.passandnote.vault.ui.screens.OnboardingFlow
import com.passandnote.vault.ui.screens.PrivacyShield
import com.passandnote.vault.ui.screens.UnlockScreen
import com.passandnote.vault.ui.screens.VaultContent
import com.passandnote.vault.ui.theme.PassAndNoteTheme

class MainActivity : ComponentActivity() {
    private val biometricAuthenticator = BiometricAuthenticator(this)
    private lateinit var vaultState: VaultAppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        protectRecentAppsPreview()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        vaultState = VaultAppState(this, biometricAuthenticator)
        setContent {
            PassAndNoteTheme { PassNoteVaultApp(vaultState) }
        }
    }

    private fun protectRecentAppsPreview() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onPause() {
        vaultState.onInactive()
        super.onPause()
    }

    override fun onStop() {
        vaultState.onBackground()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        vaultState.onForeground()
    }

    override fun onDestroy() {
        if (isFinishing) vaultState.database.close()
        super.onDestroy()
    }
}

@Composable
private fun PassNoteVaultApp(state: VaultAppState) {
    BackHandler(enabled = state.rootMode == RootMode.UNLOCKED && state.destinations.size > 1) {
        state.back()
    }
    Box(Modifier.fillMaxSize()) {
        when (state.rootMode) {
            RootMode.ONBOARDING -> OnboardingFlow(state)
            RootMode.LOCKED -> UnlockScreen(state)
            RootMode.UNLOCKED -> VaultContent(state)
        }
        if (state.privacyShieldVisible) PrivacyShield()
    }
}
