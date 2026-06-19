package me.jbusdriver.modern.data

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface CollectionDocumentGateway {
    suspend fun readText(documentUri: String): String?
    suspend fun writeText(documentUri: String, text: String)
}

@Singleton
class AndroidCollectionDocumentGateway @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CollectionDocumentGateway {
    override suspend fun readText(documentUri: String): String? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(documentUri.toUri())
            ?.bufferedReader()
            ?.use { it.readText() }
    }

    override suspend fun writeText(documentUri: String, text: String) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(documentUri.toUri())?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Unable to open document for writing")
        }
    }
}
