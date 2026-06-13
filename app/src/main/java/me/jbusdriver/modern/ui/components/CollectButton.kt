package me.jbusdriver.modern.ui.components

import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.jbusdriver.R

@Composable
fun CollectButton(
    isCollected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val collectedLabel = stringResource(R.string.uncollect_action)
    val uncollectedLabel = stringResource(R.string.collect)
    val successMsg = stringResource(R.string.collect_success)
    val uncollectMsg = stringResource(R.string.uncollect)
    IconButton(
        onClick = {
            onToggle()
            val msg = if (!isCollected) successMsg else uncollectMsg
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(if (isCollected) R.drawable.favorite_fill_24px else R.drawable.favorite_24px),
            contentDescription = if (isCollected) collectedLabel else uncollectedLabel,
            tint = if (isCollected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
