package com.example.localreader.data.model

/**
 * 支持的书籍格式。ext 用于扩展名二次过滤，label 用于界面展示。
 */
enum class BookFormat(val ext: String, val label: String) {
    TXT("txt", "TXT"),
    EPUB("epub", "EPUB"),
    MOBI("mobi", "MOBI"),
    AZW3("azw3", "AZW3"),
    PDF("pdf", "PDF");

    companion object {
        /** 根据文件扩展名推断格式，忽略大小写 */
        fun fromExtension(ext: String?): BookFormat? {
            if (ext.isNullOrBlank()) return null
            val e = ext.trimStart('.').lowercase()
            return entries.firstOrNull { it.ext == e }
        }
    }
}

/**
 * 书签/进度的位置类型：TEXT 表示章节内字符偏移，PDF_PAGE 表示 PDF 页码。
 */
enum class LocationType {
    TEXT,
    PDF_PAGE
}
