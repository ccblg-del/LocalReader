package com.example.localreader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.BookFormat
import com.example.localreader.data.model.BookmarkEntity
import com.example.localreader.data.model.ChapterEntity
import com.example.localreader.data.model.LocationType
import com.example.localreader.data.model.ReadingProgressEntity
import com.example.localreader.data.settings.ReaderSettings
import com.example.localreader.data.settings.ReaderTheme
import com.example.localreader.di.AppGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderDataState(
    val book: BookEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val progress: ReadingProgressEntity? = null,
    val settings: ReaderSettings = ReaderSettings()
)

data class JumpRequest(
    val chapterIndex: Int,
    val charOffset: Long = 0,
    val pageIndex: Int = 0
)

class ReaderViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    val bookId: Long = savedStateHandle.get<Long>("bookId") ?: 0L

    private val bookRepository = AppGraph.bookRepository
    private val settingsRepository = AppGraph.settingsRepository

    private val book = bookRepository.observeBook(bookId)
    private val chapters = bookRepository.observeChapters(bookId)
    private val bookmarks = bookRepository.observeBookmarks(bookId)
    private val progress = bookRepository.observeProgress(bookId)
    private val settings = settingsRepository.settings

    val dataState: StateFlow<ReaderDataState> = combine(book, chapters, bookmarks, progress, settings) { b, c, bm, p, s ->
        ReaderDataState(b, c, bm, p, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderDataState())

    private val _menuVisible = MutableStateFlow(false)
    val menuVisible: StateFlow<Boolean> = _menuVisible.asStateFlow()

    private val _chapterSheetVisible = MutableStateFlow(false)
    val chapterSheetVisible: StateFlow<Boolean> = _chapterSheetVisible.asStateFlow()

    private val _bookmarkSheetVisible = MutableStateFlow(false)
    val bookmarkSheetVisible: StateFlow<Boolean> = _bookmarkSheetVisible.asStateFlow()

    private val _settingsSheetVisible = MutableStateFlow(false)
    val settingsSheetVisible: StateFlow<Boolean> = _settingsSheetVisible.asStateFlow()

    private val _currentChapter = MutableStateFlow(0)
    val currentChapter: StateFlow<Int> = _currentChapter.asStateFlow()

    private val _jump = MutableSharedFlow<JumpRequest>(extraBufferCapacity = 4)
    val jump = _jump.asSharedFlow()

    private var progressJob: Job? = null

    init {
        viewModelScope.launch {
            val p = progress.first()
            if (p != null) {
                _currentChapter.value = p.chapterIndex.coerceAtLeast(0)
            }
        }
    }

    // ---- UI 状态 ----
    fun toggleMenu() { _menuVisible.value = !_menuVisible.value }
    fun hideMenu() { _menuVisible.value = false }
    fun showChapterSheet() { _chapterSheetVisible.value = true }
    fun hideChapterSheet() { _chapterSheetVisible.value = false }
    fun showBookmarkSheet() { _bookmarkSheetVisible.value = true }
    fun hideBookmarkSheet() { _bookmarkSheetVisible.value = false }
    fun showSettingsSheet() { _settingsSheetVisible.value = true }
    fun hideSettingsSheet() { _settingsSheetVisible.value = false }

    fun setCurrentChapter(index: Int) { _currentChapter.value = index.coerceAtLeast(0) }

    // ---- 跳转 ----
    fun jumpToChapter(index: Int) { _jump.tryEmit(JumpRequest(index, 0, 0)) }

    fun jumpToBookmark(bm: BookmarkEntity) {
        _jump.tryEmit(JumpRequest(bm.chapterIndex, bm.charOffset, bm.pageIndex))
    }

    // ---- 进度 ----
    fun saveProgress(chapterIndex: Int, charOffset: Long, pageIndex: Int, zoom: Float, immediate: Boolean = false) {
        progressJob?.cancel()
        if (immediate) {
            viewModelScope.launch { persist(chapterIndex, charOffset, pageIndex, zoom) }
            return
        }
        progressJob = viewModelScope.launch {
            delay(400)
            persist(chapterIndex, charOffset, pageIndex, zoom)
        }
    }

    private suspend fun persist(chapterIndex: Int, charOffset: Long, pageIndex: Int, zoom: Float) {
        val book = bookRepository.getBook(bookId) ?: return
        val percent = computePercent(book, chapterIndex, pageIndex)
        bookRepository.upsertProgress(
            ReadingProgressEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                charOffset = charOffset,
                pageIndex = pageIndex,
                zoom = zoom,
                updatedAt = System.currentTimeMillis()
            )
        )
        bookRepository.updateReadState(bookId, System.currentTimeMillis(), percent)
    }

    private fun computePercent(book: BookEntity, chapterIndex: Int, pageIndex: Int): Float = when (book.format) {
        BookFormat.PDF -> {
            if (book.totalPages > 0) pageIndex.toFloat() / book.totalPages else 0f
        }
        else -> {
            val count = dataState.value.chapters.size
            if (count > 1) chapterIndex.toFloat() / count else 0f
        }
    }

    // ---- 书签 ----
    fun addBookmark(snippet: String) {
        val book = dataState.value.book ?: return
        val p = dataState.value.progress
        viewModelScope.launch {
            bookRepository.addBookmark(
                BookmarkEntity(
                    bookId = book.id,
                    chapterIndex = p?.chapterIndex ?: _currentChapter.value,
                    charOffset = p?.charOffset ?: 0,
                    pageIndex = p?.pageIndex ?: -1,
                    textSnippet = snippet.take(80),
                    createdAt = System.currentTimeMillis(),
                    locationType = if (book.format == BookFormat.PDF) LocationType.PDF_PAGE else LocationType.TEXT
                )
            )
        }
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { bookRepository.deleteBookmark(id) }

    // ---- 阅读设置快捷调整 ----
    fun cycleTheme() {
        val next = when (dataState.value.settings.theme) {
            ReaderTheme.DAY -> ReaderTheme.NIGHT
            ReaderTheme.NIGHT -> ReaderTheme.EYE
            ReaderTheme.EYE -> ReaderTheme.DAY
        }
        viewModelScope.launch { settingsRepository.setTheme(next) }
    }

    fun changeFontSize(delta: Float) {
        val current = dataState.value.settings.fontSize
        viewModelScope.launch { settingsRepository.setFontSize((current + delta).coerceIn(12f, 30f)) }
    }
}
