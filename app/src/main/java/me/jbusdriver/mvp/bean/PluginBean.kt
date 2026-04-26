package me.jbusdriver.mvp.bean

data class PluginBean(
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val desc: String,
    val eTag: String,
    val url: String
) : Comparable<PluginBean> {

    override operator fun compareTo(other: PluginBean) = this.versionCode - other.versionCode

}