package com.example.localreader.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localreader.data.model.GroupEntity
import com.example.localreader.ui.shelf.GroupFilter
import com.example.localreader.ui.shelf.ShelfFilterStore
import com.example.localreader.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(onNavigateToShelf: () -> Unit) {
    val vm: GroupViewModel = viewModel()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val ungrouped by vm.ungroupedStats.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<GroupEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<GroupEntity?>(null) }
    var menuFor by remember { mutableStateOf<GroupEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分组") },
                actions = {
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = "新建分组") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                GroupRow(
                    title = "未分组",
                    subtitle = "${ungrouped.count} 本 · ${if ((ungrouped.lastReadAt ?: 0) > 0) TimeUtils.formatRelative(ungrouped.lastReadAt!!) else "未读"}",
                    onClick = {
                        ShelfFilterStore.request(GroupFilter.Ungrouped)
                        onNavigateToShelf()
                    }
                )
            }
            items(groups, key = { it.group.id }) { g ->
                GroupRow(
                    title = g.group.name,
                    subtitle = "${g.bookCount} 本 · ${if ((g.lastReadAt ?: 0) > 0) TimeUtils.formatRelative(g.lastReadAt!!) else "未读"}",
                    onClick = {
                        ShelfFilterStore.request(GroupFilter.ByGroup(g.group.id))
                        onNavigateToShelf()
                    },
                    trailing = {
                        Box {
                            IconButton(onClick = { menuFor = g.group }) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                            DropdownMenu(expanded = menuFor == g.group, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(text = { Text("重命名") }, onClick = { menuFor = null; renameTarget = g.group })
                                DropdownMenuItem(text = { Text("删除") }, onClick = { menuFor = null; deleteTarget = g.group })
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCreate) {
        GroupNameDialog(
            title = "新建分组",
            initial = "",
            onDismiss = { showCreate = false },
            onConfirm = { vm.createGroup(it) { showCreate = false } }
        )
    }
    renameTarget?.let { g ->
        GroupNameDialog(
            title = "重命名分组",
            initial = g.name,
            onDismiss = { renameTarget = null },
            onConfirm = { vm.renameGroup(g, it) { renameTarget = null } }
        )
    }
    deleteTarget?.let { g ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除分组") },
            text = { Text("删除分组「${g.name}」后，其中的书籍将移到“未分组”，不会删除书籍文件。") },
            confirmButton = { TextButton(onClick = { vm.deleteGroup(g); deleteTarget = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun GroupRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun GroupNameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, label = { Text("分组名称") })
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
