package me.jbusdriver.modern.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ErrorViewAssetTest {
    @Test
    fun errorViewUsesEmptyLottieAssetWithoutStaleLayerBindings() {
        val source = File("src/main/java/me/jbusdriver/modern/ui/components/ErrorView.kt").readText()

        assertTrue(source.contains("""LottieCompositionSpec.Asset("Empty.json")"""))
        assertFalse(source.contains("rememberLottieDynamicProperty"))
        assertTrue(File("src/main/assets/Empty.json").isFile)
    }
}
