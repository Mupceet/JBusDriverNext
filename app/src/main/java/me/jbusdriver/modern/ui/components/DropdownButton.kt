package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用下拉选择按钮：OutlinedButton + 展开箭头，点开 [DropdownMenu] 单选。
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
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Text(label(selected), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(2.dp))
            Text("▾", fontSize = 20.sp)
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
