package aman.rgdroid.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop 
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import aman.rgdroid.data.*
import aman.rgdroid.ui.components.ResultCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RgSearchScreen(
    context: android.content.Context,
    
    // --- INPUTS (From MainActivity) ---
    initialQuery: String,
    initialPath: String,
    initialResults: List<SearchResultItem>,
    initialGlob: String,
    initialIsCase: Boolean,
    initialIsRegex: Boolean,
    initialShowHidden: Boolean,
    initialLastRunState: SearchState?,
    
    listState: LazyListState,
    
    // --- OUTPUTS (To MainActivity) ---
    // We update ALL state variables at once to keep the parent synced
    onStateUpdate: (String, String, List<SearchResultItem>, String, Boolean, Boolean, Boolean, SearchState?) -> Unit,
    onResultClick: (String, Int) -> Unit
) {
    // --- UI State (Initialized from Inputs) ---
    var query by remember { mutableStateOf(initialQuery) }
    var path by remember { mutableStateOf(initialPath) }
    var results by remember { mutableStateOf(initialResults) }
    
    // Filters
    var isCaseInsensitive by remember { mutableStateOf(initialIsCase) }
    var isRegex by remember { mutableStateOf(initialIsRegex) }
    var showHidden by remember { mutableStateOf(initialShowHidden) }
    var glob by remember { mutableStateOf(initialGlob) }
    
    var showAdvanced by remember { mutableStateOf(false) }

    // Logic State
    var isRunning by remember { mutableStateOf(false) }
    var currentProcess by remember { mutableStateOf<Process?>(null) }
    
    // State Tracking
    var lastRunState by remember { mutableStateOf(initialLastRunState) }
    
    // Calculate current state to compare against lastRunState for FAB visibility
    val currentState = SearchState(query, path, glob, isCaseInsensitive, showHidden, isRegex)

    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    
    var showWarning by remember { mutableStateOf(false) }
    var warningMessage by remember { mutableStateOf("") }
    var pendingNavigation by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { path = UriHelper.getPathFromUri(it) }
    }

    // SYNC UP: Whenever any local state changes, notify MainActivity
    LaunchedEffect(query, path, results, glob, isCaseInsensitive, isRegex, showHidden, lastRunState) { 
        onStateUpdate(query, path, results, glob, isCaseInsensitive, isRegex, showHidden, lastRunState) 
    }

    // --- HELPER: Perform Search ---
    fun performSearch(overrideGlob: String? = null) {
        val effectiveGlob = overrideGlob ?: glob
        
        if (query.isBlank()) return
        
        keyboardController?.hide()
        isRunning = true
        
        lastRunState = if (overrideGlob != null) currentState.copy(glob = overrideGlob) else currentState
        results = emptyList() 
        
        scope.launch(Dispatchers.IO) {
            try {
                val args = mutableListOf<String>()
                args.add("--json")
                if (isCaseInsensitive) args.add("-i")
                if (showHidden) args.add("-uuu")
                if (!isRegex) args.add("-F")
                
                if (effectiveGlob.isNotBlank()) {
                    effectiveGlob.split(",").forEach { rawPattern ->
                        val pattern = rawPattern.trim()
                        if (pattern.isNotEmpty()) {
                            args.add("-g")
                            args.add(pattern)
                        }
                    }
                }
                
                val binary = RgManager.getExecutable(context)
                val fullCommand = listOf(binary.absolutePath) + args + listOf(query, path)
                
                val process = ProcessBuilder(fullCommand).redirectErrorStream(true).start()
                withContext(Dispatchers.Main) { currentProcess = process }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val gson = Gson()
                val buffer = mutableListOf<SearchResultItem>()
                
                var line: String? = reader.readLine()
                while (line != null) {
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
                            buffer.add(item)
                            if (buffer.size >= 10) {
                                val newItems = buffer.toList()
                                withContext(Dispatchers.Main) { results = results + newItems }
                                buffer.clear()
                            }
                        }
                    } catch (e: Exception) { }
                    line = reader.readLine()
                }
                
                if (buffer.isNotEmpty()) {
                    val finalItems = buffer.toList()
                    withContext(Dispatchers.Main) { results = results + finalItems }
                }

                withContext(Dispatchers.Main) { isRunning = false }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isRunning = false }
            }
        }
    }

    // --- HELPER: Stop Search ---
    fun stopSearch() {
        try {
            currentProcess?.destroy()
            isRunning = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 8.dp)
                    .statusBarsPadding()
            ) {
                // 1. Search Bar
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search code...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showAdvanced = !showAdvanced }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filters", 
                                 tint = if(showAdvanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    singleLine = true
                )

                // 2. Folder Picker
                Card(
                    onClick = { dirLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // 3. Advanced Filters
                AnimatedVisibility(visible = showAdvanced) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                         OutlinedTextField(
                            value = glob,
                            onValueChange = { glob = it },
                            placeholder = { Text("File types (e.g. *.kt, !*.json)") },
                            label = { Text("Glob Filter") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp)
                        )
                    }
                }

                // 4. Chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 8.dp)
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
            // FIX: Uses 'currentState' (built from restored filters) vs 'lastRunState' (restored)
            // If they are identical (search already run), FAB is HIDDEN.
            val isVisible = isRunning || (query.isNotBlank() && currentState != lastRunState)
            
            AnimatedVisibility(visible = isVisible) {
                ExtendedFloatingActionButton(
                    onClick = { 
                        if (isRunning) stopSearch() 
                        else performSearch()
                    },
                    icon = { 
                        if (isRunning) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp), 
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                            }
                        } else {
                            Icon(Icons.Default.Search, null) 
                        }
                    },
                    text = { Text(if (isRunning) "Stop" else "Search") },
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            
            if (results.isNotEmpty() || isRunning) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Found ${results.size} matches",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val activeGlob = lastRunState?.glob ?: ""
                    if (activeGlob.isNotBlank()) {
                         Row(
                             verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier.background(
                                 MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), 
                                 shape = MaterialTheme.shapes.small
                             ).padding(start = 8.dp)
                         ) {
                             Text(
                                 text = "Filter: $activeGlob", 
                                 style = MaterialTheme.typography.labelMedium,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                             IconButton(
                                 onClick = { 
                                     glob = ""
                                     performSearch(overrideGlob = "") 
                                 },
                                 modifier = Modifier.size(32.dp)
                             ) {
                                 Icon(
                                     Icons.Default.Close, 
                                     contentDescription = "Remove Filter",
                                     tint = MaterialTheme.colorScheme.primary,
                                     modifier = Modifier.size(16.dp)
                                 )
                             }
                         }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = { keyboardController?.hide() })
                    }
                ) {
                    items(results) { item ->
                        ResultCard(
                            item = item,
                            rootPath = path,
                            onClick = { checkAndNavigate(item.filePath, item.lineNumber) }
                        )
                    }
                    if (isRunning) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (lastRunState != null && !isRunning) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No matches found.", color = Color.Gray)
                            if ((lastRunState?.glob ?: "").isNotBlank()) {
                                TextButton(onClick = { 
                                    glob = ""
                                    performSearch(overrideGlob = "") 
                                }) {
                                    Text("Clear filter")
                                }
                            }
                        }
                    } else if (!isRunning) {
                        Text("Ready to search", color = Color.Gray)
                    }
                }
            }
        }
        
        // Warnings
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
                dismissButton = { TextButton(onClick = { showWarning = false }) { Text("Cancel") } }
            )
        }
    }
}