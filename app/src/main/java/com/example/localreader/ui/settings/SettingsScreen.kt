package com.example.localreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localreader.data.settings.FontFamilyOption
import com.example.localreader.data.settings.PageMode
import com.example.localreader.data.settings.ReaderSettings
import com.example.localreader.data.settings.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val s by vm.settings.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SectionTitle("主题")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { t ->
                    FilterChip(selected = s.theme == t, onClick = { vm.setTheme(t) }, label = { Text(t.label) })
                }
            }

            SectionTitle("字体")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontFamilyOption.entries.forEach { f ->
                    FilterChip(selected = s.fontFamily == f, onClick = { vm.setFontFamily(f) }, label = { Text(f.label) })
                }
            }

            SliderSetting("字号", s.fontSize, 12f, 30f, onChanged = { vm.setFontSize(it) })
            SliderSetting("行距", s.lineHeight, 1.0f, 2.5f, onChanged = { vm.setLineHeight(it) })
            SliderSetting("段距", s.paragraphSpacing, 0f, 40f, onChanged = { vm.setParagraphSpacing(it) })
            SliderSetting("页边距", s.pagePadding.toFloat(), 8f, 64f, onChanged = { vm.setPagePadding(it.toInt()) })
            SliderSetting("亮度", s.brightness, 0.1f, 1.0f, onChanged = { vm.setBrightness(it) })

            SectionTitle("翻页")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageMode.entries.forEach { m ->
                    FilterChip(selected = s.pageMode == m, onClick = { vm.setPageMode(m) }, label = { Text(m.label) })
                }
            }

            SwitchSetting("屏幕常亮", s.keepScreenOn) { vm.setKeepScreenOn(it) }
            SwitchSetting("PDF 夜间模式", s.pdfNightMode) { vm.setPdfNightMode(it) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SliderSetting(label: String, value: Float, min: Float, max: Float, onChanged: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(String.format("%.1f", value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onChanged, valueRange = min..max)
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
