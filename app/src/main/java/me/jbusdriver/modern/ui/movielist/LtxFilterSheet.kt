package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.R
import me.jbusdriver.modern.ui.components.DropdownButton

/**
 * LTX 篩選 Bottom Sheet。
 *
 * 布局参考复用 [CollectionFilterSheet]：标题居左、右上角为重置 + 排序双下拉
 * （內容/順序拆分，兩按鈕相鄰），筛选 chip 点击即时生效。
 * 分组：確認狀態、站內收錄（三态多选：已收錄/未收錄/未檢測）；排序入口在头部下拉。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LtxFilterSheet(
    sortField: LtxSortField,
    sortDirection: LtxSortDirection,
    statusFilter: LtxStatusFilter,
    resolveFilter: Set<LtxResolveFilter>,
    hasActiveFilters: Boolean,
    onSortFieldChange: (LtxSortField) -> Unit,
    onSortDirectionChange: (LtxSortDirection) -> Unit,
    onStatusFilterChange: (LtxStatusFilter) -> Unit,
    onToggleResolveFilter: (LtxResolveFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
        ) {
            // ── Header: title (left) + reset + sort field/direction (right) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.filter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasActiveFilters) {
                        TextButton(onClick = onReset) {
                            Text(stringResource(R.string.reset), fontSize = 12.sp)
                        }
                    }
                    // 排序內容/順序拆成兩個相鄰下拉，與收藏篩選彈窗一致
                    DropdownButton(
                        options = LtxSortField.entries.toList(),
                        selected = sortField,
                        label = { stringResource(it.labelRes) },
                        onSelect = onSortFieldChange
                    )
                    DropdownButton(
                        options = LtxSortDirection.entries.toList(),
                        selected = sortDirection,
                        label = { stringResource(it.labelRes) },
                        onSelect = onSortDirectionChange
                    )
                }
            }

            // ── 確認狀態 ──
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterSectionLabel(stringResource(R.string.ltx_filter_confirm_label))
                Spacer(Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LtxFilterChip(
                        label = stringResource(LtxStatusFilter.ALL.labelRes),
                        selected = statusFilter == LtxStatusFilter.ALL,
                        onClick = { onStatusFilterChange(LtxStatusFilter.ALL) }
                    )
                    LtxFilterChip(
                        label = stringResource(LtxStatusFilter.CONFIRMED.labelRes),
                        selected = statusFilter == LtxStatusFilter.CONFIRMED,
                        onClick = { onStatusFilterChange(LtxStatusFilter.CONFIRMED) }
                    )
                    LtxFilterChip(
                        label = stringResource(LtxStatusFilter.PENDING.labelRes),
                        selected = statusFilter == LtxStatusFilter.PENDING,
                        onClick = { onStatusFilterChange(LtxStatusFilter.PENDING) }
                    )
                }
            }

            Spacer(Modifier.padding(top = 16.dp))

            // ── 站內收錄（单一维度三态多选，默认隐藏未收錄） ──
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterSectionLabel(stringResource(R.string.ltx_site_label))
                Spacer(Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LtxResolveFilter.entries.forEach { state ->
                        LtxFilterChip(
                            label = stringResource(state.labelRes),
                            selected = state in resolveFilter,
                            onClick = { onToggleResolveFilter(state) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LtxFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}
