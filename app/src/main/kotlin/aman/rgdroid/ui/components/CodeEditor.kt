package aman.rgdroid.ui.components

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.EditorSearcher

@Composable
fun SoraCodeEditor(
    content: String,
    fileName: String,
    lineNumber: Int,
    searchQuery: String, 
    modifier: Modifier = Modifier
) {
    key(fileName) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                CodeEditor(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isEditable = false
                    setTextSize(14f)
                    setTypefaceText(Typeface.MONOSPACE) 
                    setLineNumberEnabled(true)
                    
                    colorScheme.apply {
                        setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E1E.toInt())
                        setColor(EditorColorScheme.TEXT_NORMAL, 0xFFD4D4D4.toInt())
                        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E1E.toInt())
                        setColor(EditorColorScheme.LINE_NUMBER, 0xFF858585.toInt())
                        
                        // Selection Color (Blue-ish)
                        setColor(EditorColorScheme.SELECTION_INSERT, 0xFF264F78.toInt())
                        setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF264F78.toInt())
                        
                        // Search Match Color (Yellow)
                        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, 0xFFFFEB3B.toInt())
                        setColor(EditorColorScheme.CURRENT_LINE, 0xFF2A2A2A.toInt()) 
                    }
                }
            },
            update = { editor ->
                // 1. Language Setup
                val ext = fileName.substringAfterLast('.', "").lowercase()
                val isJavaLike = ext in listOf("java", "kt", "xml", "gradle", "json")
                if (editor.editorLanguage !is JavaLanguage && isJavaLike) {
                     editor.setEditorLanguage(JavaLanguage())
                } else if (!isJavaLike) {
                     editor.setEditorLanguage(null) 
                }

                // 2. Set Text
                if (editor.text.toString() != content) {
                    editor.setText(content)
                    
                    // 3. Highlight Search Results (Yellow)
                    if (searchQuery.isNotEmpty()) {
                        try {
                            // SearchOptions: ignoreCase=true, regularExpression=false
                            editor.searcher.search(
                                searchQuery, 
                                io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions(true, false)
                            )
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                // 4. Scroll & Select Logic
                if (lineNumber > 0) {
                    val targetIndex = lineNumber - 1
                    val handler = Handler(Looper.getMainLooper())
                    var attempts = 0
                    
                    fun scrollWithRetry() {
                        try {
                            if (editor.lineCount > targetIndex) {
                                // Scroll context
                                val safeScrollIndex = (targetIndex - 5).coerceAtLeast(0)
                                editor.setSelection(0, 0)
                                editor.ensurePositionVisible(safeScrollIndex, 0)
                                
                                // Precise Selection Logic
                                // Find the EXACT word on the line to select it
                                val lineText = editor.text.getLine(targetIndex).toString()
                                val colIndex = lineText.indexOf(searchQuery, ignoreCase = true)
                                
                                if (colIndex != -1) {
                                    // Select just the word
                                    val start = editor.text.getCharIndex(targetIndex, colIndex)
                                    val end = start + searchQuery.length
                                    editor.setSelection(start, end)
                                } else {
                                    // Fallback: Select whole line if word logic fails
                                    val lineLength = editor.text.getColumnCount(targetIndex)
                                    val start = editor.text.getCharIndex(targetIndex, 0)
                                    val end = editor.text.getCharIndex(targetIndex, lineLength)
                                    editor.setSelection(start, end)
                                }
                            } else {
                                attempts++
                                if (attempts < 10) handler.postDelayed({ scrollWithRetry() }, 50)
                            }
                        } catch (e: Exception) { }
                    }
                    scrollWithRetry()
                }
            }
        )
    }
}
