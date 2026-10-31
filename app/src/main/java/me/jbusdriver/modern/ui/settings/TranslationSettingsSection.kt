package me.jbusdriver.modern.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.ui.components.SelectableDropdownItem

/**
 * 设置页"翻譯"分组:默认翻译开关与接口选择。
 * 各供应商凭据表单与目标语言由后续任务接入(Task 4)。
 */
@Composable
internal fun TranslationSettingsCard(
    translateByDefault: Boolean,
    provider: TranslationProvider,
    onTranslateByDefaultChange: (Boolean) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
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
            if (provider == TranslationProvider.GOOGLE) {
                Text(
                    stringResource(R.string.settings_translation_builtin_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
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
    TranslationProvider.YOUDAO -> stringResource(R.string.settings_translation_provider_youdao)
    TranslationProvider.LIBRE -> stringResource(R.string.settings_translation_provider_libre)
    TranslationProvider.DEEPL -> stringResource(R.string.settings_translation_provider_deepl)
    TranslationProvider.MICROSOFT -> stringResource(R.string.settings_translation_provider_microsoft)
}
