package me.jbusdriver.modern.data.localvideo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

private val Context.localVideoDataStore by preferencesDataStore("local_video")

/**
 * 本地视频文件夹偏好：保存 SAF tree URI、显示名、上次扫描时间，并管理其持久化读取权限。
 */
@Singleton
class LocalVideoFolderStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.localVideoDataStore

    val folderUri: Flow<String?> = dataStore.data.map { it[KEY_FOLDER_URI] }
    val folderDisplayName: Flow<String?> = dataStore.data.map { it[KEY_FOLDER_NAME] }
    val lastScannedAt: Flow<Long?> = dataStore.data.map { it[KEY_LAST_SCAN] }

    /** 是否在收藏页显示未收藏的本地视频（持久化设置）。 */
    val showUncollectedLocal: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_UNCOLLECTED] ?: false }

    suspend fun currentFolderUri(): String? =
        dataStore.data.first()[KEY_FOLDER_URI]

    /** 记录文件夹：申请持久化读权限，并写入 uri 与显示名。 */
    suspend fun setFolder(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val displayName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        dataStore.edit {
            it[KEY_FOLDER_URI] = uri.toString()
            if (displayName != null) it[KEY_FOLDER_NAME] = displayName
        }
    }

    suspend fun setLastScannedAt(epochMs: Long) {
        dataStore.edit { it[KEY_LAST_SCAN] = epochMs }
    }

    suspend fun setShowUncollectedLocal(value: Boolean) {
        dataStore.edit { it[KEY_SHOW_UNCOLLECTED] = value }
    }

    /** 清除文件夹：释放权限并删除全部 key。 */
    suspend fun clearFolder() = withContext(Dispatchers.IO) {
        currentFolderUri()?.let { uriStr ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uriStr.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        dataStore.edit {
            it.remove(KEY_FOLDER_URI)
            it.remove(KEY_FOLDER_NAME)
            it.remove(KEY_LAST_SCAN)
        }
    }

    private companion object {
        val KEY_FOLDER_URI = stringPreferencesKey("folder_uri")
        val KEY_FOLDER_NAME = stringPreferencesKey("folder_name")
        val KEY_LAST_SCAN = longPreferencesKey("last_scan")
        val KEY_SHOW_UNCOLLECTED = booleanPreferencesKey("show_uncollected_local")
    }
}
