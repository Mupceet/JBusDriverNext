package me.jbusdriver.modern.ui

import android.annotation.SuppressLint
import android.content.Context

/** ViewModel 发给 UI 的一次性用户消息（Toast/Snackbar）。resId = 0 时为原生文本消息。 */
data class UserMessage(val resId: Int, val args: List<Any> = emptyList()) {

    @SuppressLint("LocalContextGetResourceValueCall")
    fun format(context: Context): String {
        if (resId == 0) return args.firstOrNull()?.toString() ?: ""
        val isPlural = try {
            context.resources.getResourceTypeName(resId) == "plurals"
        } catch (_: android.content.res.Resources.NotFoundException) {
            false
        }
        return if (isPlural) {
            val quantity = (args.firstOrNull() as? Number)?.toInt() ?: 1
            context.resources.getQuantityString(resId, quantity, *args.toTypedArray())
        } else {
            context.getString(resId, *args.toTypedArray())
        }
    }

    companion object {
        /** 原生文本消息（服务端返回的站点文案等动态内容，不走资源 id）。 */
        fun ofText(text: String): UserMessage = UserMessage(resId = 0, args = listOf(text))
    }
}
