package com.example.localreader.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

enum class ReaderTheme(val label: String) {
    DAY("日间"),
    NIGHT("夜间"),
    EYE("护眼")
}

enum class PageMode(val label: String) {
    SCROLL("滚动"),
    PAGED("翻页")
}

enum class FontFamilyOption(val label: String, val cssValue: String) {
    DEFAULT("系统默认", "sans-serif"),
    SERIF("衬线", "serif"),
    MONO("等宽", "monospace")
}

/**
 * 阅读设置，持久化到 DataStore Preferences。
 * backgroundColor == 0 表示跟随主题默认背景。
 */
data class ReaderSettings(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val paragraphSpacing: Float = 8f,
    val pagePadding: Int = 20,
    val theme: ReaderTheme = ReaderTheme.DAY,
    val backgroundColor: Long = 0L,
    val fontFamily: FontFamilyOption = FontFamilyOption.DEFAULT,
    val pageMode: PageMode = PageMode.SCROLL,
    val brightness: Float = 1f,
    val keepScreenOn: Boolean = true,
    val pdfNightMode: Boolean = false
)

private object Keys {
    val fontSize = floatPreferencesKey("fontSize")
    val lineHeight = floatPreferencesKey("lineHeight")
    val paragraphSpacing = floatPreferencesKey("paragraphSpacing")
    val pagePadding = intPreferencesKey("pagePadding")
    val theme = stringPreferencesKey("theme")
    val backgroundColor = longPreferencesKey("backgroundColor")
    val fontFamily = stringPreferencesKey("fontFamily")
    val pageMode = stringPreferencesKey("pageMode")
    val brightness = floatPreferencesKey("brightness")
    val keepScreenOn = booleanPreferencesKey("keepScreenOn")
    val pdfNightMode = booleanPreferencesKey("pdfNightMode")
}

fun Preferences.toReaderSettings(): ReaderSettings = ReaderSettings(
    fontSize = this[Keys.fontSize] ?: 18f,
    lineHeight = this[Keys.lineHeight] ?: 1.6f,
    paragraphSpacing = this[Keys.paragraphSpacing] ?: 8f,
    pagePadding = this[Keys.pagePadding] ?: 20,
    theme = runCatching { ReaderTheme.valueOf(this[Keys.theme] ?: "") }.getOrDefault(ReaderTheme.DAY),
    backgroundColor = this[Keys.backgroundColor] ?: 0L,
    fontFamily = runCatching { FontFamilyOption.valueOf(this[Keys.fontFamily] ?: "") }
        .getOrDefault(FontFamilyOption.DEFAULT),
    pageMode = runCatching { PageMode.valueOf(this[Keys.pageMode] ?: "") }.getOrDefault(PageMode.SCROLL),
    brightness = this[Keys.brightness] ?: 1f,
    keepScreenOn = this[Keys.keepScreenOn] ?: true,
    pdfNightMode = this[Keys.pdfNightMode] ?: false
)

fun MutablePreferences.writeReaderSettings(s: ReaderSettings) {
    this[Keys.fontSize] = s.fontSize
    this[Keys.lineHeight] = s.lineHeight
    this[Keys.paragraphSpacing] = s.paragraphSpacing
    this[Keys.pagePadding] = s.pagePadding
    this[Keys.theme] = s.theme.name
    this[Keys.backgroundColor] = s.backgroundColor
    this[Keys.fontFamily] = s.fontFamily.name
    this[Keys.pageMode] = s.pageMode.name
    this[Keys.brightness] = s.brightness
    this[Keys.keepScreenOn] = s.keepScreenOn
    this[Keys.pdfNightMode] = s.pdfNightMode
}
