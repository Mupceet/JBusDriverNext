package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.jbusdriver.R
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie_error.json"))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 0.8f
    )

    val errorColor = MaterialTheme.colorScheme.error.toArgb()
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, errorColor, "Circle", "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, errorColor, "ExclLine", "**"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, errorColor, "ExclDot", "**")
    )

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            dynamicProperties = dynamicProperties,
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
