package com.example.localreader.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.ShelfItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books")
    suspend fun getAll(): List<BookEntity>

    /**
     * 书架数据：书籍 + 分组名（LEFT JOIN）。排序在 ViewModel 中动态处理。
     */
    @Query(
        """
        SELECT books.*, groups.name AS groupName
        FROM books
        LEFT JOIN groups ON books.groupId = groups.id
        ORDER BY books.importedAt DESC
        """
    )
    fun observeShelf(): Flow<List<ShelfItem>>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE books SET groupId = :groupId WHERE id = :id")
    suspend fun moveToGroup(id: Long, groupId: Long?)

    @Query("UPDATE books SET groupId = :groupId WHERE id IN (:ids)")
    suspend fun moveManyToGroup(ids: List<Long>, groupId: Long?)

    @Query("UPDATE books SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)

    @Query("UPDATE books SET lastReadAt = :time, progressPercent = :percent WHERE id = :id")
    suspend fun updateReadState(id: Long, time: Long, percent: Float)
}
