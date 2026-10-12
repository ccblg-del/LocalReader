package com.example.localreader.importer

import android.content.Context
import android.net.Uri
import java.io.File
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.BookFormat
import com.example.localreader.data.model.ChapterEntity
import com.example.localreader.data.repository.BookRepository
import com.example.localreader.domain.BookParser
import com.example.localreader.parser.Azw3Parser
import com.example.localreader.parser.EpubParser
import com.example.localreader.parser.MobiParser
import com.example.localreader.parser.PdfParser
import com.example.localreader.parser.TxtParser
import com.example.localreader.util.BookFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ImportResult(
    val fileName: String,
    val success: Boolean,
    val error: String?
)

/**
 * 导入编排：复制文件 -> 按格式分发解析器 -> 统一生成 Book 记录入库。
 * 全程在 IO 线程执行，进度通过 StateFlow 暴露。
 */
class ImportManager(
    private val context: Context,
    private val bookRepository: BookRepository
) {

    private val fileImporter = FileImporter(context)

    private val parsers: Map<BookFormat, BookParser> = mapOf(
        BookFormat.TXT to TxtParser(),
        BookFormat.EPUB to EpubParser(),
        BookFormat.MOBI to MobiParser(),
        BookFormat.AZW3 to Azw3Parser(),
        BookFormat.PDF to PdfParser()
    )

    data class ImportProgress(
        val total: Int,
        val finished: Int,
        val current: String,
        val message: String?
    )

    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    suspend fun import(uris: List<Uri>): List<ImportResult> {
        if (uris.isEmpty()) return emptyList()
        val booksDir = BookFiles.booksDir(context).apply { mkdirs() }
        val results = mutableListOf<ImportResult>()
        _progress.value = ImportProgress(uris.size, 0, "", null)
        uris.forEachIndexed { i, uri ->
            val result = importOne(uri, booksDir)
            results.add(result)
            _progress.value = ImportProgress(uris.size, i + 1, result.fileName, result.error)
        }
        _progress.value = null
        return results
    }

    private suspend fun importOne(uri: Uri, booksDir: File): ImportResult = withContext(Dispatchers.IO) {
        try {
            val copied = fileImporter.copyToPrivate(uri, booksDir)

            // 扩展名二次过滤：SAF 的 MIME 对电子书格式经常不可靠，以扩展名为准
            val format = BookFormat.fromExtension(copied.extension)
                ?: return@withContext ImportResult(copied.originalName, false, "不支持的文件格式: .${copied.extension}")

            val parser = parsers[format]
                ?: return@withContext ImportResult(copied.originalName, false, "无对应解析器")

            val bookDir = copied.file.parentFile
                ?: return@withContext ImportResult(copied.originalName, false, "书籍目录创建失败")
            val relativeDir = bookDir.name

            val parsed = parser.parse(copied.file, copied.mimeType, bookDir, relativeDir, copied.originalName)

            val book = BookEntity(
                title = parsed.title.ifBlank { copied.originalName.substringBeforeLast('.') },
                author = parsed.author,
                format = format,
                filePath = copied.file.absolutePath,
                originalFileName = copied.originalName,
                mimeType = copied.mimeType,
                encoding = parsed.encoding,
                coverPath = parsed.coverRelativePath,
                language = parsed.language,
                publisher = parsed.publisher,
                description = parsed.description,
                totalChars = parsed.totalChars,
                chapterCount = parsed.chapters.size,
                totalPages = parsed.totalPages,
                importedAt = System.currentTimeMillis(),
                lastReadAt = 0,
                progressPercent = 0f,
                parserVersion = 1
            )
            val chapters = parsed.chapters.map { pc ->
                ChapterEntity(
                    bookId = 0, // insertBook 会回填真实 id
                    title = pc.title,
                    startOffset = pc.startOffset,
                    endOffset = pc.endOffset,
                    chapterIndex = pc.chapterIndex,
                    href = pc.href,
                    pageIndex = pc.pageIndex
                )
            }
            bookRepository.insertBook(book, chapters)
            ImportResult(copied.originalName, true, null)
        } catch (e: Exception) {
            ImportResult(fileImporter.queryName(uri), false, e.message ?: "导入失败")
        }
    }

    companion object {
        /** SAF 选择器使用的 MIME 过滤；octet-stream 兜底，扩展名二次过滤 */
        val PICKER_MIME_TYPES = arrayOf(
            "text/plain",
            "application/epub+zip",
            "application/pdf",
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook",
            "application/octet-stream"
        )
    }
}
