package com.example.localreader.parser

import com.example.localreader.util.CharsetUtils
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * 纯 Kotlin 的 MOBI/AZW3 解析核心（不依赖 JNI）。
 *
 * 支持：
 * - PDB 头、记录偏移表
 * - MOBI 头（PalmDOC 头 + MOBI 头）
 * - EXTH 扩展元数据（书名/作者/出版社/简介/语言/封面）
 * - PalmDOC LZ77 解压（compression == 2）
 * - 文本记录提取
 *
 * 说明（降级方案）：
 * - AZW3（KF8，mobiType == 248）通常内嵌 MOBI7 兼容文本，本类优先读取该部分；
 *   若个别文件无 MOBI7 文本，将抛出明确错误，建议用 Calibre 转 EPUB/MOBI 后再导入。
 * - HUFF/CDIC（compression == 17480）极少见，暂不支持，会抛出明确错误。
 * - 生产级完整支持建议集成 libmobi 的 JNI（见 README 的 CMake/JNI 方案）。
 */
class MobiDocument(file: File) : Closeable {

    data class MobiInfo(
        val title: String,
        val author: String = "",
        val language: String? = null,
        val publisher: String? = null,
        val description: String? = null,
        val htmlText: String = "",
        val coverBytes: ByteArray? = null,
        val coverExt: String? = null
    )

    private val raf = RandomAccessFile(file, "r")

    private var name = ""
    private var numRecords = 0
    private var recordOffsets = intArrayOf()

    private var compression = 0
    private var textLength = 0
    private var textRecordCount = 0
    private var textEncoding = 1252
    private var mobiType = 0
    private var exthFlags = 0
    private var firstImageIndex = -1
    private val exth = LinkedHashMap<Int, ByteArray>()

    init {
        parseHeader()
    }

    private fun parseHeader() {
        val header = ByteArray(78)
        raf.seek(0)
        raf.readFully(header)
        name = String(header, 0, 32, Charsets.ISO_8859_1).trimEnd('\u0000', ' ')
        numRecords = u16(header, 76)
        if (numRecords <= 0) throw IOException("无效的 MOBI 文件：记录数为 0")

        // 记录偏移表
        val table = ByteArray(numRecords * 8)
        raf.seek(78)
        raf.readFully(table)
        recordOffsets = IntArray(numRecords)
        for (i in 0 until numRecords) {
            val b = i * 8
            recordOffsets[i] = u32(table, b)
        }

        // 记录 0 = PalmDOC 头 + MOBI 头
        val record0 = readRecord(0)
        if (record0.size < 16) throw IOException("无效的 MOBI 文件：头过短")
        compression = u16(record0, 0)
        textLength = u32(record0, 4)
        textRecordCount = u16(record0, 8)

        if (record0.size >= 132) {
            mobiType = u32(record0, 24)
            textEncoding = u32(record0, 28)
            firstImageIndex = u32(record0, 108)
            exthFlags = u32(record0, 128)
        }

        // EXTH
        if ((exthFlags and 0x40) != 0 && record0.size >= 32) {
            val headerLength = u32(record0, 20)
            parseExth(record0, 16 + headerLength)
        }
    }

    private fun parseExth(record0: ByteArray, offset: Int) {
        if (offset + 12 > record0.size) return
        if (String(record0, offset, 4, Charsets.ISO_8859_1) != "EXTH") return
        val count = u32(record0, offset + 8)
        var p = offset + 12
        for (i in 0 until count) {
            if (p + 8 > record0.size) break
            val type = u32(record0, p)
            val len = u32(record0, p + 4)
            if (len < 8 || p + len > record0.size) break
            exth[type] = record0.copyOfRange(p + 8, p + len)
            p += len
        }
    }

    private fun textCharset(): Charset = when (textEncoding) {
        65001 -> Charsets.UTF_8
        1252 -> runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)
        else -> runCatching { Charset.forName("windows-$textEncoding") }.getOrDefault(Charsets.UTF_8)
    }

    private fun exthString(type: Int): String? {
        val data = exth[type] ?: return null
        return CharsetUtils.decodeLenient(data, textCharset()).trimEnd('\u0000').trim().takeIf { it.isNotEmpty() }
    }

    fun info(): MobiInfo {
        val html = extractText()
        val cover = extractCover()
        return MobiInfo(
            title = (exthString(503) ?: name).ifBlank { "未命名" },
            author = exthString(100) ?: "",
            language = exthString(524),
            publisher = exthString(101),
            description = exthString(103),
            htmlText = html,
            coverBytes = cover?.first,
            coverExt = cover?.second
        )
    }

    private fun extractText(): String {
        if (textRecordCount <= 0 || textLength <= 0) return ""
        // 防止恶意/损坏文件导致超大分配
        if (textLength > MAX_TEXT_ALLOC) {
            throw IOException("MOBI 声明文本过大（${textLength} 字节），拒绝解析")
        }
        val out = ByteArray(textLength)
        var pos = 0
        val last = minOf(textRecordCount, numRecords - 1)
        for (i in 1..last) {
            val rec = readRecord(i)
            when (compression) {
                0, 1 -> {
                    val n = minOf(rec.size, out.size - pos)
                    System.arraycopy(rec, 0, out, pos, n)
                    pos += n
                }
                2 -> pos += decompressPalmDoc(rec, out, pos)
                17480 -> throw IOException("暂不支持 HUFF/CDIC 压缩的 MOBI，请先转换为 EPUB/MOBI 再导入")
                else -> throw IOException("未知 MOBI 压缩类型: $compression")
            }
            if (pos >= out.size) break
        }
        return CharsetUtils.decodeLenient(out, 0, minOf(pos, out.size), textCharset())
    }

    /** PalmDOC LZ77 解压，写入 out 的 start 位置，返回写入字节数 */
    private fun decompressPalmDoc(data: ByteArray, out: ByteArray, start: Int): Int {
        var i = 0
        var pos = start
        val n = data.size
        val max = out.size
        while (i < n && pos < max) {
            val c = data[i].toInt() and 0xFF
            i++
            when {
                c == 0x00 -> {
                    if (i >= n) break
                    val len = data[i].toInt() and 0xFF
                    i++
                    if (len == 0) {
                        out[pos++] = 0
                    } else {
                        val end = minOf(i + len, n)
                        val copy = minOf(end - i, max - pos)
                        System.arraycopy(data, i, out, pos, copy)
                        i += copy
                        pos += copy
                    }
                }
                c in 1..8 -> {
                    val end = minOf(i + c, n)
                    val copy = minOf(end - i, max - pos)
                    System.arraycopy(data, i, out, pos, copy)
                    i += copy
                    pos += copy
                }
                c in 9..0x7F -> out[pos++] = c.toByte()
                c in 0x80..0xBF -> {
                    if (i >= n) break
                    val c2 = data[i].toInt() and 0xFF
                    i++
                    val pair = (c shl 8) or c2
                    val dist = (pair shr 3) and 0x7FF
                    val len = (pair and 0x7) + 3
                    for (k in 0 until len) {
                        if (pos - dist < 0 || pos >= max) break
                        out[pos] = out[pos - dist]
                        pos++
                    }
                }
                else -> { // c >= 0xC0：空格 + 字符
                    if (pos + 1 < max) {
                        out[pos++] = 0x20
                        out[pos++] = (c xor 0x80).toByte()
                    }
                }
            }
        }
        return pos - start
    }

    private fun extractCover(): Pair<ByteArray, String>? {
        val exthCover = exth[201]?.let { if (it.size >= 4) u32(it, 0) else -1 } ?: -1
        val idx = when {
            exthCover in 1 until numRecords -> exthCover
            firstImageIndex in 1 until numRecords -> firstImageIndex
            else -> -1
        }
        if (idx < 0) return null
        return try {
            val bytes = readRecord(idx)
            val ext = detectImageExt(bytes)
            if (ext != null) bytes to ext else null
        } catch (e: Exception) {
            null
        }
    }

    private fun detectImageExt(b: ByteArray): String? {
        if (b.size < 4) return null
        return when {
            b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
            b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() -> "png"
            b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() -> "gif"
            b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte() -> "bmp"
            else -> null
        }
    }

    private fun readRecord(index: Int): ByteArray {
        val start = recordOffsets[index]
        val end = if (index + 1 < numRecords) recordOffsets[index + 1] else raf.length().toInt()
        val len = (end - start).coerceAtLeast(0)
        val buf = ByteArray(len)
        raf.seek(start.toLong())
        raf.readFully(buf)
        return buf
    }

    override fun close() {
        runCatching { raf.close() }
    }

    companion object {
        private const val MAX_TEXT_ALLOC = 300 * 1024 * 1024

        private fun u16(b: ByteArray, o: Int): Int =
            ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

        private fun u32(b: ByteArray, o: Int): Int =
            ((b[o].toInt() and 0xFF) shl 24) or
                ((b[o + 1].toInt() and 0xFF) shl 16) or
                ((b[o + 2].toInt() and 0xFF) shl 8) or
                (b[o + 3].toInt() and 0xFF)
    }
}
