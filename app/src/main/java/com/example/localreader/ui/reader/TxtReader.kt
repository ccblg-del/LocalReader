package com.example.localreader.ui.reader

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.ChapterEntity
import com.example.localreader.data.model.ReadingProgressEntity
import com.example.localreader.data.settings.FontFamilyOption
import com.example.localreader.data.settings.PageMode
import com.example.localreader.data.settings.ReaderSettings
import com.example.localreader.ui.theme.ReadingColors
import com.example.localreader.util.TextPaginator
import com.example.localreader.util.TxtContentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun FontFamilyOption.toFontFamily(): FontFamily = when (this) {
    FontFamilyOption.DEFAULT -> FontFamily.Default
    FontFamilyOption.SERIF -> FontFamily.Serif
    FontFamilyOption.MONO -> FontFamily.Monospace
}

@Composable
fun TxtReader(
    book: BookEntity,
    chapters: List<ChapterEntity>,
    settings: ReaderSettings,
    colors: ReadingColors,
    initialProgress: ReadingProgressEntity?,
    currentChapter: Int,
    jump: SharedFlow<JumpRequest>,
    onChapterChanged: (Int) -> Unit,
    onSaveProgress: (chapterIndex: Int, charOffset: Long, pageIndex: Int, zoom: Float, immediate: Boolean) -> Unit,
    onToggleMenu: () -> Unit
) {
    val contentFile = remember(book.id) { TxtContentReader.contentFileFor(book) }
    val chapter = chapters.getOrNull(currentChapter)

    val chapterText by produceState("", chapter?.id) {
        value = if (chapter != null) {
            withContext(Dispatchers.IO) {
                TxtContentReader.readRange(contentFile, chapter.startOffset, chapter.endOffset)
            }
        } else ""
    }

    val style = remember(settings, colors) {
        TextStyle(
            fontSize = settings.fontSize.sp,
            lineHeight = settings.lineHeight.em,
            color = colors.text,
            fontFamily = settings.fontFamily.toFontFamily()
        )
    }

    if (settings.pageMode == PageMode.PAGED) {
        PagedText(
            chapterText = chapterText,
            style = style,
            settings = settings,
            chapters = chapters,
            currentChapter = currentChapter,
            initialProgress = initialProgress,
            jump = jump,
            onChapterChanged = onChapterChanged,
            onSaveProgress = onSaveProgress,
            onToggleMenu = onToggleMenu
        )
    } else {
        ScrollText(
            chapterText = chapterText,
            style = style,
            settings = settings,
            currentChapter = currentChapter,
            initialProgress = initialProgress,
            jump = jump,
            onChapterChanged = onChapterChanged,
            onSaveProgress = onSaveProgress,
            onToggleMenu = onToggleMenu
        )
    }
}

@Composable
private fun PagedText(
    chapterText: String,
    style: TextStyle,
    settings: ReaderSettings,
    chapters: List<ChapterEntity>,
    currentChapter: Int,
    initialProgress: ReadingProgressEntity?,
    jump: SharedFlow<JumpRequest>,
    onChapterChanged: (Int) -> Unit,
    onSaveProgress: (Int, Long, Int, Float, Boolean) -> Unit,
    onToggleMenu: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    var pageIndex by remember { mutableIntStateOf(0) }
    var pendingOffset by remember { mutableStateOf<Long?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = density.density
        val padPx = (settings.pagePadding * scale).toInt()
        val pageW = ((maxWidth.value * scale).toInt() - padPx * 2).coerceAtLeast(1)
        val pageH = ((maxHeight.value * scale).toInt() - padPx * 2).coerceAtLeast(1)
        val paragraphSpacingPx = (settings.paragraphSpacing * scale).toInt()
        val paginator = remember(textMeasurer, style, pageW, pageH, paragraphSpacingPx) {
            TextPaginator(textMeasurer, style, pageW, pageH, paragraphSpacingPx)
        }
        val pages = remember(chapterText, paginator) { paginator.paginate(chapterText) }

        LaunchedEffect(chapterText) {
            val target = pendingOffset
                ?: if (initialProgress?.chapterIndex == currentChapter) initialProgress.charOffset else 0L
            pageIndex = if (target > 0) pages.indexOfFirst { it.startOffset >= target }.coerceAtLeast(0) else 0
            pendingOffset = null
        }
        LaunchedEffect(Unit) {
            jump.collect { jr ->
                if (jr.chapterIndex != currentChapter) {
                    pendingOffset = jr.charOffset
                    onChapterChanged(jr.chapterIndex)
                } else {
                    pageIndex = pages.indexOfFirst { it.startOffset >= jr.charOffset }.coerceAtLeast(0)
                }
            }
        }

        val page = pages.getOrNull(pageIndex)
        Box(
            Modifier.fillMaxSize().pointerInput(currentChapter, pages.size) {
                detectTapGestures(onTap = { offset ->
                    when {
                        offset.x < size.width / 3f -> {
                            if (pageIndex > 0) pageIndex--
                            else if (currentChapter > 0) onChapterChanged(currentChapter - 1)
                        }
                        offset.x > size.width * 2 / 3f -> {
                            if (pageIndex < pages.size - 1) pageIndex++
                            else if (currentChapter < chapters.size - 1) onChapterChanged(currentChapter + 1)
                        }
                        else -> onToggleMenu()
                    }
                })
            }
        ) {
            if (page != null) {
                Text(
                    text = chapterText.substring(page.startOffset, page.endOffset),
                    style = style,
                    modifier = Modifier.padding(settings.pagePadding.dp)
                )
            }
        }

        LaunchedEffect(pageIndex, currentChapter) {
            page?.let { onSaveProgress(currentChapter, it.startOffset.toLong(), 0, 1f, false) }
        }
    }
}

@Composable
private fun ScrollText(
    chapterText: String,
    style: TextStyle,
    settings: ReaderSettings,
    currentChapter: Int,
    initialProgress: ReadingProgressEntity?,
    jump: SharedFlow<JumpRequest>,
    onChapterChanged: (Int) -> Unit,
    onSaveProgress: (Int, Long, Int, Float, Boolean) -> Unit,
    onToggleMenu: () -> Unit
) {
    val paragraphs = remember(chapterText) { splitParagraphs(chapterText) }
    val initialIndex = if (initialProgress?.chapterIndex == currentChapter) {
        paragraphs.indexOfLast { it.second <= initialProgress.charOffset }.coerceAtLeast(0)
    } else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        jump.collect { jr ->
            if (jr.chapterIndex != currentChapter) {
                onChapterChanged(jr.chapterIndex)
            } else {
                val idx = paragraphs.indexOfLast { it.second <= jr.charOffset }.coerceAtLeast(0)
                listState.scrollToItem(idx)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx ->
                val off = paragraphs.getOrNull(idx)?.second ?: 0L
                onSaveProgress(currentChapter, off, 0, 1f, false)
            }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { offset ->
                when {
                    offset.x < size.width / 3f -> scope.launch { listState.animateScrollBy(-size.height * 0.9f) }
                    offset.x > size.width * 2 / 3f -> scope.launch { listState.animateScrollBy(size.height * 0.9f) }
                    else -> onToggleMenu()
                }
            })
        }
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(paragraphs.size) { i ->
                val text = paragraphs[i].first
                Text(
                    text = if (text.isEmpty()) " " else text,
                    style = style,
                    modifier = Modifier.padding(
                        start = settings.pagePadding.dp,
                        end = settings.pagePadding.dp,
                        bottom = settings.paragraphSpacing.dp
                    )
                )
            }
        }
    }
}

private fun splitParagraphs(text: String): List<Pair<String, Long>> {
    val result = mutableListOf<Pair<String, Long>>()
    var start = 0
    while (start <= text.length) {
        val nl = text.indexOf('\n', start)
        val end = if (nl == -1) text.length else nl
        result.add(text.substring(start, end) to start.toLong())
        if (nl == -1) break
        start = nl + 1
    }
    return result
}
