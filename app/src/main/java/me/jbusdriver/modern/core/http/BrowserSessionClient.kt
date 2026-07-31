package me.jbusdriver.modern.core.http

import org.jsoup.nodes.Document

/**
 * Shared browser-engine (WebView) session for the target site.
 *
 * The site gates HTML pages behind `/doc/driver-verify`, which only a real browser engine
 * can pass — plain OkHttp (a non-browser) is redirected there. This session keeps a hidden
 * WebView alive that has already passed the gate,
 * and is used by BOTH the movie/list/detail pipeline and the forum pipeline to fetch pages
 * and ajax fragments the OkHttp client cannot reach on its own.
 */
interface BrowserSessionClient {
    /** Ensure the WebView session is alive and has passed the site's verify gate. */
    suspend fun warmUp()

    /** Fetch a page URL through the WebView session and return its parsed [Document]. */
    suspend fun fetchDocument(url: String): Document

    /** Fetch an ajax URL via a same-origin XHR executed inside the WebView session. */
    suspend fun fetchAjaxDocument(url: String, referer: String): Document

    /** Tear down the WebView (typically when the host activity is finishing). */
    suspend fun destroy()
}
