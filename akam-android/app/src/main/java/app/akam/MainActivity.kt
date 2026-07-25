package app.akam

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.akam.ui.theme.AkamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.akam.AkamDb

class MainActivity : FragmentActivity() {
    private var lastBackgroundTime = 0L
    private var isUnlocked by mutableStateOf(false)
    private var isPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // catch any future main-thread disk/network violation early, debug only
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
        }
        enableEdgeToEdge()
        setContent {
            AkamTheme {
                val prefs = getSharedPreferences("akam_prefs", Context.MODE_PRIVATE)
                val appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }

                if (isUnlocked || !appLockEnabled) {
                    // resolve the path and open (schema + migrate + trash purge) off main;
                    // getFilesDir does disk I/O on first call
                    var db by remember { mutableStateOf<AkamDb?>(null) }
                    val sharedText = remember {
                        if (intent?.action == android.content.Intent.ACTION_SEND && intent.type == "text/plain") {
                            intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                        } else null
                    }
                    LaunchedEffect(Unit) {
                        db = withContext(Dispatchers.IO) {
                            AkamDb(filesDir.resolve("akam.db").absolutePath)
                        }
                    }
                    db?.let { AkamApp(it, sharedText) }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Akam", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(onClick = { showBiometricPrompt() }) {
                                Text("Tap to unlock")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lastBackgroundTime = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("akam_prefs", Context.MODE_PRIVATE)
        val appLockEnabled = prefs.getBoolean("app_lock_enabled", false)
        
        if (appLockEnabled) {
            val elapsed = System.currentTimeMillis() - lastBackgroundTime
            if (lastBackgroundTime == 0L || elapsed > 30_000) {
                isUnlocked = false
                showBiometricPrompt()
            }
        } else {
            isUnlocked = true
        }
    }

    private fun showBiometricPrompt() {
        if (isPromptShowing) return
        isPromptShowing = true
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isPromptShowing = false
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlocked = true
                    isPromptShowing = false
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    isPromptShowing = false
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Akam")
            .setSubtitle("Verify your identity")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun AkamApp(db: AkamDb, initialSharedText: String? = null) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            val note = withContext(Dispatchers.IO) { db.createNote() }
            withContext(Dispatchers.IO) {
                db.setContent(note.id, uniffi.akam.RichText(initialSharedText, emptyList()))
            }
            nav.navigate("editor/${note.id}")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.ime)
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = "notes",
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable("notes") {
                NotesScreen(
                    db,
                    onOpenNote = { nav.navigate("editor/$it") },
                    onNewNote = {
                        scope.launch {
                            val note = withContext(Dispatchers.IO) { db.createNote() }
                            nav.navigate("editor/${note.id}")
                        }
                    },
                    onSettings = { nav.navigate("settings") }
                )
            }
            composable(
                "editor/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) { entry ->
                EditorScreen(
                    db,
                    requireNotNull(entry.arguments).getLong("noteId"),
                    onBack = { nav.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
