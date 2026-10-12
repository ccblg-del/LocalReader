package com.example.localreader.util

import java.io.BufferedInputStream
import java.io.File
import java.nio.charset.Charset
import org.mozilla.universalchardet.UniversalDetector

/**
 * 文本编码检测：BOM 优先，其次 juniversalchardet，最后 UTF-8 兜底。
 * 中文 GBK/GB2312 统一归一化为 GB18030（超集，解码安全，避免乱码）。
 */
object EncodingDetector {

    private const val SAMPLE_SIZE = 512 * 1024

    data class Detected(val charsetName: String, val bomBytesToSkip: Int = 0)

    fun detect(file: File, mimeType: String? = null): Detected {
        readBom(file)?.let { return it }
        detectWithChardet(file)?.let { return it }
        return Detected("UTF-8")
    }

    private fun readBom(file: File): Detected? {
        val bytes = ByteArray(4)
        file.inputStream().use { ins ->
            val n = ins.read(bytes)
            if (n <= 0) return null
            if (n >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return Detected("UTF-8", 3)
            }
            if (n >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return Detected("UTF-16BE", 2)
            }
            if (n >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return Detected("UTF-16LE", 2)
            }
            if (n >= 4 && bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
                bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte()
            ) {
                return Detected("UTF-32BE", 4)
            }
            if (n >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() &&
                bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte()
            ) {
                return Detected("UTF-32LE", 4)
            }
        }
        return null
    }

    private fun detectWithChardet(file: File): Detected? {
        return try {
            val detector = UniversalDetector(null)
            BufferedInputStream(file.inputStream()).use { ins ->
                val buf = ByteArray(4096)
                var total = 0
                while (total < SAMPLE_SIZE) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    detector.handleData(buf, 0, n)
                    total += n
                    if (detector.isDone) break
                }
            }
            detector.dataEnd()
            detector.detectedCharset?.let { normalize(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun normalize(name: String): Detected? {
        val n = name.uppercase()
        val normalized = when {
            n.contains("GB") -> "GB18030"
            n.contains("UTF-8") -> "UTF-8"
            n.contains("UTF-16LE") -> "UTF-16LE"
            n.contains("UTF-16BE") -> "UTF-16BE"
            n.contains("BIG5") -> "Big5"
            else -> name
        }
        return try {
            Charset.forName(normalized)
            Detected(normalized)
        } catch (e: Exception) {
            null
        }
    }
}
