package aman.rgdroid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import aman.rgdroid.data.ScreenState
import aman.rgdroid.data.SearchResultItem
import aman.rgdroid.data.SearchState
import aman.rgdroid.ui.screens.FileViewScreen
import aman.rgdroid.ui.screens.RgSearchScreen
import aman.rgdroid.ui.theme.ComposeEmptyActivityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        setContent {
            ComposeEmptyActivityTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<ScreenState>(ScreenState.Search) }
    
    // --- STATE HOISTING (The Memory Bank) ---
    // These variables survive even if RgSearchScreen is destroyed
    var savedQuery by remember { mutableStateOf("") }
    var savedPath by remember { mutableStateOf("/sdcard/Download") }
    var savedResults by remember { mutableStateOf(listOf<SearchResultItem>()) }
    
    // FILTERS
    var savedGlob by remember { mutableStateOf("") }
    var savedIsCaseInsensitive by remember { mutableStateOf(false) }
    var savedIsRegex by remember { mutableStateOf(false) }
    var savedShowHidden by remember { mutableStateOf(true) }
    
    // LOGIC STATE (Crucial for FAB visibility)
    var savedLastRunState by remember { mutableStateOf<SearchState?>(null) }
    
    // UI STATE
    val searchListState = rememberLazyListState()

    BackHandler(enabled = currentScreen is ScreenState.Editor) {
        currentScreen = ScreenState.Search
    }

    when (val screen = currentScreen) {
        is ScreenState.Search -> {
            RgSearchScreen(
                context = LocalContext.current,
                // Pass Saved Values DOWN
                initialQuery = savedQuery,
                initialPath = savedPath,
                initialResults = savedResults,
                initialGlob = savedGlob,
                initialIsCase = savedIsCaseInsensitive,
                initialIsRegex = savedIsRegex,
                initialShowHidden = savedShowHidden,
                initialLastRunState = savedLastRunState,
                
                listState = searchListState,
                
                // Receive Updates UP
                onStateUpdate = { q, p, r, g, isCase, isReg, showHid, lastRun ->
                    savedQuery = q
                    savedPath = p
                    savedResults = r
                    savedGlob = g
                    savedIsCaseInsensitive = isCase
                    savedIsRegex = isReg
                    savedShowHidden = showHid
                    savedLastRunState = lastRun
                },
                onResultClick = { path, line ->
                    currentScreen = ScreenState.Editor(path, line, savedQuery)
                }
            )
        }
        is ScreenState.Editor -> {
            FileViewScreen(
                filePath = screen.filePath,
                targetLineNumber = screen.line,
                searchQuery = screen.query ?: "", 
                onBack = { currentScreen = ScreenState.Search }
            )
        }
    }
}