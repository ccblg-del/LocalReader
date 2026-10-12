package com.example.localreader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localreader.data.model.BookmarkEntity
import com.example.localreader.data.model.ChapterEntity
import com.example.localreader.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    bookmarks: List<BookmarkEntity>,
    chapters: List<ChapterEntity>,
    onJump: (BookmarkEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("书签（${bookmarks.size}）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("添加书签") }
        }
        if (bookmarks.isEmpty()) {
            Text(
                "暂无书签，点击“添加书签”记录当前位置",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(bookmarks, key = { it.id }) { bm ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onJump(bm) }
                        .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        val title = bm.textSnippet.ifBlank {
                            chapters.getOrNull(bm.chapterIndex)?.title ?: "第${bm.chapterIndex + 1}章"
                        }
                        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            chapters.getOrNull(bm.chapterIndex)?.title ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            TimeUtils.formatRelative(bm.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onDelete(bm.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除书签")
                    }
                }
            }
        }
    }
}
