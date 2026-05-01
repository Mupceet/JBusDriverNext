package me.jbusdriver.modern.core.http

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException

/**
 * OkHttp 日志拦截器，打印请求和响应的详细信息。
 *
 * 使用场景：仅在 DEBUG 构建中通过 NetClient 添加，用于开发阶段调试网络请求。
 *
 * 线程：由 OkHttp 调度器调用，通常在后台线程。
 *
 * @param tag 日志标签，为空时使用默认 "OkHttpUtils"
 * @param showResponse 是否打印响应体内容
 */
class LoggerInterceptor(
    tag: String = TAG,
    private val showResponse: Boolean = false
) : Interceptor {

    private val tag = tag.ifEmpty { TAG }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        logRequest(request)
        val response = chain.proceed(request)
        return logResponse(response)
    }

    private fun logRequest(request: Request) {
        try {
            Log.e(tag, "========request'log=======")
            Log.e(tag, "method : ${request.method}")
            Log.e(tag, "url : ${request.url}")
            request.headers.let { if (it.size > 0) Log.e(tag, "headers : $it") }
            request.body?.let { body ->
                body.contentType()?.let { ct ->
                    Log.e(tag, "requestBody's contentType : $ct")
                    if (ct.isText()) {
                        Log.e(tag, "requestBody's content : ${bodyToString(request)}")
                    } else {
                        Log.e(tag, "requestBody's content :  maybe [file part] , too large too print , ignored!")
                    }
                }
            }
            Log.e(tag, "========request'log=======end")
        } catch (_: Exception) {
        }
    }

    private fun logResponse(response: Response): Response {
        try {
            Log.e(tag, "========response'log=======")
            val clone = response.newBuilder().build()
            Log.e(tag, "url : ${clone.request.url}")
            Log.e(tag, "code : ${clone.code}")
            Log.e(tag, "protocol : ${clone.protocol}")
            clone.message.takeIf { it.isNotEmpty() }?.let { Log.e(tag, "message : $it") }

            if (showResponse) {
                clone.body?.let { body ->
                    body.contentType()?.let { ct ->
                        Log.e(tag, "responseBody's contentType : $ct")
                        if (ct.isText()) {
                            val resp = body.string()
                            Log.e(tag, "responseBody's content : $resp")
                            return response.newBuilder()
                                .body(ResponseBody.create(ct, resp))
                                .build()
                        } else {
                            Log.e(tag, "responseBody's content :  maybe [file part] , too large too print , ignored!")
                        }
                    }
                }
            }
            Log.e(tag, "========response'log=======end")
        } catch (_: Exception) {
        }
        return response
    }

    private fun MediaType.isText(): Boolean =
        type == "text" || subtype in setOf("json", "xml", "html", "webviewhtml")

    private fun bodyToString(request: Request): String = try {
        val buffer = Buffer()
        request.newBuilder().build().body?.writeTo(buffer)
        buffer.readUtf8()
    } catch (_: IOException) {
        "something error when show requestBody."
    }

    companion object {
        const val TAG = "OkHttpUtils"
    }
}
