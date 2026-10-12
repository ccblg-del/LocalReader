package com.example.localreader.di

import android.content.Context
import com.example.localreader.data.db.AppDatabase
import com.example.localreader.data.repository.BookRepository
import com.example.localreader.data.repository.GroupRepository
import com.example.localreader.data.repository.SettingsRepository
import com.example.localreader.importer.ImportManager

/**
 * 简单手工依赖容器（不引入 Hilt，保持轻量）。在 Application.onCreate 初始化。
 */
object AppGraph {

    lateinit var database: AppDatabase
        private set
    lateinit var bookRepository: BookRepository
        private set
    lateinit var groupRepository: GroupRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var importManager: ImportManager
        private set

    fun init(context: Context) {
        database = AppDatabase.getInstance(context)
        bookRepository = BookRepository(database)
        groupRepository = GroupRepository(database)
        settingsRepository = SettingsRepository(context)
        importManager = ImportManager(context, bookRepository)
    }
}
