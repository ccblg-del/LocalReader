package com.example.localreader.parser

import com.example.localreader.domain.BookParser
import com.example.localreader.domain.ParsedBook
import java.io.File

/**
 * AZW3 解析器：与 MOBI 共用同一套解析核心。
 * AZW3（KF8）通常内嵌 MOBI7 兼容文本，MobiDocument 优先读取该部分完成降级阅读；
 * 若个别文件无 MOBI7 文本，MobiParser 会抛出明确错误。
 */
class Azw3Parser : BookParser {

    private val delegate = MobiParser()

    override suspend fun parse(
        file: File,
        mimeType: String,
        resourceRoot: File,
        bookRelativeDir: String,
        originalFileName: String
    ): ParsedBook = delegate.parse(file, mimeType, resourceRoot, bookRelativeDir, originalFileName)
}
