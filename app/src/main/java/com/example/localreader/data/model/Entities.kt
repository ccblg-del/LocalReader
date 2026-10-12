package com.example.localreader.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书籍实体。
 * - filePath：复制到私有目录后的源文件绝对路径（files/books/{uuid}/source.ext）
 * - groupId：所属分组；null 表示“未分组”
 * - coverPath：封面相对路径（相对 files/books/），无封面为 null
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val format: BookFormat,
    val filePath: String,
    val originalFileName: String = "",
    val mimeType: String = "",
    val encoding: String? = null,
    val groupId: Long? = null,
    val coverPath: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val totalChars: Long = 0,
    val chapterCount: Int = 0,
    val totalPages: Int = 0,
    val importedAt: Long = 0,
    val lastReadAt: Long = 0,
    val progressPercent: Float = 0f,
    val parserVersion: Int = 1
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = 0,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val title: String,
    val startOffset: Long = 0,
    val endOffset: Long = 0,
    val chapterIndex: Int,
    val href: String? = null,
    val pageIndex: Int = -1
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val pageIndex: Int = -1,
    val textSnippet: String = "",
    val createdAt: Long = 0,
    val locationType: LocationType
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Long,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val pageIndex: Int = 0,
    val zoom: Float = 1f,
    val updatedAt: Long = 0
)

/**
 * 书架展示用：书籍 + 分组名（LEFT JOIN，未分组为 null）。
 */
data class ShelfItem(
    @Embedded val book: BookEntity,
    val groupName: String?
)
