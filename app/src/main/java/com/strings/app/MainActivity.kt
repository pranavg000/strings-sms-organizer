package com.strings.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.navigation.compose.rememberNavController
import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.data.prefs.ThemeMode
import com.strings.app.ui.navigation.StringsNavGraph
import com.strings.app.ui.theme.StringsTheme
import com.strings.app.util.BiometricAuth
import com.strings.app.work.SmsWorkScheduler
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val settingsDataStore: SettingsDataStore by inject()
    private var pendingMessageId by mutableStateOf<Long?>(null)
    private var isUnlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingMessageId = messageIdFromIntent(intent)
        setContent {
            val themeMode: ThemeMode by settingsDataStore.themeMode
                .collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme: Boolean = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val appLockEnabled: Boolean? by settingsDataStore.appLockEnabled
                .collectAsState(initial = null)
            StringsTheme(darkTheme = darkTheme) {
                // Keep the window background in sync with the composed theme
                // (including the in-app dark override) so nothing light ever
                // shows through behind transitions or the IME.
                val windowBackground: Color = MaterialTheme.colorScheme.surface
                SideEffect {
                    window.setBackgroundDrawable(windowBackground.toArgb().toDrawable())
                }
                when {
                    appLockEnabled == null -> {
                        Surface(modifier = Modifier.fillMaxSize()) {}
                    }
                    appLockEnabled == false || isUnlocked -> {
                        SmsPermissionGate {
                            val navController = rememberNavController()
                            StringsNavGraph(
                                navController = navController,
                                deepLinkMessageId = pendingMessageId,
                                onDeepLinkConsumed = { pendingMessageId = null }
                            )
                        }
                    }
                    else -> {
                        LockScreen(onUnlock = { showUnlockPrompt() })
                        LaunchedEffect(Unit) { showUnlockPrompt() }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        isUnlocked = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMessageId = messageIdFromIntent(intent)
    }

    private fun showUnlockPrompt() {
        BiometricAuth.authenticate(this, "Unlock Strings") {
            isUnlocked = true
        }
    }

    private fun messageIdFromIntent(intent: Intent?): Long? {
        if (intent == null || !intent.hasExtra(EXTRA_MESSAGE_ID)) return null
        val messageId: Long = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        return if (messageId >= 0L) messageId else null
    }

    companion object {
        const val EXTRA_MESSAGE_ID: String = "extra_message_id"
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Strings is locked",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
            Button(onClick = onUnlock) {
                Text("Unlock")
            }
        }
    }
}

private val OPTIONAL_PERMISSIONS: Array<String> = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.POST_NOTIFICATIONS
)

private fun hasReadSmsPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun missingOptionalPermissions(context: android.content.Context): Array<String> {
    return OPTIONAL_PERMISSIONS.filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()
}

@Composable
private fun SmsPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasReadSmsPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result[Manifest.permission.READ_SMS] == true
    }
    val optionalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    if (granted) {
        LaunchedEffect(Unit) {
            SmsWorkScheduler.schedule(context)
            val missing: Array<String> = missingOptionalPermissions(context)
            if (missing.isNotEmpty()) optionalLauncher.launch(missing)
        }
        content()
    } else {
        PermissionRationale(
            onRequest = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            }
        )
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Strings needs SMS access",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Strings reads your messages so it can organize them into tabs. It never sends or deletes your messages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
            Button(onClick = onRequest) {
                Text("Grant access")
            }
        }
    }
}
