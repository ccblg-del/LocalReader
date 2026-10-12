package com.example.localreader.util

import android.content.Context
import java.io.File

/**
 * 书籍私有目录布局：
 *   files/books/{uuid}/source.ext   源文件（BookEntity.filePath 指向这里）
 *   files/books/{uuid}/content.txt  TXT 归一化正文
 *   files/books/{uuid}/content/     EPUB/MOBI 解压资源
 *   files/books/{uuid}/cover.jpg    封面
 */
object BookFiles {

    fun booksDir(context: Context): File = File(context.filesDir, "books")

    fun bookDir(filePath: String): File = File(filePath).parentFile ?: File(".")

    /** 由相对封面路径（相对 files/books）解析为绝对文件 */
    fun resolveCover(context: Context, coverPath: String?): File? {
        if (coverPath.isNullOrBlank()) return null
        val f = File(booksDir(context), coverPath)
        return if (f.exists()) f else null
    }
}
