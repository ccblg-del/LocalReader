package com.example.localreader.di

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 跨层事件（当前用于 ACTION_SEND/ACTION_VIEW 分享打开）。
 * 用版本号 + 待处理队列，避免 SharedFlow replay 在 Activity 重建时重复导入。
 */
object AppEvents {

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val pending = mutableListOf<Uri>()

    @Synchronized
    fun post(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pending.addAll(uris)
        _version.value = _version.value + 1
    }

    @Synchronized
    fun drain(): List<Uri> {
        val list = pending.toList()
        pending.clear()
        return list
    }
}
