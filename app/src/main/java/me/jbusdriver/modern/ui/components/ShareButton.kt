package me.jbusdriver.modern.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.jbusdriver.R

fun shareText(context: Context, text: String, chooserTitle: String = "") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

@Composable
fun ShareButton(
    text: String,
    modifier: Modifier = Modifier,
    chooserTitle: String = stringResource(R.string.share)
) {
    val context = LocalContext.current
    IconButton(
        onClick = { shareText(context, text, chooserTitle) },
        modifier = modifier
    ) {
        Icon(
            painterResource(R.drawable.share_24px),
            contentDescription = stringResource(R.string.share),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
