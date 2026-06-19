package me.jbusdriver.modern.data.session

import me.jbusdriver.modern.core.http.BrowserSessionClient
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

interface ForumSessionClient : BrowserSessionClient

@Singleton
class DefaultForumSessionClient @Inject constructor(
    private val sessionManager: ForumSessionManager
) : ForumSessionClient {

    override suspend fun warmUp() {
        ensureSession()
    }

    override suspend fun fetchDocument(url: String): Document {
        ensureSession()
        return sessionManager.fetchDocument(url)
    }

    override suspend fun destroy() {
        sessionManager.destroy()
    }

    private suspend fun ensureSession() {
        if (sessionManager.isInitialized()) return
        sessionManager.ensureSession()
    }
}
