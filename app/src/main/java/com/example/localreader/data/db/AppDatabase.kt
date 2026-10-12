package com.example.localreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.localreader.data.dao.BookDao
import com.example.localreader.data.dao.BookmarkDao
import com.example.localreader.data.dao.ChapterDao
import com.example.localreader.data.dao.GroupDao
import com.example.localreader.data.dao.ReadingProgressDao
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.BookmarkEntity
import com.example.localreader.data.model.ChapterEntity
import com.example.localreader.data.model.GroupEntity
import com.example.localreader.data.model.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        GroupEntity::class,
        ChapterEntity::class,
        BookmarkEntity::class,
        ReadingProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun groupDao(): GroupDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "local_reader.db"
                ).build().also { INSTANCE = it }
            }
    }
}
