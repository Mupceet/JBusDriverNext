package me.jbusdriver.modern.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.ui.components.SelectableDropdownItem

/**
 * 设置页"翻譯"分组:默认翻译开关、接口选择、自定义模板/响应路径与试译。
 * 文本框在失焦时提交;无效模板(缺 {text})被 ViewModel 拒绝并 Toast 提示。
 */
@Composable
internal fun TranslationSettingsCard(
    translateByDefault: Boolean,
    provider: TranslationProvider,
    customUrlTemplate: String,
    customResponsePath: String,
    testRunning: Boolean,
    onTranslateByDefaultChange: (Boolean) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
    onCustomUrlTemplateChange: (String) -> Unit,
    onCustomResponsePathChange: (String) -> Unit,
    onTest: (template: String, responsePath: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column {
            Text(
                stringResource(R.string.settings_translation_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp)
            )
            SwitchRow(
                label = stringResource(R.string.settings_translation_default),
                checked = translateByDefault,
                onCheckedChange = onTranslateByDefaultChange
            )
            TranslationProviderRow(provider = provider, onProviderChange = onProviderChange)
            if (provider == TranslationProvider.CUSTOM) {
                CustomEndpointFields(
                    initialTemplate = customUrlTemplate,
                    initialResponsePath = customResponsePath,
                    testRunning = testRunning,
                    onTemplateChange = onCustomUrlTemplateChange,
                    onResponsePathChange = onCustomResponsePathChange,
                    onTest = onTest
                )
            }
        }
    }
}

@Composable
private fun TranslationProviderRow(
    provider: TranslationProvider,
    onProviderChange: (TranslationProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.settings_translation_provider), style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(
                providerLabel(provider),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TranslationProvider.entries.forEach { candidate ->
                    SelectableDropdownItem(
                        label = providerLabel(candidate),
                        selected = candidate == provider,
                        onClick = {
                            onProviderChange(candidate)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun providerLabel(provider: TranslationProvider): String = when (provider) {
    TranslationProvider.GOOGLE -> stringResource(R.string.settings_translation_provider_builtin)
    TranslationProvider.CUSTOM -> stringResource(R.string.settings_translation_provider_custom)
}

@Composable
private fun CustomEndpointFields(
    initialTemplate: String,
    initialResponsePath: String,
    testRunning: Boolean,
    onTemplateChange: (String) -> Unit,
    onResponsePathChange: (String) -> Unit,
    onTest: (template: String, responsePath: String) -> Unit,
) {
    var template by remember(initialTemplate) { mutableStateOf(initialTemplate) }
    var responsePath by remember(initialResponsePath) { mutableStateOf(initialResponsePath) }
    Column(
        modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = template,
            onValueChange = { template = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (!state.isFocused && template != initialTemplate) onTemplateChange(template.trim())
                },
            label = { Text(stringResource(R.string.settings_translation_url_template)) },
            placeholder = { Text(stringResource(R.string.settings_translation_url_template_hint)) },
            singleLine = true
        )
        OutlinedTextField(
            value = responsePath,
            onValueChange = { responsePath = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (!state.isFocused && responsePath != initialResponsePath) onResponsePathChange(responsePath.trim())
                },
            label = { Text(stringResource(R.string.settings_translation_response_path)) },
            placeholder = { Text(stringResource(R.string.settings_translation_response_path_hint)) },
            singleLine = true
        )
        Button(
            onClick = { onTest(template.trim(), responsePath.trim()) },
            enabled = !testRunning
        ) {
            Text(stringResource(R.string.settings_translation_test))
        }
    }
}
