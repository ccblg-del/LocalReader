package com.example.localreader.util

/**
 * 章节识别：正则匹配常见章节标题；识别不到时按固定字数切分。
 */
object ChapterParser {

    /** 识别不到章节时，按此固定字数切分 */
    const val FALLBACK_SPLIT_CHARS = 8000L
    private const val MIN_TITLE_COUNT = 2

    data class ChapterSpan(val title: String, val startOffset: Long, val endOffset: Long)

    private val titlePatterns = listOf(
        // 第1章 / 第 1 章 / 第一章 / 第一卷 / 第一回 ...
        Regex("^第\\s*[0-9零一二三四五六七八九十百千万两]+\\s*[章节卷回部篇集]\\s*[:：、.．\\-—\\s]?.*$"),
        // 序章 / 前言 / 楔子 / 后记 / 尾声 / 番外 ...
        Regex("^(序章|序言|前言|楔子|引子|后记|尾声|番外|外传|终章)"),
        // Chapter 1 ...
        Regex("^(Chapter|CHAPTER|chapter)\\s+\\d+"),
        // 第 1 节
        Regex("^第\\s*[0-9]+\\s*节")
    )

    fun isChapterTitle(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.length > 60) return null
        return if (titlePatterns.any { it.containsMatchIn(t) }) t else null
    }

    /**
     * 由已检测到的标题（标题 -> 起始偏移）构建章节区间。
     * 若标题数不足，按固定字数切分。
     */
    fun buildSpans(titles: List<Pair<String, Long>>, totalChars: Long): List<ChapterSpan> {
        if (titles.size < MIN_TITLE_COUNT) {
            if (totalChars <= 0) return listOf(ChapterSpan("全文", 0, 0))
            val spans = mutableListOf<ChapterSpan>()
            var start = 0L
            var n = 1
            while (start < totalChars) {
                val end = minOf(start + FALLBACK_SPLIT_CHARS, totalChars)
                spans.add(ChapterSpan("第${n}部分", start, end))
                start = end
                n++
            }
            return spans
        }
        val spans = mutableListOf<ChapterSpan>()
        val first = titles.first()
        if (first.second > 0) spans.add(ChapterSpan("开篇", 0, first.second))
        titles.forEachIndexed { i, (title, start) ->
            val end = if (i + 1 < titles.size) titles[i + 1].second else totalChars
            spans.add(ChapterSpan(title, start, end))
        }
        return spans
    }

    /** 针对已加载到内存的文本（EPUB/MOBI 提取后）进行章节切分 */
    fun fromText(text: String): List<ChapterSpan> {
        val titles = mutableListOf<Pair<String, Long>>()
        var idx = 0
        val len = text.length
        while (idx <= len) {
            val nl = text.indexOf('\n', idx)
            val end = if (nl == -1) len else nl
            val title = isChapterTitle(text.substring(idx, end))
            if (title != null) titles.add(title to idx.toLong())
            if (nl == -1) break
            idx = nl + 1
        }
        return buildSpans(titles, len.toLong())
    }
}
