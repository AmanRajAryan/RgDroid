package aman.rgdroid.data

// Navigation State
sealed class ScreenState {
    object Search : ScreenState()
    data class Editor(
        val filePath: String, 
        val line: Int, 
        val query: String? = null
    ) : ScreenState()
}

// Search Configuration State (Added 'glob')
data class SearchState(
    val query: String,
    val path: String,
    val glob: String,
    val ignoreCase: Boolean,
    val showHidden: Boolean,
    val isRegex: Boolean
)

// UI Result Model
data class SearchResultItem(
    val filePath: String,
    val lineNumber: Int,
    val content: String,
    val highlights: List<Pair<Int, Int>>
)

// JSON Parsing Models
data class RgResponse(val type: String, val data: RgData?)
data class RgData(
    val path: RgPath?,
    val line_number: Int?,
    val lines: RgText?,
    val submatches: List<RgSubmatch>?
)
data class RgPath(val text: String)
data class RgText(val text: String)
data class RgSubmatch(val match: RgText, val start: Int, val end: Int)
