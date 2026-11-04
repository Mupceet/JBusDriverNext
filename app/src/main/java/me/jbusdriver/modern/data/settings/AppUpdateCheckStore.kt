package me.jbusdriver.modern.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记录「上次成功检查更新」的时间，供自动检查做节流（进程重启后仍生效）。
 */
interface AppUpdateCheckStore {
    /** 上次成功检查的墙钟时间；从未检查过时为 0。 */
    fun lastSuccessfulCheckAt(): Long

    /** 记录一次成功检查（已是最新或发现新版本都算成功）。 */
    fun recordSuccessfulCheck(atMs: Long)
}

@Singleton
class DefaultAppUpdateCheckStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppUpdateCheckStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun lastSuccessfulCheckAt(): Long = prefs.getLong(KEY_LAST_SUCCESS_AT, 0L)

    override fun recordSuccessfulCheck(atMs: Long) {
        prefs.edit { putLong(KEY_LAST_SUCCESS_AT, atMs) }
    }

    companion object {
        private const val PREFS_NAME = "app_update_check"
        private const val KEY_LAST_SUCCESS_AT = "last_success_at"
    }
}
