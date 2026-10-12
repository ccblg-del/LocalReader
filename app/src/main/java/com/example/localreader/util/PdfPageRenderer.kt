package com.example.localreader.util

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

/**
 * 封装 Android 自带 PdfRenderer，带 LRU 页面缓存。
 * 注意：由调用方负责在不再使用时调用 close()。
 */
class PdfPageRenderer(file: File) : Closeable {

    private val renderer: PdfRenderer
    val pageCount: Int

    private val cache = LinkedHashMap<Int, Bitmap>(8, 0.75f, true)

    init {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        pageCount = renderer.pageCount
    }

    @Synchronized
    fun renderPage(index: Int, targetWidthPx: Int): Bitmap? {
        if (index < 0 || index >= pageCount) return null
        cache[index]?.let { return it }

        val page = renderer.openPage(index)
        return try {
            val w = targetWidthPx.coerceAtLeast(1)
            val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (cache.size >= MAX_CACHE) {
                cache.entries.firstOrNull()?.let { cache.remove(it.key) }
            }
            cache[index] = bitmap
            bitmap
        } finally {
            page.close()
        }
    }

    override fun close() {
        cache.values.forEach { it.recycle() }
        cache.clear()
        renderer.close()
    }

    companion object {
        private const val MAX_CACHE = 4
    }
}
