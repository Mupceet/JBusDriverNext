package me.jbusdriver.modern.core.http

import org.jsoup.nodes.Document

interface BrowserSessionClient {
    suspend fun warmUp()
    suspend fun fetchDocument(url: String): Document
    suspend fun destroy()
}
