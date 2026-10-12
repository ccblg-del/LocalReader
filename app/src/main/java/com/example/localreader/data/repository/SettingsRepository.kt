package com.example.localreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.localreader.data.settings.FontFamilyOption
import com.example.localreader.data.settings.PageMode
import com.example.localreader.data.settings.ReaderSettings
import com.example.localreader.data.settings.ReaderTheme
import com.example.localreader.data.settings.toReaderSettings
import com.example.localreader.data.settings.writeReaderSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader_settings")

/**
 * 阅读设置仓库，基于 DataStore Preferences。
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<ReaderSettings> = context.readerDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toReaderSettings() }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerDataStore.edit { prefs ->
            prefs.writeReaderSettings(transform(prefs.toReaderSettings()))
        }
    }

    suspend fun setFontSize(v: Float) = update { it.copy(fontSize = v) }
    suspend fun setLineHeight(v: Float) = update { it.copy(lineHeight = v) }
    suspend fun setParagraphSpacing(v: Float) = update { it.copy(paragraphSpacing = v) }
    suspend fun setPagePadding(v: Int) = update { it.copy(pagePadding = v) }
    suspend fun setTheme(t: ReaderTheme) = update { it.copy(theme = t) }
    suspend fun setBackgroundColor(v: Long) = update { it.copy(backgroundColor = v) }
    suspend fun setFontFamily(f: FontFamilyOption) = update { it.copy(fontFamily = f) }
    suspend fun setPageMode(m: PageMode) = update { it.copy(pageMode = m) }
    suspend fun setBrightness(v: Float) = update { it.copy(brightness = v) }
    suspend fun setKeepScreenOn(v: Boolean) = update { it.copy(keepScreenOn = v) }
    suspend fun setPdfNightMode(v: Boolean) = update { it.copy(pdfNightMode = v) }
}
