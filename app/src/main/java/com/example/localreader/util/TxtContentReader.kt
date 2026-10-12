package com.example.localreader.util

import com.example.localreader.data.model.BookEntity
import java.io.File
import java.io.Reader

/**
 * 读取 TXT 归一化后的 content.txt 的指定字符区间，避免一次性加载整本书。
 */
object TxtContentReader {

    /** 归一化后的正文文件路径（与源文件同目录的 content.txt） */
    fun contentFileFor(book: BookEntity): File = File(File(book.filePath).parentFile, "content.txt")

    fun readRange(contentFile: File, startOffset: Long, endOffset: Long): String {
        val len = (endOffset - startOffset).coerceAtLeast(0)
        if (len <= 0 || !contentFile.exists()) return ""
        return contentFile.reader(Charsets.UTF_8).buffered(64 * 1024).use { reader ->
            skipChars(reader, startOffset)
            val size = len.toInt()
            val buf = CharArray(size)
            var off = 0
            while (off < size) {
                val n = reader.read(buf, off, size - off)
                if (n < 0) break
                off += n
            }
            String(buf, 0, off)
        }
    }

    private fun skipChars(reader: Reader, count: Long) {
        var remaining = count
        val buf = CharArray(8192)
        while (remaining > 0) {
            val n = reader.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
            if (n < 0) break
            remaining -= n
        }
    }
}
