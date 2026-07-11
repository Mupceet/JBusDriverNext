package me.jbusdriver.modern.data.localvideo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.net.toUri

/** 扫描到的单个文件（与 Android 解耦，便于单测）。 */
data class ScannedFile(
    val name: String,
    val uri: String,
    val mime: String?,
    val size: Long,
    /**
     * 祖先子文件夹名，按"最近父文件夹优先"排序，不含用户选择的根目录名。
     * 仅包含根目录下方的子文件夹，index 0 始终是直接父文件夹。
     */
    val parentFolderNames: List<String> = emptyList(),
)

/** 视频文件枚举源。生产实现走 DocumentFile；测试用假实现。 */
interface LocalVideoFileSource {
    suspend fun listVideoFiles(): List<ScannedFile>
}

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "ts", "webm", "m4v", "mpg", "mpeg", "3gp", "rmvb",
)

/** 基于 SAF DocumentFile 的递归枚举实现。 */
class DocumentFileVideoFileSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderStore: LocalVideoFolderStore,
) : LocalVideoFileSource {

    override suspend fun listVideoFiles(): List<ScannedFile> = withContext(Dispatchers.IO) {
        val treeUriStr = folderStore.currentFolderUri() ?: return@withContext emptyList()
        val root = DocumentFile.fromTreeUri(context, treeUriStr.toUri()) ?: return@withContext emptyList()
        if (!root.canRead()) return@withContext emptyList()
        val out = mutableListOf<ScannedFile>()
        collectVideos(root, out, emptyList())
        out
    }

    private fun collectVideos(dir: DocumentFile, out: MutableList<ScannedFile>, ancestorNames: List<String>) {
        dir.listFiles().forEach { f ->
            when {
                f.isDirectory -> {
                    val name = f.name
                    val childAncestors = if (name != null) listOf(name) + ancestorNames else ancestorNames
                    collectVideos(f, out, childAncestors)
                }
                f.isFile && isVideo(f) -> {
                    val name = f.name ?: return@forEach
                    out += ScannedFile(
                        name = name,
                        uri = f.uri.toString(),
                        mime = f.type,
                        size = f.length(),
                        parentFolderNames = ancestorNames,
                    )
                }
            }
        }
    }

    private fun isVideo(f: DocumentFile): Boolean {
        f.type?.let { if (it.startsWith("video/")) return true }
        val ext = f.name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in VIDEO_EXTENSIONS
    }
}
