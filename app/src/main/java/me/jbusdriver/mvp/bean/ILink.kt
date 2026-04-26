package me.jbusdriver.mvp.bean

import java.io.Serializable

interface ILink : ICollectCategory, Serializable {
    val link: String
}