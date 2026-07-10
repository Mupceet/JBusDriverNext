package me.jbusdriver.modern.ui

import android.annotation.SuppressLint
import android.content.Context

/** ViewModel 发给 UI 的一次性用户消息（Toast/Snackbar）。 */
data class UserMessage(val resId: Int, val args: List<Any> = emptyList()) {

    @SuppressLint("LocalContextGetResourceValueCall")
    fun format(context: Context): String =
        if (args.isEmpty()) context.getString(resId)
        else {
            val q = (args.firstOrNull() as? Number)?.toInt() ?: 1
            context.resources.getQuantityString(resId, q, *args.toTypedArray())
        }
}
