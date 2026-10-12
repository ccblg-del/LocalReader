package com.example.localreader.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    /** 相对时间：刚刚 / N分钟前 / N小时前 / N天前 / yyyy-MM-dd */
    fun formatRelative(timestamp: Long): String {
        if (timestamp <= 0) return "从未"
        val diff = System.currentTimeMillis() - timestamp
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            diff < minute -> "刚刚"
            diff < hour -> "${diff / minute}分钟前"
            diff < day -> "${diff / hour}小时前"
            diff < 7 * day -> "${diff / day}天前"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun formatProgress(percent: Float): String {
        val p = percent.coerceIn(0f, 1f)
        return "${(p * 100).toInt()}%"
    }
}
