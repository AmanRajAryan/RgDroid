package aman.rgdroid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewScreen(
    filePath: String,
    targetLineNumber: Int,
    onBack: () -> Unit
) {
    var fileContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val file = File(filePath)

    LaunchedEffect(filePath) {
        scope.launch(Dispatchers.IO) {
            try {
                // We still keep the HARD LIMIT (10MB) to prevent crashing the app
                val text = if (file.length() > 10 * 1024 * 1024) {
                     file.readText().take(100000) + "\n\n... [CRITICAL SIZE LIMIT: File Truncated]"
                } else {
                    file.readText()
                }
                
                withContext(Dispatchers.Main) {
                    fileContent = text
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    fileContent = "Error reading file: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1E1E1E))
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else if (fileContent != null) {
                SoraCodeEditor(
                    content = fileContent!!,
                    fileName = file.name,
                    lineNumber = targetLineNumber,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
