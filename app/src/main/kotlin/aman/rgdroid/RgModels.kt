package aman.rgdroid

data class RgResponse(
    val type: String,
    val data: RgData?
)

data class RgData(
    val path: RgPath?,
    val line_number: Int?,
    val lines: RgText?,
    val submatches: List<RgSubmatch>?
)

data class RgPath(val text: String)
data class RgText(val text: String)

data class RgSubmatch(
    val match: RgText,
    val start: Int,
    val end: Int
)

// A simple helper class for our UI List
data class SearchResultItem(
    val filePath: String,
    val lineNumber: Int,
    val content: String,
    val highlights: List<Pair<Int, Int>> // Start, End indices
)
