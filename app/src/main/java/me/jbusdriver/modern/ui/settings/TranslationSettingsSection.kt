package me.jbusdriver.modern.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.data.translation.LIBRE_TRANSLATE_DEFAULT_URL
import me.jbusdriver.modern.data.translation.LanguageCatalog
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.data.translation.TranslatorConfig
import me.jbusdriver.modern.ui.components.SelectableDropdownItem

/**
 * 设置页"翻譯"分组:供应商选择 + 各家凭据表单(儲存/測試翻譯)、
 * 自动翻译开关、目标语言(跟隨系統 + 目录)。Adapted from komi-store (Apache-2.0).
 */
@Composable
internal fun TranslationSettingsCard(
    translateByDefault: Boolean,
    provider: TranslationProvider,
    targetLang: String,
    effectiveTargetLang: String,
    youdaoAppKey: String,
    youdaoAppSecret: String,
    libreBaseUrl: String,
    libreApiKey: String,
    deeplAuthKey: String,
    microsoftKey: String,
    microsoftRegion: String,
    testRunning: Boolean,
    onTranslateByDefaultChange: (Boolean) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
    onTargetLangChange: (String) -> Unit,
    onSaveYoudao: (appKey: String, appSecret: String) -> Unit,
    onSaveLibre: (baseUrl: String, apiKey: String) -> Unit,
    onSaveDeepl: (key: String) -> Unit,
    onSaveMicrosoft: (key: String, region: String) -> Unit,
    onTest: (TranslatorConfig) -> Unit,
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
            ProviderRow(provider = provider, onProviderChange = onProviderChange)
            when (provider) {
                TranslationProvider.GOOGLE -> GoogleForm(testRunning, effectiveTargetLang, onTest, provider)
                TranslationProvider.YOUDAO -> YoudaoForm(youdaoAppKey, youdaoAppSecret, testRunning, effectiveTargetLang, onSaveYoudao, onTest, provider)
                TranslationProvider.LIBRE -> LibreForm(libreBaseUrl, libreApiKey, testRunning, effectiveTargetLang, onSaveLibre, onTest, provider)
                TranslationProvider.DEEPL -> DeeplForm(deeplAuthKey, testRunning, effectiveTargetLang, onSaveDeepl, onTest, provider)
                TranslationProvider.MICROSOFT -> MicrosoftForm(microsoftKey, microsoftRegion, testRunning, effectiveTargetLang, onSaveMicrosoft, onTest, provider)
            }
            TargetLangRow(targetLang = targetLang, onTargetLangChange = onTargetLangChange)
        }
    }
}

@Composable
private fun ProviderRow(provider: TranslationProvider, onProviderChange: (TranslationProvider) -> Unit) {
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

@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun TestButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) {
        Text(stringResource(R.string.settings_translation_test))
    }
}

@Composable
private fun GoogleForm(
    testRunning: Boolean,
    effectiveTarget: String,
    onTest: (TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    FormColumn {
        Text(
            stringResource(R.string.settings_translation_builtin_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TestButton(enabled = !testRunning) {
            onTest(TranslatorConfig(provider, effectiveTarget))
        }
    }
}

@Composable
private fun YoudaoForm(
    savedKey: String, savedSecret: String, testRunning: Boolean, effectiveTarget: String,
    onSave: (String, String) -> Unit,
    onTest: (TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var secret by remember(savedSecret) { mutableStateOf(savedSecret) }
    FormColumn {
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.settings_translation_youdao_app_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text(stringResource(R.string.settings_translation_youdao_app_secret)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TestButton(enabled = !testRunning) {
            onTest(TranslatorConfig(provider, effectiveTarget, youdaoAppKey = key.trim(), youdaoAppSecret = secret.trim()))
        }
        Button(
            onClick = { onSave(key.trim(), secret.trim()) },
            enabled = key.isNotBlank() && secret.isNotBlank(),
        ) { Text(stringResource(R.string.settings_translation_save)) }
    }
}

@Composable
private fun LibreForm(
    savedUrl: String, savedApiKey: String, testRunning: Boolean, effectiveTarget: String,
    onSave: (String, String) -> Unit,
    onTest: (TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var apiKey by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    FormColumn {
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.settings_translation_libre_base_url)) }, placeholder = { Text(LIBRE_TRANSLATE_DEFAULT_URL) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(stringResource(R.string.settings_translation_libre_api_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TestButton(enabled = !testRunning) {
            onTest(TranslatorConfig(provider, effectiveTarget, libreBaseUrl = url.trim().ifBlank { LIBRE_TRANSLATE_DEFAULT_URL }, libreApiKey = apiKey.trim()))
        }
        Button(onClick = { onSave(url.trim(), apiKey.trim()) }) { Text(stringResource(R.string.settings_translation_save)) }
    }
}

@Composable
private fun DeeplForm(
    savedKey: String, testRunning: Boolean, effectiveTarget: String,
    onSave: (String) -> Unit,
    onTest: (TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    val context = LocalContext.current
    FormColumn {
        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.deepl.com/pro-api"))) } }) {
            Text(stringResource(R.string.settings_translation_deepl_get_key))
        }
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.settings_translation_deepl_auth_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TestButton(enabled = !testRunning) {
            onTest(TranslatorConfig(provider, effectiveTarget, deeplAuthKey = key.trim()))
        }
        Button(onClick = { onSave(key.trim()) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.settings_translation_save)) }
    }
}

@Composable
private fun MicrosoftForm(
    savedKey: String, savedRegion: String, testRunning: Boolean, effectiveTarget: String,
    onSave: (String, String) -> Unit,
    onTest: (TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var region by remember(savedRegion) { mutableStateOf(savedRegion) }
    val context = LocalContext.current
    FormColumn {
        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://portal.azure.com/#create/Microsoft.CognitiveServicesTextTranslation"))) } }) {
            Text(stringResource(R.string.settings_translation_microsoft_get_key))
        }
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.settings_translation_microsoft_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text(stringResource(R.string.settings_translation_microsoft_region)) }, placeholder = { Text("global") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        TestButton(enabled = !testRunning) {
            onTest(TranslatorConfig(provider, effectiveTarget, microsoftKey = key.trim(), microsoftRegion = region.trim()))
        }
        Button(onClick = { onSave(key.trim(), region.trim()) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.settings_translation_save)) }
    }
}

@Composable
private fun TargetLangRow(targetLang: String, onTargetLangChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.settings_translation_target), style = MaterialTheme.typography.bodyLarge)
        Box {
            val label = if (targetLang.isBlank()) {
                stringResource(R.string.settings_translation_target_follow_system)
            } else {
                LanguageCatalog.findByCode(targetLang)?.displayName ?: targetLang
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SelectableDropdownItem(
                    label = stringResource(R.string.settings_translation_target_follow_system),
                    selected = targetLang.isBlank(),
                    onClick = { onTargetLangChange(""); expanded = false }
                )
                LanguageCatalog.all.forEach { lang ->
                    SelectableDropdownItem(
                        label = lang.displayName,
                        selected = lang.code == targetLang,
                        onClick = { onTargetLangChange(lang.code); expanded = false }
                    )
                }
            }
        }
    }
}
