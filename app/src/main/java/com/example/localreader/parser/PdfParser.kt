package com.example.localreader.parser

import android.graphics.Bitmap
import com.example.localreader.domain.BookParser
import com.example.localreader.domain.ParsedBook
import com.example.localreader.domain.ParsedChapter
import com.example.localreader.util.PdfPageRenderer
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF 解析：用 Android 自带 PdfRenderer 统计页数，并渲染首页作为封面。
 */
class PdfParser : BookParser {

    override suspend fun parse(
        file: File,
        mimeType: String,
        resourceRoot: File,
        bookRelativeDir: String,
        originalFileName: String
    ): ParsedBook = withContext(Dispatchers.IO) {
        var pageCount = 0
        var coverRelative: String? = null
        try {
            PdfPageRenderer(file).use { renderer ->
                pageCount = renderer.pageCount
                if (pageCount > 0) {
                    renderer.renderPage(0, COVER_WIDTH)?.let { bmp ->
                        try {
                            val cover = File(resourceRoot, "cover.jpg")
                            cover.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                            coverRelative = "$bookRelativeDir/cover.jpg"
                        } finally {
                            if (!bmp.isRecycled) bmp.recycle()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("PDF 解析失败: ${e.message}")
        }

        ParsedBook(
            title = originalFileName.substringBeforeLast('.'),
            chapters = (0 until pageCount).map { i ->
                ParsedChapter(title = "第${i + 1}页", chapterIndex = i, pageIndex = i)
            },
            totalPages = pageCount,
            coverRelativePath = coverRelative
        )
    }

    companion object {
        private const val COVER_WIDTH = 480
    }
}
