package com.example.localreader.data.repository

import com.example.localreader.data.db.AppDatabase
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.BookmarkEntity
import com.example.localreader.data.model.ChapterEntity
import com.example.localreader.data.model.ReadingProgressEntity
import com.example.localreader.data.model.ShelfItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 书籍仓库：统一管理书籍、章节、书签、阅读进度。
 */
class BookRepository(private val db: AppDatabase) {

    private val bookDao = db.bookDao()
    private val chapterDao = db.chapterDao()
    private val bookmarkDao = db.bookmarkDao()
    private val progressDao = db.readingProgressDao()

    // ---- 书籍 ----
    fun observeShelf(): Flow<List<ShelfItem>> = bookDao.observeShelf()
    fun observeBook(id: Long): Flow<BookEntity?> = bookDao.observeById(id)
    suspend fun getBook(id: Long): BookEntity? = bookDao.getById(id)

    suspend fun insertBook(book: BookEntity, chapters: List<ChapterEntity>): Long {
        val id = bookDao.insert(book)
        if (chapters.isNotEmpty()) {
            chapterDao.insertAll(chapters.map { it.copy(bookId = id) })
        }
        progressDao.upsert(ReadingProgressEntity(bookId = id, updatedAt = System.currentTimeMillis()))
        return id
    }

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteById(book.id)
        withContext(Dispatchers.IO) {
            File(book.filePath).parentFile?.deleteRecursively()
        }
    }

    suspend fun deleteBooks(books: List<BookEntity>) {
        bookDao.deleteByIds(books.map { it.id })
        withContext(Dispatchers.IO) {
            books.forEach { File(it.filePath).parentFile?.deleteRecursively() }
        }
    }

    suspend fun moveBookToGroup(bookId: Long, groupId: Long?) = bookDao.moveToGroup(bookId, groupId)
    suspend fun moveBooksToGroup(bookIds: List<Long>, groupId: Long?) =
        bookDao.moveManyToGroup(bookIds, groupId)

    suspend fun updateReadState(bookId: Long, lastReadAt: Long, progressPercent: Float) =
        bookDao.updateReadState(bookId, lastReadAt, progressPercent)

    // ---- 章节 ----
    fun observeChapters(bookId: Long): Flow<List<ChapterEntity>> = chapterDao.observeChapters(bookId)
    suspend fun getChapters(bookId: Long): List<ChapterEntity> = chapterDao.getChapters(bookId)

    // ---- 书签 ----
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.observeBookmarks(bookId)
    suspend fun addBookmark(bookmark: BookmarkEntity): Long = bookmarkDao.insert(bookmark)
    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteById(id)

    // ---- 进度 ----
    fun observeProgress(bookId: Long): Flow<ReadingProgressEntity?> = progressDao.observe(bookId)
    suspend fun getProgress(bookId: Long): ReadingProgressEntity? = progressDao.get(bookId)
    suspend fun upsertProgress(progress: ReadingProgressEntity) = progressDao.upsert(progress)
}
