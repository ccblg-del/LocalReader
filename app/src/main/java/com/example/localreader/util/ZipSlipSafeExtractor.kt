package com.example.localreader.util

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * 安全解压 ZIP（EPUB 本质是 ZIP），防止 Zip Slip 路径穿越。
 * 每个条目都会先解析为规范化路径，确认其仍位于目标目录内才写入。
 */
object ZipSlipSafeExtractor {

    fun extract(zipFile: File, destDir: File) {
        val dest = destDir.canonicalFile
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val target = File(dest, entry.name).canonicalFile

                // Zip Slip 防护：目标必须位于解压目录之内
                if (target != dest && !target.path.startsWith(dest.path + File.separator)) {
                    throw IOException("非法 ZIP 条目路径（可能为 Zip Slip 攻击）: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}
