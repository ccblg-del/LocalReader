package com.example.localreader.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.example.localreader.data.model.BookEntity
import com.example.localreader.data.model.ChapterEntity
import com.example.localreader.data.model.ReadingProgressEntity
import com.example.localreader.data.settings.ReaderSettings
import com.example.localreader.ui.theme.ReadingColors
import com.example.localreader.util.HtmlResourceExtractor
import java.io.File
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

/** WebView JS 桥：接收滚动进度与点击区域 */
class ReaderJsBridge {
    var onScroll: ((Double) -> Unit)? = null
    var onTapZone: ((String) -> Unit)? = null

    @JavascriptInterface
    fun onScroll(percent: Double) { onScroll?.invoke(percent) }

    @JavascriptInterface
    fun onTapZone(zone: String) { onTapZone?.invoke(zone) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlReader(
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val chapter = chapters.getOrNull(currentChapter)
    val href = chapter?.href

    val bridge = remember { ReaderJsBridge() }
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    var pendingPercent by remember { mutableStateOf<Float?>(null) }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler(
                "/books/",
                WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "books"))
            )
            .build()
    }

    val latestSettings = rememberUpdatedState(settings)
    val latestColors = rememberUpdatedState(colors)
    val latestChapter = rememberUpdatedState(currentChapter)
    val latestProgress = rememberUpdatedState(initialProgress)
    val latestPending = rememberUpdatedState(pendingPercent)
    val latestDensity = rememberUpdatedState(density)

    val webViewClient = remember {
        object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                val pct = latestPending.value
                    ?: if (latestProgress.value?.chapterIndex == latestChapter.value) {
                        latestProgress.value!!.charOffset / 1000f
                    } else 0f
                view.evaluateJavascript("window.__lr_scrollToPercent && window.__lr_scrollToPercent($pct)", null)
                applyTheme(view, latestSettings.value, latestColors.value, latestDensity.value)
            }
        }
    }

    // 更新 JS 桥回调（每次重组后指向最新状态）
    SideEffect {
        bridge.onScroll = { p ->
            onSaveProgress(latestChapter.value, (p.coerceIn(0.0, 1.0) * 1000).toLong(), 0, 1f, false)
        }
        bridge.onTapZone = { zone ->
            when (zone) {
                "center" -> onToggleMenu()
                "left" -> webViewState.value?.evaluateJavascript(
                    "window.__lr_scrollByPage && window.__lr_scrollByPage(-1)", null
                )
                "right" -> webViewState.value?.evaluateJavascript(
                    "window.__lr_scrollByPage && window.__lr_scrollByPage(1)", null
                )
            }
        }
    }

    // 跳转（跨章节 => 重载；同章节 => 滚动）
    LaunchedEffect(Unit) {
        jump.collect { jr ->
            if (jr.chapterIndex != currentChapter) {
                pendingPercent = if (jr.charOffset > 0) jr.charOffset / 1000f else null
                onChapterChanged(jr.chapterIndex)
            } else {
                val pct = if (jr.charOffset > 0) jr.charOffset / 1000f else 0f
                webViewState.value?.evaluateJavascript(
                    "window.__lr_scrollToPercent && window.__lr_scrollToPercent($pct)", null
                )
            }
        }
    }

    val themeKey = remember(settings, colors) { settings.hashCode() * 31 + colors.background.toArgb() }
    var appliedThemeKey by remember { mutableIntStateOf(-1) }
    var loadedHref by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val wv = WebView(ctx)
            wv.settings.javaScriptEnabled = true
            wv.settings.allowFileAccess = false
            wv.settings.allowContentAccess = false
            wv.settings.domStorageEnabled = true
            wv.setBackgroundColor(AndroidColor.TRANSPARENT)
            wv.addJavascriptInterface(bridge, "AndroidReader")
            wv.webViewClient = webViewClient
            webViewState.value = wv
            wv
        },
        update = { webView ->
            webView.setBackgroundColor(colors.background.toArgb())
            val target = href?.let { "https://${HtmlResourceExtractor.ASSET_HOST}/books/$it" }
            if (target != null && loadedHref != href) {
                webView.loadUrl(target)
                loadedHref = href
            }
            if (appliedThemeKey != themeKey) {
                applyTheme(webView, settings, colors, density)
                appliedThemeKey = themeKey
            }
        }
    )
}

private fun applyTheme(view: WebView, settings: ReaderSettings, colors: ReadingColors, density: Density) {
    val fontSizePx = with(density) { settings.fontSize.sp.toPx() }
    val paragraphPx = with(density) { settings.paragraphSpacing.dp.toPx() }
    val paddingPx = with(density) { settings.pagePadding.dp.toPx() }
    val css = HtmlResourceExtractor.buildThemeCss(
        fontSizePx = fontSizePx,
        lineHeight = settings.lineHeight,
        paragraphSpacingPx = paragraphPx,
        pagePaddingPx = paddingPx,
        textColor = colors.textHex(),
        bgColor = colors.backgroundHex(),
        fontFamilyCss = settings.fontFamily.cssValue
    )
    view.evaluateJavascript("window.__lr_setStyle && window.__lr_setStyle(${JSONObject.quote(css)})", null)
}
