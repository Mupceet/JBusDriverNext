package me.jbusdriver.modern.data.localvideo

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** 单个本地视频文件的删除结果。 */
enum class DeleteFileResult { SUCCESS, NOT_FOUND, FAILED }

/** SAF 文件删除抽象。 */
interface LocalVideoFileDeleter {
    suspend fun delete(uri: String): DeleteFileResult
}

/** 基于 DocumentsContract 的删除实现，凭 tree 持久权限直接删子文档。 */
class DocumentFileVideoFileDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalVideoFileDeleter {

    override suspend fun delete(uri: String): DeleteFileResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val deleted = DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
            when {
                deleted -> DeleteFileResult.SUCCESS
                fileExists(uri) -> DeleteFileResult.FAILED
                else -> DeleteFileResult.NOT_FOUND
            }
        } catch (_: FileNotFoundException) {
            DeleteFileResult.NOT_FOUND
        } catch (_: SecurityException) {
            DeleteFileResult.FAILED
        } catch (_: Exception) {
            DeleteFileResult.FAILED
        }
    }

    private fun fileExists(uri: String): Boolean = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.close()
        true
    } catch (_: Exception) {
        false
    }
}
