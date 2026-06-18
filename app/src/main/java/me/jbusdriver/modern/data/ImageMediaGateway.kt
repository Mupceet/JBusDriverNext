package me.jbusdriver.modern.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.R
import java.io.File
import javax.inject.Inject

interface ImageMediaGateway {
    suspend fun saveImageToGallery(imageUrl: String)
    suspend fun shareImage(imageUrl: String)
}

class AndroidImageMediaGateway @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ImageMediaGateway {

    override suspend fun saveImageToGallery(imageUrl: String) {
        val bitmap = getCachedBitmap(imageUrl)

        withContext(Dispatchers.IO) {
            val filename = "JBus_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/JBus"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create media store entry")

            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Failed to open media store output stream")
            outputStream.use { outputStream ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }
    }

    override suspend fun shareImage(imageUrl: String) {
        val bitmap = getCachedBitmap(imageUrl)

        val file = withContext(Dispatchers.IO) {
            val shareDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(shareDir, "share_${System.currentTimeMillis()}.jpg")
            file.outputStream()
                .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            file
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.getString(R.string.share_image)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private suspend fun getCachedBitmap(imageUrl: String) =
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            (result as? SuccessResult)?.drawable?.toBitmap()
                ?: throw IllegalStateException(context.getString(R.string.image_not_loaded))
        }
}
