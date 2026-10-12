package com.example.localreader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.BookFormat
import com.example.localreader.ui.components.LoadingState
import com.example.localreader.ui.theme.ReadingPalette
import com.example.localreader.util.TimeUtils

@Composable
fun ReaderScreen(onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel()
    val data by vm.dataState.collectAsStateWithLifecycle()
    val menuVisible by vm.menuVisible.collectAsStateWithLifecycle()
    val chapterSheet by vm.chapterSheetVisible.collectAsStateWithLifecycle()
    val bookmarkSheet by vm.bookmarkSheetVisible.collectAsStateWithLifecycle()
    val settingsSheet by vm.settingsSheetVisible.collectAsStateWithLifecycle()
    val currentChapter by vm.currentChapter.collectAsStateWithLifecycle()

    val book = data.book
    val colors = ReadingPalette.forTheme(data.settings.theme)
    val view = LocalView.current

    // 沉浸式阅读
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // 屏幕常亮
    LaunchedEffect(data.settings.keepScreenOn) {
        view.keepScreenOn = data.settings.keepScreenOn
    }

    BackHandler {
        if (menuVisible || chapterSheet || bookmarkSheet || settingsSheet) {
            vm.hideMenu()
            vm.hideChapterSheet()
            vm.hideBookmarkSheet()
            vm.hideSettingsSheet()
        } else {
            onBack()
        }
    }

    if (book == null) {
        Box(Modifier.fillMaxSize().background(colors.background)) { LoadingState() }
        return
    }

    val snippet = data.chapters.getOrNull(currentChapter)?.title ?: ""

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (book.format) {
            BookFormat.TXT -> TxtReader(
                book = book,
                chapters = data.chapters,
                settings = data.settings,
                colors = colors,
                initialProgress = data.progress,
                currentChapter = currentChapter,
                jump = vm.jump,
                onChapterChanged = { vm.setCurrentChapter(it) },
                onSaveProgress = vm::saveProgress,
                onToggleMenu = { vm.toggleMenu() }
            )
            BookFormat.EPUB, BookFormat.MOBI, BookFormat.AZW3 -> HtmlReader(
                book = book,
                chapters = data.chapters,
                settings = data.settings,
                colors = colors,
                initialProgress = data.progress,
                currentChapter = currentChapter,
                jump = vm.jump,
                onChapterChanged = { vm.setCurrentChapter(it) },
                onSaveProgress = vm::saveProgress,
                onToggleMenu = { vm.toggleMenu() }
            )
            BookFormat.PDF -> PdfReader(
                book = book,
                chapters = data.chapters,
                settings = data.settings,
                colors = colors,
                initialProgress = data.progress,
                currentChapter = currentChapter,
                jump = vm.jump,
                onChapterChanged = { vm.setCurrentChapter(it) },
                onSaveProgress = vm::saveProgress,
                onToggleMenu = { vm.toggleMenu() }
            )
        }

        // 亮度遮罩（亮度 < 1 时变暗）
        if (data.settings.brightness < 1f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - data.settings.brightness).coerceIn(0f, 0.9f))))
        }

        // 顶栏
        AnimatedVisibility(
            visible = menuVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it }
        ) {
            ReaderTopBar(
                book = book,
                currentChapter = currentChapter,
                chapterCount = data.chapters.size,
                onBack = { vm.hideMenu(); onBack() }
            )
        }

        // 底部菜单
        AnimatedVisibility(
            visible = menuVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            ReaderBottomBar(
                onChapters = vm::showChapterSheet,
                onBookmarks = vm::showBookmarkSheet,
                onAddBookmark = { vm.addBookmark(snippet) },
                onCycleTheme = { vm.cycleTheme() },
                onFontMinus = { vm.changeFontSize(-1f) },
                onFontPlus = { vm.changeFontSize(1f) },
                onSettings = vm::showSettingsSheet
            )
        }
    }

    if (chapterSheet) {
        ChapterListScreen(
            chapters = data.chapters,
            currentIndex = currentChapter,
            onSelect = { vm.jumpToChapter(it); vm.hideChapterSheet() },
            onDismiss = { vm.hideChapterSheet() }
        )
    }
    if (bookmarkSheet) {
        BookmarkScreen(
            bookmarks = data.bookmarks,
            chapters = data.chapters,
            onJump = { vm.jumpToBookmark(it); vm.hideBookmarkSheet() },
            onDelete = { vm.deleteBookmark(it) },
            onAdd = { vm.addBookmark(snippet) },
            onDismiss = { vm.hideBookmarkSheet() }
        )
    }
    if (settingsSheet) {
        ReaderSettingsSheet(onDismiss = { vm.hideSettingsSheet() })
    }
}

@Composable
private fun ReaderTopBar(book: BookEntity, currentChapter: Int, chapterCount: Int, onBack: () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.55f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    book.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (chapterCount > 0) "${currentChapter + 1} / $chapterCount" else book.format.label,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
            Text(
                TimeUtils.formatProgress(book.progressPercent),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    onChapters: () -> Unit,
    onBookmarks: () -> Unit,
    onAddBookmark: () -> Unit,
    onCycleTheme: () -> Unit,
    onFontMinus: () -> Unit,
    onFontPlus: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(color = Color.Black.copy(alpha = 0.55f)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MenuAction(Icons.Filled.List, "目录", onChapters)
            MenuAction(Icons.Filled.Bookmarks, "书签", onBookmarks)
            MenuAction(Icons.Filled.BookmarkAdd, "加书签", onAddBookmark)
            MenuAction(Icons.Filled.WbSunny, "主题", onCycleTheme)
            MenuAction(Icons.Filled.Remove, "字号", onFontMinus)
            MenuAction(Icons.Filled.Add, "字号", onFontPlus)
            MenuAction(Icons.Filled.Settings, "设置", onSettings)
        }
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Spacer(Modifier.width(2.dp))
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
