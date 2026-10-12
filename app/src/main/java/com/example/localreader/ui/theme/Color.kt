package com.example.localreader.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.localreader.data.settings.ReaderTheme

// ---- App 主题色 ----
val Primary = Color(0xFF3F6FB5)
val PrimaryDark = Color(0xFF2C5282)
val Secondary = Color(0xFF7C9BC7)
val BackgroundLight = Color(0xFFFAFAFC)
val BackgroundDark = Color(0xFF141414)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1E1E1E)

// ---- 阅读主题配色 ----
data class ReadingColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color
) {
    /** 转成 #RRGGBB 十六进制，供 WebView CSS 使用 */
    fun backgroundHex(): String = background.toHex()
    fun textHex(): String = text.toHex()
}

fun Color.toHex(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

object ReadingPalette {

    fun forTheme(theme: ReaderTheme): ReadingColors = when (theme) {
        ReaderTheme.DAY -> ReadingColors(
            background = Color(0xFFF7F4EC),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF2B2B2B),
            textSecondary = Color(0xFF6B6B6B),
            accent = Color(0xFF3F6FB5)
        )
        ReaderTheme.NIGHT -> ReadingColors(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            text = Color(0xFFA8A8A8),
            textSecondary = Color(0xFF6E6E6E),
            accent = Color(0xFF7C9BC7)
        )
        ReaderTheme.EYE -> ReadingColors(
            background = Color(0xFFCCE8CF),
            surface = Color(0xFFD9EFDB),
            text = Color(0xFF2E3A2E),
            textSecondary = Color(0xFF5C6B5C),
            accent = Color(0xFF4C7A50)
        )
    }
}
