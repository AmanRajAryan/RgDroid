package aman.rgdroid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import aman.rgdroid.ui.theme.ComposeEmptyActivityTheme

// --- Navigation State ---
sealed class ScreenState {
    object Search : ScreenState()
    data class Editor(val filePath: String, val line: Int) : ScreenState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Critical for keyboard handling
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
    
    // State Preservation
    var savedQuery by remember { mutableStateOf("") }
    var savedPath by remember { mutableStateOf("/sdcard/Download") }
    var savedResults by remember { mutableStateOf(listOf<SearchResultItem>()) }

    BackHandler(enabled = currentScreen is ScreenState.Editor) {
        currentScreen = ScreenState.Search
    }

    when (val screen = currentScreen) {
        is ScreenState.Search -> {
            RgSearchScreen(
                context = LocalContext.current,
                initialQuery = savedQuery,
                initialPath = savedPath,
                initialResults = savedResults,
                onStateUpdate = { q, p, r ->
                    savedQuery = q
                    savedPath = p
                    savedResults = r
                },
                onResultClick = { path, line ->
                    currentScreen = ScreenState.Editor(path, line)
                }
            )
        }
        is ScreenState.Editor -> {
            FileViewScreen(
                filePath = screen.filePath,
                targetLineNumber = screen.line,
                onBack = { currentScreen = ScreenState.Search }
            )
        }
    }
}

// 1. Define State Object to track changes (Text AND Filters)
data class SearchState(
    val query: String,
    val path: String,
    val ignoreCase: Boolean,
    val showHidden: Boolean,
    val isRegex: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RgSearchScreen(
    context: android.content.Context,
    initialQuery: String,
    initialPath: String,
    initialResults: List<SearchResultItem>,
    onStateUpdate: (String, String, List<SearchResultItem>) -> Unit,
    onResultClick: (String, Int) -> Unit
) {
    // --- UI State ---
    var query by remember { mutableStateOf(initialQuery) }
    var path by remember { mutableStateOf(initialPath) }
    var results by remember { mutableStateOf(initialResults) }
    
    // Filters
    var isCaseInsensitive by remember { mutableStateOf(false) }
    var isRegex by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(true) }

    // Logic State
    var isRunning by remember { mutableStateOf(false) }
    
    // 2. Track the FULL state of the last run
    val currentState = SearchState(query, path, isCaseInsensitive, showHidden, isRegex)
    var lastRunState by remember { mutableStateOf<SearchState?>(null) }

    // Dialogs
    var showWarning by remember { mutableStateOf(false) }
    var warningMessage by remember { mutableStateOf("") }
    var pendingNavigation by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val scope = rememberCoroutineScope()
    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { path = UriHelper.getPathFromUri(it) }
    }

    LaunchedEffect(query, path, results) { onStateUpdate(query, path, results) }

    fun checkAndNavigate(filePath: String, lineNumber: Int) {
        val file = File(filePath)
        if (!file.exists()) return

        val size = file.length()
        val ext = file.extension.lowercase()
        val formattedSize = Formatter.formatFileSize(context, size)
        
        val isBinary = ext in listOf("apk", "dex", "so", "jar", "zip", "class", "png", "jpg")
        val isLarge = size > 1 * 1024 * 1024 

        if (isBinary || isLarge) {
            val typeWarning = if (isBinary) "This is a BINARY file (.$ext)." else "This is a LARGE file."
            warningMessage = "$typeWarning\nSize: $formattedSize\n\nOpening this might cause lag. Proceed?"
            pendingNavigation = filePath to lineNumber
            showWarning = true
        } else {
            onResultClick(filePath, lineNumber)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(), // Keyboard handling
            
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 8.dp)
                    .statusBarsPadding()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search code...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    singleLine = true
                )

                Card(
                    onClick = { dirLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Searching in:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = if (path == "/sdcard") "Internal Storage (Root)" else path.substringAfter("/0/"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 8.dp)
                ) {
                    FilterChip(
                        selected = !isCaseInsensitive,
                        onClick = { isCaseInsensitive = !isCaseInsensitive },
                        label = { Text("Match Case") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = showHidden,
                        onClick = { showHidden = !showHidden },
                        label = { Text("Hidden/Binary") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = isRegex,
                        onClick = { isRegex = !isRegex },
                        label = { Text("Regex") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            // 3. Smart Visibility: Show if running OR if CURRENT configuration != LAST run configuration
            val isVisible = isRunning || (query.isNotBlank() && currentState != lastRunState)
            
            AnimatedVisibility(visible = isVisible) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isRunning) return@ExtendedFloatingActionButton
                        if (query.isBlank()) return@ExtendedFloatingActionButton
                        
                        isRunning = true
                        lastRunState = currentState // Save the snapshot of what we just ran
                        results = emptyList() 
                        
                        scope.launch(Dispatchers.IO) {
                            try {
                                val args = mutableListOf<String>()
                                args.add("--json")
                                if (isCaseInsensitive) args.add("-i")
                                if (showHidden) args.add("-uuu")
                                if (!isRegex) args.add("-F")
                                
                                val binary = RgManager.getExecutable(context)
                                val fullCommand = listOf(binary.absolutePath) + args + listOf(query, path)
                                
                                val process = ProcessBuilder(fullCommand)
                                    .redirectErrorStream(true)
                                    .start()

                                val reader = BufferedReader(InputStreamReader(process.inputStream))
                                val gson = Gson()
                                val tempResults = mutableListOf<SearchResultItem>()
                                
                                reader.forEachLine { line ->
                                    try {
                                        val response = gson.fromJson(line, RgResponse::class.java)
                                        if (response.type == "match" && response.data != null) {
                                            val d = response.data
                                            val item = SearchResultItem(
                                                filePath = d.path?.text ?: "??",
                                                lineNumber = d.line_number ?: 0,
                                                content = d.lines?.text?.trimEnd() ?: "",
                                                highlights = d.submatches?.map { Pair(it.start, it.end) } ?: emptyList()
                                            )
                                            tempResults.add(item)
                                        }
                                    } catch (e: Exception) { }
                                }

                                withContext(Dispatchers.Main) {
                                    results = tempResults
                                    isRunning = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { isRunning = false }
                            }
                        }
                    },
                    icon = { 
                        if (isRunning) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White) 
                        else Icon(Icons.Default.Search, null) 
                    },
                    text = { Text(if (isRunning) "Searching..." else "Search") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            
            if (isRunning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (results.isNotEmpty()) {
                Text(
                    text = "Found ${results.size} matches",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn {
                    items(results) { item ->
                        ResultCard(item) { checkAndNavigate(item.filePath, item.lineNumber) }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (lastRunState != null) {
                        Text("No matches found.", color = Color.Gray)
                    } else {
                        Text("Ready to search", color = Color.Gray)
                    }
                }
            }
        }

        if (showWarning) {
            AlertDialog(
                onDismissRequest = { showWarning = false },
                title = { Text("Performance Warning") },
                text = { Text(warningMessage) },
                confirmButton = {
                    Button(
                        onClick = {
                            showWarning = false
                            pendingNavigation?.let { (p, l) -> onResultClick(p, l) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) { Text("Open Anyway") }
                },
                dismissButton = {
                    TextButton(onClick = { showWarning = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultCard(item: SearchResultItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.filePath,
                fontSize = 12.sp,
                color = Color(0xFF0D47A1),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${item.lineNumber}: ",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
                val annotatedString = buildAnnotatedString {
                    append(item.content)
                    item.highlights.forEach { (start, end) ->
                        if (start >= 0 && end <= item.content.length && start < end) {
                            addStyle(
                                style = SpanStyle(
                                    background = Color(0xFFFFEB3B),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                ),
                                start = start,
                                end = end
                            )
                        }
                    }
                }
                Text(
                    text = annotatedString,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black
                )
            }
        }
    }
}
