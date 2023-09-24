package com.bitcoin_ring.virtualboltcard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LauncherActivity : AppCompatActivity() {

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if ((errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) || (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL)){
                    startActivity(Intent(this@LauncherActivity, MainActivity::class.java))
                    finish()
                }
                // Handle the authentication error (e.g., show a message or close the app)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // Authentication was successful, start MainActivity
                startActivity(Intent(this@LauncherActivity, MainActivity::class.java))
                finish()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                finish()
                // Handle the failure (e.g., show a message)
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Log in using your biometric credential")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        // Start biometric authentication
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onResume() {
        super.onResume()
        if (::biometricPrompt.isInitialized) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

}