package me.jbusdriver.modern.core.http

import android.content.Context
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface WebViewFactory {
    fun createWebView(): WebView
}

@Singleton
class AndroidWebViewFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WebViewFactory {
    override fun createWebView(): WebView = WebViewHelper.createWebView(context)
}
