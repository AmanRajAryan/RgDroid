package aman.rgdroid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aman.rgdroid.data.SearchResultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultCard(
    item: SearchResultItem, 
    rootPath: String, 
    onClick: () -> Unit
) {
    // 1. Calculate Relative Path
    val displayPath = item.filePath
        .removePrefix(rootPath)
        .removePrefix("/")

    // 2. Prepare Content (Trim leading whitespace)
    val leadingSpaces = item.content.takeWhile { it.isWhitespace() }.length
    val rawContent = item.content.trimStart()
    
    // 3. Check for Empty/Binary Match
    val isBlank = rawContent.isBlank()
    val displayContent = if (isBlank) "[Binary match or empty line]" else rawContent
    
    // 4. Shift highlights (Only if content is NOT blank)
    val adjustedHighlights = if (isBlank) emptyList() else item.highlights.map { (start, end) ->
        val newStart = (start - leadingSpaces).coerceAtLeast(0)
        val newEnd = (end - leadingSpaces).coerceAtLeast(0)
        Pair(newStart, newEnd)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = displayPath, 
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${item.lineNumber}: ",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                
                if (isBlank) {
                    // FALLBACK: Show placeholder for binary/empty matches
                    Text(
                        text = displayContent,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    // NORMAL: Show highlighted code
                    val annotatedString = buildAnnotatedString {
                        append(displayContent)
                        adjustedHighlights.forEach { (start, end) ->
                            if (start >= 0 && end <= displayContent.length && start < end) {
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
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3
                    )
                }
            }
        }
    }
}