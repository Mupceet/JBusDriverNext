package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.R
import me.jbusdriver.modern.ui.GenreCategory
import me.jbusdriver.modern.ui.GenreUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBottomSheet(
    categories: List<GenreCategory>,
    selectedGenres: Set<GenreUiModel>,
    onSelectionChange: (Set<GenreUiModel>) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMultiSelect by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(32.dp).height(4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp)
            ) {}
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("選擇類別", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedGenres.isNotEmpty()) {
                    Text(
                        "已選 ${selectedGenres.size}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(12.dp))
                }
                TextButton(onClick = { onSelectionChange(emptySet()) }) {
                    Text("重置", fontSize = 12.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("多選", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = isMultiSelect,
                onCheckedChange = {
                    isMultiSelect = it
                    if (!it && selectedGenres.size > 1) {
                        onSelectionChange(selectedGenres.lastOrNull()?.let { setOf(it) } ?: emptySet())
                    }
                },
                modifier = Modifier.height(24.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(categories, key = { it.title }) { group ->
                var expanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        group.title,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (expanded) "▲" else "▼",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        group.genres.forEach { genre ->
                            val isSelected = genre in selectedGenres
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val newSelection = if (isMultiSelect) {
                                        if (isSelected) selectedGenres - genre else selectedGenres + genre
                                    } else {
                                        if (isSelected) emptySet() else setOf(genre)
                                    }
                                    onSelectionChange(newSelection)
                                },
                                label = {
                                    Text(genre.name, fontSize = 12.sp)
                                    if (isSelected) {
                                        Spacer(Modifier.width(2.dp))
                                        Icon(
                                            painter = painterResource(R.drawable.check_24px),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("套用篩選", fontWeight = FontWeight.SemiBold)
        }
    }
}
