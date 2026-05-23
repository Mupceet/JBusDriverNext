package me.jbusdriver.modern.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.jbusdriver.modern.JBus
import javax.inject.Inject
import javax.inject.Singleton

enum class ForumMode { NATIVE, WEBVIEW }

@Singleton
class LabSettingsStore @Inject constructor() {
    private val prefs: SharedPreferences =
        JBus.getSharedPreferences(PREFS_NAME, 0)

    private val _forumEnabled = MutableStateFlow(prefs.getBoolean(KEY_FORUM_ENABLED, false))
    val forumEnabled: StateFlow<Boolean> = _forumEnabled.asStateFlow()

    private val _forumMode = MutableStateFlow(
        try { ForumMode.valueOf(prefs.getString(KEY_FORUM_MODE, null) ?: ForumMode.NATIVE.name) }
        catch (_: Exception) { ForumMode.NATIVE }
    )
    val forumMode: StateFlow<ForumMode> = _forumMode.asStateFlow()

    fun setForumEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FORUM_ENABLED, enabled) }
        _forumEnabled.value = enabled
    }

    fun setForumMode(mode: ForumMode) {
        prefs.edit { putString(KEY_FORUM_MODE, mode.name) }
        _forumMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "lab_settings"
        private const val KEY_FORUM_ENABLED = "forum_enabled"
        private const val KEY_FORUM_MODE = "forum_mode"
    }
}
