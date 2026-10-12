package com.example.localreader.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 SAF 把所选文件复制到 App 私有目录 files/books/{uuid}/source.ext，
 * 避免原文件被移动/删除后无法阅读。
 */
class FileImporter(private val context: Context) {

    data class CopiedFile(
        val file: File,
        val originalName: String,
        val mimeType: String,
        val extension: String
    )

    suspend fun copyToPrivate(uri: Uri, booksDir: File): CopiedFile = withContext(Dispatchers.IO) {
        val originalName = queryName(uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val extension = originalName.substringAfterLast('.', "").lowercase().ifBlank { "bin" }

        val uuid = UUID.randomUUID().toString().replace("-", "")
        val bookDir = File(booksDir, uuid).apply { mkdirs() }
        val target = File(bookDir, "source.$extension")

        val input = if (uri.scheme == "file") {
            File(uri.path ?: throw IOException("无效文件路径")).inputStream()
        } else {
            context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")
        }
        input.use { ins ->
            target.outputStream().use { out -> ins.copyTo(out) }
        }
        CopiedFile(target, originalName, mimeType, extension)
    }

    fun queryName(uri: Uri): String {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
            }
        }
        return name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "未命名"
    }
}
