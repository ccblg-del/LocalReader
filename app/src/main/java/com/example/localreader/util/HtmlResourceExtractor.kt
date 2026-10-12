package com.example.localreader.util

import java.io.File

/**
 * HTML 资源提取/重写：
 * - 屏蔽所有网络资源（http/https/协议相对/javascript）
 * - 把相对资源路径解析为 WebViewAssetLoader 的本地 URL
 * - 注入基础 CSS 与 JS 桥接脚本（进度上报、点击翻页、主题）
 */
object HtmlResourceExtractor {

    /** WebViewAssetLoader 的授权域名，映射到 files/books */
    const val ASSET_HOST = "appassets.androidplatform.net"

    private const val BRIDGE_SCRIPT = """
<script>
(function(){
  if (window.__lr_bridge_installed) return;
  window.__lr_bridge_installed = true;
  function zone(x){
    var w = window.innerWidth || document.documentElement.clientWidth;
    if (x < w/3) return 'left';
    if (x > w*2/3) return 'right';
    return 'center';
  }
  document.addEventListener('touchend', function(e){
    var t = e.changedTouches && e.changedTouches[0];
    if (!t) return;
    if (e.target && e.target.closest && e.target.closest('a[href]')) return;
    if (window.AndroidReader) window.AndroidReader.onTapZone(zone(t.clientX));
  }, {passive:true});
  function report(){
    var h = document.documentElement.scrollHeight - window.innerHeight;
    var p = h > 0 ? window.scrollY / h : 0;
    if (window.AndroidReader) window.AndroidReader.onScroll(p);
  }
  var ticking = false;
  window.addEventListener('scroll', function(){
    if (!ticking){ ticking = true; requestAnimationFrame(function(){ report(); ticking = false; }); }
  }, {passive:true});
  window.addEventListener('load', function(){ report(); });
  window.__lr_scrollByPage = function(dir){ window.scrollBy({top: dir * (window.innerHeight * 0.9), behavior:'smooth'}); };
  window.__lr_scrollToPercent = function(p){
    var h = document.documentElement.scrollHeight - window.innerHeight;
    window.scrollTo(0, h * p);
  };
  window.__lr_setStyle = function(css){
    var s = document.getElementById('__lr_theme');
    if (!s){ s = document.createElement('style'); s.id='__lr_theme'; document.head.appendChild(s); }
    s.textContent = css;
  };
})();
</script>
"""

    private const val BASE_CSS = """
<style>
  img, svg, image { max-width: 100% !important; height: auto !important; }
  body { margin: 0; word-break: break-word; }
</style>
"""

    /**
     * 处理一章 HTML：包裹为完整文档、加 charset、屏蔽网络资源、注入桥接。
     */
    fun processChapter(html: String): String {
        var out = html
        if (!out.contains("<html", ignoreCase = true)) {
            out = "<html><head><meta charset=\"UTF-8\"></head><body>" + out + "</body></html>"
        } else if (!out.contains("charset", ignoreCase = true)) {
            out = injectHead(out, "<meta charset=\"UTF-8\">")
        }
        out = neutralizeNetworkUrls(out)
        out = injectHead(out, BASE_CSS + BRIDGE_SCRIPT)
        return out
    }

    /** 屏蔽网络资源：src/href 指向 http/https//、srcset、CSS 中的网络 url() */
    fun neutralizeNetworkUrls(html: String): String {
        var out = html
        out = Regex(
            """(src|href)\s*=\s*(["'])(https?:)?//[^"']*(["'])""",
            RegexOption.IGNORE_CASE
        ).replace(out, "$1=\"\"")
        out = Regex("""srcset\s*=\s*(["'])[^"']*(["'])""", RegexOption.IGNORE_CASE).replace(out, "")
        out = Regex(
            """url\(\s*['"]?(https?:)?//[^)'"]*['"]?\s*\)""",
            RegexOption.IGNORE_CASE
        ).replace(out, "url(about:blank)")
        return out
    }

    /**
     * 把章节 HTML 中的相对资源引用（img/link/a 的 src、href）解析为本地资产 URL。
     * 返回处理后的 HTML。会屏蔽网络/脚本引用、保留 data URI 与锚点。
     */
    fun resolveChapterResources(
        html: String,
        baseDir: File,
        bookDir: File,
        bookRelativeDir: String
    ): String {
        val regex = Regex(
            """\b(xlink:href|src|href)\s*=\s*(["'])([^"']*)(["'])""",
            RegexOption.IGNORE_CASE
        )
        return regex.replace(html) { m ->
            val attr = m.groupValues[1]
            val openQuote = m.groupValues[2]
            val value = m.groupValues[3]
            val closeQuote = m.groupValues[4]
            val newValue = rewriteValue(value, baseDir, bookDir, bookRelativeDir)
            "$attr=$openQuote$newValue$closeQuote"
        }
    }

    private fun rewriteValue(value: String, baseDir: File, bookDir: File, bookRelativeDir: String): String {
        val v = value.trim()
        if (v.isEmpty() || v.startsWith("#")) return v
        val lower = v.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//") ||
            lower.startsWith("javascript:") || lower.startsWith("mailto:")
        ) {
            return "" // 屏蔽网络与脚本
        }
        if (lower.startsWith("data:")) return v // 保留内嵌 data URI
        val candidate = File(baseDir, v)
        return resolveAssetUrl(bookDir, bookRelativeDir, candidate) ?: v
    }

    /** 将本地文件映射为 WebViewAssetLoader URL；文件不在书籍目录内时返回 null */
    fun resolveAssetUrl(bookDir: File, bookRelativeDir: String, candidate: File): String? {
        return try {
            val canon = candidate.canonicalFile
            val base = bookDir.canonicalFile
            if (canon != base && !canon.path.startsWith(base.path + File.separator)) return null
            val rel = canon.relativeTo(base).invariantSeparatorsPath
            "https://$ASSET_HOST/books/$bookRelativeDir/$rel"
        } catch (e: Exception) {
            null
        }
    }

    /** 在 </head> 之前插入片段；没有 head 则创建 */
    fun injectHead(html: String, fragment: String): String {
        val headEnd = html.lowercase().indexOf("</head>")
        return if (headEnd >= 0) {
            html.substring(0, headEnd) + fragment + html.substring(headEnd)
        } else {
            val htmlOpen = html.lowercase().indexOf("<html")
            val insertAt = if (htmlOpen >= 0) html.lowercase().indexOf(">", htmlOpen) + 1 else 0
            html.substring(0, insertAt) + "<head>" + fragment + "</head>" + html.substring(insertAt)
        }
    }

    /** 生成阅读主题 CSS（颜色为 #RRGGBB 字符串） */
    fun buildThemeCss(
        fontSizePx: Float,
        lineHeight: Float,
        paragraphSpacingPx: Float,
        pagePaddingPx: Float,
        textColor: String,
        bgColor: String,
        fontFamilyCss: String
    ): String = """
        html, body { background: $bgColor !important; color: $textColor !important; }
        body {
            font-size: ${fontSizePx}px !important;
            line-height: $lineHeight !important;
            font-family: $fontFamilyCss, sans-serif !important;
            padding: ${pagePaddingPx}px !important;
            margin: 0 !important;
            word-break: break-word;
        }
        p { margin: 0 0 ${paragraphSpacingPx}px 0 !important; }
        img, svg, image { max-width: 100% !important; height: auto !important; }
        a { color: inherit !important; }
    """.trimIndent()
}
