package app.akam

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

class MainActivity : ComponentActivity() {
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
                // resolve the path and open (schema + migrate + trash purge) off main;
                // getFilesDir does disk I/O on first call
                var db by remember { mutableStateOf<AkamDb?>(null) }
                LaunchedEffect(Unit) {
                    db = withContext(Dispatchers.IO) {
                        AkamDb(filesDir.resolve("akam.db").absolutePath)
                    }
                }
                db?.let { AkamApp(it) }
            }
        }
    }
}

@Composable
fun AkamApp(db: AkamDb) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
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
                    }
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
        }
    }
}
