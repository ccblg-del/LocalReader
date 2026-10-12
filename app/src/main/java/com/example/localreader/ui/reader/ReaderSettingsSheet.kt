package com.example.localreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localreader.data.settings.PageMode
import com.example.localreader.data.settings.ReaderTheme
import com.example.localreader.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(onDismiss: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val s by vm.settings.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)

            Text("主题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { t ->
                    FilterChip(selected = s.theme == t, onClick = { vm.setTheme(t) }, label = { Text(t.label) })
                }
            }

            Text("翻页模式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageMode.entries.forEach { m ->
                    FilterChip(selected = s.pageMode == m, onClick = { vm.setPageMode(m) }, label = { Text(m.label) })
                }
            }

            SliderSetting("字号", s.fontSize, 12f, 30f) { vm.setFontSize(it) }
            SliderSetting("行距", s.lineHeight, 1.0f, 2.5f) { vm.setLineHeight(it) }
            SliderSetting("段距", s.paragraphSpacing, 0f, 40f) { vm.setParagraphSpacing(it) }
            SliderSetting("亮度", s.brightness, 0.1f, 1.0f) { vm.setBrightness(it) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("屏幕常亮", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = s.keepScreenOn, onCheckedChange = { vm.setKeepScreenOn(it) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PDF 夜间模式", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = s.pdfNightMode, onCheckedChange = { vm.setPdfNightMode(it) })
            }
        }
    }
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
