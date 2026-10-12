package com.example.localreader.ui.shelf

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 跨 Tab 传递“按分组筛选书架”的请求（分组页点分组 -> 书架页应用筛选）。
 */
object ShelfFilterStore {
    private val _request = MutableStateFlow<GroupFilter?>(null)
    val request = _request

    fun request(filter: GroupFilter) { _request.value = filter }
    fun clear() { _request.value = null }
}
