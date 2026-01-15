package aman.rgdroid

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

@Composable
fun SoraCodeEditor(
    content: String,
    fileName: String, // <--- Add fileName to check extension
    lineNumber: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                isEditable = false
                setTextSize(14f)
                setTypefaceText(Typeface.MONOSPACE) 
                setLineNumberEnabled(true)
                
                // Default to Dark Theme
                colorScheme.apply {
                    setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E1E.toInt())
                    setColor(EditorColorScheme.TEXT_NORMAL, 0xFFD4D4D4.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E1E.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER, 0xFF858585.toInt())
                    setColor(EditorColorScheme.SELECTION_INSERT, 0xFF264F78.toInt())
                    setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF264F78.toInt())
                }
            }
        },
        update = { editor ->
            // 1. Smart Language Selection
            // Only use Java highlighting for actual source code files
            // This prevents "highlighting garbage" in .apk/.dex files
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val isJavaLike = ext in listOf("java", "kt", "xml", "gradle", "json")
            
            if (editor.editorLanguage !is JavaLanguage && isJavaLike) {
                 editor.setEditorLanguage(JavaLanguage())
            } else if (!isJavaLike) {
                 // Reset to plain text for binaries/logs to save performance
                 editor.setEditorLanguage(null) 
            }

            // 2. Set Text
            if (editor.text.toString() != content) {
                editor.setText(content)
            }

            // 3. Scroll Logic
            if (lineNumber > 0) {
                editor.post {
                    try {
                        val targetIndex = lineNumber - 1
                        if (targetIndex < editor.lineCount) {
                            editor.ensurePositionVisible(targetIndex, 0)
                            
                            val lineLength = editor.text.getColumnCount(targetIndex)
                            val start = editor.text.getCharIndex(targetIndex, 0)
                            val end = editor.text.getCharIndex(targetIndex, lineLength)
                            
                            editor.setSelection(start, end)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    )
}
