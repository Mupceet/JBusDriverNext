package me.jbusdriver.modern

import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers

/**
 * Debug 专用 Application 入口（仅在 debug 源集编译，release 不包含）。
 *
 * 背景：Compose 框架已知泄漏 —— Popup/DropdownMenu 关闭后，已 detach 的
 * PopupLayout 会被仍 attached 的 AndroidComposeView 内部 layoutNodes 记账结构
 * （或 LayoutNode.nodes 子节点表）持续持有，LeakCanary 因此将其误报为应用泄漏。
 * 该保留链路完全位于 Compose 框架内部，应用侧无法干预，业界（如 Mozilla Fenix
 * FenixReferenceMatchers 的 COMPOSE_POPUP_LAYOUT）通过 referenceMatcher 屏蔽。
 */
class DebugJBusApplication : JBusApplication() {

    override fun onCreate() {
        super.onCreate()
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults + listOf(
                AndroidReferenceMatchers.ignoredInstanceField(
                    className = "androidx.compose.ui.platform.AndroidComposeView",
                    fieldName = "layoutNodes",
                ),
                AndroidReferenceMatchers.ignoredInstanceField(
                    className = "androidx.compose.ui.node.LayoutNode",
                    fieldName = "nodes",
                ),
            )
        )
    }
}
