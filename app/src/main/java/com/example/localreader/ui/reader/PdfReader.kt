@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.localreader.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.ChapterEntity
import com.example.localreader.data.model.ReadingProgressEntity
import com.example.localreader.data.settings.ReaderSettings
import com.example.localreader.ui.theme.ReadingColors
import com.example.localreader.util.PdfPageRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfReader(
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
    val file = remember(book.id) { File(book.filePath) }
    val renderer = remember(book.id) { runCatching { PdfPageRenderer(file) }.getOrNull() }
    DisposableEffect(book.id) { onDispose { renderer?.close() } }

    if (renderer == null) {
        Box(Modifier.fillMaxSize()) { Text("PDF 文件不存在或无法打开", color = colors.text) }
        return
    }

    val pageCount = renderer.pageCount
    if (pageCount <= 0) {
        Box(Modifier.fillMaxSize()) { Text("PDF 无页面", color = colors.text) }
        return
    }
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidthPx = with(density) { (config.screenWidthDp.dp * 1.5f).toPx().toInt().coerceAtLeast(200) }

    val pagerState = rememberPagerState(initialPage = (initialProgress?.pageIndex ?: 0).coerceIn(0, pageCount - 1)) { pageCount }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()

    val nightFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    // 页码变化 -> 保存进度 + 同步章节高亮
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        onChapterChanged(page)
        onSaveProgress(page, 0, page, scale, false)
    }

    // 跳转
    LaunchedEffect(Unit) {
        jump.collect { jr ->
            val target = (jr.pageIndex.takeIf { it >= 0 } ?: jr.chapterIndex).coerceIn(0, pageCount - 1)
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            pagerState.scrollToPage(target)
        }
    }

    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = scale <= 1.01f) { page ->
        val bitmap by produceState<Bitmap?>(null, page, targetWidthPx) {
            value = withContext(Dispatchers.IO) { renderer.renderPage(page, targetWidthPx) }
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale
                        if (newScale > 1.01f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                        onSaveProgress(pagerState.currentPage, 0, pagerState.currentPage, newScale, false)
                    }
                }
                .pointerInput(pageCount) {
                    detectTapGestures(onTap = { offset ->
                        if (scale <= 1.01f) {
                            when {
                                offset.x < size.width / 3f -> {
                                    if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) }
                                }
                                offset.x > size.width * 2 / 3f -> {
                                    if (page < pageCount - 1) scope.launch { pagerState.animateScrollToPage(page + 1) }
                                }
                                else -> onToggleMenu()
                            }
                        }
                    })
                }
        ) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "第 ${page + 1} 页",
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = if (settings.pdfNightMode) nightFilter else null
                )
            }
        }
    }
}
