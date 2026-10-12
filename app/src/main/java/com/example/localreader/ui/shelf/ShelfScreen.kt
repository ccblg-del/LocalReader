@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.localreader.ui.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localreader.data.model.BookFormat
import com.example.localreader.data.model.ShelfItem
import com.example.localreader.importer.ImportManager
import com.example.localreader.ui.components.BookCover
import com.example.localreader.ui.components.EmptyState
import com.example.localreader.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(onOpenBook: (Long) -> Unit) {
    val vm: ShelfViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val progress by vm.importProgress.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchActive by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var moveDialogBookIds by remember { mutableStateOf<List<Long>?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importBooks(uris) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.query.selectionMode) {
                        Text("已选 ${state.query.selectedIds.size} 本")
                    } else {
                        Text("本地阅读器")
                    }
                },
                navigationIcon = {
                    if (state.query.selectionMode) {
                        IconButton(onClick = { vm.exitSelectionMode() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "取消选择")
                        }
                    }
                },
                actions = {
                    if (state.query.selectionMode) {
                        IconButton(onClick = {
                            moveDialogBookIds = state.query.selectedIds.toList()
                        }) {
                            Icon(Icons.Filled.DriveFileMove, contentDescription = "移动到分组")
                        }
                        IconButton(onClick = { vm.deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    } else {
                        IconButton(onClick = { searchActive = !searchActive; if (!searchActive) vm.setSearch("") }) {
                            Icon(if (searchActive) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "搜索")
                        }
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                ShelfSort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        onClick = { vm.setSort(s); sortMenuOpen = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { vm.setViewMode(if (state.query.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) }) {
                            Icon(
                                if (state.query.viewMode == ViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                                contentDescription = "切换视图"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.query.selectionMode) {
                FloatingActionButton(onClick = { importLauncher.launch(ImportManager.PICKER_MIME_TYPES) }) {
                    Icon(Icons.Filled.Add, contentDescription = "导入书籍")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchActive && !state.query.selectionMode) {
                OutlinedTextField(
                    value = state.query.search,
                    onValueChange = { vm.setSearch(it) },
                    placeholder = { Text("搜索书名 / 作者 / 分组") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (!state.query.selectionMode) {
                FilterChipsRow(state = state, onSelect = { vm.setFilter(it) })
            }

            val items = state.items
            when {
                items.isEmpty() -> EmptyState(
                    if (state.query.search.isNotBlank() || state.query.filter != GroupFilter.All) "没有匹配的书籍" else "书架空空如也，点击右下角导入书籍"
                )
                state.query.viewMode == ViewMode.GRID -> BookGrid(items, state, onOpenBook, onLongPress = { vm.enterSelectionMode() }, onToggle = { vm.toggleSelect(it) })
                else -> BookList(items, state, onOpenBook, onLongPress = { vm.enterSelectionMode() }, onToggle = { vm.toggleSelect(it) })
            }
        }
    }

    // 进度提示
    progress?.let { p ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("正在导入 ${p.current}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (p.total > 0) p.finished.toFloat() / p.total else 0f },
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
                Text("${p.finished}/${p.total}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // 移动分组弹窗
    moveDialogBookIds?.let { ids ->
        MoveBookDialog(
            groups = state.groups,
            onDismiss = { moveDialogBookIds = null },
            onMove = { groupId ->
                vm.moveSelectedToGroup(groupId)
                moveDialogBookIds = null
            }
        )
    }
    }
}

@Composable
private fun FilterChipsRow(state: ShelfUiState, onSelect: (GroupFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { FilterChip(selected = state.query.filter == GroupFilter.All, onClick = { onSelect(GroupFilter.All) }, label = { Text("全部") }) }
        item { FilterChip(selected = state.query.filter == GroupFilter.Recent, onClick = { onSelect(GroupFilter.Recent) }, label = { Text("最近阅读") }) }
        item { FilterChip(selected = state.query.filter == GroupFilter.Ungrouped, onClick = { onSelect(GroupFilter.Ungrouped) }, label = { Text("未分组") }) }
        items(state.groups) { g ->
            FilterChip(
                selected = state.query.filter == GroupFilter.ByGroup(g.group.id),
                onClick = { onSelect(GroupFilter.ByGroup(g.group.id)) },
                label = { Text(g.group.name) }
            )
        }
    }
}

@Composable
private fun BookGrid(
    items: List<ShelfItem>,
    state: ShelfUiState,
    onOpenBook: (Long) -> Unit,
    onLongPress: () -> Unit,
    onToggle: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items, key = { it.book.id }) { item ->
            BookGridItem(item, state, onOpenBook, onLongPress, onToggle)
        }
    }
}

@Composable
private fun BookGridItem(
    item: ShelfItem,
    state: ShelfUiState,
    onOpenBook: (Long) -> Unit,
    onLongPress: () -> Unit,
    onToggle: (Long) -> Unit
) {
    val selected = item.book.id in state.query.selectedIds
    Column(
        modifier = Modifier
            .clipSelection(selected)
            .combinedClickable(
                onClick = { if (state.query.selectionMode) onToggle(item.book.id) else onOpenBook(item.book.id) },
                onLongClick = { if (!state.query.selectionMode) { onLongPress(); onToggle(item.book.id) } }
            )
    ) {
        BookCover(item.book, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
        Spacer(Modifier.height(6.dp))
        Text(item.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${item.book.author.ifBlank { "未知作者" }} · ${item.book.format.label}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (item.book.lastReadAt > 0) "进度 ${TimeUtils.formatProgress(item.book.progressPercent)}" else "未读",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun BookList(
    items: List<ShelfItem>,
    state: ShelfUiState,
    onOpenBook: (Long) -> Unit,
    onLongPress: () -> Unit,
    onToggle: (Long) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.book.id }) { item ->
            val selected = item.book.id in state.query.selectedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipSelection(selected)
                    .combinedClickable(
                        onClick = { if (state.query.selectionMode) onToggle(item.book.id) else onOpenBook(item.book.id) },
                        onLongClick = { if (!state.query.selectionMode) { onLongPress(); onToggle(item.book.id) } }
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCover(item.book, Modifier.width(52.dp).height(72.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${item.book.author.ifBlank { "未知作者" }} · ${item.book.format.label}" +
                            (item.groupName?.let { " · $it" } ?: " · 未分组"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (item.book.lastReadAt > 0) "最近阅读 ${TimeUtils.formatRelative(item.book.lastReadAt)} · ${TimeUtils.formatProgress(item.book.progressPercent)}" else "未读",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.clipSelection(selected: Boolean): Modifier = this.then(
    if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    else Modifier
)

@Composable
private fun MoveBookDialog(groups: List<com.example.localreader.data.dao.GroupWithStats>, onDismiss: () -> Unit, onMove: (Long?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到分组") },
        text = {
            Column {
                TextButton(onClick = { onMove(null) }, modifier = Modifier.fillMaxWidth()) { Text("未分组") }
                groups.forEach { g ->
                    TextButton(onClick = { onMove(g.group.id) }, modifier = Modifier.fillMaxWidth()) { Text(g.group.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
