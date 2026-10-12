package com.example.localreader.parser

import com.example.localreader.domain.BookParser
import com.example.localreader.domain.ParsedBook
import com.example.localreader.domain.ParsedChapter
import com.example.localreader.util.CharsetUtils
import com.example.localreader.util.HtmlResourceExtractor
import com.example.localreader.util.ZipSlipSafeExtractor
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * EPUB 解析（自研，无额外依赖，完整支持 EPUB2/EPUB3）：
 * - 安全解压（防 Zip Slip）
 * - container.xml -> OPF -> spine
 * - NCX / nav.xhtml 目录
 * - 元数据：书名/作者/语言/出版社/简介/封面
 * - 章节 HTML 资源路径重写为本地资产 URL，屏蔽网络，注入桥接脚本
 */
class EpubParser : BookParser {

    override suspend fun parse(
        file: File,
        mimeType: String,
        resourceRoot: File,
        bookRelativeDir: String,
        originalFileName: String
    ): ParsedBook = withContext(Dispatchers.IO) {
        val contentDir = File(resourceRoot, "content").apply { mkdirs() }

        // 1. 安全解压
        ZipSlipSafeExtractor.extract(file, contentDir)

        // 2. container.xml -> OPF
        val opfRel = findOpfPath(contentDir)
        val opfFile = File(contentDir, opfRel)
        if (!opfFile.exists()) throw IOException("OPF 文件不存在: $opfRel")
        val opfDir = opfFile.parentFile ?: contentDir

        // 3. 解析 OPF
        val opf = parseOpf(opfFile)

        // 4. 目录
        val toc = parseToc(opf, opfDir)

        // 5. 章节处理
        val chaptersDir = File(resourceRoot, "content/chapters").apply { mkdirs() }
        val parsedChapters = mutableListOf<ParsedChapter>()
        opf.spine.forEachIndexed { i, idref ->
            val item = opf.manifest[idref] ?: return@forEachIndexed
            val href = decodeHref(item.href)
            val srcFile = runCatching { File(opfDir, href).canonicalFile }.getOrNull()
            if (srcFile == null || !srcFile.exists()) return@forEachIndexed

            val raw = readHtml(srcFile)
            val processed = processChapterHtml(raw, srcFile, resourceRoot, bookRelativeDir)
            val out = File(chaptersDir, "ch$i.html")
            out.writeText(processed)

            val title = toc[i] ?: extractHtmlTitle(raw) ?: "第${i + 1}章"
            parsedChapters.add(
                ParsedChapter(
                    title = title,
                    chapterIndex = i,
                    href = "$bookRelativeDir/content/chapters/ch$i.html"
                )
            )
        }
        if (parsedChapters.isEmpty()) throw IOException("EPUB 未解析到任何章节")

        // 6. 封面
        val coverRel = extractCover(opf, opfDir, resourceRoot, bookRelativeDir)

        ParsedBook(
            title = opf.title ?: originalFileName.substringBeforeLast('.'),
            author = opf.author ?: "",
            language = opf.language,
            publisher = opf.publisher,
            description = opf.description,
            coverRelativePath = coverRel,
            chapters = parsedChapters,
            totalChars = 0,
            totalPages = 0
        )
    }

    // ---- container.xml ----
    private fun findOpfPath(contentDir: File): String {
        val container = File(contentDir, "META-INF/container.xml")
        if (!container.exists()) throw IOException("EPUB 缺少 META-INF/container.xml")
        val doc = parseXml(container)
        val rootfiles = doc.getElementsByTagNameNS("*", "rootfile")
        for (i in 0 until rootfiles.length) {
            val el = rootfiles.item(i) as? Element ?: continue
            val path = el.getAttribute("full-path")
            if (path.isNotBlank()) return decodeHref(path)
        }
        throw IOException("container.xml 中未找到 OPF 路径")
    }

    // ---- OPF ----
    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )

    private data class Opf(
        val title: String?,
        val author: String?,
        val language: String?,
        val publisher: String?,
        val description: String?,
        val manifest: Map<String, ManifestItem>,
        val spine: List<String>,
        val coverId: String?,
        val ncxHref: String?
    )

    private fun parseOpf(opfFile: File): Opf {
        val doc = parseXml(opfFile)
        val title = firstText(doc, DC_NS, "title")
        val author = firstText(doc, DC_NS, "creator")
        val language = firstText(doc, DC_NS, "language")
        val publisher = firstText(doc, DC_NS, "publisher")
        val description = firstText(doc, DC_NS, "description")

        val manifest = LinkedHashMap<String, ManifestItem>()
        val itemNodes = doc.getElementsByTagNameNS("*", "item")
        for (i in 0 until itemNodes.length) {
            val el = itemNodes.item(i) as? Element ?: continue
            val id = el.getAttribute("id")
            val href = el.getAttribute("href")
            if (id.isBlank() || href.isBlank()) continue
            manifest[id] = ManifestItem(
                id = id,
                href = href,
                mediaType = el.getAttribute("media-type"),
                properties = el.getAttribute("properties")
            )
        }

        val spine = mutableListOf<String>()
        val itemrefNodes = doc.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until itemrefNodes.length) {
            val el = itemrefNodes.item(i) as? Element ?: continue
            val idref = el.getAttribute("idref")
            if (idref.isNotBlank()) spine.add(idref)
        }

        // 封面 id
        var coverId: String? = null
        val metaNodes = doc.getElementsByTagNameNS("*", "meta")
        for (i in 0 until metaNodes.length) {
            val el = metaNodes.item(i) as? Element ?: continue
            if (el.getAttribute("name") == "cover") {
                coverId = el.getAttribute("content")
                break
            }
        }
        if (coverId == null) {
            coverId = manifest.values.firstOrNull { "cover-image" in it.properties.split(" ") }?.id
        }

        // NCX href
        var ncxHref = manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }?.href
        if (ncxHref == null) {
            val spineNodes = doc.getElementsByTagNameNS("*", "spine")
            val spineEl = spineNodes.item(0) as? Element
            val tocId = spineEl?.getAttribute("toc")
            if (!tocId.isNullOrBlank()) ncxHref = manifest[tocId]?.href
        }

        return Opf(title, author, language, publisher, description, manifest, spine, coverId, ncxHref)
    }

    // ---- 目录 ----
    private fun parseToc(opf: Opf, opfDir: File): Map<Int, String> {
        opf.ncxHref?.let { href ->
            val ncx = runCatching { File(opfDir, decodeHref(href)).canonicalFile }.getOrNull()
            if (ncx != null && ncx.exists()) {
                val map = parseNcx(ncx, opf, opfDir)
                if (map.isNotEmpty()) return map
            }
        }
        val navItem = opf.manifest.values.firstOrNull { "nav" in it.properties.split(" ") }
        navItem?.let { item ->
            val nav = runCatching { File(opfDir, decodeHref(item.href)).canonicalFile }.getOrNull()
            if (nav != null && nav.exists()) {
                val map = parseNav(nav, opf, opfDir)
                if (map.isNotEmpty()) return map
            }
        }
        return emptyMap()
    }

    private fun parseNcx(ncxFile: File, opf: Opf, opfDir: File): Map<Int, String> {
        val doc = parseXml(ncxFile)
        val map = LinkedHashMap<Int, String>()
        val navPoints = doc.getElementsByTagNameNS("*", "navPoint")
        for (i in 0 until navPoints.length) {
            val np = navPoints.item(i) as? Element ?: continue
            val label = childText(np, "navLabel")
            val src = childAttr(np, "content", "src")
            if (src.isNullOrBlank()) continue
            val idx = srcToSpineIndex(src, opf, opfDir)
            if (idx >= 0 && label != null && !map.containsKey(idx)) map[idx] = label
        }
        return map
    }

    private fun parseNav(navFile: File, opf: Opf, opfDir: File): Map<Int, String> {
        val doc = parseXml(navFile)
        val map = LinkedHashMap<Int, String>()
        val navs = doc.getElementsByTagNameNS("*", "nav")
        for (i in 0 until navs.length) {
            val nav = navs.item(i) as? Element ?: continue
            val type = nav.getAttributeNS(OPS_NS, "type").ifBlank { nav.getAttribute("epub:type") }
            if (type != "toc") continue
            val anchors = nav.getElementsByTagNameNS("*", "a")
            for (j in 0 until anchors.length) {
                val a = anchors.item(j) as? Element ?: continue
                val href = a.getAttribute("href")
                val label = a.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                if (href.isBlank()) continue
                val idx = srcToSpineIndex(href, opf, opfDir)
                if (idx >= 0 && !map.containsKey(idx)) map[idx] = label
            }
            break
        }
        return map
    }

    private fun srcToSpineIndex(src: String, opf: Opf, opfDir: File): Int {
        val clean = src.substringBefore('#').trim()
        if (clean.isEmpty()) return -1
        val decoded = decodeHref(clean)
        val target = runCatching { File(opfDir, decoded).canonicalFile }.getOrNull() ?: return -1
        return opf.spine.indexOfFirst { idref ->
            val item = opf.manifest[idref] ?: return@indexOfFirst false
            runCatching { File(opfDir, decodeHref(item.href)).canonicalFile == target }.getOrDefault(false)
        }
    }

    // ---- 封面 ----
    private fun extractCover(opf: Opf, opfDir: File, resourceRoot: File, bookRelativeDir: String): String? {
        val coverId = opf.coverId ?: return null
        val item = opf.manifest[coverId] ?: return null
        val src = runCatching { File(opfDir, decodeHref(item.href.substringBefore('#'))).canonicalFile }.getOrNull()
            ?: return null
        if (!src.exists()) return null
        return runCatching {
            val ext = src.extension.ifBlank { "jpg" }
            val cover = File(resourceRoot, "cover.$ext")
            src.copyTo(cover, overwrite = true)
            "$bookRelativeDir/cover.$ext"
        }.getOrNull()
    }

    // ---- HTML 处理 ----
    private fun processChapterHtml(raw: String, srcFile: File, bookDir: File, bookRelativeDir: String): String {
        val baseDir = srcFile.parentFile ?: bookDir
        var html = HtmlResourceExtractor.resolveChapterResources(raw, baseDir, bookDir, bookRelativeDir)
        html = HtmlResourceExtractor.processChapter(html)
        return html
    }

    private fun readHtml(file: File): String {
        val bytes = file.readBytes()
        val head = String(bytes, 0, minOf(bytes.size, 300), Charsets.ISO_8859_1)
        val enc = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)
        val charset = if (enc != null) runCatching { Charset.forName(enc) }.getOrDefault(Charsets.UTF_8) else Charsets.UTF_8
        return CharsetUtils.decodeLenient(bytes, charset)
    }

    private fun extractHtmlTitle(html: String): String? {
        val m = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html) ?: return null
        val t = m.groupValues[1].trim()
        return t.takeIf { it.isNotEmpty() }
    }

    // ---- XML / 通用 ----
    private fun parseXml(file: File): Document {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        runCatching { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        dbf.isXIncludeAware = false
        runCatching { dbf.isExpandEntityReferences = false }
        return dbf.newDocumentBuilder().parse(file)
    }

    private fun firstText(doc: Document, ns: String, localName: String): String? {
        val list = doc.getElementsByTagNameNS(ns, localName)
        for (i in 0 until list.length) {
            val text = list.item(i)?.textContent?.trim()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    private fun childText(parent: Element, tag: String): String? =
        (parent.getElementsByTagNameNS("*", tag).item(0) as? Element)
            ?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun childAttr(parent: Element, tag: String, attr: String): String? =
        (parent.getElementsByTagNameNS("*", tag).item(0) as? Element)
            ?.getAttribute(attr)?.takeIf { it.isNotBlank() }

    private fun decodeHref(href: String): String =
        try {
            URLDecoder.decode(href, "UTF-8")
        } catch (e: Exception) {
            href
        }

    companion object {
        private const val DC_NS = "http://purl.org/dc/elements/1.1/"
        private const val OPS_NS = "http://www.idpf.org/2007/ops"
    }
}
