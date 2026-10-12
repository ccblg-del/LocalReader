package com.example.localreader.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/**
 * 文本分页器：根据字体、字号与页面像素尺寸，把一段文本切分为若干页。
 * 页内偏移使用“字符偏移”（UTF-16 码元）。
 */
class TextPaginator(
    private val textMeasurer: TextMeasurer,
    private val style: TextStyle,
    private val pageWidthPx: Int,
    private val pageHeightPx: Int,
    private val paragraphSpacingPx: Int = 0
) {

    data class Page(val startOffset: Int, val endOffset: Int)

    fun paginate(text: String): List<Page> {
        if (text.isEmpty()) return listOf(Page(0, 0))
        if (pageWidthPx <= 0 || pageHeightPx <= 0) return listOf(Page(0, text.length))

        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(maxWidth = pageWidthPx)
        )

        val pages = mutableListOf<Page>()
        var pageStart = 0
        var lineStartChar = 0
        var used = 0f
        var i = 0
        while (i < layout.lineCount) {
            val h = layout.getLineBottom(i) - layout.getLineTop(i)
            val endChar = layout.getLineEnd(i, visibleEnd = false)
            // 行末是换行符 => 下一行是新段落，额外加段距
            val isParaEnd = endChar < text.length && endChar > 0 && text[endChar - 1] == '\n'
            val extra = if (isParaEnd) paragraphSpacingPx else 0

            if (used + h + extra > pageHeightPx && lineStartChar > pageStart) {
                pages.add(Page(pageStart, lineStartChar))
                pageStart = lineStartChar
                used = 0f
            }
            used += h + extra
            lineStartChar = endChar
            i++
        }
        if (pageStart < text.length) {
            pages.add(Page(pageStart, text.length))
        } else if (pages.isEmpty()) {
            pages.add(Page(0, 0))
        }
        return pages
    }
}
