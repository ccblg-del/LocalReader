package com.example.localreader.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.localreader.data.model.GroupEntity
import kotlinx.coroutines.flow.Flow

data class GroupWithStats(
    @Embedded val group: GroupEntity,
    val bookCount: Int,
    val lastReadAt: Long?
)

@Dao
interface GroupDao {

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM groups ORDER BY sortOrder ASC, createdAt ASC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getGroups(): List<GroupEntity>

    /**
     * 分组及其书籍数量、最近阅读时间。
     */
    @Query(
        """
        SELECT g.*, COUNT(b.id) AS bookCount, MAX(b.lastReadAt) AS lastReadAt
        FROM groups g
        LEFT JOIN books b ON b.groupId = g.id
        GROUP BY g.id
        ORDER BY g.sortOrder ASC, g.createdAt ASC
        """
    )
    fun observeGroupsWithStats(): Flow<List<GroupWithStats>>
}
