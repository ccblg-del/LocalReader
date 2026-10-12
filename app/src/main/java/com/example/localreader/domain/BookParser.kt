package com.example.localreader.domain

import java.io.File

/**
 * 解析后的章节。
 * - TXT：使用 startOffset/endOffset（归一化正文中的字符偏移）
 * - EPUB/MOBI/AZW3：使用 href（相对 files/books 的 HTML 路径）
 * - PDF：使用 pageIndex
 */
data class ParsedChapter(
    val title: String,
    val chapterIndex: Int,
    val startOffset: Long = 0,
    val endOffset: Long = 0,
    val href: String? = null,
    val pageIndex: Int = -1
)

/**
 * 解析后的书籍统一结果。
 * coverRelativePath / chapter.href 均为相对 files/books 的路径。
 */
data class ParsedBook(
    val title: String,
    val author: String = "",
    val language: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val coverRelativePath: String? = null,
    val encoding: String? = null,
    val chapters: List<ParsedChapter> = emptyList(),
    val totalChars: Long = 0,
    val totalPages: Int = 0
)

/**
 * 书籍解析器统一接口。各格式解析器实现此接口。
 *
 * @param file            复制到私有目录后的源文件绝对路径
 * @param resourceRoot    书籍目录绝对路径（files/books/{uuid}）
 * @param bookRelativeDir 书籍目录相对 files/books 的名称（即 {uuid}）
 * @param originalFileName 用户选择的原始文件名（用于书名回退）
 */
interface BookParser {
    suspend fun parse(
        file: File,
        mimeType: String,
        resourceRoot: File,
        bookRelativeDir: String,
        originalFileName: String
    ): ParsedBook
}
