package me.jbusdriver.modern

import android.util.Log

/**
 * 职责：全局日志工具，封装 Android Log API
 *
 * 使用场景：全项目统一使用 KLog 替代 android.util.Log，便于后续切换日志实现
 * 线程：无限制，可在任意线程调用
 */
object KLog {
    private const val DEFAULT_TAG = "JBus"

    /** 调试级别日志 */
    fun d(msg: String, tag: String = DEFAULT_TAG) {
        Log.d(tag, msg)
    }

    /** 信息级别日志 */
    fun i(msg: String, tag: String = DEFAULT_TAG) {
        Log.i(tag, msg)
    }

    /** 警告级别日志 */
    fun w(msg: String, tag: String = DEFAULT_TAG) {
        Log.w(tag, msg)
    }

    /** 错误级别日志 */
    fun e(msg: String, tag: String = DEFAULT_TAG) {
        Log.e(tag, msg)
    }

    /** 错误级别日志，带异常堆栈 */
    fun e(msg: String, tr: Throwable, tag: String = DEFAULT_TAG) {
        Log.e(tag, msg, tr)
    }

    /**
     * 创建带固定 tag 的日志器，避免重复传递 tag
     *
     * @param tag 固定的日志标签
     * @return TagLogger 实例，后续调用无需再传 tag
     */
    fun t(tag: String): TagLogger = TagLogger(tag)
}

/**
 * 职责：绑定固定 tag 的日志器，减少重复参数
 *
 * 使用场景：通过 KLog.t("tagName") 获取，链式调用日志方法
 * 线程：无限制
 */
class TagLogger(private val tag: String) {
    fun d(msg: String) { Log.d(tag, msg) }
    fun i(msg: String) { Log.i(tag, msg) }
    fun w(msg: String) { Log.w(tag, msg) }
    fun e(msg: String) { Log.e(tag, msg) }
    fun e(msg: String, tr: Throwable) { Log.e(tag, msg, tr) }
}
