package com.example.localreader.ui.shelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localreader.data.dao.GroupWithStats
import com.example.localreader.data.model.ShelfItem
import com.example.localreader.di.AppEvents
import com.example.localreader.di.AppGraph
import com.example.localreader.importer.ImportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class GroupFilter {
    object All : GroupFilter()
    object Recent : GroupFilter()
    object Ungrouped : GroupFilter()
    data class ByGroup(val groupId: Long) : GroupFilter()
}

enum class ShelfSort(val label: String) {
    RECENT("最近阅读"),
    IMPORTED("导入时间"),
    TITLE("书名"),
    PROGRESS("阅读进度")
}

enum class ViewMode(val label: String) {
    GRID("网格"),
    LIST("列表")
}

data class ShelfQuery(
    val search: String = "",
    val filter: GroupFilter = GroupFilter.All,
    val sort: ShelfSort = ShelfSort.RECENT,
    val viewMode: ViewMode = ViewMode.GRID,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
)

data class ShelfUiState(
    val items: List<ShelfItem> = emptyList(),
    val groups: List<GroupWithStats> = emptyList(),
    val query: ShelfQuery = ShelfQuery()
)

class ShelfViewModel : ViewModel() {

    private val bookRepository = AppGraph.bookRepository
    private val groupRepository = AppGraph.groupRepository
    private val importManager = AppGraph.importManager

    private val _query = MutableStateFlow(ShelfQuery())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val importProgress: StateFlow<ImportManager.ImportProgress?> = importManager.progress

    val uiState: StateFlow<ShelfUiState> = combine(
        bookRepository.observeShelf(),
        groupRepository.observeGroups(),
        _query
    ) { items, groups, query ->
        ShelfUiState(items = filterAndSort(items, query), groups = groups, query = query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShelfUiState())

    init {
        // 处理冷启动时的分享/打开意图
        AppEvents.drain().takeIf { it.isNotEmpty() }?.let { importBooks(it) }
        viewModelScope.launch {
            AppEvents.version.drop(1).collect {
                AppEvents.drain().takeIf { it.isNotEmpty() }?.let { uris -> importBooks(uris) }
            }
        }
    }

    // ---- 查询/展示状态 ----
    fun setSearch(v: String) = _query.update { it.copy(search = v) }
    fun setFilter(v: GroupFilter) = _query.update { it.copy(filter = v, selectionMode = false, selectedIds = emptySet()) }
    fun setSort(v: ShelfSort) = _query.update { it.copy(sort = v) }
    fun setViewMode(v: ViewMode) = _query.update { it.copy(viewMode = v) }

    fun enterSelectionMode() = _query.update { it.copy(selectionMode = true, selectedIds = emptySet()) }
    fun exitSelectionMode() = _query.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    fun toggleSelect(id: Long) = _query.update {
        val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
        it.copy(selectedIds = next, selectionMode = next.isNotEmpty())
    }

    fun clearMessage() { _message.value = null }

    // ---- 导入 ----
    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val results = importManager.import(uris)
            val ok = results.count { it.success }
            val fail = results.size - ok
            _message.value = when {
                fail == 0 -> "导入成功 ${ok} 本"
                ok == 0 -> "导入失败：${results.firstOrNull()?.error ?: "未知错误"}"
                else -> "导入完成：成功 $ok 本，失败 $fail 本"
            }
        }
    }

    // ---- 移动 / 删除 ----
    fun moveSelectedToGroup(groupId: Long?) {
        val ids = _query.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookRepository.moveBooksToGroup(ids, groupId)
            exitSelectionMode()
        }
    }

    fun deleteSelected() {
        val ids = _query.value.selectedIds.toList()
        if (ids.isEmpty()) return
        val books = uiState.value.items.filter { it.book.id in ids }.map { it.book }
        viewModelScope.launch {
            bookRepository.deleteBooks(books)
            exitSelectionMode()
        }
    }

    fun moveBookToGroup(bookId: Long, groupId: Long?) = viewModelScope.launch {
        bookRepository.moveBookToGroup(bookId, groupId)
    }

    fun deleteBook(bookId: Long) {
        val book = uiState.value.items.firstOrNull { it.book.id == bookId }?.book ?: return
        viewModelScope.launch { bookRepository.deleteBook(book) }
    }

    // ---- 内部过滤排序 ----
    private fun filterAndSort(items: List<ShelfItem>, query: ShelfQuery): List<ShelfItem> {
        var list = items
        list = when (val f = query.filter) {
            is GroupFilter.All -> list
            is GroupFilter.Recent -> list.filter { it.book.lastReadAt > 0 }
            is GroupFilter.Ungrouped -> list.filter { it.book.groupId == null }
            is GroupFilter.ByGroup -> list.filter { it.book.groupId == f.groupId }
        }
        if (query.search.isNotBlank()) {
            val q = query.search.trim()
            list = list.filter {
                it.book.title.contains(q, ignoreCase = true) ||
                    it.book.author.contains(q, ignoreCase = true) ||
                    (it.groupName?.contains(q, ignoreCase = true) == true)
            }
        }
        list = when (query.sort) {
            ShelfSort.RECENT -> list.sortedByDescending { it.book.lastReadAt }
            ShelfSort.IMPORTED -> list.sortedByDescending { it.book.importedAt }
            ShelfSort.TITLE -> list.sortedBy { it.book.title }
            ShelfSort.PROGRESS -> list.sortedByDescending { it.book.progressPercent }
        }
        return list
    }
}

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
