package me.jbusdriver.common

import android.util.Log

object KLog {
    private const val DEFAULT_TAG = "JBus"

    fun d(msg: String, tag: String = DEFAULT_TAG) {
        Log.d(tag, msg)
    }

    fun i(msg: String, tag: String = DEFAULT_TAG) {
        Log.i(tag, msg)
    }

    fun w(msg: String, tag: String = DEFAULT_TAG) {
        Log.w(tag, msg)
    }

    fun e(msg: String, tag: String = DEFAULT_TAG) {
        Log.e(tag, msg)
    }

    fun e(msg: String, tr: Throwable, tag: String = DEFAULT_TAG) {
        Log.e(tag, msg, tr)
    }

    fun t(tag: String): TagLogger = TagLogger(tag)
}

class TagLogger(private val tag: String) {
    fun d(msg: String) { Log.d(tag, msg) }
    fun i(msg: String) { Log.i(tag, msg) }
    fun w(msg: String) { Log.w(tag, msg) }
    fun e(msg: String) { Log.e(tag, msg) }
    fun e(msg: String, tr: Throwable) { Log.e(tag, msg, tr) }
}
