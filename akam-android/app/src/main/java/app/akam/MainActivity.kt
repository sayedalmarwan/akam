package app.akam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.akam.ui.theme.AkamTheme
import uniffi.akam.AkamDb

class MainActivity : ComponentActivity() {
    private val db by lazy { AkamDb(filesDir.resolve("akam.db").absolutePath) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AkamTheme {
                AkamApp(db)
            }
        }
    }
}

@Composable
fun AkamApp(db: AkamDb) {
    val nav = rememberNavController()
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
                        val note = db.createNote()
                        nav.navigate("editor/${note.id}")
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
