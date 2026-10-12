package com.example.localreader.util

import java.io.File
import java.nio.charset.Charset

/**
 * 把任意编码的 TXT 源文件流式归一化为 UTF-8 的 content.txt：
 * - 跳过 BOM
 * - \r\n / \r 统一归一化为 \n
 * - 同时做章节识别，返回的偏移量以“归一化后的字符”为准（UTF-16 码元计数）
 * 全程流式处理，避免一次性把超大文件读入内存。
 */
object TxtNormalizer {

    data class Analysis(val chapters: List<ChapterParser.ChapterSpan>, val totalChars: Long)

    fun normalize(source: File, charset: Charset, bomSkip: Int, dest: File): Analysis {
        val titles = mutableListOf<Pair<String, Long>>()
        var total = 0L
        val line = StringBuilder()
        var lineStart = 0L
        var pending = -1

        source.reader(charset).buffered(64 * 1024).use { reader ->
            if (bomSkip > 0) reader.read() // 丢弃 BOM 字符

            dest.writer(Charsets.UTF_8).buffered(64 * 1024).use { writer ->
                while (true) {
                    val c = if (pending != -1) pending.also { pending = -1 } else reader.read()
                    if (c == -1) break

                    if (c == '\r'.code || c == '\n'.code) {
                        val title = ChapterParser.isChapterTitle(line.toString())
                        if (title != null) titles.add(title to lineStart)
                        line.clear()
                        writer.write('\n'.code)
                        total++
                        lineStart = total
                        if (c == '\r'.code) {
                            val n = reader.read()
                            if (n == '\n'.code) {
                                // \r\n 合并为一个 \n
                            } else if (n != -1) {
                                pending = n
                            }
                        }
                    } else {
                        line.append(c.toChar())
                        writer.write(c)
                        total++
                    }
                }
            }
        }

        // 处理最后一行（无换行符结尾）
        if (line.isNotEmpty()) {
            val title = ChapterParser.isChapterTitle(line.toString())
            if (title != null) titles.add(title to lineStart)
        }

        return Analysis(ChapterParser.buildSpans(titles, total), total)
    }
}
