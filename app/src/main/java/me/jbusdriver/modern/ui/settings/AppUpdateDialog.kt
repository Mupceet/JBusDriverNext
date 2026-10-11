package me.jbusdriver.modern.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.jbusdriver.BuildConfig
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.repository.APK_MIME_TYPE
import me.jbusdriver.modern.data.repository.AppUpdateInfo
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit

/**
 * 更新提示弹框：版本对比 + 更新说明（[AppUpdateInfo.releaseNotes] 为空时隐藏说明区块）。
 */
@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo,
    onDismiss: () -> Unit,
    onConfirmDownload: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateVersionRow(
                    label = stringResource(R.string.update_current_version),
                    version = BuildConfig.VERSION_NAME
                )
                UpdateVersionRow(
                    label = stringResource(R.string.update_latest_version),
                    version = update.versionName,
                    emphasized = true
                )
                if (update.fileSizeBytes > 0) {
                    Text(
                        stringResource(
                            R.string.update_apk_size,
                            android.text.format.Formatter.formatFileSize(context, update.fileSizeBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (update.releaseNotes.isNotBlank()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.update_notes_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            update.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmDownload) {
                Text(stringResource(R.string.update_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}

@Composable
private fun UpdateVersionRow(label: String, version: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "v$version",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else null,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * 通过系统 [DownloadManager] 下载 APK 到公共下载目录（系统通知展示进度）。
 * `getUriForDownloadedFile` 返回 content URI，无需 FileProvider。
 *
 * 生命周期为进程级（自带协程作用域 + SharedPreferences 记录进行中的任务），
 * 不随设置页销毁而失效：下载完成检测采用轮询，App 内任意页面、退到桌面
 * 期间完成都会拉起安装器；进程被杀后重启由 [resumePending] 恢复监听。
 */
@Singleton
class AppUpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 开始下载（幂等：已有进行中的任务时忽略重复触发）。
     * 未授权「安装未知应用」时先引导授权，让用户在下载期间完成授权。
     */
    fun start(update: AppUpdateInfo) {
        if (prefs.contains(KEY_DOWNLOAD_ID)) return
        if (!canRequestInstall()) {
            toast(R.string.update_install_permission_hint, Toast.LENGTH_LONG)
            launchInstallPermissionSettings()
        }
        val downloadId = try {
            enqueue(update)
        } catch (e: Exception) {
            KLog.t(TAG).e("enqueue update download failed", e)
            toast(R.string.update_download_failed)
            return
        }
        prefs.edit { putLong(KEY_DOWNLOAD_ID, downloadId) }
        toast(R.string.update_download_started)
        poll(downloadId)
    }

    /** App 启动时恢复未完成的下载监听（进程可能在下载期间被杀）。 */
    fun resumePending() {
        val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId > 0) poll(downloadId)
    }

    private fun poll(downloadId: Long) {
        scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS.milliseconds)
                when (statusOf(downloadId)) {
                    UpdateDownloadStatus.SUCCESSFUL -> {
                        if (!openDownloadedApk(downloadId)) {
                            toast(R.string.update_download_failed)
                        }
                        clearPending()
                        return@launch
                    }
                    UpdateDownloadStatus.FAILED -> {
                        toast(R.string.update_download_failed)
                        clearPending()
                        return@launch
                    }
                    UpdateDownloadStatus.RUNNING -> Unit
                }
            }
        }
    }

    private fun clearPending() {
        prefs.edit { remove(KEY_DOWNLOAD_ID) }
    }

    private fun toast(resId: Int, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, resId, length).show()
    }

    /** @return 本次下载的系统任务 id。 */
    private fun enqueue(update: AppUpdateInfo): Long {
        val request = DownloadManager.Request(update.downloadUrl.toUri())
            .setMimeType(APK_MIME_TYPE)
            .setTitle(update.fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, update.fileName)
        return downloadManager().enqueue(request)
    }

    /** 查询下载任务状态（记录消失视为失败，例如被用户清理）。 */
    private fun statusOf(downloadId: Long): UpdateDownloadStatus {
        downloadManager().query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return UpdateDownloadStatus.FAILED
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> UpdateDownloadStatus.SUCCESSFUL
                DownloadManager.STATUS_FAILED -> UpdateDownloadStatus.FAILED
                else -> UpdateDownloadStatus.RUNNING
            }
        }
    }

    /** 拉起系统安装器；下载记录不存在或未完成时返回 false。 */
    private fun openDownloadedApk(downloadId: Long): Boolean {
        val uri = downloadManager().getUriForDownloadedFile(downloadId) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Android 8+ 需要用户授予本应用「安装未知应用」权限。 */
    private fun canRequestInstall(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    private fun launchInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun downloadManager(): DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        private const val TAG = "AppUpdateDownloader"
        private const val PREFS_NAME = "app_update_download"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val POLL_INTERVAL_MS = 1_000L
    }
}

/** 下载任务状态。 */
enum class UpdateDownloadStatus { RUNNING, SUCCESSFUL, FAILED }
