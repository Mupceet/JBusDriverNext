package me.jbusdriver.modern.ui.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import me.jbusdriver.R

/**
 * A [DropdownMenuItem] that highlights the currently selected option with a
 * trailing check icon and bold, primary-colored text. Shared by the settings
 * and forum dropdowns for a consistent selection affordance.
 */
@Composable
fun SelectableDropdownItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.check_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else null,
        onClick = onClick
    )
}
