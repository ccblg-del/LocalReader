package com.example.localreader.parser

import com.example.localreader.domain.BookParser
import com.example.localreader.domain.ParsedBook
import com.example.localreader.domain.ParsedChapter
import com.example.localreader.util.ChapterParser
import com.example.localreader.util.HtmlResourceExtractor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MOBI/AZW3 解析：解析元数据与文本 -> 转纯文本 -> 章节识别 -> 生成内部 HTML 章节文件。
 * 即“导入后离线转换为内部 HTML”的降级流程，随后统一走 WebView 渲染。
 */
open class MobiParser : BookParser {

    override suspend fun parse(
        file: File,
        mimeType: String,
        resourceRoot: File,
        bookRelativeDir: String,
        originalFileName: String
    ): ParsedBook = withContext(Dispatchers.IO) {
        val info = MobiDocument(file).use { it.info() }
        if (info.htmlText.isBlank()) {
            throw IOException(
                "无法解析该文件内容（可能是不含 MOBI7 兼容文本的 AZW3/KF8）。" +
                    "建议先用 Calibre 转换为 EPUB 或 MOBI 后再导入。"
            )
        }

        val plain = htmlToText(info.htmlText)
        if (plain.isBlank()) {
            throw IOException("解析出的内容为空，无法阅读。")
        }
        val spans = ChapterParser.fromText(plain)

        // 封面
        var coverRel: String? = null
        if (info.coverBytes != null && info.coverExt != null) {
            val cover = File(resourceRoot, "cover.${info.coverExt}")
            cover.writeBytes(info.coverBytes)
            coverRel = "$bookRelativeDir/cover.${info.coverExt}"
        }

        // 章节 HTML
        val chaptersDir = File(resourceRoot, "content/chapters").apply { mkdirs() }
        val parsedChapters = spans.mapIndexed { i, span ->
            val start = span.startOffset.toInt().coerceIn(0, plain.length)
            val end = span.endOffset.toInt().coerceIn(start, plain.length)
            val seg = plain.substring(start, end)
            val html = HtmlResourceExtractor.processChapter(textToChapterHtml(seg, span.title))
            val file = File(chaptersDir, "ch$i.html")
            file.writeText(html)
            ParsedChapter(
                title = span.title,
                chapterIndex = i,
                href = "$bookRelativeDir/content/chapters/ch$i.html"
            )
        }

        ParsedBook(
            title = info.title.ifBlank { originalFileName.substringBeforeLast('.') },
            author = info.author,
            language = info.language,
            publisher = info.publisher,
            description = info.description,
            coverRelativePath = coverRel,
            chapters = parsedChapters,
            totalChars = plain.length.toLong(),
            totalPages = 0
        )
    }

    protected fun htmlToText(html: String): String {
        var t = html
        t = t.replace(Regex("(?s)<(script|style)[^>]*>.*?</\\1>", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("<[^>]+>"), "\n")
        t = t.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        t = t.replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.let { runCatching { it.toChar().toString() }.getOrDefault("") } ?: ""
        }
        t = t.replace(Regex("[ \t]+"), " ")
        t = t.replace(Regex(" *\n *"), "\n")
        t = t.replace(Regex("\n{3,}"), "\n\n")
        return t.trim()
    }

    protected fun textToChapterHtml(text: String, title: String): String {
        val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val body = esc.split("\n").joinToString("") { if (it.isBlank()) "<br/>" else "<p>$it</p>" }
        val safeTitle = title.replace("&", "&amp;").replace("<", "&lt;")
        return "<html><head><meta charset=\"UTF-8\"><title>$safeTitle</title></head><body>$body</body></html>"
    }
}
