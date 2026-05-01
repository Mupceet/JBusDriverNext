package me.jbusdriver.modern.ui

/**
 * 职责：应用唯一的 Activity 入口，承载所有 Compose UI
 *
 * 使用场景：AndroidManifest 中声明的 launcher Activity，
 * 通过 setContent 设置 Compose 根组件 JBusNavigation
 *
 * 线程：生命周期方法在主线程
 */

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import me.jbusdriver.modern.ui.theme.JBusTheme

@AndroidEntryPoint
class ModernMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JBusTheme {
                JBusNavigation()
            }
        }
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, ModernMainActivity::class.java)
            context.startActivity(intent)
        }
    }
}
