package com.example.localreader.parser

import com.example.localreader.domain.BookParser
import com.example.localreader.domain.ParsedBook
import com.example.localreader.domain.ParsedChapter
import com.example.localreader.util.EncodingDetector
import com.example.localreader.util.TxtNormalizer
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TXT 解析：检测编码 -> 归一化为 UTF-8 的 content.txt -> 章节识别。
 * 全程流式，避免超大文件 OOM。
 */
class TxtParser : BookParser {

    override suspend fun parse(
        file: File,
        mimeType: String,
        resourceRoot: File,
        bookRelativeDir: String,
        originalFileName: String
    ): ParsedBook = withContext(Dispatchers.IO) {
        val detected = EncodingDetector.detect(file, mimeType)
        val charset = Charset.forName(detected.charsetName)
        val contentFile = File(resourceRoot, "content.txt")
        val analysis = TxtNormalizer.normalize(file, charset, detected.bomBytesToSkip, contentFile)

        ParsedBook(
            title = originalFileName.substringBeforeLast('.'),
            encoding = detected.charsetName,
            chapters = analysis.chapters.mapIndexed { i, span ->
                ParsedChapter(
                    title = span.title,
                    chapterIndex = i,
                    startOffset = span.startOffset,
                    endOffset = span.endOffset
                )
            },
            totalChars = analysis.totalChars,
            totalPages = 0
        )
    }
}
