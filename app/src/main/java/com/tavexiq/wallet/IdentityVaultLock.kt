package com.tavexiq.wallet

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity

/**
 * Adds an OS-managed authentication gate in front of the ID metadata vault.
 * Authentication is required each time this composable enters composition.
 */
@Composable
internal fun IdentityVaultLock(context: Context, content: @Composable () -> Unit) {
    val activity = context as? FragmentActivity
    var unlocked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    if (unlocked) {
        content()
        return
    }

    val canAuthenticate = remember(activity) {
        activity != null && BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10243D))) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("ID-kluis vergrendeld", color = Color(0xFFE2EAF3), fontWeight = FontWeight.Bold)
                Text(
                    "Ontgrendel met de biometrische beveiliging of schermvergrendeling van je apparaat.",
                    color = Color(0xFF9CB0C8)
                )
                Button(
                    enabled = activity != null && canAuthenticate,
                    onClick = {
                        val host = activity
                        if (host == null) {
                            message = "Apparaatverificatie is niet beschikbaar in deze omgeving."
                        } else {
                            val executor = androidx.core.content.ContextCompat.getMainExecutor(host)
                            val prompt = BiometricPrompt(
                                host,
                                executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(
                                        result: BiometricPrompt.AuthenticationResult
                                    ) {
                                        unlocked = true
                                        message = null
                                    }

                                    override fun onAuthenticationError(
                                        errorCode: Int,
                                        errString: CharSequence
                                    ) {
                                        message = "Kluis niet ontgrendeld: $errString"
                                    }

                                    override fun onAuthenticationFailed() {
                                        message = "Verificatie niet herkend. Probeer opnieuw."
                                    }
                                }
                            )
                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("TAVEXIQ ID-kluis")
                                .setSubtitle("Bevestig je identiteit om verder te gaan")
                                .setAllowedAuthenticators(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                )
                                .build()
                            prompt.authenticate(promptInfo)
                        }
                    }
                ) {
                    Text("Ontgrendelen")
                }
                if (!canAuthenticate) {
                    Text(
                        "Stel eerst een schermvergrendeling of sterke biometrie in via de Android-instellingen.",
                        color = Color(0xFFFFB4AB)
                    )
                }
                message?.let { Text(it, color = Color(0xFFFFB4AB)) }
            }
        }
    }
}
