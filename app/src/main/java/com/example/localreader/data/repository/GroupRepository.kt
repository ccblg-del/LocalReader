package com.example.localreader.data.repository

import androidx.room.withTransaction
import com.example.localreader.data.dao.GroupWithStats
import com.example.localreader.data.db.AppDatabase
import com.example.localreader.data.model.GroupEntity
import kotlinx.coroutines.flow.Flow

/**
 * 分组仓库。删除分组时只把书籍移到“未分组”，不删除书籍文件。
 */
class GroupRepository(private val db: AppDatabase) {

    private val groupDao = db.groupDao()
    private val bookDao = db.bookDao()

    fun observeGroups(): Flow<List<GroupWithStats>> = groupDao.observeGroupsWithStats()
    suspend fun getGroups(): List<GroupEntity> = groupDao.getGroups()

    suspend fun createGroup(name: String): Long {
        val nextOrder = groupDao.getGroups().size
        return groupDao.insert(
            GroupEntity(name = name.trim(), createdAt = System.currentTimeMillis(), sortOrder = nextOrder)
        )
    }

    suspend fun renameGroup(group: GroupEntity, newName: String) {
        groupDao.update(group.copy(name = newName.trim()))
    }

    suspend fun deleteGroup(group: GroupEntity) {
        db.withTransaction {
            // 先把该分组下的书籍移到“未分组”（groupId = NULL）
            bookDao.clearGroup(group.id)
            groupDao.deleteById(group.id)
        }
    }
}
