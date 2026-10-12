package com.example.localreader.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localreader.data.dao.GroupWithStats
import com.example.localreader.data.model.GroupEntity
import com.example.localreader.di.AppGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UngroupedStats(val count: Int, val lastReadAt: Long?)

class GroupViewModel : ViewModel() {

    private val groupRepository = AppGraph.groupRepository
    private val bookRepository = AppGraph.bookRepository

    val groups: StateFlow<List<GroupWithStats>> = groupRepository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ungroupedStats: StateFlow<UngroupedStats> = bookRepository.observeShelf()
        .map { list ->
            val un = list.filter { it.book.groupId == null }
            UngroupedStats(un.size, un.maxOfOrNull { it.book.lastReadAt })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UngroupedStats(0, null))

    fun createGroup(name: String, onDone: () -> Unit = {}) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch { groupRepository.createGroup(n); onDone() }
    }

    fun renameGroup(group: GroupEntity, newName: String, onDone: () -> Unit = {}) {
        val n = newName.trim()
        if (n.isEmpty()) return
        viewModelScope.launch { groupRepository.renameGroup(group, n); onDone() }
    }

    fun deleteGroup(group: GroupEntity) = viewModelScope.launch { groupRepository.deleteGroup(group) }
}
