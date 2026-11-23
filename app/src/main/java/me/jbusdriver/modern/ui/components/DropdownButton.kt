package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.jbusdriver.R

/**
 * 通用下拉选择按钮：32dp 瘦身款 OutlinedButton + 展开箭头，点开 [DropdownMenu] 单选。
 * 菜单项复用 [SelectableDropdownItem] 高亮当前选中项。
 *
 * 筛选弹窗头部的排序下拉（内容/顺序拆分双按钮）共用此组件。
 */
@Composable
fun <T> DropdownButton(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(label(selected), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(2.dp))
            Icon(
                painter = painterResource(R.drawable.arrow_drop_down_24px),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                SelectableDropdownItem(
                    label = label(option),
                    selected = option == selected,
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
