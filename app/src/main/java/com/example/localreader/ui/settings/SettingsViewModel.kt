package com.example.localreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localreader.data.settings.FontFamilyOption
import com.example.localreader.data.settings.PageMode
import com.example.localreader.data.settings.ReaderSettings
import com.example.localreader.data.settings.ReaderTheme
import com.example.localreader.di.AppGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val repository = AppGraph.settingsRepository

    val settings: StateFlow<ReaderSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    fun setFontSize(v: Float) = launch { repository.setFontSize(v) }
    fun setLineHeight(v: Float) = launch { repository.setLineHeight(v) }
    fun setParagraphSpacing(v: Float) = launch { repository.setParagraphSpacing(v) }
    fun setPagePadding(v: Int) = launch { repository.setPagePadding(v) }
    fun setTheme(t: ReaderTheme) = launch { repository.setTheme(t) }
    fun setFontFamily(f: FontFamilyOption) = launch { repository.setFontFamily(f) }
    fun setPageMode(m: PageMode) = launch { repository.setPageMode(m) }
    fun setBrightness(v: Float) = launch { repository.setBrightness(v) }
    fun setKeepScreenOn(v: Boolean) = launch { repository.setKeepScreenOn(v) }
    fun setPdfNightMode(v: Boolean) = launch { repository.setPdfNightMode(v) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
