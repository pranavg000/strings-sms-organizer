package com.strings.app.util

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * Thin wrapper around the framework BiometricPrompt used for the app lock:
 * unlocking on launch and confirming before the lock is turned off.
 */
object BiometricAuth {
    private const val AUTHENTICATORS: Int =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun isAvailable(context: Context): Boolean {
        val manager: BiometricManager? = context.getSystemService(BiometricManager::class.java)
        return manager?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the system prompt and invokes [onSuccess] on successful
     * authentication. Fails open (immediate success) when no biometric or
     * device credential is available, so the user can never be locked out.
     */
    fun authenticate(context: Context, title: String, onSuccess: () -> Unit) {
        if (!isAvailable(context)) {
            onSuccess()
            return
        }
        val prompt: BiometricPrompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
    }
}
