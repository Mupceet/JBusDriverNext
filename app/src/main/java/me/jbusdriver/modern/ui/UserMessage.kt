package me.jbusdriver.modern.ui

/** ViewModel 发给 UI 的一次性用户消息（Toast/Snackbar）。 */
data class UserMessage(val resId: Int, val args: List<Any> = emptyList())
