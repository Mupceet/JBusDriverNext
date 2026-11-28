package me.jbusdriver.modern.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.forumAccountDataStore by preferencesDataStore("forum_account")

/**
 * 论坛账号展示缓存：登录成功后保存 uid/用户名/头像/用户组，
 * 供账号状态条在服务端登录态刷新前先渲染（秒显）。
 *
 * 注意：登录凭证只有 auth cookie（SessionCookieStore 追踪），这里不存任何敏感数据。
 */
interface ForumAccountCache {
    data class CachedAccount(
        val uid: Int,
        val username: String,
        val avatarUrl: String?,
        val groupName: String?
    )

    val cached: Flow<CachedAccount?>

    suspend fun save(account: CachedAccount)

    suspend fun clear()
}

@Singleton
class ForumAccountStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ForumAccountCache {

    private val dataStore = context.forumAccountDataStore

    override val cached: Flow<ForumAccountCache.CachedAccount?> = dataStore.data.map { prefs ->
        val uid = prefs[KEY_UID] ?: return@map null
        ForumAccountCache.CachedAccount(
            uid = uid,
            username = prefs[KEY_USERNAME].orEmpty(),
            avatarUrl = prefs[KEY_AVATAR],
            groupName = prefs[KEY_GROUP]
        )
    }

    override suspend fun save(account: ForumAccountCache.CachedAccount) {
        dataStore.edit {
            it[KEY_UID] = account.uid
            it[KEY_USERNAME] = account.username
            if (account.avatarUrl != null) it[KEY_AVATAR] = account.avatarUrl else it.remove(KEY_AVATAR)
            if (account.groupName != null) it[KEY_GROUP] = account.groupName else it.remove(KEY_GROUP)
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_UID = intPreferencesKey("uid")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_AVATAR = stringPreferencesKey("avatar_url")
        private val KEY_GROUP = stringPreferencesKey("group_name")
    }
}
