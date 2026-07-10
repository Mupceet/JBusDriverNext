package me.jbusdriver.modern.ui

import android.annotation.SuppressLint
import android.content.Context

/** ViewModel 发给 UI 的一次性用户消息（Toast/Snackbar）。 */
data class UserMessage(val resId: Int, val args: List<Any> = emptyList()) {

    @SuppressLint("LocalContextGetResourceValueCall")
    fun format(context: Context): String {
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
}
